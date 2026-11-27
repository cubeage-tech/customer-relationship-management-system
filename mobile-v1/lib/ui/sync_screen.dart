import 'package:flutter/material.dart';

import '../sync/outbox.dart';
import '../sync/sync_service.dart';
import 'common.dart';

/// FR-M.6: changes made on the phone that haven't reached the server yet, and any the server
/// refused (with its reason) — retry or discard them.
class SyncScreen extends StatefulWidget {
  const SyncScreen({super.key, required this.sync});

  final SyncService sync;

  @override
  State<SyncScreen> createState() => _SyncScreenState();
}

class _SyncScreenState extends State<SyncScreen> {
  List<PendingOp> _ops = const [];

  @override
  void initState() {
    super.initState();
    widget.sync.addListener(_load);
    _load();
  }

  @override
  void dispose() {
    widget.sync.removeListener(_load);
    super.dispose();
  }

  Future<void> _load() async {
    final ops = await widget.sync.outbox.all();
    if (mounted) setState(() => _ops = ops);
  }

  @override
  Widget build(BuildContext context) {
    final sync = widget.sync;
    return Scaffold(
      appBar: AppBar(title: const Text('Sync')),
      body: ListenableBuilder(
        listenable: sync,
        builder: (context, _) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(sync.running
                ? 'Syncing…'
                : sync.offline
                    ? 'Offline — changes are saved on this phone and will sync when you are back online.'
                    : sync.lastSyncedAt != null
                        ? 'Last synced ${formatDateTime(sync.lastSyncedAt!.toIso8601String())}'
                        : 'Not synced yet'),
            const SizedBox(height: 12),
            FilledButton.icon(onPressed: sync.running ? null : sync.flush, icon: const Icon(Icons.sync), label: const Text('Sync now')),
            const SizedBox(height: 16),
            if (_ops.isEmpty) const Text('Everything you changed on this phone is on the server.'),
            for (final op in _ops)
              Card(
                child: ListTile(
                  leading: Icon(op.failed ? Icons.error_outline : Icons.cloud_upload_outlined),
                  title: Text(op.label),
                  subtitle: Text(op.failed ? 'Not accepted: ${op.error}' : 'Waiting since ${formatDateTime(op.createdAt.toIso8601String())}'),
                  trailing: op.failed
                      ? Wrap(children: [
                          IconButton(tooltip: 'Retry', icon: const Icon(Icons.refresh), onPressed: () => sync.retry(op.id)),
                          IconButton(tooltip: 'Discard', icon: const Icon(Icons.delete_outline), onPressed: () => sync.discard(op.id)),
                        ])
                      : null,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
