import '../entities/connection_test_result.dart';
import '../entities/event_stats.dart';
import '../entities/gateway_event.dart';
import '../entities/gateway_settings.dart';
import '../entities/gateway_status.dart';
import '../entities/heartbeat_log.dart';
import '../entities/installed_app.dart';

/// Thrown for any gateway failure; [message] is safe to show in the UI.
class GatewayException implements Exception {
  const GatewayException(this.message);
  final String message;

  @override
  String toString() => message;
}

/// The dashboard's view of the native gateway. Capture and delivery run
/// natively and never depend on this interface.
abstract interface class GatewayRepository {
  Future<GatewayStatus> status();
  Future<EventStats> stats();
  Future<List<GatewayEvent>> events({
    int limit = 50,
    int offset = 0,
    DeliveryStatus? status,
    bool moneyOnly = false,
  });

  Future<GatewaySettings> settings();
  Future<GatewaySettings> saveSettings(SettingsUpdate update);
  Future<void> clearSecret();

  /// Launchable apps with their monitored flag, plus monitored-but-uninstalled packages.
  Future<List<InstalledApp>> installedApps();
  Future<List<String>> setMonitoredPackages(Set<String> packages);

  Future<void> openNotificationAccessSettings();
  Future<bool> requestSmsPermission();
  Future<void> openBatteryOptimizationSettings();

  Future<ConnectionTestResult> testConnection();

  /// Queues a synthetic event through delivery (filters bypassed). Returns the ingest result.
  Future<String> sendTestWebhook();

  /// Local test mode: runs a synthetic event through the real filters and queue.
  /// [localOnly] keeps it on the phone; otherwise it is sent as `simulated_*`.
  Future<String> simulateEvent({
    required String sourceType,
    required String body,
    String? packageName,
    String? title,
    String? sender,
    bool localOnly = true,
  });

  /// Sends the completeness heartbeat (last sequence, pending counts) now.
  Future<ConnectionTestResult> sendHeartbeat();

  /// The heartbeat schedule and the last [limit] attempts, newest first.
  Future<HeartbeatLog> heartbeats({int limit = 100});

  /// Makes every pending and failed event due now. Returns how many.
  Future<int> retryPending();
}
