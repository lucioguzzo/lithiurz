import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Palette e tipografia dell'app.
///
/// L'impianto e' scuro per una ragione pratica prima che estetica: l'app si
/// usa in palestra, spesso con poca luce, e il modello 3D del SiFu si legge
/// molto meglio su fondo scuro.
abstract final class AppColors {
  static const ink = Color(0xFF0A0C11);
  static const surface = Color(0xFF12151C);
  static const surfaceHigh = Color(0xFF1A1E27);
  static const surfaceTop = Color(0xFF222834);
  static const line = Color(0xFF272D3A);

  static const gold = Color(0xFFD9B15C);
  static const goldSoft = Color(0xFF8C7539);
  static const jade = Color(0xFF4E9B87);
  static const cinnabar = Color(0xFFB24A3E);

  static const text = Color(0xFFF1EEE7);
  static const textMuted = Color(0xFF98A0AF);
  static const textFaint = Color(0xFF5F6877);

  /// Colore che identifica ciascuna sezione del menu.
  static const sectionColors = <String, Color>{
    'storia': Color(0xFFB98A4A),
    'principi': Color(0xFF4E9B87),
    'forme': Color(0xFF7C7FC4),
    'sifu3d': Color(0xFFD9B15C),
    'video': Color(0xFFB24A3E),
  };
}

abstract final class AppText {
  static const display = 'Cormorant';
  static const body = 'Inter';

  /// Font cinese ridotto ai soli caratteri usati nell'app (31 KB).
  /// Senza di esso i nomi delle tecniche apparirebbero come quadratini vuoti
  /// su ogni dispositivo privo di un font CJK di sistema.
  static const cjk = 'NotoSerifTC';

  /// Ripiego per i caratteri che i font latini non hanno.
  static const fallback = <String>[cjk];
}

abstract final class AppTheme {
  static const overlayStyle = SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    statusBarBrightness: Brightness.dark,
    systemNavigationBarColor: AppColors.ink,
    systemNavigationBarIconBrightness: Brightness.light,
  );

  static ThemeData build() {
    const scheme = ColorScheme.dark(
      primary: AppColors.gold,
      onPrimary: AppColors.ink,
      secondary: AppColors.jade,
      surface: AppColors.surface,
      onSurface: AppColors.text,
      error: AppColors.cinnabar,
    );

    TextStyle d(double size, FontWeight w, {double? h, double? ls}) => TextStyle(
          fontFamily: AppText.display,
          fontFamilyFallback: AppText.fallback,
          fontSize: size,
          fontWeight: w,
          height: h,
          letterSpacing: ls,
          color: AppColors.text,
        );
    TextStyle b(double size, FontWeight w, {double? h, double? ls, Color? c}) =>
        TextStyle(
          fontFamily: AppText.body,
          fontFamilyFallback: AppText.fallback,
          fontSize: size,
          fontWeight: w,
          height: h,
          letterSpacing: ls,
          color: c ?? AppColors.text,
        );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.ink,
      fontFamily: AppText.body,
      splashFactory: InkSparkle.splashFactory,
      textTheme: TextTheme(
        displayLarge: d(44, FontWeight.w700, h: 1.06, ls: -0.5),
        displayMedium: d(34, FontWeight.w700, h: 1.10, ls: -0.3),
        displaySmall: d(28, FontWeight.w600, h: 1.15),
        headlineMedium: d(24, FontWeight.w600, h: 1.2),
        headlineSmall: d(20, FontWeight.w600, h: 1.25),
        titleLarge: b(17, FontWeight.w600, h: 1.3),
        titleMedium: b(15, FontWeight.w600, h: 1.35),
        bodyLarge: b(16, FontWeight.w400, h: 1.62, c: AppColors.text),
        bodyMedium: b(14.5, FontWeight.w400, h: 1.58, c: AppColors.textMuted),
        bodySmall: b(13, FontWeight.w400, h: 1.5, c: AppColors.textMuted),
        labelLarge: b(13, FontWeight.w600, ls: 0.6),
        labelMedium: b(11.5, FontWeight.w600, ls: 1.2, c: AppColors.textFaint),
      ),
      dividerTheme: const DividerThemeData(
          color: AppColors.line, thickness: 1, space: 1),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        systemOverlayStyle: overlayStyle,
        iconTheme: IconThemeData(color: AppColors.text),
      ),
      sliderTheme: const SliderThemeData(
        activeTrackColor: AppColors.gold,
        inactiveTrackColor: AppColors.line,
        thumbColor: AppColors.gold,
        overlayColor: Color(0x22D9B15C),
        trackHeight: 2,
      ),
    );
  }
}
