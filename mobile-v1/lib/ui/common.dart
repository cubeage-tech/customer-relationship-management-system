import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../data/repository.dart';
import '../sync/sync_service.dart';

final _dateTime = DateFormat('d MMM, HH:mm');
final _money = NumberFormat.decimalPattern();

String formatDateTime(Object? iso) {
  final d = iso is String ? DateTime.tryParse(iso) : null;
  return d == null ? '' : _dateTime.format(d);
}

String formatMoney(Object? v) => v is num ? _money.format(v) : '';

void showMessage(BuildContext context, String message) =>
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));

/// FR-M.6: says when a list comes from the phone's copy because the server can't be reached.
class OfflineNote extends StatelessWidget {
  const OfflineNote({super.key, required this.fetchedAt});

  final DateTime? fetchedAt;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      color: scheme.surfaceContainerHighest,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Text(
        fetchedAt == null ? 'Offline — nothing saved on this phone yet' : 'Offline — showing data from ${_dateTime.format(fetchedAt!)}',
        style: Theme.of(context).textTheme.bodySmall,
      ),
    );
  }
}

/// A "waiting to sync" marker on an item changed offline.
class PendingChip extends StatelessWidget {
  const PendingChip({super.key, this.failed = false});

  final bool failed;

  @override
  Widget build(BuildContext context) => Chip(
        visualDensity: VisualDensity.compact,
        avatar: Icon(failed ? Icons.error_outline : Icons.cloud_upload_outlined, size: 16),
        label: Text(failed ? 'Not synced' : 'Waiting to sync'),
      );
}

/// Loads a cached list, with pull-to-refresh, an offline note and an empty message.
class CachedList extends StatefulWidget {
  const CachedList({
    super.key,
    required this.load,
    required this.itemBuilder,
    required this.empty,
    this.reloadSignal,
  });

  final Future<Cached<List<Json>>> Function({bool refresh}) load;
  final Widget Function(BuildContext, Json, VoidCallback reload) itemBuilder;
  final String empty;

  /// Reloads (from the cache) whenever this fires — e.g. after a sync.
  final Listenable? reloadSignal;

  @override
  State<CachedList> createState() => _CachedListState();
}

class _CachedListState extends State<CachedList> {
  Cached<List<Json>>? _data;
  String? _error;

  @override
  void initState() {
    super.initState();
    widget.reloadSignal?.addListener(_reloadFromCache);
    _load(refresh: true);
  }

  @override
  void dispose() {
    widget.reloadSignal?.removeListener(_reloadFromCache);
    super.dispose();
  }

  void _reloadFromCache() => _load(refresh: false);

  Future<void> _load({required bool refresh}) async {
    try {
      final data = await widget.load(refresh: refresh);
      if (mounted) {
        setState(() {
          _data = data;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_data == null) {
      return Center(child: _error != null ? Padding(padding: const EdgeInsets.all(24), child: Text(_error!)) : const CircularProgressIndicator());
    }
    final items = _data!.data;
    return Column(
      children: [
        if (_data!.fromCache) OfflineNote(fetchedAt: _data!.fetchedAt),
        Expanded(
          child: RefreshIndicator(
            onRefresh: () => _load(refresh: true),
            child: items.isEmpty
                ? ListView(children: [Padding(padding: const EdgeInsets.all(32), child: Center(child: Text(widget.empty)))])
                : ListView.separated(
                    itemCount: items.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, i) => widget.itemBuilder(context, items[i], () => _load(refresh: false)),
                  ),
          ),
        ),
      ],
    );
  }
}

/// Tappable sync status for the app bar: offline, waiting or refused changes.
class SyncStatusButton extends StatelessWidget {
  const SyncStatusButton({super.key, required this.sync, required this.onOpen});

  final SyncService sync;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: sync,
        builder: (context, _) {
          final IconData icon;
          final String tip;
          if (sync.running) {
            icon = Icons.sync;
            tip = 'Syncing…';
          } else if (sync.failed > 0) {
            icon = Icons.sync_problem;
            tip = '${sync.failed} change(s) need attention';
          } else if (sync.offline || sync.pending > 0) {
            icon = Icons.cloud_off;
            tip = '${sync.pending} change(s) waiting to sync';
          } else {
            icon = Icons.cloud_done_outlined;
            tip = 'Everything synced';
          }
          final count = sync.pending + sync.failed;
          return IconButton(
            tooltip: tip,
            onPressed: onOpen,
            icon: Badge(isLabelVisible: count > 0, label: Text('$count'), child: Icon(icon)),
          );
        },
      );
}
