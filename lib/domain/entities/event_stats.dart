class EventStats {
  const EventStats({
    this.total = 0,
    this.delivered = 0,
    this.pending = 0,
    this.failed = 0,
    this.simulated = 0,
    this.moneyTotal = 0,
    this.moneyPending = 0,
    this.moneyFailed = 0,
    this.lastSequence = 0,
    this.oldestPendingAt,
    this.oldestMoneyPendingAt,
  });

  /// Events that will be or were sent. Simulated events are counted separately.
  final int total;
  final int delivered;
  final int pending;
  final int failed;
  final int simulated;
  final int moneyTotal;
  final int moneyPending;
  final int moneyFailed;
  final int lastSequence;
  final DateTime? oldestPendingAt;
  final DateTime? oldestMoneyPendingAt;

  /// A money event has been waiting longer than [threshold]: the backend hasn't seen it yet.
  bool moneyDelayed({Duration threshold = const Duration(minutes: 5), DateTime? now}) =>
      oldestMoneyPendingAt != null && (now ?? DateTime.now()).difference(oldestMoneyPendingAt!) > threshold;

  factory EventStats.fromMap(Map<Object?, Object?> map) {
    int read(String key) => (map[key] as num?)?.toInt() ?? 0;
    DateTime? date(String key) {
      final v = map[key];
      return v is num ? DateTime.fromMillisecondsSinceEpoch(v.toInt()) : null;
    }

    return EventStats(
      total: read('total'),
      delivered: read('delivered'),
      pending: read('pending'),
      failed: read('failed'),
      simulated: read('simulated'),
      moneyTotal: read('moneyTotal'),
      moneyPending: read('moneyPending'),
      moneyFailed: read('moneyFailed'),
      lastSequence: read('lastSequence'),
      oldestPendingAt: date('oldestPendingAt'),
      oldestMoneyPendingAt: date('oldestMoneyPendingAt'),
    );
  }
}
