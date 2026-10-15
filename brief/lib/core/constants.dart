/// Costanti condivise dell'applicazione.
class AppInfo {
  /// Nome breve: icona del telefono e spazi stretti. Sia Android sia iOS
  /// troncano le etichette lunghe sotto l'icona.
  static const String name = 'BRIEF';

  /// Nome completo: intestazione dell'app, store, testi esportati.
  static const String fullName = 'BRIEF - Vocal Sum Up';

  static const String tagline = 'Trascrivi e riassumi i tuoi audio';
  static const String version = '0.1.0';

  /// Titolare del copyright, mostrato in fondo a ogni schermata.
  /// Per cambiarlo basta questa riga.
  static const String copyrightOwner = 'Lucio Guzzo';
  static const int copyrightYear = 2026;

  /// Riga di copyright così come appare nell'app.
  static const String copyright = '© $copyrightYear $copyrightOwner';
}

/// Limiti di accettazione dei file audio in ingresso.
class AudioLimits {
  /// Oltre questa dimensione il file viene rifiutato subito (500 MB).
  static const int maxSizeBytes = 500 * 1024 * 1024;

  /// Durata massima accettata (4 ore).
  static const Duration maxDuration = Duration(hours: 4);

  /// Sotto questa durata l'audio è considerato vuoto.
  static const Duration minDuration = Duration(milliseconds: 400);

  /// Oltre questa durata la trascrizione viene spezzata in blocchi.
  static const Duration chunkThreshold = Duration(minutes: 8);

  /// Durata obiettivo di un blocco di trascrizione.
  static const Duration chunkTarget = Duration(minutes: 5);

  /// Scostamento massimo dal confine teorico per agganciare un silenzio.
  static const Duration chunkDrift = Duration(seconds: 25);

  /// Un blocco finale più corto di così viene fuso con il precedente.
  static const Duration minTailChunk = Duration(seconds: 30);

  /// Estensioni riconosciute come audio (il contenuto è comunque verificato
  /// con ffprobe: questo filtro serve solo a dare errori immediati).
  static const Set<String> knownExtensions = {
    'opus', 'ogg', 'oga', 'mp3', 'm4a', 'aac', 'wav', 'flac', 'amr', 'awb',
    '3gp', '3gpp', 'mp4', 'mka', 'mkv', 'wma', 'caf', 'aiff', 'aif', 'webm',
  };
}

/// Formati del riassunto richiedibili al motore.
enum SummaryStyle {
  breve('breve', 'Breve', 'Poche righe, solo il nocciolo'),
  standard('standard', 'Standard', 'Riassunto discorsivo equilibrato'),
  dettagliato('dettagliato', 'Dettagliato', 'Resoconto esteso per argomenti');

  const SummaryStyle(this.id, this.label, this.description);
  final String id;
  final String label;
  final String description;

  static SummaryStyle fromId(String? id) => SummaryStyle.values.firstWhere(
        (SummaryStyle s) => s.id == id,
        orElse: () => SummaryStyle.standard,
      );
}

/// Modalità di elaborazione del riassunto.
enum SummaryEngine {
  /// Nessuna rete: riassunto estrattivo calcolato sul dispositivo.
  locale('locale', 'Solo locale'),

  /// Il testo (mai l'audio) viene inviato al backend.
  cloud('cloud', 'Cloud (consigliato)');

  const SummaryEngine(this.id, this.label);
  final String id;
  final String label;

  static SummaryEngine fromId(String? id) => SummaryEngine.values.firstWhere(
        (SummaryEngine e) => e.id == id,
        orElse: () => SummaryEngine.locale,
      );
}
