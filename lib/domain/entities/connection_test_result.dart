class ConnectionTestResult {
  const ConnectionTestResult({required this.ok, required this.message, this.httpStatus, this.latencyMs});

  final bool ok;
  final String message;
  final int? httpStatus;
  final int? latencyMs;

  factory ConnectionTestResult.fromMap(Map<Object?, Object?> map) => ConnectionTestResult(
    ok: map['ok'] as bool? ?? false,
    message: map['message'] as String? ?? '',
    httpStatus: (map['httpStatus'] as num?)?.toInt(),
    latencyMs: (map['latencyMs'] as num?)?.toInt(),
  );
}
