import 'package:flutter/material.dart';

import '../app_state.dart';
import 'common.dart';
import 'inbox_screen.dart';
import 'meetings_screen.dart';
import 'pipeline_screens.dart';
import 'sync_screen.dart';
import 'today_screen.dart';

/// The signed-in app: Today (attendance, visits), Leads, Deals, Meetings and Inbox.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.app, required this.pushTick, required this.openInbox});

  final AppState app;

  /// Ticks when a push arrives while the app is open (refreshes the inbox).
  final ValueNotifier<int> pushTick;

  /// Ticks when the user opens the app from a push (jump to the inbox).
  final ValueNotifier<int> openInbox;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  static const _titles = ['Today', 'My leads', 'My deals', 'Meetings', 'Inbox'];
  int _tab = 0;
  int _unread = 0;

  @override
  void initState() {
    super.initState();
    widget.openInbox.addListener(_goToInbox);
    widget.pushTick.addListener(_onPush);
  }

  @override
  void dispose() {
    widget.openInbox.removeListener(_goToInbox);
    widget.pushTick.removeListener(_onPush);
    super.dispose();
  }

  void _goToInbox() => setState(() => _tab = 4);

  void _onPush() {
    if (mounted && _tab != 4) showMessage(context, 'New notification');
  }

  Future<void> _signOut() async {
    final pending = widget.app.sync.pending + widget.app.sync.failed;
    if (pending > 0) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Sign out?'),
          content: Text('$pending change(s) haven\'t synced yet and will be lost.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Sign out')),
          ],
        ),
      );
      if (ok != true) return;
    }
    await widget.app.signOut();
  }

  @override
  Widget build(BuildContext context) {
    final app = widget.app;
    final repo = app.repository!;
    final pages = [
      TodayScreen(repository: repo, sync: app.sync),
      LeadsScreen(repository: repo, sync: app.sync),
      OpportunitiesScreen(repository: repo, sync: app.sync),
      MeetingsScreen(repository: repo, sync: app.sync),
      InboxScreen(
        repository: repo,
        reloadSignal: widget.pushTick,
        onUnread: (n) => setState(() => _unread = n),
        // Leads and deals have their own tabs; other records (tickets, quotations…) open in the web app.
        onOpenRecord: (type) {
          if (type == 'lead' || type == 'opportunity') setState(() => _tab = type == 'opportunity' ? 2 : 1);
        },
      ),
    ];
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_tab]),
        actions: [
          SyncStatusButton(
            sync: app.sync,
            onOpen: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => SyncScreen(sync: app.sync))),
          ),
          PopupMenuButton<String>(
            onSelected: (v) => v == 'signout' ? _signOut() : null,
            itemBuilder: (context) => [
              PopupMenuItem(enabled: false, child: Text(app.session.user?.name ?? '')),
              const PopupMenuItem(value: 'signout', child: Text('Sign out')),
            ],
          ),
        ],
      ),
      body: IndexedStack(index: _tab, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: [
          const NavigationDestination(icon: Icon(Icons.today_outlined), label: 'Today'),
          const NavigationDestination(icon: Icon(Icons.person_search_outlined), label: 'Leads'),
          const NavigationDestination(icon: Icon(Icons.handshake_outlined), label: 'Deals'),
          const NavigationDestination(icon: Icon(Icons.event_note_outlined), label: 'Meetings'),
          NavigationDestination(
            icon: Badge(isLabelVisible: _unread > 0, label: Text('$_unread'), child: const Icon(Icons.notifications_outlined)),
            label: 'Inbox',
          ),
        ],
      ),
    );
  }
}
