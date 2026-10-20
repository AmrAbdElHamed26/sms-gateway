String formatTimestamp(DateTime? time) {
  if (time == null) return 'Never';
  String two(int n) => n.toString().padLeft(2, '0');
  return '${time.year}-${two(time.month)}-${two(time.day)} '
      '${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
}

String formatAgo(DateTime? time, {DateTime? now}) {
  if (time == null) return 'Never';
  final diff = (now ?? DateTime.now()).difference(time);
  if (diff.inSeconds < 60) return 'just now';
  if (diff.inMinutes < 60) return '${diff.inMinutes} min ago';
  if (diff.inHours < 24) return '${diff.inHours} h ago';
  return '${diff.inDays} d ago';
}

/// Compact duration: `45s`, `5m 49s`, `1h 02m`.
String formatDuration(Duration d) {
  final negative = d.isNegative;
  d = d.abs();
  final text = d.inHours > 0
      ? '${d.inHours}h ${(d.inMinutes % 60).toString().padLeft(2, '0')}m'
      : d.inMinutes > 0
      ? '${d.inMinutes}m ${(d.inSeconds % 60).toString().padLeft(2, '0')}s'
      : '${d.inSeconds}s';
  return negative ? '-$text' : text;
}
