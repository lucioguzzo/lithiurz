import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:whisper_ggml/whisper_ggml.dart';

import '../core/constants.dart';

/// Preferenze dell'utente. Immutabile: le modifiche passano da
/// [SettingsRepository.update].
@immutable
class AppSettings {
  const AppSettings({
    this.modelName = 'base',
    this.language = 'it',
    this.summaryStyle = SummaryStyle.standard,
    this.engine = SummaryEngine.locale,
    this.cloudConsentAt,
    this.historyEnabled = true,
    this.keepAudio = false,
    this.adsEnabled = true,
    this.ttsRate = 0.5,
    this.ttsPitch = 1.0,
    this.backendUrl = '',
    this.themeMode = ThemeModeSetting.sistema,
  });

  final String modelName;
  final String language;
  final SummaryStyle summaryStyle;
  final SummaryEngine engine;

  /// Data del consenso all'invio del testo al cloud (null = mai dato).
  final DateTime? cloudConsentAt;
  final bool historyEnabled;
  final bool keepAudio;
  final bool adsEnabled;
  final double ttsRate;
  final double ttsPitch;
  final String backendUrl;
  final ThemeModeSetting themeMode;

  bool get cloudAllowed =>
      engine == SummaryEngine.cloud && cloudConsentAt != null;

  WhisperModel get whisperModel => switch (modelName) {
        'tiny' => WhisperModel.tiny,
        'small' => WhisperModel.small,
        'medium' => WhisperModel.medium,
        _ => WhisperModel.base,
      };

  AppSettings copyWith({
    String? modelName,
    String? language,
    SummaryStyle? summaryStyle,
    SummaryEngine? engine,
    DateTime? cloudConsentAt,
    bool clearConsent = false,
    bool? historyEnabled,
    bool? keepAudio,
    bool? adsEnabled,
    double? ttsRate,
    double? ttsPitch,
    String? backendUrl,
    ThemeModeSetting? themeMode,
  }) =>
      AppSettings(
        modelName: modelName ?? this.modelName,
        language: language ?? this.language,
        summaryStyle: summaryStyle ?? this.summaryStyle,
        engine: engine ?? this.engine,
        cloudConsentAt:
            clearConsent ? null : (cloudConsentAt ?? this.cloudConsentAt),
        historyEnabled: historyEnabled ?? this.historyEnabled,
        keepAudio: keepAudio ?? this.keepAudio,
        adsEnabled: adsEnabled ?? this.adsEnabled,
        ttsRate: ttsRate ?? this.ttsRate,
        ttsPitch: ttsPitch ?? this.ttsPitch,
        backendUrl: backendUrl ?? this.backendUrl,
        themeMode: themeMode ?? this.themeMode,
      );
}

enum ThemeModeSetting { sistema, chiaro, scuro }

/// Lettura/scrittura delle preferenze su [SharedPreferences].
class SettingsRepository extends ValueNotifier<AppSettings> {
  SettingsRepository(this._prefs) : super(_read(_prefs));

  final SharedPreferences _prefs;

  static Future<SettingsRepository> open() async =>
      SettingsRepository(await SharedPreferences.getInstance());

  static const String _kModel = 'model_name';
  static const String _kLang = 'language';
  static const String _kStyle = 'summary_style';
  static const String _kEngine = 'summary_engine';
  static const String _kConsent = 'cloud_consent_at';
  static const String _kHistory = 'history_enabled';
  static const String _kKeepAudio = 'keep_audio';
  static const String _kAds = 'ads_enabled';
  static const String _kRate = 'tts_rate';
  static const String _kPitch = 'tts_pitch';
  static const String _kBackend = 'backend_url';
  static const String _kTheme = 'theme_mode';

  static AppSettings _read(SharedPreferences p) {
    final int? consent = p.getInt(_kConsent);
    return AppSettings(
      modelName: p.getString(_kModel) ?? 'base',
      language: p.getString(_kLang) ?? 'it',
      summaryStyle: SummaryStyle.fromId(p.getString(_kStyle)),
      engine: SummaryEngine.fromId(p.getString(_kEngine)),
      cloudConsentAt:
          consent == null ? null : DateTime.fromMillisecondsSinceEpoch(consent),
      historyEnabled: p.getBool(_kHistory) ?? true,
      keepAudio: p.getBool(_kKeepAudio) ?? false,
      adsEnabled: p.getBool(_kAds) ?? true,
      ttsRate: p.getDouble(_kRate) ?? 0.5,
      ttsPitch: p.getDouble(_kPitch) ?? 1.0,
      backendUrl: p.getString(_kBackend) ?? '',
      themeMode:
          ThemeModeSetting.values[(p.getInt(_kTheme) ?? 0).clamp(0, 2)],
    );
  }

  Future<void> update(AppSettings next) async {
    value = next;
    notifyListeners();
    await Future.wait<Object?>(<Future<Object?>>[
      _prefs.setString(_kModel, next.modelName),
      _prefs.setString(_kLang, next.language),
      _prefs.setString(_kStyle, next.summaryStyle.id),
      _prefs.setString(_kEngine, next.engine.id),
      next.cloudConsentAt == null
          ? _prefs.remove(_kConsent)
          : _prefs.setInt(_kConsent, next.cloudConsentAt!.millisecondsSinceEpoch),
      _prefs.setBool(_kHistory, next.historyEnabled),
      _prefs.setBool(_kKeepAudio, next.keepAudio),
      _prefs.setBool(_kAds, next.adsEnabled),
      _prefs.setDouble(_kRate, next.ttsRate),
      _prefs.setDouble(_kPitch, next.ttsPitch),
      _prefs.setString(_kBackend, next.backendUrl),
      _prefs.setInt(_kTheme, next.themeMode.index),
    ]);
  }

  /// Identificativo anonimo del dispositivo, usato dal backend solo per
  /// applicare le quote. Non contiene dati personali.
  String deviceId() {
    const String key = 'device_id';
    final String? existing = _prefs.getString(key);
    if (existing != null && existing.length >= 16) return existing;
    final String generated = _randomId();
    _prefs.setString(key, generated);
    return generated;
  }

  static String _randomId() {
    const String chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    final int seed = DateTime.now().microsecondsSinceEpoch;
    final StringBuffer b = StringBuffer();
    int x = seed;
    for (int i = 0; i < 24; i++) {
      x = (x * 1103515245 + 12345) & 0x7fffffff;
      b.write(chars[x % chars.length]);
    }
    return b.toString();
  }
}
