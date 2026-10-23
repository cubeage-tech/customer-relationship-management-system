/// Where the SmartCRM API lives. Set it at build time, e.g.
/// `flutter run --dart-define=API_BASE_URL=https://api.example.com`.
/// The default reaches a backend on the development machine from the Android emulator.
const String apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');

/// How often the app retries unsynced changes while it is open (FR-M.6).
const Duration syncInterval = Duration(minutes: 2);
