import 'package:flutter/material.dart';

import '../data/repository.dart';
import 'common.dart';

/// FR-M.4: assignments, follow-ups due and manager messages (also pushed to the phone).
class InboxScreen extends StatefulWidget {
  const InboxScreen({super.key, required this.repository, required this.reloadSignal, required this.onOpenRecord, this.onUnread});

  final CrmRepository repository;
  final Listenable reloadSignal;

  /// Opens the Leads or Deals tab for a notification about a lead or opportunity.
  final void Function(String entityType) onOpenRecord;
  final void Function(int unread)? onUnread;

  @override
  State<InboxScreen> createState() => _InboxScreenState();
}

class _InboxScreenState extends State<InboxScreen> {
  Cached<Json>? _inbox;

  @override
  void initState() {
    super.initState();
    widget.reloadSignal.addListener(_refresh);
    _load(refresh: true);
  }

  @override
  void dispose() {
    widget.reloadSignal.removeListener(_refresh);
    super.dispose();
  }

  void _refresh() => _load(refresh: true);

  Future<void> _load({required bool refresh}) async {
    final inbox = await widget.repository.inbox(refresh: refresh);
    if (!mounted) return;
    setState(() => _inbox = inbox);
    widget.onUnread?.call((inbox.data['unread'] as num?)?.toInt() ?? 0);
  }

  Future<void> _open(Json n) async {
    if (n['read'] != true) {
      await widget.repository.markRead((n['id'] as num).toInt());
      await _load(refresh: false);
    }
    final type = n['entityType'] as String?;
    if (type != null) widget.onOpenRecord(type);
  }

  static IconData _icon(String? type) => switch (type) {
        'assignment' => Icons.assignment_ind_outlined,
        'follow_up' => Icons.alarm,
        _ => Icons.campaign_outlined,
      };

  @override
  Widget build(BuildContext context) {
    if (_inbox == null) return const Center(child: CircularProgressIndicator());
    final items = ((_inbox!.data['items'] as List?) ?? const []).cast<Json>();
    return Column(
      children: [
        if (_inbox!.fromCache) OfflineNote(fetchedAt: _inbox!.fetchedAt),
        Expanded(
          child: RefreshIndicator(
            onRefresh: () => _load(refresh: true),
            child: items.isEmpty
                ? ListView(children: const [Padding(padding: EdgeInsets.all(32), child: Center(child: Text('No notifications yet.')))])
                : ListView.separated(
                    itemCount: items.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, i) {
                      final n = items[i];
                      final unread = n['read'] != true;
                      return ListTile(
                        leading: Icon(_icon(n['type'] as String?)),
                        title: Text(n['title'] as String? ?? '', style: unread ? const TextStyle(fontWeight: FontWeight.bold) : null),
                        subtitle: Text([
                          n['body'],
                          if (n['senderName'] != null && n['type'] == 'message') 'from ${n['senderName']}',
                          formatDateTime(n['createdAt']),
                        ].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
                        trailing: unread ? const Icon(Icons.circle, size: 10) : null,
                        onTap: () => _open(n),
                      );
                    },
                  ),
          ),
        ),
      ],
    );
  }
}
