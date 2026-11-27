import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smartcrm_mobile/api/api_client.dart';
import 'package:smartcrm_mobile/data/repository.dart';
import 'package:smartcrm_mobile/storage/local_store.dart';
import 'package:smartcrm_mobile/sync/outbox.dart';
import 'package:smartcrm_mobile/sync/sync_service.dart';

/// A fake server: answers by handler, records requests, and can be "offline".
class FakeServer {
  bool offline = false;
  final requests = <http.Request>[];
  http.Response Function(http.Request) handler = (_) => http.Response(jsonEncode({'success': true, 'data': {}}), 200);

  late final client = MockClient((request) async {
    if (offline) throw const SocketException('offline');
    requests.add(request);
    return handler(request);
  });
}

/// Like the backend: UTF-8 JSON with no charset in the content type.
http.Response json(Object body, int status) =>
    http.Response.bytes(utf8.encode(jsonEncode(body)), status, headers: {'content-type': 'application/json'});

http.Response ok(Object? data) => json({'success': true, 'data': data}, 200);

http.Response error(int status, String message) => json({'success': false, 'message': message}, status);

void main() {
  late FakeServer server;
  late MemoryStore store;
  late SyncService sync;
  late CrmRepository repo;

  setUp(() {
    server = FakeServer();
    store = MemoryStore();
    final api = ApiClient(baseUrl: 'http://crm.test', token: () async => 'tkn', client: server.client);
    sync = SyncService(api, Outbox(store));
    repo = CrmRepository(api: api, store: store, sync: sync, userId: 7);
  });

  test('changes made offline wait on the phone and sync in order when back online', () async {
    server.offline = true;
    await repo.checkIn(latitude: 18.5, longitude: 73.8);
    await repo.logVisit(customerId: 3, customerName: 'Acme', purpose: 'Demo', outcome: 'Wants a quote');
    await sync.flush();
    expect(sync.offline, isTrue);
    expect(sync.pending, 2);
    expect(server.requests, isEmpty);

    server.offline = false;
    expect(await sync.flush(), 2);
    expect(sync.pending, 0);
    expect(server.requests.map((r) => r.url.path), ['/api/attendance/check-in', '/api/sales-activities/visits']);
    expect(server.requests.first.headers['Authorization'], 'Bearer tkn');

    final visit = jsonDecode(server.requests[1].body) as Map;
    expect(visit['purpose'], 'Demo');
    expect(visit['outcome'], 'Wants a quote');
    expect(visit['clientRef'], isNotEmpty, reason: 'lets the server ignore a retried visit');
    expect(visit['occurredAt'], matches(RegExp(r'^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d$')));
    final checkIn = jsonDecode(server.requests[0].body) as Map;
    expect(checkIn['at'], isNotNull, reason: 'the check-in keeps the time it happened offline');
  });

  test('a refused change is kept with its reason and the rest still sync', () async {
    server.offline = true;
    await repo.changeLeadStage({'id': 5, 'leadName': 'Ravi', 'stage': 'contacted'}, 'meeting');
    await repo.markRead(9);
    server.offline = false;
    server.handler = (r) => r.url.path.contains('/leads/') ? error(403, 'You can only access your own lead') : ok({});

    await sync.flush();
    final ops = await sync.outbox.all();
    expect(ops, hasLength(1));
    expect(ops.single.failed, isTrue);
    expect(ops.single.error, 'You can only access your own lead');
    expect(sync.failed, 1);

    server.handler = (_) => ok({});
    await sync.retry(ops.single.id);
    expect(await sync.outbox.all(), isEmpty);
  });

  test('a server error stops the sync without losing anything', () async {
    server.offline = true;
    await repo.checkIn();
    await repo.checkOut();
    server.offline = false;
    server.handler = (_) => error(500, 'boom');
    await sync.flush();
    expect(await sync.outbox.all(), hasLength(2));
    expect(server.requests, hasLength(1), reason: 'keeps the order: check-out waits for check-in');
  });

  test('server messages keep non-Latin characters', () async {
    server.handler = (_) => error(400, 'Discount over 10% needs approval — ask your manager (₹)');
    final api = ApiClient(baseUrl: 'http://crm.test', token: () async => null, client: server.client);
    await expectLater(api.get('/api/x'), throwsA(isA<ApiException>().having((e) => e.message, 'message', contains('— ask your manager (₹)'))));
  });

  test('a check-in the server already has counts as done', () async {
    server.offline = true;
    await repo.checkIn();
    server.offline = false;
    server.handler = (_) => error(409, 'You are already checked in — check out first');
    await sync.flush();
    expect(await sync.outbox.all(), isEmpty);
  });

  test('lists come from the phone when offline, with unsynced changes shown', () async {
    server.handler = (_) => ok([
          {'id': 5, 'leadName': 'Ravi', 'stage': 'contacted'},
          {'id': 6, 'leadName': 'Asha', 'stage': 'new_lead'},
        ]);
    final online = await repo.leads();
    expect(online.fromCache, isFalse);
    expect(server.requests.single.url.queryParameters['ownerId'], '7');

    server.offline = true;
    await repo.changeLeadStage(online.data.first, 'meeting');
    final offline = await repo.leads();
    expect(offline.fromCache, isTrue);
    expect(offline.data.first['stage'], 'meeting');
    expect(offline.data.first['_pending'], isTrue);

    // Back online but the change hasn't synced yet: the fresh list still shows it.
    server.offline = false;
    server.handler = (r) => r.url.path == '/api/leads' ? ok([{'id': 5, 'leadName': 'Ravi', 'stage': 'contacted'}]) : error(500, 'later');
    final refreshed = await repo.leads();
    expect(refreshed.data.first['stage'], 'meeting');
  });

  test('attendance shows the local state while a check-in waits to sync', () async {
    server.offline = true;
    await repo.checkIn();
    final state = await repo.attendance();
    expect(state.data?['checkInAt'], isNotNull);
    await repo.checkOut();
    expect((await repo.attendance()).data, isNull);
  });

  test('notifications read offline stay read', () async {
    server.handler = (_) => ok({
          'unread': 2,
          'items': [
            {'id': 1, 'type': 'assignment', 'title': 'New lead', 'read': false},
            {'id': 2, 'type': 'message', 'title': 'Team call', 'read': false},
          ],
        });
    await repo.inbox();
    server.offline = true;
    await repo.markRead(1);
    final inbox = await repo.inbox();
    expect(inbox.data['unread'], 1);
    expect((inbox.data['items'] as List).first['read'], isTrue);
  });
}
