import 'dart:async';

import 'package:uuid/uuid.dart';

import '../api/api_client.dart';
import '../storage/local_store.dart';
import '../sync/outbox.dart';
import '../sync/sync_service.dart';

typedef Json = Map<String, dynamic>;

/// A list as last seen: from the server just now, or from the phone's cache when offline.
class Cached<T> {
  Cached(this.data, {required this.fromCache, this.fetchedAt});

  final T data;
  final bool fromCache;
  final DateTime? fetchedAt;
}

/// Lead pipeline stages (fixed in the CRM).
const leadStages = ['new_lead', 'contacted', 'meeting', 'quotation', 'negotiation', 'converted'];

/// FR-M.1–M.6: everything the app reads and changes. Reads come from the server when it can be
/// reached and are cached on the phone; changes are applied to the cache at once, queued in the
/// [Outbox] and synced by [SyncService] — so the core functions work offline.
class CrmRepository {
  CrmRepository({required this.api, required this.store, required this.sync, required this.userId});

  final ApiClient api;
  final LocalStore store;
  final SyncService sync;
  final int userId;
  static const _uuid = Uuid();

  Outbox get outbox => sync.outbox;

  // ==================== Reads (cached) ====================

  /// FR-M.2: leads assigned to me.
  Future<Cached<List<Json>>> leads({bool refresh = true}) =>
      _list('leads', '/api/leads', {'ownerId': '$userId'}, refresh, overlay: _overlayLeadStages);

  /// FR-M.2: opportunities assigned to me.
  Future<Cached<List<Json>>> opportunities({bool refresh = true}) =>
      _list('opportunities', '/api/opportunities', {'ownerId': '$userId'}, refresh, overlay: _overlayOpportunityStages);

  /// The tenant's opportunity stages, for the stage picker.
  Future<Cached<List<Json>>> opportunityStages({bool refresh = true}) => _list('opportunityStages', '/api/opportunity-stages', null, refresh);

  /// FR-M.3: my meetings from the last two weeks and upcoming.
  Future<Cached<List<Json>>> meetings({bool refresh = true}) =>
      _list('meetings', '/api/meetings', {'recent': 'true'}, refresh, overlay: _overlayMeetingReports);

  /// For choosing who a visit was with.
  Future<Cached<List<Json>>> customers({bool refresh = true}) => _list('customers', '/api/customers', null, refresh);

  /// FR-M.1: visits I logged recently (server list plus ones still waiting to sync).
  Future<Cached<List<Json>>> recentVisits({bool refresh = true}) async {
    final now = DateTime.now();
    final from = _date(now.subtract(const Duration(days: 14)));
    final result = await _list('visits', '/api/sales-activities/visits', {'userId': '$userId', 'from': from, 'to': _date(now)}, refresh);
    final pending = await _pendingVisits();
    return Cached([...pending, ...result.data], fromCache: result.fromCache, fetchedAt: result.fetchedAt);
  }

  /// FR-M.4: my notifications.
  Future<Cached<Json>> inbox({bool refresh = true}) async {
    final cached = await _cached('inbox');
    if (refresh) {
      try {
        final data = (await api.get('/api/notifications') as Map).cast<String, dynamic>();
        await _remember('inbox', data);
        return Cached(await _overlayRead(data), fromCache: false, fetchedAt: DateTime.now());
      } on NetworkException {
        // fall through to the cache
      }
    }
    final data = (cached?['data'] as Map?)?.cast<String, dynamic>() ?? {'unread': 0, 'items': <dynamic>[]};
    return Cached(await _overlayRead(data), fromCache: true, fetchedAt: _fetchedAt(cached));
  }

  /// FR-M.5: am I checked in? Local state wins while a check-in/out is still syncing.
  Future<Cached<Json?>> attendance({bool refresh = true}) async {
    final pending = (await outbox.all()).where((o) => !o.failed && (o.kind == 'checkIn' || o.kind == 'checkOut')).toList();
    if (refresh && pending.isEmpty) {
      try {
        final data = (await api.get('/api/attendance/me') as Map).cast<String, dynamic>();
        final current = (data['current'] as Map?)?.cast<String, dynamic>();
        await _remember('attendance', current);
        return Cached(current, fromCache: false, fetchedAt: DateTime.now());
      } on NetworkException {
        // fall through
      }
    }
    final cached = await _cached('attendance');
    return Cached((cached?['data'] as Map?)?.cast<String, dynamic>(), fromCache: true, fetchedAt: _fetchedAt(cached));
  }

