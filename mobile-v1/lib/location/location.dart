import 'package:geolocator/geolocator.dart';

/// A position, or null when the user declines location or it isn't available (FR-M.1, FR-M.5:
/// location is optional — the visit or check-in is still recorded).
Future<Position?> currentPosition() async {
  try {
    if (!await Geolocator.isLocationServiceEnabled()) return null;
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) return null;
    return await Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 10)),
    );
  } catch (_) {
    return null;
  }
}

/// Six decimals, as the server stores them.
double round6(double v) => (v * 1e6).round() / 1e6;
