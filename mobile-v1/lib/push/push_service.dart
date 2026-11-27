import 'dart:async';
import 'dart:io';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

import '../api/api_client.dart';

/// FR-M.4: push notifications via Firebase Cloud Messaging (APNs on iOS). The phone's token is
/// registered with the backend for the signed-in user; the backend pushes assignments, follow-ups
/// and manager messages to it. Without Firebase config files in the build, push is simply off and
/// notifications still arrive in the in-app inbox.
class PushService {
  PushService(this._api);

  final ApiClient _api;
  bool _available = false;
  String? _token;
  StreamSubscription<RemoteMessage>? _onMessage;
  StreamSubscription<RemoteMessage>? _onOpened;
  StreamSubscription<String>? _onRefresh;

  bool get available => _available;

  /// Starts Firebase. Returns false (and push stays off) if the app has no Firebase config.
  static Future<bool> initFirebase() async {
    try {
      await Firebase.initializeApp();
      return true;
    } catch (e) {
      debugPrint('Push notifications off: $e');
      return false;
    }
  }

  /// After sign-in: ask permission, register this phone, and listen for messages.
  /// [onMessage] runs for a push received while the app is open; [onOpened] when the user taps one.
  Future<void> start({required bool firebaseReady, required void Function(RemoteMessage) onMessage,
      required void Function(RemoteMessage) onOpened}) async {
    _available = firebaseReady;
    if (!firebaseReady) return;
    final messaging = FirebaseMessaging.instance;
    final settings = await messaging.requestPermission(alert: true, badge: true, sound: true);
    if (settings.authorizationStatus == AuthorizationStatus.denied) {
      _available = false;
      return;
    }
    // The backend sends with FCM HTTP v1 `message.token`, which takes these registration tokens.
    // (The newer register()/onRegistered API yields installation IDs for a different send model.)
    // ignore: deprecated_member_use
    _token = await messaging.getToken();
    await _register();
    // ignore: deprecated_member_use
    _onRefresh = messaging.onTokenRefresh.listen((token) {
      _token = token;
      _register();
    });
    _onMessage = FirebaseMessaging.onMessage.listen(onMessage);
    _onOpened = FirebaseMessaging.onMessageOpenedApp.listen(onOpened);
    final initial = await messaging.getInitialMessage();
    if (initial != null) onOpened(initial);
  }

  /// On sign-out: this phone stops getting the user's notifications.
  Future<void> stop() async {
    await _onMessage?.cancel();
    await _onOpened?.cancel();
    await _onRefresh?.cancel();
    if (_token != null) {
      try {
        await _api.post('/api/devices/unregister', {'token': _token, 'platform': _platform});
      } on Exception {
        // offline: the backend drops the token when FCM reports it gone
      }
    }
    _token = null;
  }

  Future<void> _register() async {
    if (_token == null) return;
    try {
      await _api.post('/api/devices', {'token': _token, 'platform': _platform});
    } on Exception catch (e) {
      debugPrint('Could not register for push yet: $e'); // retried on next start / token refresh
    }
  }

  static String get _platform => Platform.isIOS ? 'ios' : 'android';
}
