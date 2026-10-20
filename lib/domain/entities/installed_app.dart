import 'dart:typed_data';

class InstalledApp {
  const InstalledApp({
    required this.packageName,
    required this.label,
    required this.monitored,
    this.installed = true,
    this.icon,
  });

  final String packageName;
  final String label;
  final bool monitored;

  /// False for a monitored package that has since been uninstalled.
  final bool installed;

  /// PNG bytes, or null when the icon couldn't be read.
  final Uint8List? icon;

  InstalledApp copyWith({bool? monitored}) => InstalledApp(
    packageName: packageName,
    label: label,
    monitored: monitored ?? this.monitored,
    installed: installed,
    icon: icon,
  );

  factory InstalledApp.fromMap(Map<Object?, Object?> map) => InstalledApp(
    packageName: map['packageName'] as String,
    label: map['label'] as String? ?? map['packageName'] as String,
    monitored: map['monitored'] as bool? ?? false,
    installed: map['installed'] as bool? ?? true,
    icon: map['icon'] as Uint8List?,
  );
}
