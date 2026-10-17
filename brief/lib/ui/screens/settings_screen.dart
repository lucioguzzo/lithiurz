import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../core/constants.dart';
import '../../core/formatters.dart';
import '../../data/settings.dart';
import '../../services/transcription/engine.dart';
import '../../services/transcription/model_manager.dart';
import '../theme.dart';
import '../widgets/app_footer.dart';
import '../widgets/common.dart';
import 'models_screen.dart';
import 'privacy_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  int _modelBytes = 0;
  int _noteCount = 0;
  bool _systemEngineAvailable = false;
  String? _systemEngineReason;
  bool _cloudEngineConfigured = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    final AppServices s = ServicesScope.of(context);
    final int bytes = await s.models.installedBytes();
    final int count = await s.notes.count();
    final bool systemOk = await s.systemEngine.isAvailable();
    final bool cloudOk = s.cloudEngine.isConfigured;
    final String? reason =
        systemOk ? null : await s.systemEngine.unavailableReason();
    if (mounted) {
      setState(() {
        _modelBytes = bytes;
        _noteCount = count;
        _systemEngineAvailable = systemOk;
        _systemEngineReason = reason;
        _cloudEngineConfigured = cloudOk;
      });
    }
  }

  Future<void> _set(AppSettings next) async {
    await ServicesScope.of(context).settings.update(next);
    if (mounted) setState(() {});
  }

  /// Il passaggio al cloud richiede un consenso esplicito e informato.
  Future<bool> _askCloudConsent() async {
    final bool? ok = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('Riassunto nel cloud'),
        content: const SingleChildScrollView(
          child: Text(
            'Per generare il riassunto con un modello linguistico viene '
            'inviato al nostro server soltanto il testo della trascrizione.\n\n'
            '• Per il riassunto viene inviato solo il testo: nessun audio, '
            'qualunque sia il motore di trascrizione scelto.\n'
            '• Il testo è usato solo per produrre il riassunto e non viene '
            'conservato dopo la risposta.\n'
            '• Il server applica limiti di utilizzo giornalieri.\n\n'
            'Puoi revocare il consenso in qualsiasi momento tornando alla '
            'modalità "solo locale".',
          ),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('No, resto in locale'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Acconsento'),
          ),
        ],
      ),
    );
    return ok ?? false;
  }

  /// L'audio che esce dal telefono è un passo diverso dal testo: ha un
  /// consenso suo, con scritto per esteso che cosa viene inviato e dove.
  Future<bool> _askAudioCloudConsent() async {
    final bool? ok = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('Trascrizione nel cloud'),
        content: const SingleChildScrollView(
          child: Text(
            'Con questo motore la registrazione viene inviata al server di '
            'BRIEF, che la fa trascrivere da un modello di Google (Gemini) '
            'e restituisce il testo.\n\n'
            '• Viene inviato l\'audio, non solo il testo: è la differenza '
            'rispetto al riassunto nel cloud.\n'
            '• L\'audio viene inoltrato e basta: il server non lo salva su '
            'disco e non lo scrive nei registri.\n'
            '• Serve la connessione a internet e valgono limiti giornalieri '
            'di utilizzo.\n'
            '• Whisper continua a funzionare senza connessione e senza '
            'inviare niente: puoi tornarci quando vuoi.\n\n'
            'Non usare questo motore per registrazioni riservate o che '
            'riguardano altre persone senza il loro consenso.',
          ),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('No, resto in locale'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Acconsento'),
          ),
        ],
      ),
    );
    return ok ?? false;
  }

  /// Accende o spegne la trascrizione nel cloud, chiedendo il consenso la
  /// prima volta e revocandolo quando si torna indietro.
  Future<void> _setCloudEngine(AppSettings set, bool on) async {
    if (!on) {
      await _set(set.copyWith(
        transcriptionEngine: TranscriptionEngineId.whisper,
        clearAudioConsent: true,
      ));
      return;
    }
    if (set.audioCloudConsentAt == null && !await _askAudioCloudConsent()) {
      return;
    }
    if (!mounted) return;
    await _set(set.copyWith(
      transcriptionEngine: TranscriptionEngineId.cloud,
      audioCloudConsentAt: DateTime.now(),
    ));
  }

  Future<void> _editBackendUrl() async {
    final AppServices s = ServicesScope.of(context);
    final TextEditingController controller =
        TextEditingController(text: s.settings.value.backendUrl);
    final String? url = await showDialog<String>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('Server dei riassunti'),
        content: TextField(
          controller: controller,
          autofocus: true,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(
            hintText: 'https://…',
            helperText: 'Deve usare HTTPS. Lascia vuoto per il valore '
                'predefinito della build.',
          ),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Annulla'),
          ),
          FilledButton(
            onPressed: () =>
                Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Salva'),
          ),
        ],
      ),
    );
    if (url == null || !mounted) return;
    if (url.isNotEmpty && !url.startsWith('https://')) {
      showMessage(context, 'Per sicurezza sono ammessi solo indirizzi HTTPS.',
          error: true);
      return;
    }
    await _set(s.settings.value.copyWith(backendUrl: url));
  }

  @override
  Widget build(BuildContext context) {
    final AppServices s = ServicesScope.of(context);
    final AppSettings set = s.settings.value;
    final ThemeData theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Impostazioni')),
      bottomNavigationBar: const BottomBar(showAd: false),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: <Widget>[
            SectionCard(
              title: 'Trascrizione',
              icon: Icons.record_voice_over_outlined,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text('Motore', style: theme.textTheme.bodyMedium),
                  Gaps.h8,
                  Row(
                    children: <Widget>[
                      Icon(Icons.download_done,
                          size: 18, color: theme.colorScheme.primary),
                      Gaps.w8,
                      Expanded(
                        child: Text(
                          set.transcriptionEngine ==
                                  TranscriptionEngineId.whisper
                              ? 'Whisper · consigliato'
                              : 'Whisper · consigliato (non attivo)',
                          style: theme.textTheme.titleSmall,
                        ),
                      ),
                    ],
                  ),
                  Gaps.h8,
                  Text(
                    'Trascrive con il modello scaricato sul telefono: '
                    'funziona su qualunque dispositivo, resta offline e '
                    'impiega all\'incirca il tempo dell\'audio.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  Gaps.h8,
                  Theme(
                    data: theme.copyWith(dividerColor: Colors.transparent),
                    child: ExpansionTile(
                      tilePadding: EdgeInsets.zero,
                      childrenPadding: const EdgeInsets.only(bottom: 8),
                      initiallyExpanded: set.transcriptionEngine ==
                          TranscriptionEngineId.cloud,
                      title: Text(
                        'Trascrizione nel cloud · più precisa',
                        style: theme.textTheme.bodyMedium,
                      ),
                      subtitle: Text(
                        set.transcriptionEngine == TranscriptionEngineId.cloud
                            ? 'Attiva'
                            : 'Disattivata · l\'audio resta sul telefono',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      children: <Widget>[
                        _EngineWarning(
                          icon: Icons.cloud_upload_outlined,
                          text: 'Manda la registrazione al server di BRIEF, '
                              'che la fa trascrivere da un modello molto più '
                              'grande di quello che sta in un telefono: è la '
                              'scelta più accurata e la più rapida. In '
                              'cambio, è l\'unica in cui l\'audio esce dal '
                              'dispositivo, e serve la connessione.',
                        ),
                        if (!_cloudEngineConfigured)
                          _EngineWarning(
                            icon: Icons.info_outline,
                            text: s.cloudEngine.unavailableReason() ?? '',
                          ),
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          dense: true,
                          title: const Text('Trascrivi nel cloud'),
                          subtitle: set.audioCloudConsentAt != null
                              ? Text(
                                  'Consenso dato il '
                                  '${Fmt.dateTime(set.audioCloudConsentAt!)}',
                                  style: theme.textTheme.bodySmall,
                                )
                              : null,
                          value: set.transcriptionEngine ==
                              TranscriptionEngineId.cloud,
                          onChanged: _cloudEngineConfigured
                              ? (bool on) => _setCloudEngine(set, on)
                              : null,
                        ),
                      ],
                    ),
                  ),
                  Theme(
                    // L'ExpansionTile disegnerebbe due righe divisorie
                    // proprie, che qui si sommerebbero a quella della
                    // sezione.
                    data: theme.copyWith(dividerColor: Colors.transparent),
                    child: ExpansionTile(
                      tilePadding: EdgeInsets.zero,
                      childrenPadding: const EdgeInsets.only(bottom: 8),
                      initiallyExpanded: set.transcriptionEngine ==
                          TranscriptionEngineId.sistema,
                      title: Text(
                        'Motore del telefono (sperimentale)',
                        style: theme.textTheme.bodyMedium,
                      ),
                      subtitle: Text(
                        _systemEngineAvailable
                            ? (set.transcriptionEngine ==
                                    TranscriptionEngineId.sistema
                                ? 'Attivo'
                                : 'Disattivato')
                            : 'Non disponibile su questo telefono',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      children: <Widget>[
                        _EngineWarning(
                          text: 'Il riconoscitore vocale di sistema è molto '
                              'più veloce, ma su diversi telefoni smette di '
                              'ascoltare dopo i primi secondi e restituisce '
                              'solo una parte del testo. Nelle nostre prove '
                              '(nubia NX713J, Android 13) ha trascritto 15 '
                              'parole su 36 secondi di audio, contro le 127 '
                              'di Whisper. Attivalo solo per provarlo e '
                              'confronta il risultato.',
                        ),
                        if (!_systemEngineAvailable &&
                            _systemEngineReason != null)
                          _EngineWarning(
                            icon: Icons.info_outline,
                            text: _systemEngineReason!,
                          ),
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          dense: true,
                          title: const Text('Usa il motore del telefono'),
                          value: set.transcriptionEngine ==
                              TranscriptionEngineId.sistema,
                          onChanged: _systemEngineAvailable
                              ? (bool on) => _set(set.copyWith(
                                    transcriptionEngine: on
                                        ? TranscriptionEngineId.sistema
                                        : TranscriptionEngineId.whisper,
                                  ))
                              : null,
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 24),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Modello'),
                    subtitle: Text(
                      '${ModelOption.byId(set.modelName).label}'
                      '${_modelBytes > 0 ? ' · ${Fmt.bytes(_modelBytes)} occupati' : ''}',
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () async {
                      await Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const ModelsScreen(),
                        ),
                      );
                      await _load();
                    },
                  ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Lingua dell\'audio'),
                    subtitle: Text(_languageLabel(set.language)),
                    trailing: DropdownButton<String>(
                      value: set.language,
                      underline: const SizedBox.shrink(),
                      items: const <DropdownMenuItem<String>>[
                        DropdownMenuItem<String>(
                            value: 'it', child: Text('Italiano')),
                        DropdownMenuItem<String>(
                            value: 'en', child: Text('Inglese')),
                        DropdownMenuItem<String>(
                            value: 'es', child: Text('Spagnolo')),
                        DropdownMenuItem<String>(
                            value: 'fr', child: Text('Francese')),
                        DropdownMenuItem<String>(
                            value: 'de', child: Text('Tedesco')),
                        DropdownMenuItem<String>(
                            value: 'auto', child: Text('Rileva')),
                      ],
                      onChanged: (String? v) =>
                          v == null ? null : _set(set.copyWith(language: v)),
                    ),
                  ),
                ],
              ),
            ),
            Gaps.h16,
            SectionCard(
              title: 'Riassunto',
              icon: Icons.auto_awesome_outlined,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text('Tipo predefinito', style: theme.textTheme.bodyMedium),
                  Gaps.h8,
                  Wrap(
                    spacing: 8,
                    children: <Widget>[
                      for (final SummaryStyle style in SummaryStyle.values)
                        ChoiceChip(
                          label: Text(style.label),
                          selected: set.summaryStyle == style,
                          onSelected: (_) =>
                              _set(set.copyWith(summaryStyle: style)),
                        ),
                    ],
                  ),
                  Gaps.h16,
                  Text('Motore', style: theme.textTheme.bodyMedium),
                  Gaps.h8,
                  SegmentedButton<SummaryEngine>(
                    segments: const <ButtonSegment<SummaryEngine>>[
                      ButtonSegment<SummaryEngine>(
                        value: SummaryEngine.locale,
                        label: Text('Solo locale'),
                        icon: Icon(Icons.phone_android, size: 16),
                      ),
                      ButtonSegment<SummaryEngine>(
                        value: SummaryEngine.cloud,
                        label: Text('Cloud'),
                        icon: Icon(Icons.cloud_outlined, size: 16),
                      ),
                    ],
                    selected: <SummaryEngine>{set.engine},
                    onSelectionChanged: (Set<SummaryEngine> values) async {
                      final SummaryEngine next = values.first;
                      if (next == SummaryEngine.cloud &&
                          set.cloudConsentAt == null) {
                        final bool ok = await _askCloudConsent();
                        if (!ok) return;
                        await _set(set.copyWith(
                          engine: next,
                          cloudConsentAt: DateTime.now(),
                        ));
                        return;
                      }
                      await _set(set.copyWith(engine: next));
                    },
                  ),
                  Gaps.h8,
                  Text(
                    set.engine == SummaryEngine.cloud
                        ? 'Al server viene inviato solo il testo trascritto, '
                            'mai l\'audio.'
                        : 'Riassunto estrattivo calcolato sul telefono: '
                            'funziona offline, è meno elaborato di quello '
                            'generato da un modello linguistico.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  if (set.engine == SummaryEngine.cloud) ...<Widget>[
                    Gaps.h8,
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Server'),
                      subtitle: Text(
                        set.backendUrl.isEmpty
                            ? (const String.fromEnvironment(
                                        'BRIEF_BACKEND_URL')
                                    .isEmpty
                                ? 'Non configurato: userò il riassunto locale'
                                : 'Predefinito della build')
                            : set.backendUrl,
                      ),
                      trailing: const Icon(Icons.edit_outlined),
                      onTap: _editBackendUrl,
                    ),
                  ],
                ],
              ),
            ),
            Gaps.h16,
            SectionCard(
              title: 'Lettura vocale',
              icon: Icons.volume_up_outlined,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text('Velocità: ${(set.ttsRate * 2).toStringAsFixed(1)}×',
                      style: theme.textTheme.bodyMedium),
                  Slider(
                    value: set.ttsRate,
                    min: 0.2,
                    max: 1.0,
                    divisions: 8,
                    onChanged: (double v) => _set(set.copyWith(ttsRate: v)),
                  ),
                  Text('Tono: ${set.ttsPitch.toStringAsFixed(1)}',
                      style: theme.textTheme.bodyMedium),
                  Slider(
                    value: set.ttsPitch,
                    min: 0.5,
                    max: 1.5,
                    divisions: 10,
                    onChanged: (double v) => _set(set.copyWith(ttsPitch: v)),
                  ),
                  OutlinedButton.icon(
                    onPressed: () async {
                      await s.tts.apply(rate: set.ttsRate, pitch: set.ttsPitch);
                      await s.tts.speak(
                        'Ecco come leggerò i tuoi riassunti.',
                      );
                    },
                    icon: const Icon(Icons.play_arrow),
                    label: const Text('Prova la voce'),
                  ),
                ],
              ),
            ),
            Gaps.h16,
            SectionCard(
              title: 'Privacy e dati',
              icon: Icons.privacy_tip_outlined,
              child: Column(
                children: <Widget>[
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: set.historyEnabled,
                    title: const Text('Salva la cronologia'),
                    subtitle: Text(
                      set.historyEnabled
                          ? '$_noteCount elaborazioni salvate su questo '
                              'dispositivo'
                          : 'Le elaborazioni non vengono salvate',
                    ),
                    onChanged: (bool v) =>
                        _set(set.copyWith(historyEnabled: v)),
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: set.keepAudio,
                    title: const Text('Conserva l\'audio originale'),
                    subtitle: const Text(
                      'Disattivato: il file viene eliminato subito dopo la '
                      'trascrizione',
                    ),
                    onChanged: (bool v) => _set(set.copyWith(keepAudio: v)),
                  ),
                  if (set.cloudConsentAt != null)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Consenso al cloud'),
                      subtitle: Text(
                        'Dato il ${Fmt.dateTime(set.cloudConsentAt!)}',
                      ),
                      trailing: TextButton(
                        onPressed: () => _set(set.copyWith(
                          clearConsent: true,
                          engine: SummaryEngine.locale,
                        )),
                        child: const Text('Revoca'),
                      ),
                    ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Informativa privacy'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => const PrivacyScreen(),
                      ),
                    ),
                  ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Licenze dei componenti'),
                    subtitle: const Text(
                      'whisper.cpp, FFmpeg e le altre librerie usate',
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => showLicensePage(
                      context: context,
                      applicationName: AppInfo.fullName,
                      applicationVersion: AppInfo.version,
                      applicationLegalese:
                          '${AppInfo.copyright}\n\n'
                          'Trascrizione con whisper.cpp (MIT). Conversione '
                          'audio con FFmpeg (LGPL 2.1+, collegamento '
                          'dinamico).',
                    ),
                  ),
                ],
              ),
            ),
            Gaps.h16,
            SectionCard(
              title: 'Aspetto e pubblicità',
              icon: Icons.palette_outlined,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text('Tema', style: theme.textTheme.bodyMedium),
                  Gaps.h8,
                  SegmentedButton<ThemeModeSetting>(
                    segments: const <ButtonSegment<ThemeModeSetting>>[
                      ButtonSegment<ThemeModeSetting>(
                        value: ThemeModeSetting.sistema,
                        label: Text('Sistema'),
                      ),
                      ButtonSegment<ThemeModeSetting>(
                        value: ThemeModeSetting.chiaro,
                        label: Text('Chiaro'),
                      ),
                      ButtonSegment<ThemeModeSetting>(
                        value: ThemeModeSetting.scuro,
                        label: Text('Scuro'),
                      ),
                    ],
                    selected: <ThemeModeSetting>{set.themeMode},
                    onSelectionChanged: (Set<ThemeModeSetting> v) =>
                        _set(set.copyWith(themeMode: v.first)),
                  ),
                  Gaps.h8,
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: set.adsEnabled,
                    title: const Text('Mostra i banner'),
                    subtitle: Text(
                      s.ads.usingTestIds
                          ? 'Build di sviluppo: sono attivi gli annunci di '
                              'test di Google'
                          : 'Banner discreti, mai durante l\'elaborazione',
                    ),
                    onChanged: (bool v) async {
                      await _set(set.copyWith(adsEnabled: v));
                      if (!context.mounted) return;
                      showMessage(
                        context,
                        'Riavvia l\'app per applicare la modifica.',
                      );
                    },
                  ),
                ],
              ),
            ),
            Gaps.h16,
            Center(
              child: Text(
                '${AppInfo.fullName} ${AppInfo.version}',
                style: theme.textTheme.labelSmall,
              ),
            ),
          ],
        ),
      ),
    );
  }

  static String _languageLabel(String code) => switch (code) {
        'it' => 'Italiano',
        'en' => 'Inglese',
        'es' => 'Spagnolo',
        'fr' => 'Francese',
        'de' => 'Tedesco',
        _ => 'Rilevamento automatico',
      };
}

/// Riga di avviso con icona, usata per i limiti noti di un motore.
class _EngineWarning extends StatelessWidget {
  const _EngineWarning({
    required this.text,
    this.icon = Icons.warning_amber_outlined,
  });

  final String text;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Icon(icon, size: 16, color: theme.colorScheme.outline),
          Gaps.w8,
          Expanded(
            child: Text(
              text,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
