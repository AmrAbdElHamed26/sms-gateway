import 'package:flutter/material.dart';

import '../../domain/repositories/gateway_repository.dart';
import '../dashboard_controller.dart';
import '../widgets/common.dart';

class TestingScreen extends StatefulWidget {
  const TestingScreen({super.key, required this.controller});

  final DashboardController controller;

  @override
  State<TestingScreen> createState() => _TestingScreenState();
}

class _TestingScreenState extends State<TestingScreen> {
  static const _samples = {
    'notification': ('Payment received', 'You received a payment of 100.00 EGP from Ahmed. Ref 482913'),
    'sms': ('', 'تم استلام مبلغ 150.00 جنيه من 01012345678. رقم العملية 123456789'),
  };

  String _type = 'notification';
  String? _package;
  final _sender = TextEditingController(text: 'VF-Cash');
  final _title = TextEditingController(text: _samples['notification']!.$1);
  final _body = TextEditingController(text: _samples['notification']!.$2);
  bool _localOnly = true;
  String? _result;
  bool _busy = false;

  Future<void> _do(String label, Future<String> Function(GatewayRepository r) action) async {
    setState(() => _busy = true);
    try {
      final message = await widget.controller.run(action);
      setState(() => _result = '$label: $message');
    } on GatewayException catch (e) {
      setState(() => _result = '$label failed: ${e.message}');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _setType(String type) {
    setState(() {
      _type = type;
      _title.text = _samples[type]!.$1;
      _body.text = _samples[type]!.$2;
    });
  }

  @override
  void dispose() {
    _sender.dispose();
    _title.dispose();
    _body.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final monitored = c.settings?.monitoredPackages ?? const <String>[];
    final package = monitored.contains(_package) ? _package : (monitored.isEmpty ? null : monitored.first);
    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 8),
      children: [
        SectionCard(
          title: 'Simulate a payment event',
          children: [
            const Text(
              'Runs a fake event through the real filters, dedupe and queue. '
              'No real financial message is used.',
            ),
            const SizedBox(height: 12),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(
                  value: 'notification',
                  icon: Icon(Icons.notifications_outlined),
                  label: Text('Notification'),
                ),
                ButtonSegment(value: 'sms', icon: Icon(Icons.sms_outlined), label: Text('SMS')),
              ],
              selected: {_type},
              onSelectionChanged: (s) => _setType(s.single),
            ),
            const SizedBox(height: 8),
            if (_type == 'notification') ...[
              if (monitored.isEmpty)
                const Text('Select at least one app in the Apps tab first.')
              else
                DropdownButtonFormField<String>(
                  initialValue: package,
                  decoration: const InputDecoration(labelText: 'As if posted by'),
                  items: [for (final p in monitored) DropdownMenuItem(value: p, child: Text(c.labelFor(p)))],
                  onChanged: (v) => setState(() => _package = v),
                ),
              TextField(
                controller: _title,
                decoration: const InputDecoration(labelText: 'Title'),
              ),
            ] else
              TextField(
                controller: _sender,
                decoration: const InputDecoration(labelText: 'Sender'),
              ),
            TextField(
              controller: _body,
              minLines: 2,
              maxLines: 5,
              decoration: const InputDecoration(labelText: 'Text'),
            ),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: _localOnly,
              onChanged: (v) => setState(() => _localOnly = v ?? true),
              title: const Text('Local only'),
              subtitle: Text(
                _localOnly
                    ? 'Stored as "simulated" on this phone, never sent.'
                    : 'Sent to the backend with source "simulated_$_type". The backend must never activate on it.',
              ),
            ),
            FilledButton.icon(
              onPressed: _busy || (_type == 'notification' && package == null)
                  ? null
                  : () => _do(
                      'Simulation',
                      (r) => r.simulateEvent(
                        sourceType: _type,
                        packageName: package,
                        title: _title.text,
                        sender: _sender.text.trim(),
                        body: _body.text,
                        localOnly: _localOnly,
                      ),
                    ),
              icon: const Icon(Icons.play_arrow),
              label: const Text('Simulate'),
            ),
          ],
        ),
        SectionCard(
          title: 'Backend',
          children: [
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton.icon(
                  onPressed: _busy
                      ? null
                      : () => _do('Connection', (r) async {
                          final t = await r.testConnection();
                          return '${t.ok ? 'OK' : 'FAILED'}: ${t.message}'
                              '${t.latencyMs != null ? ' (${t.latencyMs} ms)' : ''}';
                        }),
                  icon: const Icon(Icons.wifi_tethering),
                  label: const Text('Test connection'),
                ),
                OutlinedButton.icon(
                  onPressed: _busy ? null : () => _do('Test webhook', (r) => r.sendTestWebhook()),
                  icon: const Icon(Icons.send),
                  label: const Text('Send test webhook'),
                ),
                OutlinedButton.icon(
                  onPressed: _busy
                      ? null
                      : () => _do('Heartbeat', (r) async {
                          final t = await r.sendHeartbeat();
                          return '${t.ok ? 'OK' : 'FAILED'}: ${t.message}';
                        }),
                  icon: const Icon(Icons.monitor_heart_outlined),
                  label: const Text('Send heartbeat'),
                ),
                OutlinedButton.icon(
                  onPressed: _busy
                      ? null
                      : () => _do('Retry', (r) async => '${await r.retryPending()} event(s) re-queued'),
                  icon: const Icon(Icons.replay),
                  label: const Text('Retry pending & failed'),
                ),
              ],
            ),
          ],
        ),
        if (_result != null)
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(_result!, style: Theme.of(context).textTheme.bodyLarge),
          ),
      ],
    );
  }
}
