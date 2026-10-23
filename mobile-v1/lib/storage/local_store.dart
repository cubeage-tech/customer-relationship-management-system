import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

/// FR-M.6: what the app keeps on the phone — cached lists and the queue of unsynced changes —
/// as JSON values by key, so the core screens work without a connection.
abstract class LocalStore {
  Future<dynamic> read(String key);

  Future<void> write(String key, Object? value);

  /// Removes everything (on sign-out).
  Future<void> clear();
}

/// One JSON file per key in the app's private documents folder.
class FileStore implements LocalStore {
  Directory? _dir;

  Future<Directory> _folder() async {
    if (_dir != null) return _dir!;
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}${Platform.pathSeparator}smartcrm');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return _dir = dir;
  }

  Future<File> _file(String key) async => File('${(await _folder()).path}${Platform.pathSeparator}$key.json');

  @override
  Future<dynamic> read(String key) async {
    final file = await _file(key);
    if (!await file.exists()) return null;
    try {
      return jsonDecode(await file.readAsString());
    } on FormatException {
      return null; // a half-written file from a crash: treat as empty
    }
  }

  @override
  Future<void> write(String key, Object? value) async {
    final file = await _file(key);
    // Write then rename, so a crash mid-write never leaves a broken file.
    final tmp = File('${file.path}.tmp');
    await tmp.writeAsString(jsonEncode(value), flush: true);
    await tmp.rename(file.path);
  }

  @override
  Future<void> clear() async {
    final dir = await _folder();
    if (await dir.exists()) {
      await dir.delete(recursive: true);
    }
    _dir = null;
  }
}

/// In memory, for tests.
class MemoryStore implements LocalStore {
  final Map<String, String> _data = {};

  @override
  Future<dynamic> read(String key) async => _data.containsKey(key) ? jsonDecode(_data[key]!) : null;

  @override
  Future<void> write(String key, Object? value) async => _data[key] = jsonEncode(value);

  @override
  Future<void> clear() async => _data.clear();
}
