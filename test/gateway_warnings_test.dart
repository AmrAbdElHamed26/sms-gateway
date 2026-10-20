import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/domain/entities/event_stats.dart';
import 'package:sms_gateway/domain/entities/gateway_status.dart';
import 'package:sms_gateway/domain/gateway_warnings.dart';

GatewayStatus _status({
  bool access = true,
  bool connected = true,
  int monitored = 2,
  bool smsEnabled = false,
  bool smsPermission = false,
  int allowlist = 0,
  bool battery = true,
}) => GatewayStatus.fromMap({
  'gatewayEnabled': true,
  'backendConfigured': true,
  'notificationAccess': access,
  'listenerConnected': connected,
  'monitoredCount': monitored,
  'smsEnabled': smsEnabled,
  'smsPermission': smsPermission,
  'smsAllowlistCount': allowlist,
  'ignoringBatteryOptimizations': battery,
  'networkOnline': true,
});

void main() {
  test('a fully configured gateway has no warnings', () {
    expect(gatewayWarnings(_status(), const EventStats()), isEmpty);
  });

  test('disabled notification access is a critical warning with an action', () {
    final w = gatewayWarnings(_status(access: false), const EventStats()).single;
    expect(w.critical, isTrue);
    expect(w.action, WarningAction.openNotificationAccess);
  });

  test('a disconnected listener is flagged when access is granted', () {
    expect(
      gatewayWarnings(_status(connected: false), const EventStats()).single.message,
      contains('disconnected'),
    );
  });

  test('no monitored apps is flagged', () {
    expect(gatewayWarnings(_status(monitored: 0), const EventStats()).single.action, WarningAction.openApps);
  });

  test('sms module problems only matter when the module is on', () {
    expect(gatewayWarnings(_status(smsEnabled: false), const EventStats()), isEmpty);
    final actions = gatewayWarnings(_status(smsEnabled: true), const EventStats()).map((w) => w.action);
    expect(actions, containsAll([WarningAction.grantSms, WarningAction.openSettings]));
  });

  test('stuck money events and battery restrictions are reported', () {
    final now = DateTime(2026, 10, 14, 12);
    final stats = EventStats(oldestMoneyPendingAt: now.subtract(const Duration(minutes: 10)));
    final warnings = gatewayWarnings(_status(battery: false), stats, now: now);
    expect(warnings.map((w) => w.critical), [true, false]);
  });
}
