enum DeliveryStatus {
  pending,
  delivered,
  failed,

  /// Local test-mode event: filtered and queued on the phone, never sent.
  simulated;

  static DeliveryStatus parse(String? value) =>
      DeliveryStatus.values.firstWhere((s) => s.name == value, orElse: () => DeliveryStatus.pending);
}

/// One captured notification or SMS, as queued on the phone.
class GatewayEvent {
  const GatewayEvent({
    required this.id,
    required this.eventId,
    required this.sourceType,
    required this.body,
    required this.receivedAt,
    required this.source,
    required this.status,
    required this.attempts,
    this.sequence,
    this.sourcePackage,
    this.sender,
    this.title,
    this.moneyHint = false,
    this.nextAttemptAt,
    this.lastError,
    this.lastHttpStatus,
    this.deliveredAt,
  });

  final int id;

  /// Per-install, gap-free number sent to the backend. Null for simulated events.
  final int? sequence;
  final String eventId;

  /// `notification` or `sms`.
  final String sourceType;
  final String? sourcePackage;
  final String? sender;
  final String? title;

  /// Empty once delivered: the phone wipes content the backend already holds.
  final String body;
  final DateTime receivedAt;
  final String source;
  final DeliveryStatus status;

  /// The gateway's generic "mentions money" hint. The backend decides what it really is.
  final bool moneyHint;
  final int attempts;
  final DateTime? nextAttemptAt;
  final String? lastError;
  final int? lastHttpStatus;
  final DateTime? deliveredAt;

  bool get isNotification => sourceType == 'notification';
  bool get isReal => source == 'android_sms' || source == 'android_notification';
  bool get contentWiped => status == DeliveryStatus.delivered && body.isEmpty;

  /// Package name for notifications, sender ID for SMS.
  String get origin => (isNotification ? sourcePackage : sender) ?? 'unknown';

  /// Attempts beyond the first.
  int get retryCount => attempts > 1 ? attempts - 1 : 0;

  factory GatewayEvent.fromMap(Map<Object?, Object?> map) => GatewayEvent(
    id: (map['id'] as num).toInt(),
    sequence: (map['sequence'] as num?)?.toInt(),
    eventId: map['eventId'] as String,
    sourceType: map['sourceType'] as String? ?? 'sms',
    sourcePackage: map['sourcePackage'] as String?,
    sender: map['sender'] as String?,
    title: map['title'] as String?,
    body: map['body'] as String? ?? '',
    receivedAt: _date(map['receivedAt'])!,
    source: map['source'] as String,
    status: DeliveryStatus.parse(map['status'] as String?),
    moneyHint: map['moneyHint'] as bool? ?? false,
    attempts: (map['attempts'] as num?)?.toInt() ?? 0,
    nextAttemptAt: _date(map['nextAttemptAt']),
    lastError: map['lastError'] as String?,
    lastHttpStatus: (map['lastHttpStatus'] as num?)?.toInt(),
    deliveredAt: _date(map['deliveredAt']),
  );
}

DateTime? _date(Object? epochMs) =>
    epochMs is num ? DateTime.fromMillisecondsSinceEpoch(epochMs.toInt()) : null;
