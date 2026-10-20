import 'entities/event_stats.dart';
import 'entities/gateway_status.dart';

enum WarningAction { openNotificationAccess, openApps, openSettings, grantSms, openBattery }

class GatewayWarning {
  const GatewayWarning(this.message, {this.action, this.critical = true});

  final String message;
  final WarningAction? action;

  /// Critical = events are being missed or can't be sent right now.
  final bool critical;
}

/// Everything the operator must fix, most severe first. Pure, so it is unit-tested.
List<GatewayWarning> gatewayWarnings(GatewayStatus s, EventStats stats, {DateTime? now}) {
  final warnings = <GatewayWarning>[];
  if (!s.backendConfigured) {
    warnings.add(
      const GatewayWarning('Backend URL or webhook secret not set', action: WarningAction.openSettings),
    );
  }
  if (!s.gatewayEnabled) {
    warnings.add(
      const GatewayWarning('Gateway is off: nothing is captured', action: WarningAction.openSettings),
    );
  }
  if (!s.notificationAccess) {
    warnings.add(
      const GatewayWarning(
        'Notification Access is off: app notifications are not captured',
        action: WarningAction.openNotificationAccess,
      ),
    );
  } else if (!s.listenerConnected) {
    warnings.add(
      const GatewayWarning(
        'Notification listener is disconnected; toggle Notification Access off and on',
        action: WarningAction.openNotificationAccess,
      ),
    );
  }
  if (s.monitoredCount == 0) {
    warnings.add(const GatewayWarning('No apps selected for monitoring', action: WarningAction.openApps));
  }
  if (s.smsEnabled && !s.smsPermission) {
    warnings.add(
      const GatewayWarning(
        'SMS monitoring is on but SMS permission is denied',
        action: WarningAction.grantSms,
      ),
    );
  }
  if (s.smsEnabled && s.smsAllowlistCount == 0) {
    warnings.add(
      const GatewayWarning(
        'SMS monitoring is on but no senders are approved',
        action: WarningAction.openSettings,
      ),
    );
  }
  if (stats.moneyDelayed(now: now) || stats.moneyFailed > 0) {
    warnings.add(const GatewayWarning('Payment events are not reaching the backend'));
  }
  if (!s.ignoringBatteryOptimizations) {
    warnings.add(
      const GatewayWarning(
        'Battery optimisation may delay delivery',
        action: WarningAction.openBattery,
        critical: false,
      ),
    );
  }
  if (!s.networkOnline) {
    warnings.add(const GatewayWarning('Offline: events are queued and sent on reconnect', critical: false));
  }
  return warnings;
}
