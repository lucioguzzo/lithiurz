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
  static const String copyrightOwner = 'Lucio Salvatore Guzzo';
  static const int copyrightYear = 2026;

  /// Riga di copyright così come appare nell'app.
  static const String copyright = '© $copyrightYear $copyrightOwner';
}

/// Valori decisi al momento della compilazione (`--dart-define`).
///
/// È così che si consegna un APK già pronto: l'indirizzo del server è
/// cucito nella build, l'utente non deve impostare niente. Senza questi
/// valori l'app resta perfettamente funzionante, ma solo in locale.
class BuildConfig {
  /// Server di BRIEF da usare quando l'utente non ne ha indicato un altro.
  static const String backendUrl =
      String.fromEnvironment('BRIEF_BACKEND_URL');

  /// Token pubblico dell'app. **Non è un segreto**: chiunque può
  /// estrarlo dall'APK. Serve solo a scremare il traffico casuale; i
  /// limiti veri sono le quote per dispositivo applicate dal server.
  static const String appToken = String.fromEnvironment('BRIEF_APP_TOKEN');

  /// `true` se questa build arriva con un server già configurato.
  static bool get hasBackend => backendUrl.trim().isNotEmpty;
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
  ///
  /// La soglia è bassa di proposito: whisper.cpp restituisce il testo solo a
  /// fine elaborazione, quindi è la suddivisione in blocchi a permettere di
  /// mostrare la trascrizione mentre procede, invece di far fissare
  /// all'utente una barra per minuti interi.
  static const Duration chunkThreshold = Duration(minutes: 2);

  /// Durata obiettivo di un blocco di trascrizione.
  ///
  /// Un minuto e mezzo è il compromesso scelto: abbastanza corto perché il
  /// testo compaia con regolarità, abbastanza lungo da non moltiplicare
  /// l'avvio dell'inferenza (il modello resta comunque in memoria fra un
  /// blocco e l'altro).
  static const Duration chunkTarget = Duration(seconds: 90);

  /// Scostamento massimo dal confine teorico per agganciare un silenzio.
  static const Duration chunkDrift = Duration(seconds: 12);

  /// Un blocco finale più corto di così viene fuso con il precedente.
  static const Duration minTailChunk = Duration(seconds: 20);

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
