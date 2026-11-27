import 'package:flutter/material.dart';

/// UI-5: the same design tokens as the web app (see /design-tokens.json and frontend-v1/src/index.css).
class CrmTokens {
  static const primary = Color(0xFF454FDB);
  static const background = Color(0xFFF9FAFD);
  static const foreground = Color(0xFF111826);
  static const mutedForeground = Color(0xFF687284);
  static const border = Color(0xFFE2E5EA);
  static const destructive = Color(0xFFE62B34);
  static const success = Color(0xFF00A365);
  static const warning = Color(0xFFED990E);
  static const radius = 14.0;
}

ThemeData crmTheme(Brightness brightness) {
  final light = brightness == Brightness.light;
  final scheme = ColorScheme.fromSeed(
    seedColor: CrmTokens.primary,
    brightness: brightness,
    primary: light ? CrmTokens.primary : null,
    error: CrmTokens.destructive,
    surface: light ? CrmTokens.background : null,
    onSurface: light ? CrmTokens.foreground : null,
    outline: light ? CrmTokens.border : null,
  );
  final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(CrmTokens.radius));
  return ThemeData(
    colorScheme: scheme,
    useMaterial3: true,
    cardTheme: CardThemeData(shape: shape, elevation: 0, color: light ? Colors.white : null,
        margin: const EdgeInsets.symmetric(vertical: 4)),
    inputDecorationTheme: InputDecorationTheme(border: OutlineInputBorder(borderRadius: BorderRadius.circular(CrmTokens.radius - 4))),
    filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(shape: shape)),
  );
}
