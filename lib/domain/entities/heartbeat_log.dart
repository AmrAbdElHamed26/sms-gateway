/// One heartbeat attempt, as logged on the phone.
class HeartbeatRecord {
  const HeartbeatRecord({
    required this.id,
    required this.trigger,
    required this.dueAt,
    required this.sentAt,
    required this.ok,
    required this.latencyMs,
    this.httpStatus,
    this.error,
  });

  final int id;

  /// `alarm` (scheduled) or `manual` (Send heartbeat button).
  final String trigger;

  /// When it was scheduled to go out.
  final DateTime dueAt;
  final DateTime sentAt;
  final bool ok;
  final int latencyMs;
  final int? httpStatus;
  final String? error;

  /// How long Android (Doze, battery managers) held the beat past its schedule.
  Duration get delay => sentAt.difference(dueAt);

  bool get isManual => trigger == 'manual';

  String get result => httpStatus != null ? 'HTTP $httpStatus' : (error ?? 'network error');

  factory HeartbeatRecord.fromMap(Map<Object?, Object?> map) => HeartbeatRecord(
    id: (map['id'] as num).toInt(),
    trigger: map['trigger'] as String? ?? 'alarm',
    dueAt: DateTime.fromMillisecondsSinceEpoch((map['dueAt'] as num).toInt()),
    sentAt: DateTime.fromMillisecondsSinceEpoch((map['sentAt'] as num).toInt()),
    ok: map['ok'] as bool? ?? false,
    latencyMs: (map['latencyMs'] as num?)?.toInt() ?? 0,
    httpStatus: (map['httpStatus'] as num?)?.toInt(),
    error: map['error'] as String?,
  );
}

/// The heartbeat schedule plus its recent history, newest first.
class HeartbeatLog {
  const HeartbeatLog({
    this.interval = const Duration(minutes: 2),
    this.nextAt,
    this.exactAlarms = true,
    this.records = const [],
  });

  /// The backend raises `heartbeat_missing` after this long without a beat.
  static const backendAlertAfter = Duration(minutes: 5);

  final Duration interval;
  final DateTime? nextAt;

  /// False when Android revoked exact alarms; beats then drift inside Doze.
  final bool exactAlarms;
  final List<HeartbeatRecord> records;

  /// Time since the previous successful beat, for each record (null for the oldest).
  /// A gap over [backendAlertAfter] is what the backend alerts on.
  Duration? gapBefore(int index) {
    for (var i = index + 1; i < records.length; i++) {
      if (records[i].ok) return records[index].sentAt.difference(records[i].sentAt);
    }
    return null;
  }

  factory HeartbeatLog.fromMap(Map<Object?, Object?> map) => HeartbeatLog(
    interval: Duration(milliseconds: (map['intervalMs'] as num?)?.toInt() ?? 120000),
    nextAt: map['nextAt'] is num ? DateTime.fromMillisecondsSinceEpoch((map['nextAt'] as num).toInt()) : null,
    exactAlarms: map['exactAlarms'] as bool? ?? true,
    records: [
      for (final r in map['records'] as List<Object?>? ?? const [])
        HeartbeatRecord.fromMap(r! as Map<Object?, Object?>),
    ],
  );
}
