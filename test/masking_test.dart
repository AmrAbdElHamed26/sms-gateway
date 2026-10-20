import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/core/masking.dart';

void main() {
  group('maskSensitive', () {
    test('masks long digit runs, keeps last two', () {
      expect(maskSensitive('From 01012345678 ref 987654'), 'From •••••••••78 ref ••••54');
    });

    test('keeps short numbers such as amounts readable', () {
      expect(maskSensitive('Received 150.00 EGP'), 'Received 150.00 EGP');
    });

    test('masks Arabic-Indic digits and preserves Arabic text', () {
      expect(maskSensitive('تم التحويل من ٠١٠١٢٣٤٥٦٧٨'), 'تم التحويل من •••••••••٧٨');
    });
  });

  test('previewOf flattens whitespace, masks and truncates', () {
    final preview = previewOf('Line one 12345678\n\nline two ${'x' * 200}', maxLength: 40);
    expect(preview, startsWith('Line one ••••••78 line two'));
    expect(preview.length, 41);
    expect(preview, endsWith('…'));
  });
}
