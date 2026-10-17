import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:whisper_ggml/whisper_ggml.dart';

import '../../core/constants.dart';
import '../../core/failures.dart';
import 'model_manager.dart';

/// Motori di trascrizione disponibili.
enum TranscriptionEngineId {
  /// Riconoscitore vocale del sistema operativo: lo stesso usato dalle app
  /// di trascrizione del telefono. Sulla carta è gratuito, offline e molto
  /// più rapido, perché ottimizzato per quel chip.
  ///
  /// Nella prova sul campo però non ha mantenuto la promessa: su un nubia
  /// NX713J con Android 13 si ferma dopo i primi secondi e restituisce una
  /// manciata di parole, qualunque sia la lunghezza degli spezzoni e per
  /// quanto si allunghino le soglie di silenzio. Resta selezionabile come
  /// opzione sperimentale per chi ha un telefono che lo implementa meglio,
  /// ma non è il motore predefinito e non lo diventerà.
  sistema('sistema', 'Motore del telefono', experimental: true),

  /// whisper.cpp con il modello Small compresso: funziona ovunque, ma
  /// impiega all'incirca il tempo dell'audio. È il motore predefinito.
  whisper('whisper', 'Whisper (modello scaricato)'),

  /// Trascrizione eseguita dal backend di BRIEF, che interroga un modello
  /// grande (Gemini). È il più accurato, ma è l'unico che fa uscire
  /// l'audio dal telefono: richiede connessione, un server configurato e
  /// il consenso esplicito dell'utente.
  cloud('cloud', 'Cloud (server di BRIEF)');

  const TranscriptionEngineId(this.id, this.label,
      {this.experimental = false});

  final String id;
  final String label;

  /// Motore non affidabile su tutti i dispositivi: va scelto dall'utente
  /// in modo esplicito e segnalato come tale nell'interfaccia.
  final bool experimental;

  /// Il motore su cui ricade l'app quando non c'è una scelta valida.
  static const TranscriptionEngineId predefinito =
      TranscriptionEngineId.whisper;

  static TranscriptionEngineId fromId(String? id) =>
      TranscriptionEngineId.values.firstWhere(
        (TranscriptionEngineId e) => e.id == id,
        orElse: () => predefinito,
      );
}

/// Un motore che trascrive un blocco audio già pronto.
///
/// L'audio in ingresso è sempre un WAV PCM 16 bit, 16 kHz, mono: lo
/// prepara [MediaTools], così i motori non si occupano di conversioni.
abstract class TranscriptionEngine {
  TranscriptionEngineId get id;

  /// Durata preferita dei blocchi in cui spezzare l'audio.
  ///
  /// Non è un dettaglio estetico: il riconoscitore di sistema è tarato sul
  /// dettato e tende a considerare finito il discorso alle pause lunghe,
  /// quindi lavora meglio su spezzoni brevi, uno per sessione. whisper
  /// invece rende meglio su blocchi più lunghi, dove ha più contesto.
  Duration get preferredChunkLength;

  /// `true` se il motore è utilizzabile adesso su questo dispositivo.
  Future<bool> isAvailable();

  /// Trascrive il file e restituisce il testo.
  Future<String> transcribeFile({
    required String wavPath,
    required String language,
    void Function(int percent)? onProgress,
  });

  /// Libera le risorse trattenute fra un blocco e l'altro.
  Future<void> release() async {}
}

/// whisper.cpp in locale.
class WhisperEngine implements TranscriptionEngine {
  WhisperEngine({
    required this.models,
    WhisperController? controller,
    this.keepLoaded = true,
    this.independentChunks = true,
  }) : _whisper = controller ?? WhisperController();

  final ModelManager models;
  final WhisperController _whisper;

  /// Tiene il modello in memoria fra un blocco e l'altro: senza, ogni
  /// blocco pagherebbe di nuovo i secondi di caricamento.
  final bool keepLoaded;

  /// Decodifica ogni blocco senza il contesto del precedente, così una
  /// frase sbagliata non si propaga per tutta la registrazione.
  final bool independentChunks;

