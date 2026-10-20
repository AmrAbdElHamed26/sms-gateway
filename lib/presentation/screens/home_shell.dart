import 'package:flutter/material.dart';

import '../dashboard_controller.dart';
import 'apps_screen.dart';
import 'events_screen.dart';
import 'pulses_screen.dart';
import 'settings_screen.dart';
import 'status_screen.dart';
import 'testing_screen.dart';

enum GatewayTab { status, apps, events, pulses, settings, testing }

class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.controller});

  final DashboardController controller;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> with WidgetsBindingObserver {
  GatewayTab _tab = GatewayTab.status;

  DashboardController get _c => widget.controller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _c.refresh();
    _c.startPolling();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _c.refresh(silent: true); // access or permission may have changed in system settings
      _c.startPolling();
    } else if (state == AppLifecycleState.paused) {
      _c.stopPolling();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _c.dispose();
    super.dispose();
  }

  void _goTo(GatewayTab tab) => setState(() => _tab = tab);

  @override
  Widget build(BuildContext context) {
    const titles = {
      GatewayTab.status: 'Gateway',
      GatewayTab.apps: 'Monitored apps',
      GatewayTab.events: 'Event log',
      GatewayTab.pulses: 'Heartbeat pulses',
      GatewayTab.settings: 'Settings',
      GatewayTab.testing: 'Testing',
    };
    return ListenableBuilder(
      listenable: _c,
      builder: (context, _) => Scaffold(
        appBar: AppBar(
          title: Text(titles[_tab]!),
          actions: [
            IconButton(
              tooltip: 'Refresh',
              onPressed: _c.loading ? null : _c.refresh,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: Column(
          children: [
            if (_c.error != null)
              MaterialBanner(
                content: Text(_c.error!),
                actions: [TextButton(onPressed: _c.refresh, child: const Text('Retry'))],
              ),
            Expanded(
              child: IndexedStack(
                index: _tab.index,
                children: [
                  StatusScreen(controller: _c, onNavigate: _goTo),
                  AppsScreen(controller: _c, visible: _tab == GatewayTab.apps),
                  EventsScreen(controller: _c),
                  PulsesScreen(controller: _c),
                  SettingsScreen(controller: _c),
                  TestingScreen(controller: _c),
                ],
              ),
            ),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _tab.index,
          onDestinationSelected: (i) => _goTo(GatewayTab.values[i]),
          destinations: [
            NavigationDestination(
              icon: Badge(
                isLabelVisible: _c.warnings.any((w) => w.critical),
                child: const Icon(Icons.sensors),
              ),
              label: 'Status',
            ),
            const NavigationDestination(icon: Icon(Icons.apps), label: 'Apps'),
            const NavigationDestination(icon: Icon(Icons.receipt_long_outlined), label: 'Events'),
            const NavigationDestination(icon: Icon(Icons.monitor_heart_outlined), label: 'Pulses'),
            const NavigationDestination(icon: Icon(Icons.settings_outlined), label: 'Settings'),
            const NavigationDestination(icon: Icon(Icons.science_outlined), label: 'Testing'),
          ],
        ),
      ),
    );
  }
}