  /// After a sync: reload everything so the cache matches the server.
  Future<void> refreshAll() async {
    for (final load in <Future<Object?> Function()>[leads, opportunities, meetings, inbox, attendance]) {
      try {
        await load();
      } on ApiException {
        // e.g. a role without access to one list: the others still refresh
      }
    }
  }

  // ==================== Changes (offline-first) ====================

  /// FR-M.2: move one of my leads. Skipping ahead needs [force] (BR-1).
  Future<void> changeLeadStage(Json lead, String stage, {bool force = false}) async {
    await _patchCached('leads', lead['id'], {'stage': stage});
    await _queue('leadStage', 'PATCH', '/api/leads/${lead['id']}/stage', {'stage': stage, 'force': force},
        'Lead ${lead['leadName']} → ${label(stage)}');
  }

  /// FR-M.2: move one of my opportunities (a lost deal needs a reason).
  Future<void> changeOpportunityStage(Json opportunity, String stage, {String? lossReason}) async {
    await _patchCached('opportunities', opportunity['id'], {'stage': stage, 'lossReason': lossReason});
    await _queue('opportunityStage', 'PATCH', '/api/opportunities/${opportunity['id']}/stage',
        {'stage': stage, 'lossReason': ?lossReason}, 'Deal ${opportunity['customerName']} → ${label(stage)}');
  }

  /// FR-M.1: log a visit — when, where (if the phone allows), purpose and outcome.
  Future<void> logVisit({
    int? customerId,
    String? customerName,
    required String purpose,
    required String outcome,
    String? notes,
    DateTime? occurredAt,
    double? latitude,
    double? longitude,
  }) async {
    final id = _uuid.v4();
    final body = <String, dynamic>{
      'customerId': customerId,
      'purpose': purpose,
      'outcome': outcome,
      'notes': notes,
      'occurredAt': _localIso(occurredAt ?? DateTime.now()),
      'latitude': latitude,
      'longitude': longitude,
      'clientRef': id, // FR-M.6: a retried sync returns the visit already logged
    };
    await _queue('visit', 'POST', '/api/sales-activities/visits', body,
        'Visit${customerName != null ? ' to $customerName' : ''}: $purpose', id: id);
  }

  /// FR-M.3: the report after a meeting.
  Future<void> submitMeetingReport(Json meeting, {required String outcome, required String notes, String? nextSteps}) async {
    await _patchCached('meetings', meeting['id'], {
      'reportOutcome': outcome,
      'reportNotes': notes,
      'nextSteps': nextSteps,
      'reportedAt': _localIso(DateTime.now()),
    });
    await _queue('meetingReport', 'PUT', '/api/meetings/${meeting['id']}/report',
        {'outcome': outcome, 'notes': notes, 'nextSteps': nextSteps}, 'Report: ${meeting['title']}');
  }

  /// FR-M.5: check in / out at this moment (synced later with the same time if offline).
  Future<void> checkIn({double? latitude, double? longitude}) async {
    final at = _localIso(DateTime.now());
    await _remember('attendance', {'checkInAt': at, 'checkOutAt': null});
    await _queue('checkIn', 'POST', '/api/attendance/check-in', {'at': at, 'latitude': latitude, 'longitude': longitude}, 'Check-in');
  }

  Future<void> checkOut({double? latitude, double? longitude}) async {
    final at = _localIso(DateTime.now());
    await _remember('attendance', null);
    await _queue('checkOut', 'POST', '/api/attendance/check-out', {'at': at, 'latitude': latitude, 'longitude': longitude}, 'Check-out');
  }

  Future<void> markRead(int notificationId) =>
      _queue('markRead', 'PATCH', '/api/notifications/$notificationId/read', null, 'Notification read');

  // ==================== Helpers ====================

  Future<void> _queue(String kind, String method, String path, Json? body, String label, {String? id}) async {
    await outbox.add(PendingOp(
      id: id ?? _uuid.v4(),
      kind: kind,
      method: method,
      path: path,
      body: body,
      label: label,
      createdAt: DateTime.now(),
    ));
    await sync.refreshCounts();
    // Try right away without making the user wait; offline it just stays queued.
    unawaited(sync.flush());
  }

