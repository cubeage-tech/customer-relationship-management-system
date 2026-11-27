import 'package:flutter/foundation.dart';

import '../api/api_client.dart';
import 'outbox.dart';

/// FR-M.6: sends queued changes to the server, oldest first, whenever the app gets a chance
/// (connection back, app reopened, every few minutes, pull to refresh).
///
/// * No connection or a server error: stop and try again later — nothing is lost.
/// * The server refuses a change (4xx): keep it with the reason and carry on with the rest;
///   the user can retry or discard it.
/// * A check-in the server already has (e.g. sent before the phone lost signal): done.
class SyncService extends ChangeNotifier {
  SyncService(this._api, this.outbox);

  final ApiClient _api;
  final Outbox outbox;

  /// Called after at least one change was synced, to refresh the cached lists.
  Future<void> Function()? onSynced;

  bool _running = false;
  bool offline = false;
  DateTime? lastSyncedAt;
  int pending = 0;
  int failed = 0;

  bool get running => _running;

  /// Sends what can be sent; returns how many changes were synced. Called while a sync is
  /// running, it waits for that one and then goes round once more (so nothing just queued is missed).
  Future<int> flush() {
    if (_current != null) {
      _again = true;
      return _current!;
    }
    final run = _run();
    _current = run;
    return run.whenComplete(() => _current = null);
  }

  Future<int>? _current;
  bool _again = false;

  Future<int> _run() async {
    _running = true;
    notifyListeners();
    var synced = 0;
    try {
      do {
        _again = false;
        synced += await _flushOnce();
      } while (_again && !offline && !_stalled);
    } finally {
      _running = false;
      await _count();
      notifyListeners();
    }
    if (synced > 0 && onSynced != null) {
      await onSynced!();
    }
    return synced;
  }

  /// The server had trouble (5xx) in this round: wait for the next trigger rather than retry at once.
  bool _stalled = false;

  Future<int> _flushOnce() async {
    _stalled = false;
    var synced = 0;
    var reachedServer = true;
    for (final op in await outbox.all()) {
      if (op.failed) continue;
      try {
        await _api.send(op.method, op.path, body: op.body);
        await outbox.remove(op.id);
        synced++;
      } on NetworkException {
        reachedServer = false;
        break;
      } on ApiException catch (e) {
        if (_alreadyApplied(op, e)) {
          await outbox.remove(op.id);
        } else if (e.isClientError) {
          await outbox.markFailed(op.id, e.message);
        } else {
          _stalled = true;
          break; // server trouble: keep the order, try later
        }
      }
    }
    offline = !reachedServer;
    if (reachedServer) {
      lastSyncedAt = DateTime.now();
    }
    return synced;
  }

  /// Tries a refused change again (e.g. after the record was fixed on the web).
  Future<void> retry(String id) async {
    await outbox.markFailed(id, null);
    await flush();
  }

  Future<void> discard(String id) async {
    await outbox.remove(id);
    await _count();
    notifyListeners();
  }

  Future<void> refreshCounts() async {
    await _count();
    notifyListeners();
  }

  Future<void> _count() async {
    final ops = await outbox.all();
    failed = ops.where((o) => o.failed).length;
    pending = ops.length - failed;
  }

  static bool _alreadyApplied(PendingOp op, ApiException e) => op.kind == 'checkIn' && e.statusCode == 409;
}
