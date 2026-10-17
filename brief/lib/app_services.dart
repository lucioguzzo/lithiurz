import 'package:flutter/material.dart';

import 'core/constants.dart';
import 'data/note_repository.dart';
import 'data/settings.dart';
import 'services/ads/ads_service.dart';
import 'services/export/export_service.dart';
import 'services/pipeline/processing_controller.dart';
import 'services/share/share_intake_service.dart';
import 'services/summary/summary_api_client.dart';
import 'services/summary/summary_service.dart';
import 'services/transcription/cloud_engine.dart';
import 'services/transcription/engine.dart';
import 'services/transcription/model_manager.dart';
import 'services/transcription/transcription_service.dart';
import 'services/tts/tts_service.dart';

/// Contenitore dei servizi condivisi, propagato con un [InheritedWidget].
/// Niente framework di dependency injection: l'app è piccola e così resta
/// evidente chi dipende da cosa.
class AppServices {
  AppServices({
    required this.settings,
    required this.notes,
    required this.models,
    required this.systemEngine,
    required this.cloudEngine,
    required this.transcription,
    required this.share,
    required this.tts,
    required this.ads,
    required this.export,
    required this.processing,
    required this.summaryClient,
  });

  final SettingsRepository settings;
  final NoteRepository notes;
  final ModelManager models;

  /// Serve alle impostazioni per dire se il motore del telefono c'è.
  final SystemSpeechEngine systemEngine;

  /// Serve alle impostazioni per dire se il cloud è configurato.
  final CloudTranscriptionEngine cloudEngine;

  final TranscriptionService transcription;
  final ShareIntakeService share;
  final TtsService tts;
  final AdsService ads;
  final ExportService export;
  final ProcessingController processing;
  final SummaryApiClient summaryClient;

  /// Costruisce l'albero dei servizi a partire dalle preferenze salvate.
  static Future<AppServices> bootstrap({
    required SettingsRepository settings,
    required NoteRepository notes,
  }) async {
    // L'indirizzo scelto a mano vince su quello cucito nella build: così
    // una build già pronta resta modificabile da chi vuole il proprio
    // server, senza che nessun altro debba configurare niente.
    String resolveBackendUrl() => settings.value.backendUrl.isNotEmpty
        ? settings.value.backendUrl
        : BuildConfig.backendUrl;

    final SummaryApiClient client = SummaryApiClient(
      baseUrl: resolveBackendUrl(),
      deviceId: settings.deviceId(),
    );
    // Se l'utente cambia server nelle impostazioni, il client lo recepisce
    // senza riavviare l'app.
    settings.addListener(() => client.baseUrl = resolveBackendUrl());
    final AdsService ads = AdsService(enabled: settings.value.adsEnabled);
    await ads.initialize();

    final ModelManager models = ModelManager();
    final SystemSpeechEngine systemEngine = SystemSpeechEngine();
    final WhisperEngine whisperEngine = WhisperEngine(models: models);
    final CloudTranscriptionEngine cloudEngine = CloudTranscriptionEngine(
      baseUrl: resolveBackendUrl,
      deviceId: settings.deviceId(),
    );

    TranscriptionEngine engineFor(TranscriptionEngineId id) =>
        switch (id) {
          TranscriptionEngineId.sistema => systemEngine,
          TranscriptionEngineId.cloud => cloudEngine,
          TranscriptionEngineId.whisper => whisperEngine,
        };

    final TranscriptionService transcription = TranscriptionService(
      models: models,
      engine: engineFor(settings.value.transcriptionEngine),
    );
    // Il cambio di motore nelle impostazioni ha effetto dalla prossima
    // elaborazione, senza riavviare l'app.
    settings.addListener(
      () => transcription.engine = engineFor(settings.value.transcriptionEngine),
    );
    final ProcessingController processing = ProcessingController(
      settings: settings,
      notes: notes,
      transcription: transcription,
      summaries: SummaryService(client: client),
    );

    return AppServices(
      settings: settings,
      notes: notes,
      models: models,
      systemEngine: systemEngine,
      cloudEngine: cloudEngine,
      transcription: transcription,
      share: ShareIntakeService(),
      tts: TtsService(),
      ads: ads,
      export: const ExportService(),
      processing: processing,
      summaryClient: client,
    );
  }
}

class ServicesScope extends InheritedWidget {
  const ServicesScope({
    super.key,
    required this.services,
    required super.child,
  });

  final AppServices services;

  static AppServices of(BuildContext context) {
    final ServicesScope? scope =
        context.dependOnInheritedWidgetOfExactType<ServicesScope>();
    assert(scope != null, 'ServicesScope non trovato nell\'albero dei widget');
    return scope!.services;
  }

  @override
  bool updateShouldNotify(ServicesScope oldWidget) =>
      oldWidget.services != services;
}
