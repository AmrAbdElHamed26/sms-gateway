import 'package:flutter/material.dart';

import '../../domain/entities/installed_app.dart';
import '../dashboard_controller.dart';

class AppsScreen extends StatefulWidget {
  const AppsScreen({super.key, required this.controller, required this.visible});

  final DashboardController controller;

  /// Apps are loaded the first time the tab is shown (icons are heavy).
  final bool visible;

  @override
  State<AppsScreen> createState() => _AppsScreenState();
}

class _AppsScreenState extends State<AppsScreen> {
  final _search = TextEditingController();
  bool _selectedOnly = false;

  @override
  void didUpdateWidget(AppsScreen old) {
    super.didUpdateWidget(old);
    final c = widget.controller;
    if (widget.visible && c.apps == null && !c.appsLoading) c.loadApps();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  List<InstalledApp> _visibleApps(List<InstalledApp> apps) {
    final q = _search.text.trim().toLowerCase();
    return apps.where((a) {
      if (_selectedOnly && !a.monitored) return false;
      return q.isEmpty || a.label.toLowerCase().contains(q) || a.packageName.toLowerCase().contains(q);
    }).toList()..sort((a, b) {
      if (a.monitored != b.monitored) return a.monitored ? -1 : 1;
      return a.label.toLowerCase().compareTo(b.label.toLowerCase());
    });
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final status = c.status;
    final apps = c.apps;
    final selected = apps?.where((a) => a.monitored).length ?? status?.monitoredCount ?? 0;
    return Column(
      children: [
        if (status != null && !status.notificationAccess)
          MaterialBanner(
            content: const Text(
              'Notification Access is off. Selected apps are not monitored until you grant it.',
            ),
            actions: [
              TextButton(
                onPressed: c.repository.openNotificationAccessSettings,
                child: const Text('Grant access'),
              ),
            ],
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: TextField(
            controller: _search,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              hintText: 'Search apps or packages',
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              Expanded(child: Text('$selected monitored · only these apps are ever read')),
              FilterChip(
                label: const Text('Selected'),
                selected: _selectedOnly,
                onSelected: (v) => setState(() => _selectedOnly = v),
              ),
            ],
          ),
        ),
        Expanded(
          child: apps == null
              ? const Center(child: CircularProgressIndicator())
              : RefreshIndicator(
                  onRefresh: c.loadApps,
                  child: ListView(
                    children: [
                      for (final app in _visibleApps(apps))
                        SwitchListTile(
                          secondary: _AppIcon(app),
                          title: Text(app.label),
                          subtitle: Text(
                            app.installed ? app.packageName : '${app.packageName} · not installed',
                          ),
                          value: app.monitored,
                          onChanged: (v) => c.setMonitored(app.packageName, v),
                        ),
                    ],
                  ),
                ),
        ),
      ],
    );
  }
}

class _AppIcon extends StatelessWidget {
  const _AppIcon(this.app);

  final InstalledApp app;

  @override
  Widget build(BuildContext context) {
    final icon = app.icon;
    return SizedBox.square(
      dimension: 40,
      child: icon == null
          ? const Icon(Icons.android)
          : Image.memory(icon, gaplessPlayback: true, filterQuality: FilterQuality.medium),
    );
  }
}
