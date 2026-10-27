import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smartcrm_mobile/api/api_client.dart';
import 'package:smartcrm_mobile/auth/session.dart';
import 'package:smartcrm_mobile/ui/login_screen.dart';

class MemorySecrets implements SecretStore {
  final Map<String, String> values = {};

  @override
  Future<void> delete(String key) async => values.remove(key);

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;
}

void main() {
  test('an expired session is renewed with the refresh token and the request repeated', () async {
    var calls = 0;
    final client = MockClient((request) async {
      calls++;
      if (request.url.path == '/auth/refresh') {
        expect(jsonDecode(request.body)['refreshToken'], 'r1');
        return http.Response(jsonEncode({
          'success': true,
          'data': {
            'token': 'jwt2',
            'refreshToken': 'r2',
            'user': {'id': 7, 'name': 'Asha', 'email': 'asha@x.test', 'role': 'sales_executive', 'tenantId': 1},
          },
        }), 200);
      }
      if (request.headers['Authorization'] == 'Bearer jwt2') {
        return http.Response(jsonEncode({'success': true, 'data': 'fresh'}), 200);
      }
      return http.Response(jsonEncode({'success': false, 'message': 'expired'}), 401);
    });
    final secrets = MemorySecrets()
      ..values['token'] = 'jwt1'
      ..values['refreshToken'] = 'r1'
      ..values['user'] = jsonEncode({'id': 7, 'name': 'Asha', 'email': 'asha@x.test', 'role': 'sales_executive', 'tenantId': 1});
    final session = Session(secrets);
    await session.restore();
    final api = ApiClient(baseUrl: 'http://crm.test', token: session.token, client: client);
    api.refreshSession = () => session.refresh(api);

    expect(await api.get('/api/leads'), 'fresh');
    expect(calls, 3);
    expect(secrets.values['refreshToken'], 'r2');
  });

  testWidgets('two-step sign-in asks for the authenticator code after the password', (tester) async {
    final client = MockClient((request) async {
      final body = jsonDecode(request.body) as Map;
      if (request.url.path == '/auth/login') {
        return http.Response(jsonEncode({'success': true, 'data': {'mfaRequired': true, 'mfaToken': 'challenge'}}), 200);
      }
      if (body['mfaToken'] == 'challenge' && body['code'] == '123456') {
        return http.Response(jsonEncode({
          'success': true,
          'data': {
            'token': 'jwt',
            'user': {'id': 1, 'name': 'Admin', 'email': 'admin@x.test', 'role': 'admin', 'tenantId': 1},
          },
        }), 200);
      }
      return http.Response(jsonEncode({'success': false, 'message': "That code isn't right"}), 401);
    });
    final session = Session(MemorySecrets());
    final api = ApiClient(baseUrl: 'http://crm.test', token: session.token, client: client);

    await tester.pumpWidget(MaterialApp(home: LoginScreen(session: session, api: api)));
    await tester.enterText(find.byKey(const Key('email')), 'admin@x.test');
    await tester.enterText(find.byKey(const Key('password')), 'secret');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('code')), findsOneWidget);
    expect(session.signedIn, isFalse);

    await tester.enterText(find.byKey(const Key('code')), '000000');
    await tester.tap(find.text('Verify'));
    await tester.pumpAndSettle();
    expect(find.text("That code isn't right"), findsOneWidget);

    await tester.enterText(find.byKey(const Key('code')), '123456');
    await tester.tap(find.text('Verify'));
    await tester.pumpAndSettle();
    expect(session.signedIn, isTrue);
  });

  testWidgets('signing in stores the session; a wrong password shows the server message', (tester) async {
    var accept = false;
    final client = MockClient((request) async {
      final body = jsonDecode(request.body) as Map;
      if (accept && body['password'] == 'right') {
        return http.Response(jsonEncode({
          'success': true,
          'data': {
            'token': 'jwt',
            'user': {'id': 7, 'name': 'Asha', 'email': 'asha@x.test', 'role': 'sales_executive', 'tenantId': 1},
          },
        }), 200);
      }
      return http.Response(jsonEncode({'success': false, 'message': 'Invalid email or password'}), 401);
    });
    final secrets = MemorySecrets();
    final session = Session(secrets);
    final api = ApiClient(baseUrl: 'http://crm.test', token: session.token, client: client);

    await tester.pumpWidget(MaterialApp(home: LoginScreen(session: session, api: api)));
    await tester.tap(find.text('Sign in'));
    await tester.pump();
    expect(find.text('Enter your email'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('email')), 'asha@x.test');
    await tester.enterText(find.byKey(const Key('password')), 'wrong');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();
    expect(find.text('Invalid email or password'), findsOneWidget);
    expect(session.signedIn, isFalse);

    accept = true;
    await tester.enterText(find.byKey(const Key('password')), 'right');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();
    expect(session.signedIn, isTrue);
    expect(session.user!.id, 7);
    expect(secrets.values['token'], 'jwt');

    // A restart restores it without the network.
    final restored = Session(secrets);
    await restored.restore();
    expect(restored.signedIn, isTrue);
    expect(restored.user!.name, 'Asha');
  });
}
