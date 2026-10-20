import 'package:flutter/material.dart';

import '../../domain/entities/gateway_event.dart';

class SectionCard extends StatelessWidget {
  const SectionCard({super.key, required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ...children,
          ],
        ),
      ),
    );
  }
}

class StatusRow extends StatelessWidget {
  const StatusRow({super.key, required this.label, required this.value, this.ok, this.action});

  final String label;
  final String value;

  /// true = green, false = red, null = neutral.
  final bool? ok;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final (icon, color) = switch (ok) {
      true => (Icons.check_circle, Colors.green),
      false => (Icons.error, scheme.error),
      null => (Icons.info_outline, scheme.outline),
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 12),
          Expanded(child: Text(label)),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: TextStyle(color: scheme.onSurfaceVariant),
            ),
          ),
          if (action != null) ...[const SizedBox(width: 8), action!],
        ],
      ),
    );
  }
}

class StatTile extends StatelessWidget {
  const StatTile({super.key, required this.label, required this.value, this.color});

  final String label;
  final int value;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Expanded(
      child: Column(
        children: [
          Text('$value', style: theme.textTheme.headlineSmall?.copyWith(color: color)),
          Text(label, style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}

class DeliveryStatusChip extends StatelessWidget {
  const DeliveryStatusChip(this.status, {super.key});

  final DeliveryStatus status;

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      DeliveryStatus.delivered => Colors.green,
      DeliveryStatus.pending => Colors.orange,
      DeliveryStatus.failed => Theme.of(context).colorScheme.error,
      DeliveryStatus.simulated => Theme.of(context).colorScheme.tertiary,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        status.name,
        style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600),
      ),
    );
  }
}

void showMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}
