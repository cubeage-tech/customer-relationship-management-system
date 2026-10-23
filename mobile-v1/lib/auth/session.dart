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

class Session extends ChangeNotifier {
  Session(this._secrets);

  static const _tokenKey = 'token';
  static const _userKey = 'user';

  final SecretStore _secrets;
  String? _token;
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

  Future<void> signIn(ApiClient api, String email, String password) async {
    final data = await api.post('/auth/login', {'email': email.trim(), 'password': password}) as Map;
    final token = data['token'] as String;
    final signedIn = SessionUser.fromJson((data['user'] as Map).cast<String, dynamic>());
    await _secrets.write(_tokenKey, token);
    await _secrets.write(_userKey, jsonEncode(signedIn.toJson()));
    _token = token;
    user = signedIn;
    notifyListeners();
  }

  Future<void> signOut() async {
    await _secrets.delete(_tokenKey);
    await _secrets.delete(_userKey);
    _token = null;
    user = null;
    notifyListeners();
  }
}