  @override
  TranscriptionEngineId get id => TranscriptionEngineId.whisper;

  @override
  Duration get preferredChunkLength => AudioLimits.chunkTarget;

  @override
  Future<bool> isAvailable() => models.isInstalled(ModelOption.small);

  @override
  Future<String> transcribeFile({
    required String wavPath,
    required String language,
    void Function(int percent)? onProgress,
  }) async {
    final String? vadPath = await models.installedVadPath();
    final result = await _whisper.transcribe(
      model: ModelOption.small.model,
      audioPath: wavPath,
      lang: language,
      maxContext: independentChunks ? 0 : null,
      keepModelLoaded: keepLoaded,
      vadModelPath: vadPath,
      vadSpeechPadMs: 100,
      onProgress: onProgress,
    );
    if (result == null) {
      throw const AppFailure(
        'Trascrizione non riuscita. Se il problema si ripete, prova il '
        'motore del telefono dalle impostazioni.',
      );
    }
    return result.transcription.text.trim();
  }

  @override
  Future<void> release() => _whisper.releaseModel();
}

/// Riconoscitore vocale del sistema operativo, raggiunto via canale nativo.
///
/// Su Android è lo stesso motore delle app di trascrizione del telefono
/// (richiede Android 13 per leggere da file); su iOS è `SFSpeechRecognizer`
/// in modalità su dispositivo.
class SystemSpeechEngine implements TranscriptionEngine {
  SystemSpeechEngine({MethodChannel? channel})
      : _channel = channel ?? const MethodChannel('brief/system_speech');

  final MethodChannel _channel;
  bool? _available;

  @override
  TranscriptionEngineId get id => TranscriptionEngineId.sistema;

  /// Spezzoni brevi: una sessione di riconoscimento per ciascuno. Se il
  /// motore chiude in anticipo su una pausa si perdono pochi secondi,
  /// non metà registrazione.
  @override
  Duration get preferredChunkLength => const Duration(seconds: 25);

  @override
  Future<bool> isAvailable() async {
    if (_available != null) return _available!;
    try {
      final bool? ok = await _channel.invokeMethod<bool>('isAvailable');
      _available = ok ?? false;
    } on PlatformException catch (e) {
      debugPrint('Motore di sistema non disponibile: ${e.message}');
      _available = false;
    } on MissingPluginException {
      _available = false;
    }
    return _available!;
  }

  /// Descrizione del motivo per cui il motore non è utilizzabile, da
  /// mostrare nelle impostazioni.
  Future<String?> unavailableReason() async {
    try {
      return await _channel.invokeMethod<String>('unavailableReason');
    } on Object {
      return null;
    }
  }

  @override
  Future<String> transcribeFile({
    required String wavPath,
    required String language,
    void Function(int percent)? onProgress,
  }) async {
    try {
      // Il motore di sistema non espone un avanzamento: si segnala solo
      // l'inizio, il resto lo scandisce la successione dei blocchi.
      onProgress?.call(5);
      final String? text = await _channel.invokeMethod<String>(
        'transcribeFile',
        <String, Object?>{
          'path': wavPath,
          'language': _bcp47(language),
        },
      );
      onProgress?.call(100);
      return (text ?? '').trim();
    } on PlatformException catch (e) {
      throw AppFailure(
        e.message ?? 'Il motore del telefono non è riuscito a trascrivere.',
        cause: e.code,
      );
    } on MissingPluginException catch (e) {
      throw AppFailure(
        'Il motore del telefono non è disponibile in questa versione '
        'dell\'app.',
        cause: e,
      );
    }
  }

  @override
  Future<void> release() async {
    // Il motore di sistema chiude da sé la sessione a fine file.
  }

  /// Il riconoscitore di sistema vuole un codice lingua completo.
  static String _bcp47(String code) => switch (code) {
        'it' => 'it-IT',
        'en' => 'en-US',
        'es' => 'es-ES',
        'fr' => 'fr-FR',
        'de' => 'de-DE',
        'auto' => 'it-IT',
        _ => code.contains('-') ? code : '$code-${code.toUpperCase()}',
      };
}
