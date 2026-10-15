import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';

enum TtsState { fermo, inLettura, inPausa }

/// Lettura vocale del riassunto con la sintesi nativa del sistema.
/// Nessun testo lascia il dispositivo.
class TtsService extends ChangeNotifier {
  TtsService({FlutterTts? engine}) : _tts = engine ?? FlutterTts() {
    _configure();
  }

  final FlutterTts _tts;
  TtsState _state = TtsState.fermo;
  bool _ready = false;
  bool _pauseSupported = true;

  TtsState get state => _state;
  bool get isSpeaking => _state == TtsState.inLettura;
  bool get pauseSupported => _pauseSupported;

  Future<void> _configure() async {
    try {
      await _tts.awaitSpeakCompletion(true);
      await _tts.setLanguage('it-IT');
      await _tts.setSpeechRate(0.5);
      await _tts.setPitch(1.0);
      _tts.setCompletionHandler(() => _set(TtsState.fermo));
      _tts.setCancelHandler(() => _set(TtsState.fermo));
      _tts.setPauseHandler(() => _set(TtsState.inPausa));
      _tts.setContinueHandler(() => _set(TtsState.inLettura));
      _tts.setErrorHandler((Object? message) {
        debugPrint('Errore sintesi vocale: $message');
        _set(TtsState.fermo);
      });
      _ready = true;
    } on Object catch (e) {
      debugPrint('Sintesi vocale non inizializzata: $e');
      _ready = false;
    }
  }

  /// Verifica se la voce italiana è installata sul dispositivo.
  Future<bool> italianAvailable() async {
    try {
      final Object? langs = await _tts.getLanguages;
      if (langs is List) {
        return langs.any(
          (Object? l) => l.toString().toLowerCase().startsWith('it'),
        );
      }
    } on Object catch (e) {
      debugPrint('Elenco lingue TTS non disponibile: $e');
    }
    return true;
  }

  Future<void> apply({double? rate, double? pitch}) async {
    if (rate != null) await _tts.setSpeechRate(rate);
    if (pitch != null) await _tts.setPitch(pitch);
  }

  Future<void> speak(String text) async {
    if (!_ready) await _configure();
    final String content = text.trim();
    if (content.isEmpty) return;
    await stop();
    _set(TtsState.inLettura);
    try {
      await _tts.speak(content);
    } on Object catch (e) {
      debugPrint('speak fallito: $e');
    } finally {
      if (_state == TtsState.inLettura) _set(TtsState.fermo);
    }
  }

  Future<void> pause() async {
    if (_state != TtsState.inLettura) return;
    try {
      await _tts.pause();
      _set(TtsState.inPausa);
    } on Object catch (e) {
      // Su alcune versioni di Android `pause` non è implementata.
      debugPrint('pause non supportata, fermo la lettura: $e');
      _pauseSupported = false;
      await stop();
    }
  }

  Future<void> stop() async {
    try {
      await _tts.stop();
    } on Object catch (e) {
      debugPrint('stop fallito: $e');
    }
    _set(TtsState.fermo);
  }

  void _set(TtsState s) {
    if (_state == s) return;
    _state = s;
    notifyListeners();
  }

  @override
  void dispose() {
    _tts.stop();
    super.dispose();
  }
}
