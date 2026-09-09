# Wing Chun SiFu Online

App Android e iOS per lo studio del Wing Chun: storia, principi, forme, un SiFu
tridimensionale animato che mostra le tecniche, e una selezione di video dai
maestri di riferimento.

Scritta in Flutter, un solo codice per entrambe le piattaforme. Tutto ciò che
serve allo studio — modello 3D, testi, immagini — è dentro l'app e funziona
senza rete; la connessione serve solo alla sezione video.

---

## Le cinque sezioni

| Sezione | Cosa contiene |
|---|---|
| **Storia del Wing Chun** | Capitoli in ordine cronologico, dalla leggenda di Ng Mui alle scuole italiane di oggi. |
| **Principi e tecniche di base** | 27 voci in 6 gruppi: struttura, massime del combattimento, forze del Weng Chun, lavoro interno, le mani, posizioni e passi. |
| **Forme** | 12 forme su tre lignaggi, con struttura in sezioni e cosa sviluppa ciascuna. |
| **3D SiFu — Lessons** | 21 lezioni animate. Il SiFu esegue la tecnica; si ruota con un dito e si guarda da qualsiasi angolazione. |
| **Video selection** | 15 video verificati dai canali ufficiali, organizzati per tema. |

Le sezioni sono collegate fra loro: da un principio si arriva alla lezione 3D
che lo mostra, dalla lezione si torna al principio e alle forme che lo
contengono, dalla forma ai video che la documentano.

---

## Le fonti dei contenuti

I contenuti tecnici fanno riferimento alle due scuole indicate dal committente:

- **SiFu Massimo Fiorentini — IDPA** (International Dragon & Phoenix
  Association, fondata a Napoli il 21 gennaio 2006). L'IDPA appartiene alla
  famiglia del Chi Sim Weng Chun del Gran Maestro Andreas Hoffmann: da qui
  vengono le sei forze (Tai, Lan, Dim, Kit, Got, Wun), il principio del fluire
  (Lau) e le forme Saam Bai Fat, Jong Kuen, Fa Kuen.
- **SiFu Sergio Pascal Iadarola — Eternal Spring Institute** (e IWKA). Da qui
  vengono il lavoro interno, il programma dei Six Core Elements, la linea Snake
  Crane e le forme ancestrali (Siu Lin Tau, Weng Chun Kuen della famiglia Tang,
  Emei Zhe Zhuang).

I testi sono originali e scritti per l'app; i video restano sui canali dei
rispettivi autori e vengono riprodotti tramite il player ufficiale di YouTube,
senza che l'app ne ospiti copia.

**Sezione Storia:** i capitoli attualmente presenti sono provvisori e ricavati
da fonti pubbliche. L'app lo dichiara apertamente. Per sostituirli con la
documentazione definitiva basta riscrivere `assets/data/history.json` — nessuna
modifica al codice, vedi più sotto.

---

## Il SiFu 3D

Il modello non è un asset scaricato: è **generato da codice**, ed è per questo
che si può correggere, versionare e verificare come qualunque altra parte del
progetto.

```
tools/
  rig.py                    scheletro: 41 giunti, dita comprese
  meshgen.py                mesh skinnata: giacca, pantaloni, viso, mani
  kinematics.py             cinematica diretta e inversa
  poses.py                  le posizioni del Wing Chun e le forme di mano
  animations.py             le 21 lezioni animate
  gltf_export.py            esportatore glTF 2.0 binario
  generate_sifu_model.py    genera assets/models/sifu.glb
  verify_poses.py           verifica che le posizioni siano corrette
  render_preview.py         renderer di controllo + miniature
  generate_icon.py          icone Android e iOS
```

### Le dita contano

Nel Wing Chun la forma della mano fa parte della tecnica: un Biu Tze e'
definito dalle dita tese, un Chung Kuen dal pugno chiuso, un Fook Sau dalla
mano rilassata. Il rig ha quindi cinque dita per mano con due falangi
ciascuna, e ogni tecnica dichiara la propria forma di mano, che si chiude
mentre il braccio si muove invece di scattare a fine corsa.

### Il corpo partecipa

