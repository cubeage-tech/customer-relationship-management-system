import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/widgets.dart';

import 'api/api_client.dart';
import 'auth/session.dart';
import 'config.dart';
import 'data/repository.dart';
import 'push/push_service.dart';
import 'storage/local_store.dart';
import 'sync/outbox.dart';
import 'sync/sync_service.dart';

/// Wires the app together and keeps unsynced changes moving (FR-M.6): sync when the
/// connection returns, when the app comes back to the foreground, and every few minutes.
class AppState with WidgetsBindingObserver {
  AppState({required this.session, required this.store, required this.firebaseReady}) {
    api = ApiClient(baseUrl: apiBaseUrl, token: session.token);
    sync = SyncService(api, Outbox(store));
    push = PushService(api);
  }

  final Session session;
  final LocalStore store;
  final bool firebaseReady;
  late final ApiClient api;
  late final SyncService sync;
  late final PushService push;
  CrmRepository? repository;

  StreamSubscription<List<ConnectivityResult>>? _connectivity;
  Timer? _timer;

  /// Called once signed in (or the earlier sign-in was restored).
  void startSession({required VoidCallback onPush, required VoidCallback onPushOpened}) {
    repository = CrmRepository(api: api, store: store, sync: sync, userId: session.user!.id);
    sync.onSynced = repository!.refreshAll;
    WidgetsBinding.instance.addObserver(this);
    _connectivity = Connectivity().onConnectivityChanged.listen((results) {
      if (!results.contains(ConnectivityResult.none)) sync.flush();
    });
    _timer = Timer.periodic(syncInterval, (_) => sync.flush());
    sync.refreshCounts();
    sync.flush();
    push.start(firebaseReady: firebaseReady, onMessage: (_) => onPush(), onOpened: (_) => onPushOpened());
  }

  /// Signs out and wipes the phone's copy of the CRM data (and any unsynced changes).
  Future<void> signOut() async {
    await push.stop();
    WidgetsBinding.instance.removeObserver(this);
    await _connectivity?.cancel();
    _timer?.cancel();
    await sync.outbox.clear();
    await store.clear();
    repository = null;
    await session.signOut();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) sync.flush();
  }
}
