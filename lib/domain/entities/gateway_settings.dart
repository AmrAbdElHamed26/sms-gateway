class GatewaySettings {
  const GatewaySettings({
    required this.backendUrl,
    required this.deviceId,
    required this.secretConfigured,
    required this.gatewayEnabled,
    required this.monitoredPackages,
    required this.smsEnabled,
    required this.smsAllowlist,
    required this.retentionDays,
    this.installId = '',
  });

  final String backendUrl;
  final String deviceId;

  /// Random per-install ID; sequences are gap-checked per (deviceId, installId).
  final String installId;

  /// The secret itself is write-only; the UI only ever learns whether one is set.
  final bool secretConfigured;
  final bool gatewayEnabled;
  final List<String> monitoredPackages;
  final bool smsEnabled;
  final List<String> smsAllowlist;
  final int retentionDays;

  factory GatewaySettings.fromMap(Map<Object?, Object?> map) => GatewaySettings(
    backendUrl: map['backendUrl'] as String? ?? '',
    deviceId: map['deviceId'] as String? ?? '',
    installId: map['installId'] as String? ?? '',
    secretConfigured: map['secretConfigured'] as bool? ?? false,
    gatewayEnabled: map['gatewayEnabled'] as bool? ?? false,
    monitoredPackages: (map['monitoredPackages'] as List<Object?>? ?? const []).cast<String>(),
    smsEnabled: map['smsEnabled'] as bool? ?? false,
    smsAllowlist: (map['smsAllowlist'] as List<Object?>? ?? const []).cast<String>(),
    retentionDays: (map['retentionDays'] as num?)?.toInt() ?? 30,
  );
}

/// A settings change. [secret] null or empty keeps the stored secret.
/// Monitored apps are saved separately, from the Apps tab.
class SettingsUpdate {
  const SettingsUpdate({
    required this.backendUrl,
    required this.deviceId,
    required this.gatewayEnabled,
    required this.smsEnabled,
    required this.smsAllowlist,
    required this.retentionDays,
    this.secret,
  });

  final String backendUrl;
  final String deviceId;
  final bool gatewayEnabled;
  final bool smsEnabled;
  final List<String> smsAllowlist;
  final int retentionDays;
  final String? secret;

  Map<String, Object?> toMap() => {
    'backendUrl': backendUrl.trim(),
    'deviceId': deviceId.trim(),
    'gatewayEnabled': gatewayEnabled,
    'smsEnabled': smsEnabled,
    'smsAllowlist': smsAllowlist,
    'retentionDays': retentionDays,
    if (secret != null && secret!.isNotEmpty) 'secret': secret,
  };
}
