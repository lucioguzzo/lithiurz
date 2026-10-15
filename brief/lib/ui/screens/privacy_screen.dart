import 'package:flutter/material.dart';

import '../../core/constants.dart';
import '../widgets/app_footer.dart';

/// Informativa mostrata nell'app (la versione pubblicata sugli store deve
/// puntare anche a una pagina web, come richiesto da Google e Apple).
class PrivacyScreen extends StatelessWidget {
  const PrivacyScreen({super.key});

  static const String text = '''
${AppInfo.fullName} è pensata per trattare il minimo indispensabile dei tuoi dati.

AUDIO
L'audio che condividi viene copiato in una cartella privata dell'app e
trascritto sul dispositivo con whisper.cpp. Non viene caricato su internet.
Al termine dell'elaborazione il file viene eliminato, a meno che tu non
attivi "Conserva l'audio originale".

TRASCRIZIONE E RIASSUNTO
La trascrizione resta sul dispositivo. Se scegli il riassunto "cloud", e solo
dopo il tuo consenso esplicito, il testo trascritto viene inviato in HTTPS al
nostro server, che lo gira a un fornitore di modelli linguistici. Il testo è
usato solo per produrre la risposta: non viene conservato né usato per
addestrare modelli. Se scegli "solo locale", nulla esce dal telefono.

CRONOLOGIA
Le elaborazioni sono salvate in un database SQLite locale. Puoi disattivare
la cronologia o cancellarla, in parte o del tutto, dalle impostazioni.
Disinstallando l'app i dati vengono rimossi.

IDENTIFICATIVI
Per applicare i limiti di utilizzo del servizio cloud l'app genera un
identificativo casuale del dispositivo, che non contiene dati personali e
non è collegato alla tua identità.

PUBBLICITÀ
L'app è gratuita e mostra banner. Il fornitore (Google AdMob) può trattare
identificativi pubblicitari secondo le proprie informative. Non vengono mai
condivisi con l'inserzionista l'audio, le trascrizioni o i riassunti.

LOG
Audio, trascrizioni e riassunti non vengono scritti nei log del server.

DIRITTI
Puoi cancellare in qualunque momento tutti i dati trattati, direttamente
dall'app. Per esercitare i diritti previsti dal GDPR puoi contattare il
titolare del trattamento indicato nella scheda dello store.
''';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Informativa privacy')),
      bottomNavigationBar: const BottomBar(showAd: false),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: <Widget>[
            SelectableText(text, style: Theme.of(context).textTheme.bodyMedium),
          ],
        ),
      ),
    );
  }
}
