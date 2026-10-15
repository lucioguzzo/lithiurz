import 'package:flutter/material.dart';

import 'data/note_repository.dart';
import 'data/settings.dart';
import 'services/ads/ads_service.dart';
import 'services/export/export_service.dart';
import 'services/pipeline/processing_controller.dart';
import 'services/share/share_intake_service.dart';
import 'services/summary/summary_api_client.dart';
import 'services/summary/summary_service.dart';
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
    String resolveBackendUrl() => settings.value.backendUrl.isNotEmpty
        ? settings.value.backendUrl
        : const String.fromEnvironment('BRIEF_BACKEND_URL');

    final SummaryApiClient client = SummaryApiClient(
      baseUrl: resolveBackendUrl(),
      deviceId: settings.deviceId(),
    );
    // Se l'utente cambia server nelle impostazioni, il client lo recepisce
    // senza riavviare l'app.
    settings.addListener(() => client.baseUrl = resolveBackendUrl());
    final AdsService ads = AdsService(enabled: settings.value.adsEnabled);
    await ads.initialize();

    final TranscriptionService transcription = TranscriptionService();
    final ProcessingController processing = ProcessingController(
      settings: settings,
      notes: notes,
      transcription: transcription,
      summaries: SummaryService(client: client),
    );

    return AppServices(
      settings: settings,
      notes: notes,
      models: ModelManager(),
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
