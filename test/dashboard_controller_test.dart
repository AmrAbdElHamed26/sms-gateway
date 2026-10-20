import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/domain/entities/connection_test_result.dart';
import 'package:sms_gateway/domain/entities/event_stats.dart';
import 'package:sms_gateway/domain/entities/gateway_event.dart';
import 'package:sms_gateway/domain/entities/gateway_settings.dart';
import 'package:sms_gateway/domain/entities/gateway_status.dart';
import 'package:sms_gateway/domain/entities/heartbeat_log.dart';
import 'package:sms_gateway/domain/entities/installed_app.dart';
import 'package:sms_gateway/domain/repositories/gateway_repository.dart';
import 'package:sms_gateway/presentation/dashboard_controller.dart';

class FakeRepository implements GatewayRepository {
  List<GatewayEvent> stored = [];
  Set<String> monitored = {'com.bank.app'};
  bool failStatus = false;
  bool failMonitored = false;

  @override
  Future<GatewayStatus> status() async {
    if (failStatus) throw const GatewayException('channel down');
    return GatewayStatus.fromMap({
      'notificationAccess': true,
      'listenerConnected': true,
      'monitoredCount': monitored.length,
    });
  }

  @override
  Future<EventStats> stats() async => EventStats(total: stored.length, pending: stored.length);

  @override
  Future<GatewaySettings> settings() async =>
      GatewaySettings.fromMap({'deviceId': 'gw-1', 'monitoredPackages': monitored.toList()});

  @override
  Future<List<GatewayEvent>> events({
    int limit = 50,
    int offset = 0,
    DeliveryStatus? status,
    bool moneyOnly = false,
  }) async => stored
      .where((e) => status == null || e.status == status)
      .where((e) => !moneyOnly || e.moneyHint)
      .skip(offset)
      .take(limit)
      .toList();

  @override
  Future<List<InstalledApp>> installedApps() async => [
    InstalledApp(packageName: 'com.bank.app', label: 'Bank', monitored: monitored.contains('com.bank.app')),
    InstalledApp(packageName: 'com.chat.app', label: 'Chat', monitored: monitored.contains('com.chat.app')),
  ];

  @override
  Future<List<String>> setMonitoredPackages(Set<String> packages) async {
    if (failMonitored) throw const GatewayException('write failed');
    monitored = packages;
    return packages.toList();
  }

  @override
  Future<String> simulateEvent({
    required String sourceType,
    required String body,
    String? packageName,
    String? title,
    String? sender,
    bool localOnly = true,
  }) async {
    stored = [_event(stored.length + 1, DeliveryStatus.simulated), ...stored];
    return 'simulated';
  }

  @override
  Future<GatewaySettings> saveSettings(SettingsUpdate update) => throw UnimplementedError();
  @override
  Future<void> clearSecret() async {}
  @override
  Future<void> openNotificationAccessSettings() async {}
  @override
  Future<bool> requestSmsPermission() async => true;
  @override
  Future<void> openBatteryOptimizationSettings() async {}
  @override
  Future<ConnectionTestResult> testConnection() => throw UnimplementedError();
  @override
  Future<String> sendTestWebhook() async => 'stored';
  @override
  Future<ConnectionTestResult> sendHeartbeat() async =>
      const ConnectionTestResult(ok: true, message: 'HTTP 200');
  @override
  Future<HeartbeatLog> heartbeats({int limit = 100}) async => const HeartbeatLog();
  @override
  Future<int> retryPending() async => 0;
}

GatewayEvent _event(int id, DeliveryStatus status) => GatewayEvent(
  id: id,
  sequence: id,
  eventId: 'e-$id',
  sourceType: 'notification',
  sourcePackage: 'com.bank.app',
  body: 'b',
  receivedAt: DateTime(2026, 10, 14),
  source: 'android_notification',
  status: status,
  attempts: 0,
  moneyHint: id % 3 == 0,
);

void main() {
  late FakeRepository repo;
  late DashboardController controller;

  setUp(() {
    repo = FakeRepository()
      ..stored = [
        for (var i = 1; i <= 60; i++) _event(i, i.isEven ? DeliveryStatus.delivered : DeliveryStatus.pending),
      ];
    controller = DashboardController(repo);
  });

  tearDown(() => controller.dispose());

  test('refresh loads status, stats, settings and first page', () async {
    await controller.refresh();
    expect(controller.status!.notificationAccess, isTrue);
    expect(controller.stats.total, 60);
    expect(controller.settings!.monitoredPackages, ['com.bank.app']);
    expect(controller.events, hasLength(DashboardController.pageSize));
    expect(controller.hasMore, isTrue);
  });

  test('loadMore appends the next page', () async {
    await controller.refresh();
    await controller.loadMore();
    expect(controller.events, hasLength(60));
    expect(controller.hasMore, isFalse);
  });

  test('status and money filters restrict events', () async {
    await controller.setFilter(DeliveryStatus.delivered);
    expect(controller.events, hasLength(30));
    await controller.setFilter(null);
    await controller.setMoneyOnly(true);
    expect(controller.events.every((e) => e.moneyHint), isTrue);
  });

  test('toggling an app updates the monitored set', () async {
    await controller.loadApps();
    expect(controller.labelFor('com.bank.app'), 'Bank');
    await controller.setMonitored('com.chat.app', true);
    expect(repo.monitored, {'com.bank.app', 'com.chat.app'});
    expect(controller.status!.monitoredCount, 2);
  });

  test('a failed app toggle is reverted', () async {
    await controller.loadApps();
    repo.failMonitored = true;
    await controller.setMonitored('com.chat.app', true);
    expect(controller.apps!.firstWhere((a) => a.packageName == 'com.chat.app').monitored, isFalse);
    expect(controller.error, 'write failed');
  });

  test('warnings come from the latest status', () async {
    repo.monitored = {};
    await controller.refresh();
    expect(controller.warnings.map((w) => w.message), contains('No apps selected for monitoring'));
  });

  test('errors are surfaced, not thrown', () async {
    repo.failStatus = true;
    await controller.refresh();
    expect(controller.error, 'channel down');
    expect(controller.loading, isFalse);
  });

  test('run refreshes after an action', () async {
    await controller.refresh();
    await controller.run((r) => r.simulateEvent(sourceType: 'notification', body: 'b'));
    expect(controller.stats.total, 61);
  });
}
