import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/domain/settings_validator.dart';

void main() {
  group('backendUrl', () {
    test('accepts https and empty', () {
      expect(SettingsValidator.backendUrl('https://api.example.com'), isNull);
      expect(SettingsValidator.backendUrl(''), isNull);
    });

    test('rejects http, relative, credentials and queries', () {
      for (final bad in [
        'http://api.example.com',
        'api.example.com',
        'https://u:p@x.com',
        'https://x.com?a=1',
      ]) {
        expect(SettingsValidator.backendUrl(bad), isNotNull, reason: bad);
      }
    });
  });

  test('deviceId', () {
    expect(SettingsValidator.deviceId('android-gateway-001'), isNull);
    expect(SettingsValidator.deviceId('ab'), isNotNull);
    expect(SettingsValidator.deviceId('has space'), isNotNull);
  });

  test('secret: empty keeps existing, short is rejected', () {
    expect(SettingsValidator.secret(''), isNull);
    expect(SettingsValidator.secret('short'), isNotNull);
    expect(SettingsValidator.secret('x' * 32), isNull);
  });

  test('retentionDays', () {
    expect(SettingsValidator.retentionDays('30'), isNull);
    expect(SettingsValidator.retentionDays('0'), isNotNull);
    expect(SettingsValidator.retentionDays('abc'), isNotNull);
  });

  test('gateway requires url and secret', () {
    expect(SettingsValidator.gateway(enabled: false, backendUrl: '', secretAvailable: false), isNull);
    expect(SettingsValidator.gateway(enabled: true, backendUrl: '', secretAvailable: true), isNotNull);
    expect(
      SettingsValidator.gateway(enabled: true, backendUrl: 'https://x.com', secretAvailable: false),
      isNotNull,
    );
    expect(
      SettingsValidator.gateway(enabled: true, backendUrl: 'https://x.com', secretAvailable: true),
      isNull,
    );
  });

  test('sms module requires at least one approved sender', () {
    expect(SettingsValidator.smsModule(enabled: false, allowlist: const []), isNull);
    expect(SettingsValidator.smsModule(enabled: true, allowlist: const []), isNotNull);
    expect(SettingsValidator.smsModule(enabled: true, allowlist: const ['VF-Cash']), isNull);
  });

  test('parseAllowlist splits lines and commas, trims, dedupes', () {
    expect(SettingsValidator.parseAllowlist(' VF-Cash \nInstaPay,VF-Cash\n\n'), ['VF-Cash', 'InstaPay']);
  });
}
