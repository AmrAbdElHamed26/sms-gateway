import 'package:flutter/material.dart';

import '../../core/formatters.dart';
import '../../domain/entities/heartbeat_log.dart';
import '../../domain/repositories/gateway_repository.dart';
import '../dashboard_controller.dart';
import '../widgets/common.dart';

/// Heartbeat schedule and history: when each pulse was due, when it really
/// went out, and the gaps the backend's `heartbeat_missing` alert sees.
class PulsesScreen extends StatefulWidget {
  const PulsesScreen({super.key, required this.controller});

  final DashboardController controller;

  @override
  State<PulsesScreen> createState() => _PulsesScreenState();
}

class _PulsesScreenState extends State<PulsesScreen> {
  bool _sending = false;

  Future<void> _sendNow() async {
    setState(() => _sending = true);
    try {
      final t = await widget.controller.run((r) => r.sendHeartbeat());
      if (mounted) showMessage(context, 'Heartbeat: ${t.ok ? 'OK' : 'FAILED'} · ${t.message}');
    } on GatewayException catch (e) {
      if (mounted) showMessage(context, 'Heartbeat failed: ${e.message}');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final log = widget.controller.heartbeats;
    final status = widget.controller.status;
    final now = DateTime.now();
    final day = [
      for (var i = 0; i < log.records.length; i++)
        if (now.difference(log.records[i].sentAt) <= const Duration(hours: 24)) i,
    ];
    final gaps = [for (final i in day) log.gapBefore(i)].whereType<Duration>().toList();
    final longest = gaps.isEmpty ? null : gaps.reduce((a, b) => a > b ? a : b);
    final alerts = gaps.where((g) => g > HeartbeatLog.backendAlertAfter).length;

    return RefreshIndicator(
      onRefresh: widget.controller.refresh,
      child: ListView(
        padding: const EdgeInsets.only(bottom: 16),
        children: [
          SectionCard(
            title: 'Schedule',
            children: [
              StatusRow(label: 'Interval', value: 'every ${formatDuration(log.interval)}'),
              StatusRow(
                label: 'Next pulse',
                value: log.nextAt == null
                    ? 'Not scheduled'
                    : '${formatTimestamp(log.nextAt)} · '
                          '${log.nextAt!.isAfter(now) ? 'in ${formatDuration(log.nextAt!.difference(now))}' : 'overdue ${formatDuration(now.difference(log.nextAt!))}'}',
                ok: log.nextAt == null
                    ? false
                    : (log.nextAt!.isAfter(now.subtract(const Duration(minutes: 1))) ? true : null),
              ),
              StatusRow(
                label: 'Exact alarms (fire in Doze)',
                value: log.exactAlarms ? 'Allowed' : 'Revoked: pulses drift',
                ok: log.exactAlarms,
              ),
              if (status != null)
                StatusRow(
                  label: 'Battery optimisation',
                  value: status.ignoringBatteryOptimizations ? 'Exempt' : 'Restricted',
                  ok: status.ignoringBatteryOptimizations ? true : false,
                ),
              const SizedBox(height: 8),
              FilledButton.tonalIcon(
                onPressed: _sending ? null : _sendNow,
                icon: const Icon(Icons.monitor_heart_outlined),
                label: const Text('Send pulse now'),
              ),
            ],
          ),
          SectionCard(
            title: 'Last 24 hours',
            children: [
              Row(
                children: [
                  StatTile(
                    label: 'Sent',
                    value: day.where((i) => log.records[i].ok).length,
                    color: Colors.green,
                  ),
                  StatTile(
                    label: 'Failed',
                    value: day.where((i) => !log.records[i].ok).length,
                    color: Theme.of(context).colorScheme.error,
                  ),
                  StatTile(
                    label: 'Gaps > ${HeartbeatLog.backendAlertAfter.inMinutes} min',
                    value: alerts,
                    color: Colors.orange,
                  ),
                ],
              ),
              const SizedBox(height: 8),
              StatusRow(
                label: 'Longest gap',
                value: longest == null ? '-' : formatDuration(longest),
                ok: longest == null ? null : longest <= HeartbeatLog.backendAlertAfter,
              ),
            ],
          ),
          if (log.records.isEmpty)
            const Padding(
              padding: EdgeInsets.all(32),
              child: Center(child: Text('No pulses logged yet')),
            )
          else
            for (var i = 0; i < log.records.length; i++) ...[
              _PulseTile(record: log.records[i], gap: log.gapBefore(i)),
              const Divider(height: 1),
            ],
        ],
      ),
    );
  }
}

class _PulseTile extends StatelessWidget {
  const _PulseTile({required this.record, required this.gap});

  final HeartbeatRecord record;
  final Duration? gap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final error = theme.colorScheme.error;
    final alert = gap != null && gap! > HeartbeatLog.backendAlertAfter;
    final held = record.delay;
    final parts = <InlineSpan>[
      if (gap != null)
        TextSpan(
          text: 'gap ${formatDuration(gap!)}${alert ? ' (backend alert)' : ''}',
          style: alert ? TextStyle(color: error, fontWeight: FontWeight.w600) : null,
        ),
      if (!record.isManual && held > const Duration(seconds: 30))
        TextSpan(text: 'held ${formatDuration(held)} by Android'),
      TextSpan(text: '${record.latencyMs} ms'),
      if (record.isManual) const TextSpan(text: 'manual'),
    ];
    return ListTile(
      leading: Icon(
        record.isManual ? Icons.touch_app_outlined : Icons.alarm,
        color: record.ok ? Colors.green : error,
      ),
      title: Row(
        children: [
          Expanded(child: Text(formatTimestamp(record.sentAt))),
          Text(
            record.result,
            style: TextStyle(
              color: record.ok ? Colors.green : error,
              fontWeight: FontWeight.w600,
              fontSize: 12,
            ),
          ),
        ],
      ),
      subtitle: Text.rich(
        TextSpan(
          style: theme.textTheme.bodySmall,
          children: [
            for (var i = 0; i < parts.length; i++) ...[if (i > 0) const TextSpan(text: ' · '), parts[i]],
          ],
        ),
      ),
    );
  }
}