  Future<Cached<List<Json>>> _list(String key, String path, Map<String, String>? query, bool refresh,
      {Future<List<Json>> Function(List<Json>)? overlay}) async {
    if (refresh) {
      try {
        final data = await api.get(path, query);
        final items = (data as List).map((e) => (e as Map).cast<String, dynamic>()).toList();
        await _remember(key, items);
        return Cached(overlay != null ? await overlay(items) : items, fromCache: false, fetchedAt: DateTime.now());
      } on NetworkException {
        // offline: use the cache
      }
    }
    final cached = await _cached(key);
    final items = ((cached?['data'] as List?) ?? const []).map((e) => (e as Map).cast<String, dynamic>()).toList();
    return Cached(overlay != null ? await overlay(items) : items, fromCache: true, fetchedAt: _fetchedAt(cached));
  }

  Future<Json?> _cached(String key) async => (await store.read('cache_$key') as Map?)?.cast<String, dynamic>();

  Future<void> _remember(String key, Object? data) =>
      store.write('cache_$key', {'data': data, 'fetchedAt': DateTime.now().toIso8601String()});

  static DateTime? _fetchedAt(Json? cached) => cached?['fetchedAt'] != null ? DateTime.tryParse(cached!['fetchedAt'] as String) : null;

  Future<void> _patchCached(String key, Object? id, Json changes) async {
    final cached = await _cached(key);
    final items = ((cached?['data'] as List?) ?? const []).map((e) => (e as Map).cast<String, dynamic>()).toList();
    for (final item in items) {
      if (item['id'] == id) item.addAll(changes);
    }
    await store.write('cache_$key', {'data': items, 'fetchedAt': cached?['fetchedAt']});
  }

  /// Unsynced stage changes still show after a refresh from the server.
  Future<List<Json>> _overlayLeadStages(List<Json> items) => _overlay(items, 'leadStage', (op) => {'stage': op.body!['stage']});

  Future<List<Json>> _overlayOpportunityStages(List<Json> items) =>
      _overlay(items, 'opportunityStage', (op) => {'stage': op.body!['stage']});

  Future<List<Json>> _overlayMeetingReports(List<Json> items) => _overlay(items, 'meetingReport',
      (op) => {'reportOutcome': op.body!['outcome'], 'reportNotes': op.body!['notes'], 'nextSteps': op.body!['nextSteps']});

  Future<List<Json>> _overlay(List<Json> items, String kind, Json Function(PendingOp) changes) async {
    final ops = (await outbox.all()).where((o) => o.kind == kind && !o.failed);
    for (final op in ops) {
      final id = int.tryParse(op.path.split('/')[3]);
      for (final item in items) {
        if (item['id'] == id) {
          item.addAll(changes(op));
          item['_pending'] = true;
        }
      }
    }
    return items;
  }

  Future<Json> _overlayRead(Json inbox) async {
    final readIds = (await outbox.all()).where((o) => o.kind == 'markRead').map((o) => int.tryParse(o.path.split('/')[3])).toSet();
    final items = ((inbox['items'] as List?) ?? const []).map((e) => (e as Map).cast<String, dynamic>()).toList();
    var unread = (inbox['unread'] as num?)?.toInt() ?? 0;
    for (final item in items) {
      if (readIds.contains(item['id']) && item['read'] != true) {
        item['read'] = true;
        unread--;
      }
    }
    return {'unread': unread < 0 ? 0 : unread, 'items': items};
  }

  Future<List<Json>> _pendingVisits() async => (await outbox.all())
      .where((o) => o.kind == 'visit')
      .map((o) => {...?o.body, 'id': o.id, 'label': o.label, '_pending': true, '_error': o.error})
      .toList();

  static String _date(DateTime d) => '${d.year}-${_two(d.month)}-${_two(d.day)}';

  /// The server stores local date-times (no time zone).
  static String _localIso(DateTime d) => '${_date(d)}T${_two(d.hour)}:${_two(d.minute)}:${_two(d.second)}';

  static String _two(int v) => v.toString().padLeft(2, '0');
}

/// "new_lead" → "New lead".
String label(String? code) {
  if (code == null || code.isEmpty) return '';
  final words = code.replaceAll('_', ' ');
  return words[0].toUpperCase() + words.substring(1);
}
