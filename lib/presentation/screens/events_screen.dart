import 'package:flutter/material.dart';

import '../../core/formatters.dart';
import '../../core/masking.dart';
import '../../domain/entities/gateway_event.dart';
import '../dashboard_controller.dart';
import '../widgets/common.dart';

/// Event log. Message content is hidden by default and wiped once delivered.
class EventsScreen extends StatelessWidget {
  const EventsScreen({super.key, required this.controller});

  final DashboardController controller;

  @override
  Widget build(BuildContext context) {
    final events = controller.events;
    return Column(
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: Row(
            children: [
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: FilterChip(
                  avatar: const Icon(Icons.payments_outlined, size: 18),
                  label: const Text('Money'),
                  selected: controller.moneyOnly,
                  onSelected: controller.setMoneyOnly,
                ),
              ),
              _StatusChip(controller: controller, value: null, label: 'All'),
              for (final s in DeliveryStatus.values)
                _StatusChip(controller: controller, value: s, label: s.name),
            ],
          ),
        ),
        Expanded(
          child: RefreshIndicator(
            onRefresh: controller.refresh,
            child: events.isEmpty
                ? ListView(
                    children: const [
                      Padding(
                        padding: EdgeInsets.all(32),
                        child: Center(child: Text('No events')),
                      ),
                    ],
                  )
                : ListView.separated(
                    itemCount: events.length + (controller.hasMore ? 1 : 0),
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, i) {
                      if (i == events.length) {
                        return TextButton(onPressed: controller.loadMore, child: const Text('Load more'));
                      }
                      return _EventTile(event: events[i], controller: controller);
                    },
                  ),
          ),
        ),
      ],
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.controller, required this.value, required this.label});

  final DashboardController controller;
  final DeliveryStatus? value;
  final String label;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(right: 8),
    child: ChoiceChip(
      label: Text(label),
      selected: controller.filter == value,
      onSelected: (_) => controller.setFilter(value),
    ),
  );
}

class _EventTile extends StatelessWidget {
  const _EventTile({required this.event, required this.controller});

  final GatewayEvent event;
  final DashboardController controller;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final origin = event.isNotification ? controller.labelFor(event.origin) : event.origin;
    return ListTile(
      leading: Icon(event.isNotification ? Icons.notifications_outlined : Icons.sms_outlined),
      title: Row(
        children: [
          if (event.moneyHint) ...[
            Icon(Icons.payments_outlined, size: 18, color: theme.colorScheme.primary),
            const SizedBox(width: 6),
          ],
          Expanded(child: Text(origin, overflow: TextOverflow.ellipsis)),
          DeliveryStatusChip(event.status),
        ],
      ),
      // Metadata only: no message content in the list.
      subtitle: Text(
        [
          if (event.sequence != null) '#${event.sequence}',
          formatTimestamp(event.receivedAt),
          if (event.retryCount > 0) 'retries: ${event.retryCount}',
          if (event.status != DeliveryStatus.delivered && event.lastError != null) event.lastError!,
          if (!event.isReal) event.source,
        ].join(' · '),
        style: theme.textTheme.bodySmall,
      ),
      onTap: () => showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (_) => _EventDetails(event: event, origin: origin),
      ),
    );
  }
}

class _EventDetails extends StatefulWidget {
  const _EventDetails({required this.event, required this.origin});

  final GatewayEvent event;
  final String origin;

  @override
  State<_EventDetails> createState() => _EventDetailsState();
}

class _EventDetailsState extends State<_EventDetails> {
  bool _revealed = false;

  @override
  Widget build(BuildContext context) {
    final e = widget.event;
    final theme = Theme.of(context);
    Widget row(String label, String value) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 120, child: Text(label, style: theme.textTheme.bodySmall)),
          Expanded(child: SelectableText(value)),
        ],
      ),
    );
    final content = [if (e.title != null) e.title!, e.body].where((t) => t.isNotEmpty).join('\n');
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(child: Text(widget.origin, style: theme.textTheme.titleLarge)),
                DeliveryStatusChip(e.status),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: e.contentWiped
                  ? const Text('Content removed from the phone after delivery.')
                  : _revealed
                  ? Text(content, textDirection: _directionOf(content))
                  : Text(
                      'Hidden · ${maskSensitive(content).length} characters',
                      style: TextStyle(color: theme.colorScheme.outline),
                    ),
            ),
            if (!e.contentWiped)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () => setState(() => _revealed = !_revealed),
                  icon: Icon(_revealed ? Icons.visibility_off : Icons.visibility),
                  label: Text(_revealed ? 'Hide' : 'Reveal content'),
                ),
              ),
            row('Source', '${e.sourceType} · ${e.source}'),
            if (e.sourcePackage != null) row('Package', e.sourcePackage!),
            if (e.sender != null) row('Sender', e.sender!),
            if (e.sequence != null) row('Sequence', '#${e.sequence}'),
            row('Received', formatTimestamp(e.receivedAt)),
            row('Money hint', e.moneyHint ? 'Yes' : 'No'),
            row('Attempts', '${e.attempts} (retries: ${e.retryCount})'),
            if (e.status == DeliveryStatus.pending && e.attempts > 0)
              row('Next attempt', formatTimestamp(e.nextAttemptAt)),
            if (e.deliveredAt != null) row('Delivered', formatTimestamp(e.deliveredAt)),
            if (e.lastHttpStatus != null) row('Last HTTP', '${e.lastHttpStatus}'),
            if (e.lastError != null) row('Failure reason', e.lastError!),
            row('Event ID', e.eventId),
          ],
        ),
      ),
    );
  }

  static TextDirection _directionOf(String text) =>
      RegExp(r'[؀-ۿ]').hasMatch(text) ? TextDirection.rtl : TextDirection.ltr;
}