Ogni tecnica muove anche la vita, il peso e lo sguardo, non solo il braccio.
La rotazione della vita e' tenuta separata da quella delle anche: applicarla
al bacino durante un colpo trascinerebbe le gambe e torcerebbe la posizione.
Sotto ogni lezione scorre un respiro di meno di due gradi, perche' nessun
fotogramma sia mai identico al precedente: una figura perfettamente immobile
fra due tecniche legge come un manichino anche quando la posizione e' esatta.

Le tecniche partono dal pugno in camera al fianco, come nel Siu Nim Tau, non
dalla guardia: dalla guardia un Tan Sau e un Man Sau finirebbero quasi nello
stesso posto e la tecnica non si leggerebbe.

### Perché la cinematica inversa

Le posizioni non sono angoli scritti a mano. Sono descritte come le
descriverebbe un maestro — *"la mano sulla linea centrale all'altezza della
gola, il gomito basso a un pugno dal petto, il palmo verso l'alto"* — e l'IK le
converte in rotazioni dei giunti.

Il vantaggio è concreto: il **pole vector** del gomito è esattamente ciò che nel
Wing Chun distingue una posizione corretta da una sbagliata. Descrivendo dove
punta il gomito invece di quanto ruota la spalla, si scrive la regola dello
stile, non la sua conseguenza.

### La verifica

`tools/verify_poses.py` esegue 128 controlli che traducono i criteri
strutturali del sistema in asserzioni: il gomito del Tan Sau sotto il polso,
quello del Bong Sau sopra, la mano sulla linea centrale, il ginocchio addotto
in Yee Ji Kim Yeung Ma, il calcio non oltre la vita, le normali della mesh
rivolte all'esterno.

Il controllo più importante è sulla **traiettoria**, non sulle posizioni
chiave: l'angolo del gomito viene misurato lungo tutta l'animazione, non solo
all'inizio e alla fine. Due posizioni corrette possono essere collegate da un
percorso sbagliato, e il braccio che si distende a metà strada è esattamente
ciò che fa sembrare scoordinato un movimento altrimenti giusto. Il limite è
diverso per i colpi (che arrivano quasi distesi) e per le deviazioni (che se
si distendono hanno già perso la struttura).

Serve perché un modello 3D può essere formalmente valido e mostrare comunque
posizioni sbagliate — e per un'app che insegna quello è il difetto peggiore.

```bash
python3 tools/verify_poses.py        # 128 verifiche sulle posizioni
python3 tools/verify_in_engine.py    # carica il modello nel motore reale
python3 tools/generate_sifu_model.py # rigenera sifu.glb + lessons.json
```

### Verifica nel motore reale

`verify_poses.py` guarda il modello dall'interno, con la nostra matematica.
`verify_in_engine.py` lo guarda da fuori: carica il `.glb` nello stesso
`model-viewer` che gira nella WebView dell'app, dentro Chromium, e verifica
che il modello carichi davvero e che ogni inquadratura contenga la figura.

Serve perché due difetti si sono visti solo aprendo l'app su un telefono, e
nessun controllo interno poteva vederli:

- il modello restava in caricamento all'infinito, perché il visualizzatore
  serve il `.glb` alla WebView da un server HTTP locale e Android blocca il
  traffico in chiaro dalla API 28 (vedi `network_security_config.xml`);
- le inquadrature tagliavano la figura, perché model-viewer usa un campo
  visivo verticale di **30 gradi**, non i 45 che verrebbe naturale supporre,
  e i raggi calcolati a mente erano tutti troppo corti.

Entrambi i controlli girano in CI.

La CI rigenera il modello a ogni push e fallisce se il `.glb` committato non
corrisponde al suo generatore.

### Sostituire il modello con un personaggio realizzato da un artista

Il file è un glTF 2.0 standard. Un modello professionale può prendere il posto
di questo senza toccare il codice dell'app, a due condizioni:

1. sia in `assets/models/sifu.glb`, skinnato;
2. contenga animazioni con **gli stessi nomi** degli `id` in
   `assets/data/lessons.json` (`tan_sau`, `bong_sau`, …).

---

## Modificare i contenuti senza toccare il codice

