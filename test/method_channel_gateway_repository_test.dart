import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/data/method_channel_gateway_repository.dart';
import 'package:sms_gateway/domain/entities/gateway_event.dart';
import 'package:sms_gateway/domain/entities/gateway_settings.dart';
import 'package:sms_gateway/domain/repositories/gateway_repository.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel(MethodChannelGatewayRepository.channelName);
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final calls = <MethodCall>[];
  late MethodChannelGatewayRepository repo;

  void respond(Object? Function(MethodCall call) handler) {
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return handler(call);
    });
  }

  setUp(() {
    calls.clear();
    repo = MethodChannelGatewayRepository();
  });

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('parses notification and sms events', () async {
    respond(
      (_) => [
        {
          'id': 7,
          'sequence': 5,
          'eventId': 'e-7',
          'sourceType': 'notification',
          'sourcePackage': 'com.bank.app',
          'sender': null,
          'title': 'Payment received',
          'body': 'You received 100.00 EGP',
          'receivedAt': 1791964800000,
          'source': 'android_notification',
          'moneyHint': true,
          'status': 'pending',
          'attempts': 3,
          'lastError': 'HTTP 503',
          'lastHttpStatus': 503,
        },
        {
          'id': 8,
          'sequence': null,
          'eventId': 'e-8',
          'sourceType': 'sms',
          'sender': 'VF-Cash',
          'body': 'تم استلام 150 جنيه',
          'receivedAt': 1791964800000,
          'source': 'simulated_sms',
          'status': 'simulated',
          'attempts': 0,
        },
      ],
    );

    final events = await repo.events(status: DeliveryStatus.pending, moneyOnly: true);

    expect(calls.single.arguments, {'limit': 50, 'offset': 0, 'status': 'pending', 'moneyOnly': true});
    final n = events.first;
    expect(n.isNotification, isTrue);
    expect(n.origin, 'com.bank.app');
    expect(n.sequence, 5);
    expect(n.retryCount, 2);
    expect(n.isReal, isTrue);
    final s = events.last;
    expect(s.origin, 'VF-Cash');
    expect(s.status, DeliveryStatus.simulated);
    expect(s.sequence, isNull);
    expect(s.isReal, isFalse);
  });

  test('delivered events with wiped content are recognised', () async {
    respond(
      (_) => [
        {
          'id': 1,
          'eventId': 'e',
          'sourceType': 'notification',
          'body': '',
          'receivedAt': 1,
          'source': 'android_notification',
          'status': 'delivered',
          'attempts': 1,
        },
      ],
    );
    expect((await repo.events()).single.contentWiped, isTrue);
  });

  test('installed apps carry icons and monitored flags', () async {
    respond(
      (_) => [
        {
          'packageName': 'com.bank.app',
          'label': 'Bank',
          'icon': Uint8List.fromList([1, 2]),
          'monitored': true,
        },
        {'packageName': 'com.gone', 'label': 'com.gone', 'icon': null, 'installed': false, 'monitored': true},
      ],
    );
    final apps = await repo.installedApps();
    expect(apps.first.icon, [1, 2]);
    expect(apps.last.installed, isFalse);
  });

  test('setMonitoredPackages sends the package set', () async {
    respond((call) => (call.arguments as Map)['packages']);
    expect(await repo.setMonitoredPackages({'a', 'b'}), unorderedEquals(['a', 'b']));
  });

  test('saveSettings omits an empty secret so the stored one is kept', () async {
    respond((call) => {...(call.arguments as Map), 'secretConfigured': true});

    await repo.saveSettings(
      const SettingsUpdate(
        backendUrl: ' https://api.example.com ',
        deviceId: 'gw-1',
        gatewayEnabled: true,
        smsEnabled: true,
        smsAllowlist: ['VF-Cash'],
        retentionDays: 30,
        secret: '',
      ),
    );

    final args = calls.single.arguments as Map;
    expect(args.containsKey('secret'), isFalse);
    expect(args['backendUrl'], 'https://api.example.com');
    expect(args['smsAllowlist'], ['VF-Cash']);
  });

  test('simulateEvent passes local-only mode', () async {
    respond((_) => 'simulated');
    final result = await repo.simulateEvent(
      sourceType: 'notification',
      packageName: 'com.bank.app',
      body: 'x',
    );
    expect(result, 'simulated');
    expect((calls.single.arguments as Map)['localOnly'], isTrue);
  });

  test('status parses module state and filtered counts', () async {
    respond(
      (_) => {
        'notificationAccess': true,
        'listenerConnected': false,
        'monitoredCount': 2,
        'smsEnabled': false,
        'filteredCounts': {'otp': 3},
        'backend': {'ok': false, 'httpStatus': 401, 'message': 'HTTP 401', 'checkedAt': 1},
      },
    );
    final status = await repo.status();
    expect(status.listenerConnected, isFalse);
    expect(status.monitoredCount, 2);
    expect(status.filteredCounts, {'otp': 3});
    expect(status.backend.httpStatus, 401);
  });

  test('platform errors surface as GatewayException with the native message', () async {
    respond((_) => throw PlatformException(code: 'INVALID_ARGUMENT', message: 'Backend URL must be https'));
    expect(
      repo.testConnection(),
      throwsA(isA<GatewayException>().having((e) => e.message, 'message', 'Backend URL must be https')),
    );
  });
}
