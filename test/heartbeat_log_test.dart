import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/domain/entities/heartbeat_log.dart';

Map<String, Object?> _beat(int id, int dueMin, int sentSec, {int? status = 200}) => {
  'id': id,
  'trigger': 'alarm',
  'dueAt': dueMin * 60000,
  'sentAt': sentSec * 1000,
  'httpStatus': status,
  'error': status == null ? 'UnknownHostException' : null,
  'latencyMs': 250,
  'ok': status != null && status < 300,
};

void main() {
  test('parses the native map and measures gaps from the previous successful pulse', () {
    final log = HeartbeatLog.fromMap({
      'intervalMs': 120000,
      'nextAt': 1000,
      'exactAlarms': false,
      // Newest first: a 6-minute Doze hold, a failure, then two on-time beats.
      'records': [
        _beat(4, 8, 14 * 60),
        _beat(3, 4, 4 * 60, status: null),
        _beat(2, 2, 2 * 60 + 1),
        _beat(1, 0, 1),
      ],
    });

    expect(log.interval, const Duration(minutes: 2));
    expect(log.exactAlarms, isFalse);
    expect(log.records.first.delay, const Duration(minutes: 6));
    expect(log.records[1].result, 'UnknownHostException');
    // The failed beat doesn't count: the backend never saw it.
    expect(log.gapBefore(0), const Duration(minutes: 11, seconds: 59));
    expect(log.gapBefore(0)! > HeartbeatLog.backendAlertAfter, isTrue);
    expect(log.gapBefore(2), const Duration(minutes: 2));
    expect(log.gapBefore(3), isNull);
  });
}
