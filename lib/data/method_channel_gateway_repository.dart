import 'package:flutter/services.dart';

import '../domain/entities/connection_test_result.dart';
import '../domain/entities/event_stats.dart';
import '../domain/entities/gateway_event.dart';
import '../domain/entities/gateway_settings.dart';
import '../domain/entities/gateway_status.dart';
import '../domain/entities/heartbeat_log.dart';
import '../domain/entities/installed_app.dart';
import '../domain/repositories/gateway_repository.dart';

/// [GatewayRepository] backed by the native `GatewayChannel` (Kotlin).
class MethodChannelGatewayRepository implements GatewayRepository {
  MethodChannelGatewayRepository({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(channelName);

  static const channelName = 'dev.smsgateway.app/gateway';

  final MethodChannel _channel;

  @override
  Future<GatewayStatus> status() async => GatewayStatus.fromMap(await _map('getStatus'));

  @override
  Future<EventStats> stats() async => EventStats.fromMap(await _map('getStats'));

  @override
  Future<List<GatewayEvent>> events({
    int limit = 50,
    int offset = 0,
    DeliveryStatus? status,
    bool moneyOnly = false,
  }) async {
    final list = await _invoke<List<Object?>>('getEvents', {
      'limit': limit,
      'offset': offset,
      if (status != null) 'status': status.name,
      if (moneyOnly) 'moneyOnly': true,
    });
    return (list ?? const []).map((e) => GatewayEvent.fromMap(e! as Map<Object?, Object?>)).toList();
  }

  @override
  Future<GatewaySettings> settings() async => GatewaySettings.fromMap(await _map('getSettings'));

  @override
  Future<GatewaySettings> saveSettings(SettingsUpdate update) async =>
      GatewaySettings.fromMap(await _map('saveSettings', update.toMap()));

  @override
  Future<void> clearSecret() => _invoke<void>('clearSecret');

  @override
  Future<List<InstalledApp>> installedApps() async {
    final list = await _invoke<List<Object?>>('getInstalledApps');
    return (list ?? const []).map((e) => InstalledApp.fromMap(e! as Map<Object?, Object?>)).toList();
  }

  @override
  Future<List<String>> setMonitoredPackages(Set<String> packages) async {
    final list = await _invoke<List<Object?>>('setMonitoredPackages', {'packages': packages.toList()});
    return (list ?? const []).cast<String>();
  }

  @override
  Future<void> openNotificationAccessSettings() => _invoke<void>('openNotificationAccessSettings');

  @override
  Future<bool> requestSmsPermission() async => await _invoke<bool>('requestSmsPermission') ?? false;

  @override
  Future<void> openBatteryOptimizationSettings() => _invoke<void>('openBatteryOptimizationSettings');

  @override
  Future<ConnectionTestResult> testConnection() async =>
      ConnectionTestResult.fromMap(await _map('testConnection'));

  @override
  Future<String> sendTestWebhook() async => await _invoke<String>('sendTestWebhook') ?? 'unknown';

  @override
  Future<String> simulateEvent({
    required String sourceType,
    required String body,
    String? packageName,
    String? title,
    String? sender,
    bool localOnly = true,
  }) async =>
      await _invoke<String>('simulateEvent', {
        'sourceType': sourceType,
        'body': body,
        'packageName': packageName,
        'title': title,
        'sender': sender,
        'localOnly': localOnly,
      }) ??
      'unknown';

  @override
  Future<ConnectionTestResult> sendHeartbeat() async =>
      ConnectionTestResult.fromMap(await _map('sendHeartbeat'));

  @override
  Future<HeartbeatLog> heartbeats({int limit = 100}) async =>
      HeartbeatLog.fromMap(await _map('getHeartbeats', {'limit': limit}));

  @override
  Future<int> retryPending() async => await _invoke<int>('retryPending') ?? 0;

  Future<Map<Object?, Object?>> _map(String method, [Object? args]) async =>
      await _invoke<Map<Object?, Object?>>(method, args) ?? const {};

  Future<T?> _invoke<T>(String method, [Object? args]) async {
    try {
      return await _channel.invokeMethod<T>(method, args);
    } on PlatformException catch (e) {
      throw GatewayException(e.message ?? e.code);
    } on MissingPluginException {
      throw const GatewayException('Native gateway unavailable (Android only)');
    }
  }
}
