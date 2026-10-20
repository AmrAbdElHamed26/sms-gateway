import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/entities/event_stats.dart';
import '../domain/entities/gateway_event.dart';
import '../domain/entities/gateway_settings.dart';
import '../domain/entities/gateway_status.dart';
import '../domain/entities/heartbeat_log.dart';
import '../domain/entities/installed_app.dart';
import '../domain/gateway_warnings.dart';
import '../domain/repositories/gateway_repository.dart';

/// Dashboard state. Polls while visible; the gateway itself never waits on it.
class DashboardController extends ChangeNotifier {
  DashboardController(this.repository, {this.pollInterval = const Duration(seconds: 5)});

  final GatewayRepository repository;
  final Duration pollInterval;
  static const pageSize = 50;

  GatewayStatus? status;
  EventStats stats = const EventStats();
  GatewaySettings? settings;
  List<GatewayEvent> events = const [];
  HeartbeatLog heartbeats = const HeartbeatLog();
  DeliveryStatus? filter;
  bool moneyOnly = false;
  bool hasMore = false;
  String? error;
  bool loading = false;

  /// Loaded on demand (icons are heavy); not polled.
  List<InstalledApp>? apps;
  bool appsLoading = false;

  Timer? _timer;
  bool _disposed = false;

  List<GatewayWarning> get warnings => status == null ? const [] : gatewayWarnings(status!, stats);

  /// Monitored apps as label lookup, for the simulator and event list.
  String labelFor(String packageName) =>
      apps?.where((a) => a.packageName == packageName).firstOrNull?.label ?? packageName;

  void startPolling() {
    _timer ??= Timer.periodic(pollInterval, (_) => refresh(silent: true));
  }

  void stopPolling() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> refresh({bool silent = false}) async {
    if (!silent) _set(() => loading = true);
    try {
      final results = await Future.wait([
        repository.status(),
        repository.stats(),
        repository.settings(),
        repository.events(limit: _loadedCount, status: filter, moneyOnly: moneyOnly),
        repository.heartbeats(),
      ]);
      _set(() {
        status = results[0] as GatewayStatus;
        stats = results[1] as EventStats;
        settings = results[2] as GatewaySettings;
        events = results[3] as List<GatewayEvent>;
        heartbeats = results[4] as HeartbeatLog;
        hasMore = events.length >= _loadedCount;
        error = null;
      });
    } on GatewayException catch (e) {
      _set(() => error = e.message);
    } finally {
      if (!silent) _set(() => loading = false);
    }
  }

  Future<void> loadApps() async {
    _set(() => appsLoading = true);
    try {
      final loaded = await repository.installedApps();
      _set(() => apps = loaded);
    } on GatewayException catch (e) {
      _set(() => error = e.message);
    } finally {
      _set(() => appsLoading = false);
    }
  }

  /// Optimistic toggle; reverted if the native side rejects it.
  Future<void> setMonitored(String packageName, bool monitored) async {
    final before = apps;
    if (before == null) return;
    final next = [
      for (final a in before) a.packageName == packageName ? a.copyWith(monitored: monitored) : a,
    ];
    _set(() => apps = next);
    try {
      await repository.setMonitoredPackages({
        for (final a in next)
          if (a.monitored) a.packageName,
      });
      await refresh(silent: true);
    } on GatewayException catch (e) {
      _set(() {
        apps = before;
        error = e.message;
      });
    }
  }

  Future<void> setFilter(DeliveryStatus? value) async {
    filter = value;
    events = const [];
    await refresh();
  }

  Future<void> setMoneyOnly(bool value) async {
    moneyOnly = value;
    events = const [];
    await refresh();
  }

  Future<void> loadMore() async {
    try {
      final next = await repository.events(
        limit: pageSize,
        offset: events.length,
        status: filter,
        moneyOnly: moneyOnly,
      );
      _set(() {
        events = [...events, ...next];
        hasMore = next.length == pageSize;
      });
    } on GatewayException catch (e) {
      _set(() => error = e.message);
    }
  }

  /// Runs [action], refreshes, and returns its result or rethrows its error.
  Future<T> run<T>(Future<T> Function(GatewayRepository repo) action) async {
    try {
      return await action(repository);
    } finally {
      await refresh(silent: true);
    }
  }

  int get _loadedCount => events.length < pageSize ? pageSize : events.length;

  void _set(VoidCallback change) {
    if (_disposed) return;
    change();
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    stopPolling();
    super.dispose();
  }
}
