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
