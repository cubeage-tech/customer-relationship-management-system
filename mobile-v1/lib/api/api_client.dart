import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

/// The server answered with an error (4xx/5xx); [message] is safe to show.
class ApiException implements Exception {
  ApiException(this.statusCode, this.message);

  final int statusCode;
  final String message;

  /// The request itself was refused — retrying it unchanged won't help.
  bool get isClientError => statusCode >= 400 && statusCode < 500;

  @override
  String toString() => message;
}

/// The server couldn't be reached (offline, timeout). Safe to retry later.
class NetworkException implements Exception {
  NetworkException(this.message);

  final String message;

  @override
  String toString() => message;
}

typedef TokenProvider = Future<String?> Function();

/// JSON client for the SmartCRM API. Successful calls return the response's `data`;
/// failures throw [ApiException] or [NetworkException].
class ApiClient {
  ApiClient({required this.baseUrl, required this.token, http.Client? client}) : _http = client ?? http.Client();

  final String baseUrl;
  final TokenProvider token;
  final http.Client _http;

  /// Called when the server rejects the session (expired token), so the app can sign out.
  void Function()? onUnauthorized;

  /// API-G01: renews the session (refresh token) after a 401; the request is then sent once more.
  Future<bool> Function()? refreshSession;

  static const _timeout = Duration(seconds: 20);

  Future<dynamic> get(String path, [Map<String, String>? query]) => send('GET', path, query: query);

  Future<dynamic> post(String path, [Object? body]) => send('POST', path, body: body);

  Future<dynamic> put(String path, [Object? body]) => send('PUT', path, body: body);

  Future<dynamic> patch(String path, [Object? body]) => send('PATCH', path, body: body);

  Future<dynamic> send(String method, String path, {Object? body, Map<String, String>? query, bool retried = false}) async {
    var uri = Uri.parse('$baseUrl$path');
    if (query != null && query.isNotEmpty) {
      uri = uri.replace(queryParameters: query);
    }
    final request = http.Request(method, uri)..headers['Accept'] = 'application/json';
    final bearer = await token();
    if (bearer != null) {
      request.headers['Authorization'] = 'Bearer $bearer';
    }
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }

    final http.Response response;
    try {
      response = await http.Response.fromStream(await _http.send(request).timeout(_timeout));
    } on SocketException {
      throw NetworkException('No connection');
    } on TimeoutException {
      throw NetworkException('The server took too long to answer');
    } on http.ClientException catch (e) {
      throw NetworkException(e.message);
    }

    // Spring sends JSON without a charset, which `http` would read as Latin-1: decode as UTF-8.
    final decoded = _decode(utf8.decode(response.bodyBytes, allowMalformed: true));
    if (response.statusCode >= 300) {
      if (response.statusCode == 401) {
        if (bearer != null && !retried && !path.startsWith('/auth/') && refreshSession != null && await refreshSession!()) {
          return send(method, path, body: body, query: query, retried: true);
        }
        onUnauthorized?.call();
      }
      final message = decoded is Map && decoded['message'] is String
          ? decoded['message'] as String
          : 'Request failed (${response.statusCode})';
      throw ApiException(response.statusCode, message);
    }
    return decoded is Map ? decoded['data'] : decoded;
  }

  static dynamic _decode(String body) {
    if (body.isEmpty) return null;
    try {
      return jsonDecode(body);
    } on FormatException {
      return null;
    }
  }
}
