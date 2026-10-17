import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../core/constants.dart';
import '../../services/transcription/engine.dart';
import '../theme.dart';
import '../widgets/app_footer.dart';
import '../widgets/common.dart';
import 'home_screen.dart';
import 'privacy_screen.dart';

/// Benvenuto al primo avvio.
///
/// Esiste per una ragione sola: un'app che si consegna già pronta non può
/// chiedere a chi la installa di configurare un server o di capire che
/// cos'è un modello. Qui si sceglie una volta come trascrivere, con due
/// frasi per ciascuna opzione, e non se ne parla più — la scelta resta
/// modificabile nelle impostazioni.
///
/// Se la build non porta con sé un server ([BuildConfig.hasBackend] falso)
/// l'opzione cloud non viene nemmeno mostrata: proporre qualcosa che non
/// può funzionare è peggio che non proporlo.
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  bool _working = false;

  Future<void> _choose(TranscriptionEngineId engine) async {
    if (_working) return;
    setState(() => _working = true);
    final AppServices s = ServicesScope.of(context);
    final DateTime now = DateTime.now();

    await s.settings.update(s.settings.value.copyWith(
      transcriptionEngine: engine,
      onboardedAt: now,
      // Scegliendo il cloud in questa schermata il consenso è dato qui:
      // il testo che l'utente ha appena letto dice cosa viene inviato.
      audioCloudConsentAt:
          engine == TranscriptionEngineId.cloud ? now : null,
    ));

    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(builder: (_) => const HomeScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool cloud = BuildConfig.hasBackend;

    return Scaffold(
      bottomNavigationBar: const BottomBar(showAd: false),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 32, 20, 24),
          children: <Widget>[
            Icon(Icons.graphic_eq,
                size: 56, color: theme.colorScheme.primary),
            Gaps.h16,
            Text(AppInfo.fullName,
                style: theme.textTheme.headlineSmall,
                textAlign: TextAlign.center),
            Gaps.h8,
            Text(
              AppInfo.tagline,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
            Gaps.h24,
            Text(
              cloud
                  ? 'Come preferisci trascrivere? Puoi cambiare idea quando '
                      'vuoi dalle impostazioni.'
                  : 'Tutto avviene sul telefono: l\'audio non viene mai '
                      'inviato da nessuna parte.',
              style: theme.textTheme.bodyMedium,
            ),
            Gaps.h16,
            if (cloud) ...<Widget>[
              _ChoiceCard(
                icon: Icons.cloud_done_outlined,
                title: 'Trascrizione nel cloud',
                recommended: true,
                body: 'La più precisa e la più rapida, pronta subito: '
                    'niente da scaricare. La registrazione viene inviata '
                    'al server di ${AppInfo.name}, che la fa trascrivere e '
                    'restituisce il testo; non viene conservata. Serve la '
                    'connessione a internet.',
                buttonLabel: 'Usa il cloud',
                onPressed: _working
                    ? null
                    : () => _choose(TranscriptionEngineId.cloud),
              ),
              Gaps.h12,
            ],
            _ChoiceCard(
              icon: Icons.phone_android,
              title: 'Solo sul telefono',
              recommended: !cloud,
              body: 'L\'audio non esce mai dal dispositivo e funziona '
                  'senza connessione. Alla prima trascrizione va scaricato '
                  'un modello di 252 MB, e il tempo di elaborazione è '
                  'all\'incirca pari alla durata dell\'audio.',
              buttonLabel: 'Resta in locale',
              onPressed: _working
                  ? null
                  : () => _choose(TranscriptionEngineId.whisper),
            ),
            Gaps.h16,
            Center(
              child: TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const PrivacyScreen(),
                  ),
                ),
                child: const Text('Come vengono trattati i tuoi dati'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ChoiceCard extends StatelessWidget {
  const _ChoiceCard({
    required this.icon,
    required this.title,
    required this.body,
    required this.buttonLabel,
    required this.onPressed,
    this.recommended = false,
  });

  final IconData icon;
  final String title;
  final String body;
  final String buttonLabel;
  final VoidCallback? onPressed;
  final bool recommended;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Icon(icon, size: 20, color: theme.colorScheme.primary),
              Gaps.w8,
              Expanded(
                child: Text(title, style: theme.textTheme.titleMedium),
              ),
              if (recommended)
                Chip(
                  label: const Text('consigliata'),
                  visualDensity: VisualDensity.compact,
                  labelStyle: theme.textTheme.labelSmall,
                ),
            ],
          ),
          Gaps.h8,
          Text(body, style: theme.textTheme.bodySmall),
          Gaps.h12,
          SizedBox(
            width: double.infinity,
            child: recommended
                ? FilledButton(onPressed: onPressed, child: Text(buttonLabel))
                : OutlinedButton(
                    onPressed: onPressed, child: Text(buttonLabel)),
          ),
        ],
      ),
    );
  }
}
