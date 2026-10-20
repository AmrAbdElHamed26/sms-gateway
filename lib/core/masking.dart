final _longNumber = RegExp(r'[0-9٠-٩]{4,}');

/// Masks account, phone, card and reference numbers for list previews:
/// any run of 4+ digits (Western or Arabic-Indic) keeps only its last 2.
/// Short numbers such as amounts ("150.00") stay readable.
String maskSensitive(String text) => text.replaceAllMapped(_longNumber, (m) {
  final digits = m[0]!;
  return '•' * (digits.length - 2) + digits.substring(digits.length - 2);
});

/// Single-line preview, masked and truncated.
String previewOf(String body, {int maxLength = 120}) {
  final flat = maskSensitive(body).replaceAll(RegExp(r'\s+'), ' ').trim();
  return flat.length <= maxLength ? flat : '${flat.substring(0, maxLength)}…';
}
