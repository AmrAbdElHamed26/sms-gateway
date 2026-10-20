import 'package:flutter/material.dart';

import '../../core/formatters.dart';
import '../../domain/gateway_warnings.dart';
import '../../domain/repositories/gateway_repository.dart';
import '../dashboard_controller.dart';
import '../widgets/common.dart';
import 'home_shell.dart';

class StatusScreen extends StatelessWidget {
  const StatusScreen({super.key, required this.controller, required this.onNavigate});

  final DashboardController controller;
  final void Function(GatewayTab tab) onNavigate;

  Future<void> _act(BuildContext context, WarningAction action) async {
    try {
      switch (action) {
        case WarningAction.openNotificationAccess:
          await controller.repository.openNotificationAccessSettings();
        case WarningAction.openApps:
          onNavigate(GatewayTab.apps);
        case WarningAction.openSettings:
          onNavigate(GatewayTab.settings);
        case WarningAction.grantSms:
          final granted = await controller.run((r) => r.requestSmsPermission());
          if (context.mounted && !granted) {
            showMessage(context, 'Permission denied. Grant it in Android Settings → Apps → Payment Gateway.');
          }
        case WarningAction.openBattery:
          await controller.repository.openBatteryOptimizationSettings();
      }
    } on GatewayException catch (e) {
      if (context.mounted) showMessage(context, e.message);
    }
  }

  static String _actionLabel(WarningAction action) => switch (action) {
    WarningAction.openNotificationAccess => 'Open',
    WarningAction.openApps => 'Select',
    WarningAction.openSettings => 'Settings',
    WarningAction.grantSms => 'Grant',
    WarningAction.openBattery => 'Exempt',
  };

  @override
  Widget build(BuildContext context) {
    final s = controller.status;
    final stats = controller.stats;
    if (s == null) {
      return Center(
        child: controller.loading ? const CircularProgressIndicator() : const Text('No status yet'),
      );
    }
    final scheme = Theme.of(context).colorScheme;
    final warnings = controller.warnings;
    final filtered = s.filteredCounts.entries.where((e) => e.value > 0).toList();
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          for (final w in warnings)
            Card(
              margin: const EdgeInsets.fromLTRB(16, 4, 16, 4),
              color: w.critical ? scheme.errorContainer : scheme.surfaceContainerHighest,
              child: ListTile(
                dense: true,
                leading: Icon(w.critical ? Icons.warning_amber : Icons.info_outline),
                title: Text(w.message),
                trailing: w.action == null
                    ? null
                    : TextButton(
                        onPressed: () => _act(context, w.action!),
                        child: Text(_actionLabel(w.action!)),
                      ),
              ),
            ),
          SectionCard(
            title: 'Notifications',
            children: [
              StatusRow(
                label: 'Notification Access',
                value: s.notificationAccess ? 'Granted' : 'Not granted',
                ok: s.notificationAccess,
              ),
              StatusRow(
                label: 'Listener',
                value: s.listenerConnected ? 'Connected' : 'Disconnected',
                ok: s.notificationAccess ? s.listenerConnected : null,
              ),
              StatusRow(label: 'Monitored apps', value: '${s.monitoredCount}', ok: s.monitoredCount > 0),
              StatusRow(label: 'Last notification', value: formatAgo(s.lastNotificationAt)),
            ],
          ),
          SectionCard(
            title: 'SMS (optional)',
            children: [
              StatusRow(label: 'SMS monitoring', value: s.smsEnabled ? 'On' : 'Off'),
              if (s.smsEnabled) ...[
                StatusRow(
                  label: 'SMS permission',
                  value: s.smsPermission ? 'Granted' : 'Not granted',
                  ok: s.smsPermission,
                ),
                StatusRow(
                  label: 'Approved senders',
                  value: '${s.smsAllowlistCount}',
                  ok: s.smsAllowlistCount > 0,
                ),
                StatusRow(label: 'Last SMS', value: formatAgo(s.lastSmsReceivedAt)),
              ],
            ],
          ),
          SectionCard(
            title: 'Delivery',
            children: [
              StatusRow(
                label: 'Backend',
                value: !s.backendConfigured
                    ? 'Not configured'
                    : s.backend.isUnknown
                    ? 'Not contacted yet'
                    : '${s.backend.ok ? 'OK' : s.backend.message ?? 'Error'} · ${formatAgo(s.backend.checkedAt)}',
                ok: !s.backendConfigured ? false : (s.backend.isUnknown ? null : s.backend.ok),
              ),
              StatusRow(label: 'Last delivered', value: formatAgo(s.lastDeliveredAt)),
              StatusRow(label: 'Pending', value: '${stats.pending}', ok: stats.pending == 0 ? true : null),
              StatusRow(
                label: 'Heartbeat',
                value: s.heartbeat.isUnknown
                    ? 'Not sent yet'
                    : '${s.heartbeat.ok ? 'OK' : 'HTTP ${s.heartbeat.httpStatus ?? '-'}'} · '
                          '${formatAgo(s.heartbeat.checkedAt)}',
                ok: s.heartbeat.isUnknown ? null : s.heartbeat.ok,
              ),
              StatusRow(label: 'Network', value: s.networkOnline ? 'Online' : 'Offline', ok: s.networkOnline),
              StatusRow(
                label: 'Battery optimisation',
                value: s.ignoringBatteryOptimizations ? 'Exempt' : 'Restricted',
                ok: s.ignoringBatteryOptimizations ? true : null,
              ),
            ],
          ),
          SectionCard(
            title: 'Events',
            children: [
              Row(
                children: [
                  StatTile(label: 'Captured', value: stats.total),
                  StatTile(label: 'Delivered', value: stats.delivered, color: Colors.green),
                  StatTile(label: 'Pending', value: stats.pending, color: Colors.orange),
                  StatTile(label: 'Failed', value: stats.failed, color: scheme.error),
                ],
              ),
              const Divider(height: 24),
              Row(
                children: [
                  StatTile(label: 'Money', value: stats.moneyTotal),
                  StatTile(label: 'Money pending', value: stats.moneyPending, color: Colors.orange),
                  StatTile(label: 'Simulated', value: stats.simulated),
                  StatTile(label: 'Last seq #', value: stats.lastSequence),
                ],
              ),
              if (filtered.isNotEmpty) ...[
                const Divider(height: 24),
                Text(
                  'Filtered out (never stored): ${filtered.map((e) => '${e.value} ${e.key.replaceAll('_', ' ')}').join(' · ')}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}
