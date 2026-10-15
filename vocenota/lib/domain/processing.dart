/// Fasi della pipeline mostrate all'utente.
enum ProcessingStage {
  inAttesa('In attesa'),
  preparazione('Preparazione audio'),
  trascrizione('Trascrizione in corso'),
  riassunto('Generazione riassunto'),
  completato('Completato'),
  annullato('Annullato'),
  errore('Errore');

  const ProcessingStage(this.label);
  final String label;

  bool get isTerminal =>
      this == completato || this == annullato || this == errore;
}

/// Stato osservabile dell'elaborazione.
class ProcessingUpdate {
  const ProcessingUpdate({
    required this.stage,
    this.progress,
    this.detail,
    this.partialTranscript,
  });

  final ProcessingStage stage;

  /// 0..1, `null` quando l'avanzamento non è misurabile.
  final double? progress;
  final String? detail;
  final String? partialTranscript;

  ProcessingUpdate copyWith({
    ProcessingStage? stage,
    double? progress,
    String? detail,
    String? partialTranscript,
  }) =>
      ProcessingUpdate(
        stage: stage ?? this.stage,
        progress: progress ?? this.progress,
        detail: detail ?? this.detail,
        partialTranscript: partialTranscript ?? this.partialTranscript,
      );
}
