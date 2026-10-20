/// Mirrors the native checks so the form can flag errors before saving.
/// Each method returns an error message, or null when valid.
class SettingsValidator {
  const SettingsValidator._();

  static const minSecretLength = 16;
  static final _deviceId = RegExp(r'^[A-Za-z0-9._-]{3,64}$');

  static String? backendUrl(String? value) {
    final text = value?.trim() ?? '';
    if (text.isEmpty) return null; // allowed while forwarding is off
    final uri = Uri.tryParse(text);
    if (uri == null || !uri.isAbsolute || uri.scheme != 'https' || uri.host.isEmpty) {
      return 'Must be an absolute https:// URL';
    }
    if (uri.userInfo.isNotEmpty || uri.hasQuery || uri.hasFragment) {
      return 'No credentials, query or fragment in the URL';
    }
    return null;
  }

  static String? deviceId(String? value) =>
      _deviceId.hasMatch(value?.trim() ?? '') ? null : '3-64 chars: letters, digits, . _ -';

  /// Empty means "keep the stored secret".
  static String? secret(String? value) {
    if (value == null || value.isEmpty) return null;
    return value.length < minSecretLength ? 'At least $minSecretLength characters' : null;
  }

  static String? retentionDays(String? value) {
    final days = int.tryParse(value?.trim() ?? '');
    return days == null || days < 1 || days > 365 ? 'Between 1 and 365 days' : null;
  }

  static String? gateway({required bool enabled, required String backendUrl, required bool secretAvailable}) {
    if (!enabled) return null;
    if (backendUrl.trim().isEmpty || !secretAvailable) {
      return 'Set a backend URL and webhook secret before enabling the gateway';
    }
    return null;
  }

  /// SMS forwarding is allowlist-only: enabling it with no approved sender is an error.
  static String? smsModule({required bool enabled, required List<String> allowlist}) =>
      enabled && allowlist.isEmpty ? 'Add at least one approved sender to enable SMS monitoring' : null;

  static List<String> parseAllowlist(String text) =>
      text.split(RegExp(r'[\n,]')).map((s) => s.trim()).where((s) => s.isNotEmpty).toSet().toList();
}
