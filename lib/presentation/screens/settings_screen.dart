import 'package:flutter/material.dart';

import '../../domain/entities/gateway_settings.dart';
import '../../domain/repositories/gateway_repository.dart';
import '../../domain/settings_validator.dart';
import '../dashboard_controller.dart';
import '../widgets/common.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.controller});

  final DashboardController controller;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _form = GlobalKey<FormState>();
  final _url = TextEditingController();
  final _deviceId = TextEditingController();
  final _secret = TextEditingController();
  final _allowlist = TextEditingController();
  final _retention = TextEditingController();
  bool _gatewayEnabled = false;
  bool _smsEnabled = false;
  bool _secretConfigured = false;
  bool _obscureSecret = true;
  bool _busy = false;
  GatewaySettings? _loadedFrom;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_syncFromController);
    _syncFromController();
  }

  /// Fills the form the first time settings arrive; never clobbers edits in progress.
  void _syncFromController() {
    final s = widget.controller.settings;
    if (s == null || _loadedFrom != null) return;
    _apply(s);
  }

  void _apply(GatewaySettings s) {
    setState(() {
      _loadedFrom = s;
      _url.text = s.backendUrl;
      _deviceId.text = s.deviceId;
      _allowlist.text = s.smsAllowlist.join('\n');
      _retention.text = '${s.retentionDays}';
      _gatewayEnabled = s.gatewayEnabled;
      _smsEnabled = s.smsEnabled;
      _secretConfigured = s.secretConfigured;
      _secret.clear();
    });
  }

  /// SMS permission is requested only here, when the operator turns the module on.
  Future<void> _toggleSms(bool enable) async {
    if (!enable) return setState(() => _smsEnabled = false);
    try {
      final granted = await widget.controller.run((r) => r.requestSmsPermission());
      if (!mounted) return;
      if (!granted) return showMessage(context, 'SMS permission denied; SMS monitoring stays off.');
      setState(() => _smsEnabled = true);
    } on GatewayException catch (e) {
      if (mounted) showMessage(context, e.message);
    }
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    final allowlist = SettingsValidator.parseAllowlist(_allowlist.text);
    final error =
        SettingsValidator.gateway(
          enabled: _gatewayEnabled,
          backendUrl: _url.text,
          secretAvailable: _secretConfigured || _secret.text.isNotEmpty,
        ) ??
        SettingsValidator.smsModule(enabled: _smsEnabled, allowlist: allowlist);
    if (error != null) return showMessage(context, error);

    setState(() => _busy = true);
    try {
      final saved = await widget.controller.run(
        (r) => r.saveSettings(
          SettingsUpdate(
            backendUrl: _url.text,
            deviceId: _deviceId.text,
            secret: _secret.text,
            gatewayEnabled: _gatewayEnabled,
            smsEnabled: _smsEnabled,
            smsAllowlist: allowlist,
            retentionDays: int.parse(_retention.text.trim()),
          ),
        ),
      );
      _apply(saved);
      if (mounted) showMessage(context, 'Settings saved');
    } on GatewayException catch (e) {
      if (mounted) showMessage(context, e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _testConnection() async {
    setState(() => _busy = true);
    try {
      final t = await widget.controller.run((r) => r.testConnection());
      if (mounted) {
        showMessage(
          context,
          '${t.ok ? 'OK' : 'Failed'}: ${t.message}${t.latencyMs != null ? ' (${t.latencyMs} ms)' : ''}',
        );
      }
    } on GatewayException catch (e) {
      if (mounted) showMessage(context, e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _clearSecret() async {
    try {
      await widget.controller.run((r) => r.clearSecret());
      setState(() {
        _secretConfigured = false;
        _gatewayEnabled = false;
      });
      if (mounted) showMessage(context, 'Secret cleared; gateway disabled');
    } on GatewayException catch (e) {
      if (mounted) showMessage(context, e.message);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_syncFromController);
    for (final c in [_url, _deviceId, _secret, _allowlist, _retention]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_loadedFrom == null) return const Center(child: CircularProgressIndicator());
    return Form(
      key: _form,
      child: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          SectionCard(
            title: 'Webhook',
            children: [
              TextFormField(
                controller: _url,
                decoration: const InputDecoration(
                  labelText: 'Backend API URL',
                  hintText: 'https://api.example.com',
                  helperText: 'Events: <URL>/gateway/events',
                ),
                keyboardType: TextInputType.url,
                autocorrect: false,
                validator: SettingsValidator.backendUrl,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _deviceId,
                decoration: const InputDecoration(
                  labelText: 'Device identifier',
                  helperText: 'Must match the ID provisioned on the backend',
                ),
                autocorrect: false,
                validator: SettingsValidator.deviceId,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _secret,
                obscureText: _obscureSecret,
                autocorrect: false,
                enableSuggestions: false,
                decoration: InputDecoration(
                  labelText: 'Device credential (HMAC secret)',
                  helperText: _secretConfigured
                      ? 'Stored encrypted. Leave empty to keep it.'
                      : 'Shared secret configured for this device on your backend. Min ${SettingsValidator.minSecretLength} chars.',
                  suffixIcon: IconButton(
                    icon: Icon(_obscureSecret ? Icons.visibility : Icons.visibility_off),
                    onPressed: () => setState(() => _obscureSecret = !_obscureSecret),
                  ),
                ),
                validator: SettingsValidator.secret,
              ),
              Wrap(
                spacing: 8,
                children: [
                  TextButton.icon(
                    onPressed: _busy ? null : _testConnection,
                    icon: const Icon(Icons.wifi_tethering),
                    label: const Text('Test connection (saved settings)'),
                  ),
                  if (_secretConfigured)
                    TextButton(onPressed: _clearSecret, child: const Text('Clear stored secret')),
                ],
              ),
            ],
          ),
          SectionCard(
            title: 'Gateway',
            children: [
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Gateway enabled'),
                subtitle: const Text('Capture and forward events from monitored apps and approved senders.'),
                value: _gatewayEnabled,
                onChanged: (v) => setState(() => _gatewayEnabled = v),
              ),
            ],
          ),
          SectionCard(
            title: 'SMS monitoring (optional)',
            children: [
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Monitor incoming SMS'),
                subtitle: const Text(
                  'Only approved senders are forwarded. Asks for SMS permission when turned on.',
                ),
                value: _smsEnabled,
                onChanged: _toggleSms,
              ),
              TextFormField(
                controller: _allowlist,
                enabled: _smsEnabled,
                minLines: 3,
                maxLines: 8,
                decoration: const InputDecoration(
                  labelText: 'Approved sender IDs and numbers',
                  hintText: 'One per line, e.g.\nVF-Cash\nInstaPay\n01012345678',
                  helperText: 'Case, space and dash insensitive; +20 / 0 numbers match',
                ),
              ),
            ],
          ),
          SectionCard(
            title: 'Local data',
            children: [
              TextFormField(
                controller: _retention,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Retention (days)',
                  helperText: 'Finished events older than this are deleted. Pending are kept.',
                ),
                validator: SettingsValidator.retentionDays,
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: FilledButton.icon(
              onPressed: _busy ? null : _save,
              icon: const Icon(Icons.save),
              label: Text(_busy ? 'Saving…' : 'Save settings'),
            ),
          ),
        ],
      ),
    );
  }
}
