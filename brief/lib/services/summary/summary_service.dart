import 'package:flutter/foundation.dart';

import '../../core/constants.dart';
import '../../core/failures.dart';
import '../../domain/summary.dart';
import 'local_summarizer.dart';
import 'summary_api_client.dart';

/// Esito della sintesi, con l'indicazione di come è stata ottenuta.
class SummaryOutcome {
  const SummaryOutcome({required this.bundle, this.fallbackReason});

  final SummaryBundle bundle;

  /// Valorizzato quando il cloud non è riuscito e si è usato il locale.
  final String? fallbackReason;

  bool get usedCloud => bundle.engine == SummaryEngine.cloud;
}

/// Sceglie il motore di sintesi in base alle impostazioni, con ripiego
/// automatico sul riassunto locale quando il cloud non è disponibile.
class SummaryService {
  SummaryService({
    required this.client,
    this.local = const LocalSummarizer(),
  });

  final SummaryApiClient? client;
  final LocalSummarizer local;

  Future<SummaryOutcome> summarize({
    required String transcript,
    required SummaryStyle style,
    required bool cloudAllowed,
    String language = 'it',
  }) async {
    if (!cloudAllowed || client == null || !client!.isConfigured) {
      return SummaryOutcome(
        bundle: local.summarize(transcript, style: style),
      );
    }

    try {
      final SummaryBundle bundle = await client!.summarize(
        transcript: transcript,
        style: style,
        language: language,
      );
      if (bundle.isEmpty) {
        return SummaryOutcome(
          bundle: local.summarize(transcript, style: style),
          fallbackReason: 'Il server ha restituito un riassunto vuoto.',
        );
      }
      return SummaryOutcome(bundle: bundle);
    } on AppFailure catch (e) {
      debugPrint('Riassunto cloud non riuscito: ${e.message}');
      return SummaryOutcome(
        bundle: local.summarize(transcript, style: style),
        fallbackReason: e.message,
      );
    }
  }
}
