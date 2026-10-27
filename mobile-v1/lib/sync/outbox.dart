import '../storage/local_store.dart';

/// FR-M.6: one change made in the app, waiting to be sent to the server.
class PendingOp {
  PendingOp({
    required this.id,
    required this.kind,
    required this.method,
    required this.path,
    required this.label,
    required this.createdAt,
    this.body,
    this.error,
  });

  /// Also sent as the visit's clientRef, so a retried visit isn't logged twice.
  final String id;

  /// visit | leadStage | opportunityStage | meetingReport | checkIn | checkOut | markRead
  final String kind;
  final String method;
  final String path;
  final Map<String, dynamic>? body;

  /// What the user did, for the "waiting to sync" list.
  final String label;
  final DateTime createdAt;

  /// Set when the server refused the change; it then waits for the user to retry or discard it.
  final String? error;

  bool get failed => error != null;

  PendingOp withError(String? message) => PendingOp(
        id: id,
        kind: kind,
        method: method,
        path: path,
        label: label,
        createdAt: createdAt,
        body: body,
        error: message,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'kind': kind,
        'method': method,
        'path': path,
        'label': label,
        'createdAt': createdAt.toIso8601String(),
        'body': body,
        'error': error,
      };

  factory PendingOp.fromJson(Map<String, dynamic> json) => PendingOp(
        id: json['id'] as String,
        kind: json['kind'] as String,
        method: json['method'] as String,
        path: json['path'] as String,
        label: json['label'] as String? ?? json['kind'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String),
        body: (json['body'] as Map?)?.cast<String, dynamic>(),
        error: json['error'] as String?,
      );
}

/// The queue of unsynced changes, kept on the phone in the order they were made.
class Outbox {
  Outbox(this._store);

  static const _key = 'outbox';
  final LocalStore _store;
  List<PendingOp>? _ops;

  Future<List<PendingOp>> all() async {
    if (_ops == null) {
      final raw = await _store.read(_key);
      _ops = raw is List ? raw.map((e) => PendingOp.fromJson((e as Map).cast<String, dynamic>())).toList() : <PendingOp>[];
    }
    return List.unmodifiable(_ops!);
  }

  Future<void> add(PendingOp op) async {
    await all();
    _ops!.add(op);
    await _save();
  }

  Future<void> remove(String id) async {
    await all();
    _ops!.removeWhere((o) => o.id == id);
    await _save();
  }

  /// The server refused it: keep it (with the reason) until the user retries or discards it.
  Future<void> markFailed(String id, String? error) async {
    await all();
    _ops = _ops!.map((o) => o.id == id ? o.withError(error) : o).toList();
    await _save();
  }

  Future<void> clear() async {
    _ops = [];
    await _save();
  }

  Future<void> _save() => _store.write(_key, _ops!.map((o) => o.toJson()).toList());
}
