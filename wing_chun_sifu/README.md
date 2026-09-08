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
  rig.py                    scheletro: 21 giunti, proporzioni su 1,74 m
  meshgen.py                mesh skinnata: giacca, pantaloni, viso, mani
  kinematics.py             cinematica diretta e inversa
  poses.py                  le posizioni del Wing Chun
  animations.py             le 21 lezioni animate
  gltf_export.py            esportatore glTF 2.0 binario
  generate_sifu_model.py    genera assets/models/sifu.glb
  verify_poses.py           verifica che le posizioni siano corrette
  render_preview.py         renderer di controllo + miniature
  generate_icon.py          icone Android e iOS
```

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

`tools/verify_poses.py` esegue 66 controlli che traducono i criteri strutturali
del sistema in asserzioni: il gomito del Tan Sau sotto il polso, quello del Bong
Sau sopra, la mano sulla linea centrale, il ginocchio addotto in Yee Ji Kim
Yeung Ma, il calcio non oltre la vita, nessun braccio in iperestensione, le
normali della mesh rivolte all'esterno.

Serve perché un modello 3D può essere formalmente valido e mostrare comunque
posizioni sbagliate — e per un'app che insegna quello è il difetto peggiore.

```bash
python3 tools/verify_poses.py       # 66 verifiche
python3 tools/generate_sifu_model.py # rigenera sifu.glb + lessons.json
```

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
