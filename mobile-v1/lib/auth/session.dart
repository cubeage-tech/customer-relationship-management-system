import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../api/api_client.dart';

/// The signed-in user, as returned by POST /auth/login.
class SessionUser {
  SessionUser({required this.id, required this.name, required this.email, required this.role});

  final int id;
  final String name;
  final String email;
  final String role;

  bool get isManager => role == 'admin' || role == 'sales_manager' || role == 'executive_owner';

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'email': email, 'role': role};

  factory SessionUser.fromJson(Map<String, dynamic> json) => SessionUser(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String? ?? '',
        email: json['email'] as String? ?? '',
        role: json['role'] as String? ?? '',
      );
}

/// Keeps the API token in the phone's secure storage (Keychain / Keystore), never in plain files.
abstract class SecretStore {
  Future<String?> read(String key);

  Future<void> write(String key, String value);

  Future<void> delete(String key);
}

class SecureSecretStore implements SecretStore {
  static const _storage = FlutterSecureStorage();

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) => _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

/// SEC-1c: the account's role requires two-step sign-in and it isn't set up yet.
class MfaSetupRequired implements Exception {
  @override
  String toString() => 'Set up two-step sign-in in the SmartCRM web app first, then sign in here.';
}

class Session extends ChangeNotifier {
  Session(this._secrets);

  static const _tokenKey = 'token';
  static const _refreshKey = 'refreshToken';
  static const _userKey = 'user';

  final SecretStore _secrets;
  String? _token;
  Future<bool>? _refreshing;
  SessionUser? user;

  bool get signedIn => _token != null && user != null;

  Future<String?> token() async => _token;

  /// Restores the previous sign-in, so the app opens straight to the data even offline.
  Future<void> restore() async {
    _token = await _secrets.read(_tokenKey);
    final rawUser = await _secrets.read(_userKey);
    user = rawUser != null ? SessionUser.fromJson((jsonDecode(rawUser) as Map).cast<String, dynamic>()) : null;
    if (user == null) _token = null;
    notifyListeners();
  }

  /// Signs in with email and password. Returns null when signed in, or — SEC-1c, two-step sign-in —
  /// the challenge token to finish with [verifyMfa]. Throws [MfaSetupRequired] when the account
  /// still has to set up its authenticator app (done in the web app).
  Future<String?> signIn(ApiClient api, String email, String password) async {
    final data = await api.post('/auth/login', {'email': email.trim(), 'password': password}) as Map;
    if (data['mfaSetupRequired'] == true) throw MfaSetupRequired();
    if (data['mfaRequired'] == true) return data['mfaToken'] as String;
    await _store(data);
    return null;
  }

  /// SEC-1c: the authenticator code (or a recovery code) after the password.
  Future<void> verifyMfa(ApiClient api, String mfaToken, String code) async {
    final data = await api.post('/auth/mfa/verify', {'mfaToken': mfaToken, 'code': code.trim()}) as Map;
    await _store(data);
  }

  /// API-G01: trades the stored refresh token for a new access token (and a new refresh token).
  /// Concurrent callers share one request. False when the session can't be renewed.
  Future<bool> refresh(ApiClient api) {
    return _refreshing ??= () async {
      try {
        final refreshToken = await _secrets.read(_refreshKey);
        if (refreshToken == null) return false;
        final data = await api.post('/auth/refresh', {'refreshToken': refreshToken}) as Map;
        await _store(data);
        return true;
      } on ApiException {
        return false;
      } on NetworkException {
        return false;
      } finally {
        _refreshing = null;
      }
    }();
  }

  Future<void> _store(Map data) async {
    final token = data['token'] as String;
    final signedIn = SessionUser.fromJson((data['user'] as Map).cast<String, dynamic>());
    await _secrets.write(_tokenKey, token);
    if (data['refreshToken'] is String) await _secrets.write(_refreshKey, data['refreshToken'] as String);
    await _secrets.write(_userKey, jsonEncode(signedIn.toJson()));
    _token = token;
    user = signedIn;
    notifyListeners();
  }

  Future<void> signOut() async {
    await _secrets.delete(_tokenKey);
    await _secrets.delete(_refreshKey);
    await _secrets.delete(_userKey);
    _token = null;
    user = null;
    notifyListeners();
  }
}