Tutti i testi vivono in `assets/data/`. Il codice non contiene contenuti
editoriali.

| File | Cosa governa |
|---|---|
| `history.json` | Capitoli della sezione Storia. |
| `principles.json` | Gruppi e voci di principi e tecniche. |
| `forms.json` | Forme e lignaggi. |
| `videos.json` | Catalogo video e canali ufficiali. |
| `lessons.json` | Indice delle lezioni 3D (**generato**, non modificare a mano). |

### Sostituire la sezione Storia

Riscrivi `assets/data/history.json` mantenendo questa forma:

```json
{
  "status": "definitivo",
  "notice": "",
  "intro": "Testo introduttivo della sezione.",
  "chapters": [
    {
      "id": "identificativo-univoco",
      "title": "Titolo del capitolo",
      "era": "Periodo storico",
      "summary": "Una riga di sintesi.",
      "body": ["Primo paragrafo.", "Secondo paragrafo."],
      "sources": ["Riferimento bibliografico"]
    }
  ]
}
```

Con `"status"` diverso da `"provvisorio"` l'avviso in cima alla sezione
scompare. `era`, `summary` e `sources` sono facoltativi.

### Aggiungere un video

In `videos.json`, dentro la categoria scelta:

```json
{ "id": "ID_YOUTUBE_11_CARATTERI", "title": "Titolo",
  "author": "Nome del canale", "note": "Perché è utile." }
```

Un test verifica il formato degli identificativi e che ogni video citato da una
forma esista nel catalogo.

---

## Sviluppo

```bash
flutter pub get
flutter run

flutter analyze     # analisi statica
flutter test        # 10 test su contenuti e modello 3D
```

### Struttura

```
lib/
  main.dart              avvio: carica contenuti e progressi
  app.dart               MaterialApp e stato condiviso
  core/
    theme.dart           palette, tipografia, font cinese
    router.dart          rotte e transizioni
    widgets/common.dart  componenti condivisi
  data/
    models.dart              modelli immutabili
    content_repository.dart  caricamento degli asset
    progress_service.dart    avanzamento locale dell'allievo
  features/              una cartella per sezione
```

### Note tecniche

- **Font cinesi.** I nomi delle tecniche contengono 78 caratteri cinesi. I font
  latini non li hanno, e su un dispositivo privo di font CJK di sistema
  apparirebbero come quadratini. L'app include un sottoinsieme di Noto Serif TC
  con esattamente quei 78 caratteri: 31 KB invece dei 10 MB del font completo.
- **Progressi.** Restano sul dispositivo (`shared_preferences`). Nessun account,
  nessuna rete: l'app deve funzionare in una palestra senza campo.
- **Permessi.** Su Android solo `INTERNET`, necessario al player video e alle
  miniature.
- **Traffico locale.** Il visualizzatore 3D serve il modello alla WebView da
  `http://127.0.0.1`. Android lo consente solo tramite
  `network_security_config.xml`, che qui apre il traffico in chiaro
  esclusivamente verso il loopback e lascia il resto della rete su HTTPS; su
  iOS serve `NSAllowsLocalNetworking`. Un test di regressione
  (`test/platform_config_test.dart`) controlla che le due dichiarazioni ci
  siano: senza, il SiFu resta in caricamento per sempre.

---

## Build

La CI (`.github/workflows/wing-chun-sifu.yml`) verifica, compila e pubblica gli
artefatti a ogni push:

- **APK** e **App Bundle** Android (`wing-chun-sifu-apk`, `wing-chun-sifu-aab`)
- **build iOS non firmata** (`wing-chun-sifu-ios-unsigned`)

Localmente:

```bash
flutter build apk --release        # Android
flutter build appbundle --release  # Play Store
flutter build ios --release        # iOS (richiede macOS e certificato)
```

### Per pubblicare

- **Play Store:** serve una chiave di firma (`keystore`) configurata in
  `android/key.properties`; l'App Bundle è già prodotto dalla CI.
- **App Store:** serve un account Apple Developer e un certificato di
  distribuzione. La build iOS della CI non è firmata: verifica che il progetto
  compili, non produce un pacchetto installabile.
