class BackendHealth {
  const BackendHealth({required this.ok, this.httpStatus, this.message, this.checkedAt});

  final bool ok;
  final int? httpStatus;
  final String? message;
  final DateTime? checkedAt;

  bool get isUnknown => checkedAt == null;

  factory BackendHealth.fromMap(Map<Object?, Object?>? map) => BackendHealth(
    ok: map?['ok'] as bool? ?? false,
    httpStatus: (map?['httpStatus'] as num?)?.toInt(),
    message: map?['message'] as String?,
    checkedAt: _date(map?['checkedAt']),
  );
}

class GatewayStatus {
  const GatewayStatus({
    required this.gatewayEnabled,
    required this.backendConfigured,
    required this.notificationAccess,
    required this.listenerConnected,
    required this.monitoredCount,
    required this.smsEnabled,
    required this.smsPermission,
    required this.smsReceiverEnabled,
    required this.smsAllowlistCount,
    required this.ignoringBatteryOptimizations,
    required this.networkOnline,
    required this.backend,
    required this.heartbeat,
    this.filteredCounts = const {},
    this.lastNotificationAt,
    this.lastSmsReceivedAt,
    this.lastDeliveredAt,
  });

  final bool gatewayEnabled;
  final bool backendConfigured;

  // Notification module
  final bool notificationAccess;
  final bool listenerConnected;
  final int monitoredCount;
  final DateTime? lastNotificationAt;

  // SMS module
  final bool smsEnabled;
  final bool smsPermission;
  final bool smsReceiverEnabled;
  final int smsAllowlistCount;
  final DateTime? lastSmsReceivedAt;

  final bool ignoringBatteryOptimizations;
  final bool networkOnline;
  final DateTime? lastDeliveredAt;

  /// Dropped-by-filter counts (`otp`, `not_approved`, …). Content is never kept.
  final Map<String, int> filteredCounts;
  final BackendHealth backend;
  final BackendHealth heartbeat;

  factory GatewayStatus.fromMap(Map<Object?, Object?> map) {
    bool flag(String key) => map[key] as bool? ?? false;
    int count(String key) => (map[key] as num?)?.toInt() ?? 0;
    return GatewayStatus(
      gatewayEnabled: flag('gatewayEnabled'),
      backendConfigured: flag('backendConfigured'),
      notificationAccess: flag('notificationAccess'),
      listenerConnected: flag('listenerConnected'),
      monitoredCount: count('monitoredCount'),
      lastNotificationAt: _date(map['lastNotificationAt']),
      smsEnabled: flag('smsEnabled'),
      smsPermission: flag('smsPermission'),
      smsReceiverEnabled: flag('smsReceiverEnabled'),
      smsAllowlistCount: count('smsAllowlistCount'),
      lastSmsReceivedAt: _date(map['lastSmsReceivedAt']),
      ignoringBatteryOptimizations: flag('ignoringBatteryOptimizations'),
      networkOnline: flag('networkOnline'),
      lastDeliveredAt: _date(map['lastDeliveredAt']),
      filteredCounts: {
        for (final e in (map['filteredCounts'] as Map<Object?, Object?>? ?? const {}).entries)
          e.key as String: (e.value as num).toInt(),
      },
      backend: BackendHealth.fromMap(map['backend'] as Map<Object?, Object?>?),
      heartbeat: BackendHealth.fromMap(map['heartbeat'] as Map<Object?, Object?>?),
    );
  }
}

DateTime? _date(Object? epochMs) =>
    epochMs is num ? DateTime.fromMillisecondsSinceEpoch(epochMs.toInt()) : null;
