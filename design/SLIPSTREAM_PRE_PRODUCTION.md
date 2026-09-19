# SLIPSTREAM — *Blood Circuit*
### Documento di Pre-Produzione — Arena FPS competitivo per Android
**Codename interno:** Project VELOCE · **Versione:** 0.9 (draft publisher) · **Data:** Settembre 2026
**Classificazione:** Confidenziale — Pitch esterno

---

## SOMMARIO ESECUTIVO

**SLIPSTREAM** è un arena shooter competitivo *mobile-first*, erede diretto di Quake III Arena e Unreal Tournament, progettato per dispositivi Android dalla prima riga di codice — non portato.

Il documento parte da una singola tesi progettuale, che regge ogni scelta successiva:

> **Su touchscreen il tetto di abilità di mira è fisicamente limitato. Quindi non costruiamo il gioco sulla mira: lo costruiamo sul movimento, sul timing delle risorse e sulla lettura della mappa — tre assi di abilità che il touch *non* comprime.**

Tutti gli arena shooter moderni (Quake Champions, Diabotical, Toxikk) hanno fallito commercialmente su PC contro incumbent da centinaia di milioni di dollari. La nostra scommessa non è "rifare Quake meglio": è **occupare una categoria vuota**. Su mobile non esiste un arena shooter con movimento profondo. Esistono battle royale (Free Fire, PUBGM), tactical shooter (CODM, Valorant Mobile) e hero shooter. Nessuno vende *velocità*.

| Parametro | Target |
|---|---|
| Piattaforma launch | Android (Google Play + APK diretto), iOS in anno 2 |
| Modello | Free-to-play, cosmetico puro |
| Sessione | 4–8 minuti (Duel 6–10) |
| Device floor | Snapdragon 6-series / Dimensity 810, 4 GB RAM, 60 fps stabili |
| Device target | Snapdragon 7+ Gen 3, 120 fps |
| Team picco | 52 persone |
| Budget a lancio (24 mesi) | 14,8 M$ + 10 M$ UA |
| Live ops | 6,2 M$/anno |

---

# 1) IDENTITÀ DEL GIOCO
*Owner: Creative Director · Contributi: Lead Game Designer, Art Director*

## 1.1 Nome

**SLIPSTREAM** — *"scia"*, la corrente d'aria in cui si entra per andare più veloci di quanto si potrebbe da soli.

È il nome giusto per tre ragioni non estetiche:
1. **Descrive la meccanica centrale.** Il giocatore non "corre": eredita e conserva la propria inerzia. Il verbo del gioco è *mantenere*, non *premere*.
2. **È pronunciabile e ricercabile** in tutti i mercati target (EU, LATAM, SEA, India), non collide con marchi FPS esistenti, e funziona come nome di lega esport ("The Slipstream Circuit").
3. **Non promette realismo.** Chi scarica sa già che non troverà una guerra moderna.

Sottotitolo di lancio: **Blood Circuit**. La stagione 1 si chiama *Ignition*.

## 1.2 Ambientazione

**2214. Il Circuito.**

Dopo la Guerra dei Bacini Orbitali, i conglomerati che controllano l'energia del sistema solare hanno smesso di combattere con le flotte: troppo costose. Risolvono le dispute territoriali in **arene da combattimento trasmesse**, dove i contendenti schierano atleti *ristampati*: corpi coltivati, memoria montata da backup, morte reversibile.

Questa fiction risolve tre problemi di design, e per quello esiste:

- **Il respawn è diegetico.** Non "riappari": la tua stampante ti riemette. Le morti sono spettacolo, non fallimento narrativo. Questo sostiene emotivamente un gioco dove si muore 25 volte in 8 minuti — la barriera psicologica principale del genere per il pubblico moderno.
- **Gli oggetti sono infrastruttura.** Le armature sono pacchetti di lega reattiva calati dai piloni dell'arena; i power-up sono picchi di rete. Il "map control" è letteralmente controllo della rete elettrica dell'arena, ed è leggibile a schermo come tale.
- **Le mappe possono essere astratte senza sembrare povere.** Un'arena costruita per la telecamera *deve* essere geometrica, simmetrica, colorata a zone. Questo ci fa risparmiare milioni in art budget e allo stesso tempo è la cosa giusta per la leggibilità competitiva su schermo da 6 pollici.

Nessuna campagna. Nessun lore obbligatorio. La narrazione vive nei commenti dell'annunciatore, nei nomi delle mappe, nelle sponsorizzazioni finte sulle pareti e in 8 "Atleti" giocabili — **puramente cosmetici**, senza abilità differenziate (vedi §1.4).

## 1.3 Stile artistico

**"Sport brutalista al neon"** — leggibilità prima della fedeltà.

| Scelta | Motivazione |
|---|---|
| Palette a **3 colori per zona** (base fredda, accento caldo, neutro) | Il giocatore deve sapere in che parte della mappa si trova da un fotogramma di 100 ms visto di sbieco. Su schermo piccolo la memoria spaziale passa dal colore, non dalla geometria. |
| **Silhouette a leggibilità forzata**: nemici sempre in rosso saturo su ambienti desaturati, alleati in ciano | Contrasto silhouette/fondo ≥ 4,5:1 misurato a 50% di luminosità schermo. È un requisito di accessibilità *e* competitivo. Vedi §5.6. |
| Materiali piatti, **niente PBR fotorealistico**, illuminazione precalcolata | Budget termico. Un pixel shader complesso su un device da 200€ costa fps al minuto 6 della partita, quando conta. |
| Animazioni **a pose forti**, 12–16 fps di key per i nemici a distanza | Su 6 pollici un'animazione fluida a bassa ampiezza è invisibile; una posa esagerata comunica "sta caricando il salto". |
| VFX **a budget fisso**: max 3 effetti fullscreen simultanei, nessuna esplosione che occluda il centro schermo | Non si può vincere ciò che non si vede. L'occlusione da VFX è una fonte di sconfitta ingiusta, e su mobile lo schermo è già parzialmente coperto dai pollici. |

**Regola d'oro dell'art direction:** ogni asset viene approvato guardandolo su un telefono di fascia media, in esterno, con l'80% di luce ambientale. Non su un monitor da studio.

## 1.4 Filosofia del gameplay

Cinque pilastri. Ogni feature proposta in produzione deve mappare su almeno uno; se non ci riesce, non entra.

**P1 — Tutti partono uguali.**
Nessun loadout, nessun perk, nessuna abilità sbloccabile, nessuna statistica che cambi tra due giocatori. La differenza in partita nasce *dentro* la partita: chi ha preso l'armatura pesante è più forte di chi non l'ha presa, fino alla sua morte. Questo è l'unico "power progression" ammesso, e dura 40 secondi.

**P2 — Il movimento è il gioco.**
Muoversi bene deve essere piacevole *anche a zero nemici in vista*. Se togliamo le armi, deve restare un buon gioco di velocità (vedi §3.7).

**P3 — La mappa è un orologio.**
Gli oggetti rinascono a intervalli fissi. Chi sa che ora è, controlla la mappa. Questo trasforma uno shooter in un gioco a informazione imperfetta e a gestione di tempo — il vero motivo per cui il duel di Quake è ancora studiato trent'anni dopo.

**P4 — Sconfitta leggibile.**
Ogni morte deve avere una causa che il giocatore possa nominare in una frase: *"mi sono esposto sulla rampa senza armatura"*. Niente morti da nulla, niente one-shot da fuori campo visivo senza pre-avviso audio.

**P5 — Partite corte, ritorno immediato.**
Nessun tempo morto. Respawn ≤ 2 secondi, coda ≤ 25 secondi, rematch in un tap. Il tempo tra "voglio giocare" e "sto sparando" è il nostro KPI di retention numero uno: target **< 30 secondi da app chiusa**.

## 1.5 Cosa lo distingue dagli FPS moderni

| Standard di mercato | SLIPSTREAM | Perché |
|---|---|---|
| Loadout preparati fuori partita | Arsenale sulla mappa, si raccoglie | Riporta la decisione dentro il match. Nessuno perde per una scelta fatta in un menu. |
| Progressione che sblocca potere | Progressione solo cosmetica e di *conoscenza* | Un nuovo giocatore perde perché gioca peggio, non perché ha sbloccato meno. |
| TTK bassissimo (0,2–0,5 s), premia chi spara per primo | TTK 0,9–1,6 s a pari risorse | Dà spazio alla reazione, al disimpegno, al movimento. Il combattimento diventa un dialogo, non un colpo di dadi. |
| Sparare da fermi, angoli, attesa | Sparare in movimento, nessuna penalità di accuratezza al movimento | La staticità è una scelta di design che su mobile produce partite noiose e sessioni lunghe. |
| Eroi con abilità | Atleti identici, solo estetica | Mantiene il bilanciamento a costo ~0 e rende le patch prevedibili. Un hero shooter richiede un team di bilanciamento permanente che non possiamo permetterci. |
| Partite 20–35 minuti | 4–8 minuti | Un telefono viene usato in finestre di 5 minuti. Questo non è un compromesso: è il formato nativo. |

## 1.6 Perché un giocatore dovrebbe scegliere questo e non Fortnite / CoD / Valorant

Non lo sceglierà *invece*. Lo sceglierà **in aggiunta**, e questo va detto al publisher senza ottimismo di comodo.

Il nostro cuneo di mercato è composto da quattro argomenti verificabili:

1. **Nessuno su mobile vende velocità.** CODM e Valorant Mobile premiano l'attesa e il posizionamento perché sono disegnati intorno alla mira, che su touch è il collo di bottiglia. Noi spostiamo l'abilità dove il touch non è il collo di bottiglia. È una proposta di gioco *diversa*, non una versione peggiore della stessa.
2. **Sessione da 6 minuti con parità totale.** Non c'è niente da macinare, niente da sbloccare, nessun "sono indietro rispetto agli amici". Per un giocatore adulto con 20 minuti liberi, questo è il valore principale, ed è quello che gli incumbent strutturalmente non possono offrire (il loro modello economico è fatto di progressione).
3. **Clip.** Un gioco di movimento produce 8 secondi di video condivisibile ogni partita. Il costo di acquisizione organica di un gioco che genera clip è drasticamente più basso — è il meccanismo che ha creato Rocket League e Apex. Progettiamo esplicitamente per questo: replay istantaneo dell'ultima uccisione, esportazione verticale a 1 tap, watermark.
4. **Il primo arena shooter mobile con esport reale.** Il formato 1v1 è il più economico da trasmettere al mondo: due giocatori, una mappa, nessuna produzione. Possiamo far girare una lega settimanale con 40.000 $ l'anno.

**Argomento onesto controcorrente, da mettere sul tavolo del publisher:** chi gioca a Fortnite per stare con gli amici non verrà. Non stiamo competendo per quel giocatore. Stiamo competendo per il giocatore che ha smesso di giocare agli shooter perché sono diventati lenti — un pubblico più piccolo, più anziano (25–40), con più capacità di spesa, e attualmente non servito da nessuno su mobile.

---

# 2) GAMEPLAY FONDAMENTALE
*Owner: Lead Game Designer · Contributi: Combat Designer*

## 2.1 Il core combat loop

Il ciclo dura tra i 12 e i 25 secondi e si ripete ~25 volte per partita:

```
  LEGGERE L'OROLOGIO          "L'armatura pesante torna tra 4 secondi"
        ↓
  MUOVERSI PER GUADAGNARE     costruisco velocità sul percorso,
  POSIZIONE E VELOCITÀ        arrivo a 24 m/s dal lato alto
        ↓
  INGAGGIARE O DISIMPEGNARE   decisione basata su risorse mie/sue
        ↓
  DUELLO 0,9–1,6 s            arma giusta per la distanza + movimento
        ↓
  CONVERTIRE                  raccolgo la risorsa, nego il suo respawn
        ↓
  RESETTARE L'OROLOGIO        nuova finestra, nuovo piano
```

Il punto critico: **la maggior parte delle decisioni importanti viene presa quando nessuno sta sparando.** Il gioco è vinto nei 15 secondi prima dello scontro. Questo è ciò che separa un arena shooter da un twitch shooter, ed è anche ciò che lo rende giocabile su touch: la parte in cui il touch è debole (la mira) è la parte più breve del ciclo.

## 2.2 Parametri di movimento

Unità interne: 1 u = 2,54 cm (eredità Quake, comoda per i designer di livello). In tabella riporto anche m/s.

| Parametro | Valore | Note |
|---|---|---|
| Velocità base a terra | 320 u/s (**8,1 m/s**) | ~3x un umano reale. Deve *sembrare* veloce al primo secondo di gioco. |
| Accelerazione a terra | 10× velocità/s | 90% della velocità di punta in 0,18 s. Niente sensazione di "pattinaggio". |
| Attrito a terra | 6,0 | Stop completo in ~0,35 s |
| Accelerazione in aria | 1,0 u/tick, cap 30 u/s sulla componente ortogonale | **Questa è la riga più importante del documento.** È il parametro che rende possibile guadagnare velocità in aria. |
| Gravità | 800 u/s² (20,3 m/s²) | 2× reale: salti corti e leggibili, tempo d'aria 0,68 s |
| Impulso di salto | 270 u/s (altezza 1,16 m) | |
| Velocità massima pratica | ~1250 u/s (**31,7 m/s**) | Cap morbido: sopra i 1100 u/s l'attrito aereo cresce linearmente |
| Penalità velocità all'atterraggio | −25% orizzontale | **Annullata** se si risalta entro 100 ms (finestra di *hop*) |
| Nessuna penalità di sparo al movimento | — | Sparare non rallenta mai. Regola non negoziabile. |

**Strafing.** Il modello è quello classico: l'accelerazione applicata è proiettata sul vettore di input ed è limitata dalla componente *già* presente nella direzione di movimento. Conseguenza matematica: se il vettore di input è quasi perpendicolare alla velocità, la proiezione è piccola, il cap non morde, e la velocità totale cresce. È lo stesso "bug" del 1996, mantenuto perché è il migliore sistema di movimento mai prodotto per accidente.

**Bunny hopping — e il problema mobile.** Il bunny hop classico richiede tre input simultanei e continui: salto ritmico, strafe laterale mantenuto, rotazione della visuale sincronizzata. Su touch, due pollici gestiscono già stick e visuale: **il terzo input non esiste.** Copiare il sistema così com'è significherebbe consegnare il tetto di abilità solo a chi gioca con controller, spaccando la community al lancio.

La nostra soluzione è il **Momentum Arc**, descritto in §3.3: mantiene la stessa fisica sottostante, ma sposta l'input dal "tenere premuto uno strafe" al "disegnare un arco con il pollice della visuale". Stessa curva di apprendimento, stessa profondità, input compatibile con due pollici.

**Rocket jump.** Presente e centrale. Il Void Launcher infligge 55 di splash a se stessi ma con **moltiplicatore di spinta 1,9×** sull'autodanno: il salto esplosivo costa vita, non è gratuito, ed è per questo che l'armatura è una risorsa *di mobilità* oltre che di sopravvivenza. Un giocatore forte spende armatura per arrivare prima alla prossima armatura.

**Controllo verticale.** Tutte le mappe hanno 3–4 livelli con **tempi di transizione asimmetrici**: scendere costa 1 s, risalire costa 4–6 s. Questo crea valore posizionale reale senza bisogno di "punti di cattura": l'alto vale perché è costoso da raggiungere, non perché il designer l'ha deciso.

## 2.3 Precisione contro tattica

Bilanciamento deliberato, misurato in percentuale di danno che un giocatore professionista ottiene rispetto a un principiante:

- **Precisione pura** (mira): un pro ha ~2,2× l'accuratezza di un novizio. Su PC lo stesso rapporto è ~3,5×. Il touch comprime questo asse, e noi ne prendiamo atto.
- **Tattica + timing** (dove sono, quando arrivo, con quanta vita): un pro ottiene ~5× il valore per risorsa. **Qui mettiamo il tetto di abilità.**

Le conseguenze concrete su tutto il resto del design:
- L'arsenale è a maggioranza **proiettili** (non hitscan): il proiettile premia la *predizione* del movimento avversario, un'abilità cognitiva, non un'abilità di polso.
- Una sola arma hitscan pura (Railcoil), con costo di opportunità alto.
- Le hitbox sono leggermente generose (+8% su busto), calibrate contro la precisione mediana di un pollice su schermo da 6,1".
- Il danno da splash è la nostra "mira per principianti", e il costo dell'autodanno è la tassa che paga il giocatore esperto per usarlo come mobilità.

## 2.4 Composizione dell'abilità

Stima target a regime, misurata via ablazione nei playtest (facciamo giocare gli stessi utenti con alcune informazioni oscurate e misuriamo il crollo di win rate):

| Asse | Peso | Confronto Quake su PC |
|---|---|---|
| Abilità meccanica (mira + esecuzione movimento) | **30%** | 45% |
| Conoscenza della mappa e timing risorse | **30%** | 25% |
| Strategia (gestione risorse, negazione, scelta ingaggio) | **25%** | 20% |
| Riflessi puri (tempo di reazione) | **15%** | 10% |

Abbiamo spostato 15 punti dalla meccanica alla conoscenza/strategia. È una scelta obbligata dall'input, e la trasformiamo in un vantaggio: **la conoscenza si può insegnare, la mira no.** Un gioco in cui il 55% dell'abilità è insegnabile ha una curva di ritenzione migliore, perché il giocatore che migliora *sa perché* sta migliorando.

## 2.5 Cosa separa un principiante da un professionista

Sette differenze osservabili, in ordine di quanto pesano sul risultato. Ognuna è anche una voce del sistema di allenamento in-game (§8.6).

1. **L'orologio.** Il principiante raccoglie oggetti che incontra. Il pro è già in movimento verso un oggetto che comparirà tra 3 secondi, e ci arriva con 0,5 s di anticipo e alle spalle il percorso di fuga.
2. **La velocità come stato permanente.** Il pro è sopra i 15 m/s per il 70% del tempo di gioco; il principiante per il 15%. Non è una gara: è che la velocità è simultaneamente mobilità, difesa (bersaglio difficile) e opzione di disimpegno.
3. **La matematica delle risorse.** "Ho 100+50, lui è uscito dal fight con circa 40 e senza armatura, il rocket è dalla sua parte tra 6 secondi": il pro fa questo calcolo continuamente e ingaggia solo quando è favorito. È **l'abilità più importante del gioco** e non richiede alcuna destrezza.
4. **La scelta dell'arma prima dell'angolo.** Il pro cambia arma 0,8 s *prima* di vedere il nemico, in base alla distanza che l'angolo imporrà.
5. **La negazione.** Prendere l'armatura pesante con 190 hp non serve a lui: serve a toglierla all'avversario. Il principiante non raccoglie ciò che non gli serve.
6. **L'audio.** Il pro localizza un avversario a 30 m dal suono dei passi e dal tipo di superficie. Investiamo in audio 3D per questo (§10.7) perché è il canale informativo che il piccolo schermo *non* toglie.
7. **La disciplina del disimpegno.** Il pro perde i duelli che ha deciso di perdere. Abbandona lo scontro a 40 hp e torna 12 secondi dopo con 150. Il principiante finisce ogni scontro che inizia.

---
# 3) SISTEMA DI MOVIMENTO
*Owner: Lead Game Designer · Contributi: Technical Director (determinismo), UX Designer (input touch)*

## 3.1 Principio guida

Il movimento è costruito su una regola sola: **il sistema non dà velocità, la conserva.** Ogni meccanica è un modo di *non perdere* l'energia che hai già. Questo produce automaticamente una curva di abilità continua — non ci sono "sblocchi", c'è solo una percentuale di energia conservata che cresce con la pratica, da ~35% di un novizio a ~92% di un professionista.

Corollario tecnico importante: **niente abilità con cooldown come fonte primaria di mobilità.** Un dash a cooldown produce un tetto di abilità piatto (o hai il dash o non ce l'hai). Il nostro dash esiste ma è secondario, e serve come *cerniera* tra tecniche, non come sostituto.

## 3.2 Parametri completi

| Sistema | Parametro | Valore |
|---|---|---|
| **Velocità** | Base a terra | 320 u/s (8,1 m/s) |
| | Cap morbido | 1100 u/s (28 m/s) — oltre, drag lineare |
| | Cap duro | 1400 u/s (35,5 m/s) — per la sanità del netcode |
| **Inerzia** | Accel. terra / aria | 10×v/s · 1,0 u-tick (cap ortogonale 30 u/s) |
| | Attrito terra / aria | 6,0 · 0,02 (sopra cap morbido: 0,9) |
| **Salto** | Impulso / altezza / tempo d'aria | 270 u/s · 1,16 m · 0,68 s |
| | Finestra di *hop* (conserva velocità) | 100 ms dal contatto |
| | Coyote time | 90 ms |
| | Buffer di input salto | 120 ms |
| **Scivolata** | Attivazione | Da ≥ 400 u/s, tap-giù sul lato sinistro |
| | Durata / attrito | 0,75 s · 1,8 (scala a 4,0 sotto i 300 u/s) |
| | Boost iniziale | +12% orizzontale |
| | Altezza hitbox | −45% (passa sotto alcune aperture: vantaggio di mappa) |
| **Dash** | Cariche / ricarica | 2 · 3,5 s per carica, solo a contatto col suolo |
| | Impulso | 450 u/s nella direzione di input, conserva la velocità esistente fino al cap |
| | Costo | Nessuno in risorse — ma la carica è l'unica risorsa di emergenza |
| **Calcio a muro** | Cariche | 2, si ricaricano toccando terra o una *Surge Panel* |
| | Impulso | 300 u/s lungo la normale + 180 u/s verticale |
| | Angolo valido | ≤ 65° rispetto alla normale del muro |
| **Aria** | Controllo aereo | Pieno, nessuna penalità |
| | Aggancio bordo (*Edge Snap*) | Entro 0,35 m dal ciglio, conserva 100% velocità e annulla la caduta |

## 3.3 Il problema dell'input touch, e come lo risolviamo

Questa è la sezione che un publisher deve leggere due volte, perché è qui che il progetto vive o muore.

**Il vincolo:** due pollici. Sinistro = stick virtuale di movimento. Destro = visuale + fuoco. Le tecniche di movimento classiche richiedono un terzo canale.

**La soluzione — Momentum Arc.** Reinterpretiamo l'input, non la fisica.

Quando il giocatore è in aria e il pollice destro compie un movimento di rotazione **continuo, monotono, con velocità angolare compresa tra 45°/s e 190°/s**, il sistema sintetizza il vettore di strafe corrispondente (il vettore che un giocatore PC terrebbe premuto) e lo passa *invariato* al medesimo codice di accelerazione aerea di §3.2. La fisica è identica a quella di Quake; cambia solo chi produce il vettore di input.

Perché questo non è "automazione":
- **Ha una finestra di tolleranza stretta.** Se il pollice è troppo lento, troppo veloce, o inverte direzione, il vettore non viene sintetizzato e si perde energia. In test interni la percentuale di archi riusciti sale dal 20% (prima ora) al 85% (ventesima ora). C'è una vera curva.
- **Va sincronizzato con il salto.** L'arco deve iniziare entro 120 ms dallo stacco e finire prima dell'atterraggio. Sbagliare il tempo costa il 25% della velocità.
- **Rotazione = esposizione.** Mentre disegni l'arco, la tua visuale *ruota davvero*: stai guardando altrove. L'arco veloce è il più efficiente ma ti gira la schiena al nemico. Questo è il costo tattico che rende la tecnica una decisione e non un tasto.

**Feedback.** Anello di velocità attorno al reticolo: sottile e blu a 8 m/s, spesso e bianco a 28 m/s, con un "tick" audio a ogni salto ben eseguito. Il giocatore capisce la meccanica prima di saperla nominare. Questo anello è probabilmente l'elemento UI più importante del gioco.

**Parità di input.** I giocatori con controller o mouse hanno accesso allo strafe classico e *non* al Momentum Arc. Abbiamo calibrato i due sistemi allo stesso guadagno di velocità entro il ±3%. Le code competitive restano comunque separate per tipo di input (§8.3), perché il rischio di una parità imperfetta è troppo alto per lasciarlo al caso.

## 3.4 Le tecniche avanzate

Sette tecniche emergenti. Le prime cinque sono progettate; le ultime due sono state *scoperte* nei prototipi e le abbiamo mantenute — la politica del team è **non correggere mai un exploit di movimento che sia (a) coerente con la fisica, (b) non rompa le mappe, (c) abbia un costo**.

| # | Tecnica | Input | Beneficio | Ore per padroneggiarla | Costo / rischio |
|---|---|---|---|---|---|
| 1 | **Arc Chain** — catena di Momentum Arc | Salto sul frame di atterraggio + arco continuo, alternando verso | Da 8,1 → 28 m/s in ~6 salti | 15–25 | Visuale continuamente ruotata; un errore azzera il 25% |
| 2 | **Kinetic Slide** — scivolata caricata nel dash | Scivolata, dash negli ultimi 150 ms | Dash a 1,3× impulso + reset angolo istantaneo | 4–8 | Consuma una carica; se il dash parte presto è solo un dash normale |
| 3 | **Blast Vault** — salto esplosivo direzionale | Guardare in basso-dietro, sparare, arco immediato | +9 m in verticale o +22 m orizzontali | 10–20 | 55 di danno, o 18 con armatura pesante. Non fattibile sotto i 60 hp |
| 4 | **Ricochet** — catena di calci a muro | Calcio ≤65° con velocità ≥ 700 u/s | Cambio di linea totale senza perdita di energia | 20–30 | Solo 2 cariche; un calcio fuori angolo ti ferma e ti lascia esposto |
| 5 | **Ramp Convert** — conversione rampa | Toccare una rampa ≥ 25° a oltre 18 m/s senza premere indietro | Converte energia verticale in orizzontale, fino a +15% | 8–15 | Serve una linea precisa; sbagliare significa fermarsi sul pendio |
| 6 | **Edge Cancel** *(emergente)* | Passare a filo di un ciglio durante l'atterraggio | Annulla del tutto la penalità di atterraggio | 25–40 | Tolleranza 0,35 m. Fallire = perdere il 25% e restare bersaglio fermo |
| 7 | **Tele-Carry** *(emergente)* | Entrare in un teletrasporto oltre i 900 u/s | Il teletrasporto conserva tutta la velocità e la riorienta | 3 (facile), ma i percorsi che la sfruttano costano 30+ | I teletrasporti sono i punti più camperati della mappa |

**Gerarchia di apprendimento progettata:** un giocatore dovrebbe incontrare la tecnica 2 nella prima ora, la 1 e la 5 nella prima settimana, la 3 e la 4 nel primo mese, la 6 e la 7 quando cerca attivamente di migliorare. Ogni scalino dà un guadagno di velocità percepibile: è la nostra curva di retention a medio termine, e ci arriva *gratis* dal sistema di movimento invece che da un battle pass.

## 3.5 Determinismo e vincoli di rete

Il movimento va simulato identicamente su client e server (§10.3). Vincoli non negoziabili posti dal Technical Director sul designer:

- **Tick fisso a 60 Hz**, matematica in virgola fissa a 16.16 sui vettori di movimento. Nessun `float` nella pipeline predittiva: su Android girano ARMv8 con FMA diversi tra SoC, e una differenza sul bit meno significativo produce desincronizzazione visibile dopo ~400 tick.
- **Nessuna meccanica dipendente dal frame rate.** Tutte le finestre sono espresse in millisecondi e valutate sul tick, mai sui frame renderizzati. Un giocatore a 30 fps e uno a 120 fps eseguono l'Edge Cancel con la stessa difficoltà — la differenza è solo nella latenza di *percezione*, che quantifichiamo in ~25 ms di svantaggio a 30 fps.
- **Costo di un tick di movimento ≤ 0,08 ms per giocatore** sul server, per stare dentro i 16,6 ms con 8 giocatori + rete + margine.

## 3.6 Comfort, accessibilità e mani

Su mobile questa non è una sezione di contorno: è una sezione di retention.

- **Layout HUD completamente spostabile e scalabile**, con 4 preset (2 dita, 3 dita, 4 dita, controller). Il preset a 3 dita — indice sinistro sul salto — è quello dei giocatori forti, e lo insegniamo esplicitamente al tutorial 3.
- **Giroscopio opzionale** per la micro-correzione della mira, su tutta o solo sulla mira secondaria. È un innalzamento reale del tetto di abilità di mira ed è gratis.
- **Modalità "una mano bloccata"** per accessibilità: Momentum Arc su gesto singolo, con efficienza ridotta al 90%. Non è competitivamente valida ai livelli alti, ed è dichiarato.
- **Sudore e dimensione dello schermo:** nessun elemento interattivo a meno di 8 mm dal bordo inferiore; zona morta dei pollici mappata su un modello di 5 taglie di mano raccolto durante il soft launch.

## 3.7 Perché il movimento deve essere divertente senza sparare

È il punto più sottovalutato del genere, ed è anche il nostro principale motore economico.

1. **È il tutorial che nessuno percepisce come tutorial.** Un giocatore che si diverte a muoversi si allena per ore senza sapere di allenarsi. Quando arriva il primo scontro, è già avanti.
2. **È l'attività che sopravvive alla sconfitta.** Perdere 25 a 8 è doloroso. Ma se negli ultimi 30 secondi hai incatenato quattro archi perfetti e sei arrivato a 29 m/s, quella partita persa contiene comunque un successo. **Questo è il singolo meccanismo di ritenzione più forte che abbiamo contro il churn da frustrazione**, che è il killer numero uno degli shooter competitivi.
3. **Genera contenuto.** Un movimento profondo produce naturalmente time attack, speedrun, mappe-percorso. Al lancio spediamo **Circuit Run**: 12 percorsi a tempo con classifica globale e fantasmi, costo di produzione trascurabile (usa le mappe esistenti), valore di retention e condivisione altissimo.
4. **È giocabile in 90 secondi.** L'unico contenuto che regge una sessione in ascensore. Sostiene il nostro KPI di sessioni/giorno.

**Test di validazione, da superare al mese 3 (gate del prototipo):** consegnare a 30 tester una build **senza armi**, solo movimento e percorsi. Se il tempo mediano di sessione volontaria è sotto i 7 minuti, il sistema di movimento non è abbastanza buono e si riprogetta prima di costruire qualsiasi altra cosa.

---

# 4) ARMI E BILANCIAMENTO
*Owner: Combat Designer · Revisione: Lead Game Designer, Esports Designer*

## 4.1 Regole di progettazione dell'arsenale

Quattro vincoli decisi prima di disegnare la prima arma:

1. **Nessuna arma è la migliore a due distanze su tre.** Ogni arma ha una campana di efficacia con una sola vetta.
2. **Il DPS teorico non è il valore reale.** Il valore reale è `DPS × probabilità di colpire a quella distanza contro un bersaglio a 20 m/s`. È questa la quantità che bilanciamo, ed è misurata sui dati, non stimata.
3. **Le munizioni sono la vera leva di bilanciamento.** Non nerferemo quasi mai il danno: cambieremo la disponibilità di munizioni sulla mappa. È reversibile, chirurgico, e non tradisce la memoria muscolare del giocatore.
4. **Ogni arma deve avere un uso non-letale.** Spinta, negazione dell'area, mobilità, informazione. Un'arma che serve solo a fare danno è un'arma che si usa solo quando è la migliore.

Base: 100 hp, armatura fino a 200 (assorbe il 66% del danno finché dura). TTK di riferimento contro 100 hp nudi.

## 4.2 L'arsenale

### 1. SPLITTER — *arma iniziale*
| | |
|---|---|
| **Funzione** | Mitragliatore compatto, proiettili rapidi ma non hitscan (velocità 6.000 u/s) |
| **Danno** | 11 per colpo (testa ×1,4) |
| **Cadenza** | 600 RPM — DPS 110 |
| **Raggio efficace** | 0–18 m |
| **Difficoltà** | Bassa |
| **Munizioni** | 100 all'inizio, cap 200 |
| **TTK** | 0,91 s (10 colpi) |
| **Ruolo** | Arma di rispetto: non ti fa vincere, non ti fa perdere automaticamente. Serve a finire feriti, a contestare un oggetto senza armi, e a fare *chip damage* a media distanza. |

*Nota di design:* è deliberatamente **noiosa ma dignitosa**. Un'arma iniziale troppo forte (vedi le pistole competitive moderne) uccide l'intera economia degli oggetti sulla mappa: se lo starter basta, nessuno rischia per il resto.

### 2. NAILER — *sostenuta a media distanza*
| | |
|---|---|
| **Funzione** | Chiodi a gravità zero, proiettili visibili con tempo di volo (3.200 u/s) |
| **Danno** | 16 |
| **Cadenza** | 420 RPM — DPS 112 |
| **Raggio efficace** | 6–26 m |
| **Difficoltà** | Media (richiede anticipo sul bersaglio: 0,25 s di volo a 20 m) |
| **TTK** | 1,0 s (7 colpi) |
| **Ruolo** | L'arma dell'arena media, quella che premia la *lettura* del movimento avversario. I chiodi attraversano la loro stessa scia visiva: il nemico vede la linea di fuoco e può romperla con un arco. È il duello a scacchi del nostro gioco. |

### 3. EMBER — *fucile a frammentazione*
| | |
|---|---|
| **Funzione** | 9 frammenti che rimbalzano una volta, 10 di danno ciascuno |
| **Danno** | 90 massimo a contatto, ~45 tipico a 6 m |
| **Cadenza** | 55 RPM (1,1 s per colpo) |
| **Raggio efficace** | 0–9 m (i rimbalzi consentono tiri d'angolo) |
| **Difficoltà** | Media |
| **TTK** | 1,1–2,2 s |
| **Ruolo** | Regina degli spazi chiusi e delle rampe. **Uso non-letale:** il *backblast* — sparare a terra dà 160 u/s di spinta senza autodanno, un micro-strumento di mobilità che i giocatori forti usano continuamente per rompere la linea di tiro. |

### 4. TETHER — *controllo d'area*
| | |
|---|---|
| **Funzione** | Spara un nodo che si ancora a una superficie e genera un campo elettrico (raggio 4 m, durata 6 s, max 2 nodi attivi). Due nodi entro 12 m generano un **filo** tra loro. |
| **Danno** | 38 DPS nel campo, 55 DPS sul filo; nessun danno diretto d'impatto |
| **Cadenza** | 1 nodo / 1,4 s |
| **Raggio efficace** | Piazzamento fino a 25 m |
| **Difficoltà** | Alta (è un'arma *predittiva*: si spara dove il nemico sarà tra 2 secondi) |
| **Ruolo** | Nega corridoi, teletrasporti, punti di spawn degli oggetti. Il DPS è basso di proposito: il suo valore è togliere strade, non uccidere. **È l'arma che converte la conoscenza della mappa in vantaggio concreto** — e quindi l'arma preferita dei professionisti e quasi inutile per i novizi. Questo è voluto. |

### 5. VOID LAUNCHER — *esplosiva*
| | |
|---|---|
| **Funzione** | Razzo a proiettile lento (1.400 u/s ≈ 35 m/s) |
| **Danno** | 95 diretto · 55 splash a raggio 3,2 m (caduta lineare) |
| **Cadenza** | 71 RPM (0,85 s) |
| **Raggio efficace** | 3–20 m |
| **Difficoltà** | Media-alta |
| **TTK** | 1,7 s (2 colpi diretti) |
| **Ruolo** | Il pilastro dell'arsenale. Danno d'area, controllo verticale (spingere il nemico giù dalle piattaforme), e **mobilità**: il moltiplicatore di spinta sull'autodanno è 1,9×. È l'arma che collega combattimento e movimento, ed è per questo che il suo posizionamento sulla mappa è la decisione di level design più importante di ogni arena. |

### 6. RAILCOIL — *precisione*
| | |
|---|---|
| **Funzione** | Unico hitscan del gioco. Caricamento udibile di 0,22 s prima dello sparo. |
| **Danno** | 85 (testa ×1,25 = 106) |
| **Cadenza** | 40 RPM (1,5 s di ciclo) |
| **Raggio efficace** | Illimitato (ma le mappe cappano a ~38 m, §5.6) |
| **Difficoltà** | **Molto alta** su touch |
| **Munizioni** | 10 max sulla mappa, molto scarse |
| **Ruolo** | Punisce l'esposizione. Non uccide da solo (85 < 100): serve sempre un secondo input, che è il vero costo. La carica di 0,22 s con segnale audio direzionale dà una finestra di contro-reazione reale, ed è ciò che impedisce al railgun di diventare — come in molti arena shooter — l'unica arma che conta ai livelli alti. Su mobile è anche una concessione all'input: 220 ms permettono di *stabilizzare* il colpo col pollice. |

### 7. STREAM — *raggio continuo, alta abilità*
| | |
|---|---|
| **Funzione** | Raggio elettrico continuo, portata secca a 17 m |
| **Danno** | 9 ogni 100 ms — **DPS 90**, ma solo mentre il raggio resta agganciato |
| **Raggio efficace** | 0–17 m |
| **Difficoltà** | Alta (inseguimento continuo) |
| **Munizioni** | Consuma 1 cella ogni 100 ms, cap 150 → **15 secondi totali di fuoco** |
| **Ruolo** | L'arma da *tracking*. L'inseguimento continuo è l'unica abilità di mira in cui il touch è competitivo col mouse (il pollice è buono nei movimenti lenti e continui, pessimo nei flick). Questa è l'arma che regala al giocatore mobile la sensazione di avere una mira d'élite. Bilanciata dalle munizioni, non dal danno. |

### 8. OVERLOAD CANNON — *rischio alto, premio alto*
| | |
|---|---|
| **Funzione** | Orb a carica. Rilascio tra 0,4 s e 2,0 s. Oltre 2,3 s **detona in mano**. |
| **Danno** | 40 (carica minima) → **165** (carica piena), splash 5 m. Autodetonazione: 45 a te stesso + 0,6 s di stordimento del movimento |
| **Cadenza** | 1 colpo / 2,6 s + tempo di carica |
| **Velocità proiettile** | 900 u/s (lentissimo, visibile, schivabile) |
| **Raggio efficace** | 5–22 m |
| **Difficoltà** | Molto alta |
| **Munizioni** | 5 colpi, rispawn 45 s — l'arma più rara della mappa |
| **Ruolo** | L'arma da *decisione*. Caricare al massimo significa restare prevedibili e vulnerabili per 2 secondi pieni. Un colpo pieno uccide chiunque, anche con armatura pesante. È l'arma che produce i momenti da clip e il thrill dell'esport, e la sua rarità impedisce che diventi la strategia dominante. |

### Corpo a corpo — SHIV
30 di danno, 0,5 s di ciclo, sempre disponibile. Esiste solo perché un giocatore senza munizioni non debba mai essere completamente impotente; **annulla il proprio momentum del 40% quando colpisce**, quindi non è mai la scelta giusta se hai alternative.

## 4.3 Matrice di efficacia (il cuore del bilanciamento)

Valore atteso normalizzato (1,00 = riferimento Splitter a 10 m) contro bersaglio in movimento a 20 m/s:

| Arma | 0–6 m | 6–15 m | 15–25 m | 25 m+ | Vetta |
|---|---|---|---|---|---|
| Splitter | 1,05 | 1,00 | 0,62 | 0,30 | corta-media |
| Nailer | 0,80 | **1,35** | 1,10 | 0,55 | media |
| Ember | **1,80** | 0,70 | 0,15 | 0,05 | cortissima |
| Tether | 0,95 | 1,15 | 0,90 | 0,35 | controllo |
| Void Launcher | 1,30 | **1,60** | 0,95 | 0,40 | media |
| Railcoil | 0,45 | 0,90 | 1,25 | **1,70** | lunga |
| Stream | 1,50 | **1,55** | 0,50 | 0,00 | corta-media |
| Overload | 0,30 | 1,10 | **1,45** | 0,80 | media-lunga |

Nessuna colonna ha una singola arma dominante, e nessuna riga ha due vette. Questa tabella è un **documento vivo**: viene ricalcolata ogni due settimane con i dati di telemetria reali, e le celle che divergono dal modello di oltre il 15% sono la coda di lavoro del Combat Designer.

## 4.4 Come evitare che un'arma diventi dominante

Sei meccanismi, dal più strutturale al più reattivo.

**1. Vincolo strutturale: una sola vetta per arma.** Già discusso. Prevenzione, non cura.

**2. Economia delle munizioni.** Le armi forti hanno riserve piccole e respawn lenti. Il Railcoil con munizioni infinite sarebbe rotto; con 10 colpi ogni 30 secondi è una *risorsa* che va speso bene. Questa è la leva che useremo per il 70% dei ritocchi di bilanciamento, perché è invisibile alla memoria muscolare.

**3. Il costo posizionale.** Le armi migliori sono in posizioni pessime: l'Overload Cannon è in fondo a un pozzo con una sola via d'uscita lenta, il Railcoil è su una piattaforma esposta a tre linee di tiro. Raccogliere l'arma forte significa accettare un rischio, e quel rischio è regolabile dal level designer senza toccare una riga di bilanciamento.

**4. Il costo del cambio arma.** 0,35 s di estrazione, nessun annullamento. Avere l'arma giusta *in mano* è una previsione, non una reazione. Questo limita il dominio di qualsiasi arma alle situazioni che il suo portatore ha anticipato correttamente.

**5. Analisi per fascia di abilità.** Un'arma sopra la media in Bronzo ma sotto la media in Elite **non è rotta**: è facile. Un'arma sotto la media in Bronzo e dominante in Elite *è* rotta, e va corretta subito perché avvelena l'esport. Distinguere i due casi è la disciplina di bilanciamento più importante e quella che la maggior parte degli studi sbaglia. Misuriamo sempre:

```
Δ(arma) = winrate_Elite(arma) − winrate_Bronzo(arma)
```
`|Δ| > 6 punti` apre automaticamente un ticket.

**6. Cadenza di patch dichiarata.** Bilanciamento ogni 4 settimane, mai durante una stagione esport in corso, sempre con note pubbliche che spiegano il *perché* con i numeri. La fiducia della community competitiva è un asset finanziario: vale più di qualunque singolo aggiustamento.

**Anti-pattern che rifiutiamo esplicitamente:** armi a rarità, mod, allegati, livelli d'arma, skin con statistiche. Nessuno di questi entrerà mai nel gioco. Vedi §11.

---
# 5) MAP DESIGN COMPETITIVO
*Owner: Level Designer competitivo · Revisione: Esports Designer, Technical Director (budget di draw call)*

## 5.1 I principi matematici

Un'arena competitiva non è un luogo: è un **grafo orientato con pesi temporali**. Progettiamo prima il grafo, poi la geometria.

**a) Il grafo di percorrenza.**
Nodi = punti di interesse (oggetto, power-up, incrocio, spawn). Archi = tempi di percorrenza *alla velocità realmente raggiungibile su quel percorso*. Regole:
- **Grado minimo 3.** Ogni nodo significativo ha almeno tre uscite. Un nodo di grado 2 è un corridoio; un nodo di grado 1 è una trappola mortale e non deve esistere se non come deposito di risorsa ad altissimo valore (e allora è deliberato).
- **Asimmetria degli archi:** `t(A→B) ≠ t(B→A)` per almeno il 40% degli archi. Le cadute a senso unico e le rampe lente creano il valore posizionale. Un grafo simmetrico produce mappe che si giocano avanti e indietro sullo stesso asse, cioè noia.
- **Diametro del grafo** (percorrenza massima tra due nodi qualsiasi) compreso tra 9 e 14 s a velocità di crociera. Sotto i 9 s la mappa non ha spazio per disimpegnarsi; sopra i 14 s i giocatori non si incontrano abbastanza e su mobile la partita diventa tempo morto.

**b) Il reticolo dei tempi.**
Gli oggetti principali rinascono a 25 s (armature) e 35 s (Mega). Il minimo comune multiplo è **175 s**: i due cicli coincidono solo tre volte in una partita da 8 minuti. Questa deriva è deliberata — genera configurazioni sempre diverse e impedisce che il gioco si riduca a una rotazione memorizzata. La distanza tra due oggetti principali è progettata *non* divisibile per il loro ciclo: se armatura e Mega distano 12 s, nessun percorso permette di prendere entrambi ogni ciclo senza rinunciare a qualcosa.

**c) Il budget di linee di tiro.**
Distribuzione target delle distanze di ingaggio misurata campionando 2.000 coppie di punti visibili tra loro:
- ≤ 12 m: **60%**
- 12–25 m: **30%**
- 25 m+: **10%**

Questo profilo è la nostra difesa strutturale contro il dominio del Railcoil e, insieme, l'adattamento al touch: la mira di precisione a lunga distanza è la meccanica più punitiva su schermo.

**d) Il vincolo dei pixel (regola specifica del mobile).**
Un nemico alto 1,9 m, con FOV verticale di 55° su schermo da 1080 px di altezza, occupa:

```
θ = 2·atan(0,95/d)      px = 1080 · θ/55°
d = 20 m → 5,44° → 107 px
d = 30 m → 3,63° →  71 px
d = 40 m → 2,72° →  53 px
d = 60 m → 1,81° →  36 px
```

Sotto i ~50 px un bersaglio in movimento su telefono, in condizioni di luce reale, non è affidabilmente distinguibile dal fondo. **Quindi: nessuna linea di tiro della mappa supera i 38 metri.** Questo è il motivo per cui le nostre arene sono il 25–30% più piccole degli equivalenti PC. Non è un compromesso di fedeltà: è geometria ottica.

**e) La regola dei 3 angoli.**
In nessun punto calpestabile il giocatore deve poter essere attaccato da più di **3 direzioni distinte simultaneamente**. La velocità di rotazione della visuale col pollice è circa metà di quella del mouse; quattro angoli su touch sono una morte non leggibile, e violano il pilastro P4.

**f) Distribuzione del traffico.**
Misuriamo le heatmap in playtest e imponiamo una distribuzione di tipo Zipf smorzata: l'area più trafficata non deve superare il **22%** dei passaggi totali. Un picco oltre il 30% significa che esiste una rotazione dominante e la mappa ha un solo modo di essere giocata. Sotto il 10% l'area è morta e va tagliata o arricchita.

## 5.2 I principi psicologici

- **Landmark unici.** Ogni area ha un colore dominante, una silhouette architettonica e un *riverbero audio* propri. Il giocatore deve sapere dove si trova anche con lo schermo mezzo coperto dal pollice. Su mobile la mappa si impara con la periferia della visione, non con l'attenzione.
- **Gradiente di esposizione.** Le risorse migliori stanno nei massimi locali di esposizione. Il valore percepito di un oggetto nasce dal prezzo pagato per raggiungerlo, non dal suo effetto numerico.
- **Avversione alla perdita.** La Mega Cell sta sempre *in basso*: prenderla significa rinunciare all'alto. Costringiamo il giocatore a perdere qualcosa per guadagnare qualcosa — il tipo di decisione che produce rimpianto, e il rimpianto è il carburante del "ancora una partita".
- **Memoria per rotte, non per planimetrie.** Nessuno memorizza mappe: la gente memorizza *percorsi*. Ogni arena è progettata intorno a 4–6 rotte canoniche insegnabili in una frase ("dal rocket, rampa, calcio a muro, sei sull'armatura").
- **Nessuna sorpresa senza pre-avviso.** Ogni percorso che sbuca alle spalle emette un suono udibile nell'area d'arrivo 0,4 s prima. Sorprendere è divertente per chi sorprende e ostile per chi subisce; l'audio anticipato conserva il primo e rimuove il secondo.

## 5.3 Le cinque mappe di lancio

### MAPPA 1 — **GANTRY** (`arena_gantry`)
| | |
|---|---|
| **Modalità** | Duel 1v1 (mappa-firma del competitivo), FFA fino a 4 |
| **Dimensione** | 58 × 66 m, volume compatto |
| **Livelli** | 3 (Pozzo −4 m · Piano 0 · Ballatoio +7 m) |
| **Diametro grafo** | 9,5 s |

**Layout.** Un anello asimmetrico attorno a un vuoto centrale a doppia altezza. Il ballatoio superiore vede dentro il pozzo ma non copre la rampa nord. Due teletrasporti collegano gli angoli opposti del Piano 0 con uscite sul ballatoio — l'uscita conserva la velocità (Tele-Carry).

**Punti di controllo.** *Ballatoio Ovest* (vede Armatura Pesante + uscita teletrasporto nord); *Pozzo* (nessuna visibilità ma tre uscite, Mega Cell).

**Percorsi principali.** (1) Rampa Nord: piano 0 → ballatoio in 4,2 s, esposta. (2) Salto esplosivo dal pozzo al ballatoio: 1,4 s, costa 55 hp. (3) Teletrasporto Est: 2,0 s ma uscita prevedibile e camperabile.

**Aree rischiose.** Il Pozzo: la Mega Cell è l'oggetto più prezioso e sta nel punto con la peggiore vista della mappa. La rampa Nord: 4,2 s allo scoperto sotto il ballatoio.

**Posizionamento.**
- Railcoil → ballatoio est (esposto a 3 linee)
- Void Launcher → piano 0 centro (il nodo più trafficato: è l'arma che tutti vogliono, va dove tutti passano)
- Ember → pozzo
- Stream → sotto la rampa nord
- Armatura Pesante (100) → ballatoio ovest
- Armatura Leggera (50) ×2 → angoli opposti piano 0
- Mega Cell → fondo del pozzo
- Nessun power-up maggiore: **il duel si gioca sulle armature.** Overload e Surge sbilancerebbero un formato a due giocatori dove non esiste compensazione di squadra.

---

### MAPPA 2 — **SOLARIS** (`arena_solaris`)
| | |
|---|---|
| **Modalità** | TDM 4v4, Surge |
| **Dimensione** | 94 × 94 m, pianta circolare |
| **Livelli** | 4 (Anello esterno · Corona · Torre +14 m · Sottopassaggio −6 m) |
| **Diametro grafo** | 12,8 s |

**Layout.** Struttura radiale: una torre centrale a tre piani, quattro "raggi" che la collegano all'anello esterno, un sottopassaggio circolare che corre sotto tutto e collega i quattro quadranti senza passare dal centro. La torre vede tutto ma è raggiungibile solo per rampe lente (5,5 s) o con salto esplosivo.

**Punti di controllo.** *Cima Torre* (visione totale, ma una sola via di fuga sicura → trappola per chi ci resta troppo); *Incroci del sottopassaggio* (invisibili dall'alto, i veri corridoi di rotazione di una squadra organizzata).

**Percorsi.** Anello esterno (veloce, alta velocità sostenuta, esposto ai raggi); raggi (rapidi ma prevedibili); sottopassaggio (lento −15% di velocità di punta ma coperto).

**Aree rischiose.** Cima Torre e i quattro innesti dei raggi sull'anello, dove tre linee di tiro convergono.

**Posizionamento.** Railcoil → cima torre (chi la prende deve poi scendere). Void Launcher ×2 → quadranti opposti dell'anello. Tether → sottopassaggio (nega le rotazioni: è lì che vale di più). Overload Cannon → piattaforma sospesa raggiungibile solo con salto esplosivo o Ricochet. Armature Pesanti ×2 → quadranti nord ed est dell'anello. Mega Cell → centro del sottopassaggio. **Surge (velocità)** → base della torre.

*Perché funziona:* la radialità produce combattimenti circolari continui — nessuna squadra può "tenere una linea", che è la morte del ritmo negli shooter a squadre.

---

### MAPPA 3 — **KILN** (`arena_kiln`)
| | |
|---|---|
| **Modalità** | Capture the Flag 4v4, TDM |
| **Dimensione** | 120 × 72 m, specchiata sull'asse corto |
| **Livelli** | 3 per base + un ponte centrale sospeso a +9 m |
| **Diametro grafo** | 13,5 s |

**Layout.** Due basi a specchio, ciascuna con sala bandiera a −3 m e due uscite (una rampa lenta e coperta, una caduta veloce a senso unico). Tra le basi, tre corsie: **Corsia Alta** (il ponte, lunga e pulita — la corsia della velocità: lì si arriva a 30 m/s), **Corsia Bassa** (tortuosa, coperta, lenta), **Corsia Morta** (a zig-zag, la più sicura e la più lenta).

**Punti di controllo.** Le teste del ponte: chi le tiene decide se la corsia veloce esiste.

**Aree rischiose.** Il ponte è indifendibile ma è l'unico posto dove un portatore di bandiera può costruire abbastanza velocità per un ritorno diretto. Rischio e premio coincidono geometricamente: è la migliore mappa del set per mostrare la nostra filosofia.

**Posizionamento.** Railcoil ×2 → piattaforme laterali che battono il ponte (il contro-gioco alla corsia veloce). Void Launcher → ingresso di ogni base. Tether → sala bandiera (difesa). Ember → corsia bassa. Armatura Pesante → *a metà strada tra le due basi, sulla corsia morta* — così il momento più forte della squadra la costringe ad allontanarsi dalla propria difesa. Mega Cell ×2 → dietro ogni base (ripristino dopo un attacco fallito).

---

### MAPPA 4 — **HOLLOW** (`arena_hollow`)
| | |
|---|---|
| **Modalità** | FFA 6–8, TDM |
| **Dimensione** | 78 × 78 m, sviluppo verticale 32 m |
| **Livelli** | 5, impilati attorno a un pozzo centrale |
| **Diametro grafo** | 11,0 s |

**Layout.** Una cisterna cilindrica. Cinque ballatoi anulari a quote diverse, collegati da: due colonne d'aria ascensionale (salita di 12 m in 1,8 s, ma si è bersagli lenti e prevedibili), tre passerelle diametrali, e la caduta libera nel pozzo (il modo più veloce per scendere, gratis).

**Punti di controllo.** Il ballatoio L4 (penultimo): domina il pozzo e le uscite delle colonne d'aria.

**Aree rischiose.** Le colonne d'aria — il più classico "gioco di negazione": chi le tiene sotto tiro controlla la circolazione verticale. Il fondo del pozzo (L0) è il punto peggiore della mappa, e per questo contiene l'Overload Cannon.

**Posizionamento.** Overload Cannon → L0. Railcoil → L4 ma su un balcone con un solo accesso battuto. Stream → L2, area di transito. Nailer ×2 → L1 e L3. Armature Leggere ×4 distribuite su tutte le quote (in FFA la densità di risorse deve essere più alta: più giocatori, stesso ciclo). Surge → passerella L3. **Overload (quad damage)** → cima, L5, raggiungibile solo con Ricochet o doppio salto esplosivo.

*Nota:* in FFA a 8 giocatori accorciamo tutti i respawn del 20%. Con più giocatori, lo stesso ciclo produce fame di risorse e stallo.

---

### MAPPA 5 — **VERGE** (`arena_verge`)
| | |
|---|---|
| **Modalità** | **Surge** (modalità nuova, §7.5), TDM |
| **Dimensione** | Anello 140 m di circonferenza, larghezza pista 14–22 m |
| **Livelli** | 2 + tre piloni verticali |
| **Diametro grafo** | 10,5 s (per progetto, nessun punto è mai lontano) |

**Layout.** Un circuito chiuso — una pista sospesa nel vuoto, senza soffitto. Superfici lisce, rampe paraboliche, pareti inclinate per il Ricochet. Tre **Piloni** (i punti di scarica di Surge) su piattaforme rialzate a 120° l'una dall'altra. Due scorciatoie interne attraversano l'anello: più corte ma piene di curve strette dove è impossibile restare veloci.

**Il trucco di design.** La geometria è costruita perché la **traiettoria più veloce sia anche la più esposta**: il bordo esterno della pista è liscio e continuo (velocità massima) ma non ha copertura; l'interno ha copertura ma spezza la velocità a ogni curva. Il giocatore negozia continuamente velocità contro sicurezza, che è esattamente la domanda che la modalità Surge pone.

**Aree rischiose.** Tutte le piattaforme dei piloni: rialzate, isolate, con una sola linea di ingresso veloce. Il vuoto: è possibile cadere fuori pista, e uccide (unica mappa del set con questa regola — e ci serve per la tensione da spettacolo).

**Posizionamento.** Nessun Railcoil (una mappa ad anello con un railgun diventa un poligono di tiro). Void Launcher ×2 alle scorciatoie. Tether ×2 sulle piattaforme dei piloni. Ember sulle curve strette. Nessuna Mega Cell; solo armature leggere distribuite lungo il bordo esterno — **si raccolgono senza rallentare**, e questo dice al giocatore che qui il gioco è restare in movimento.

## 5.4 Processo di produzione delle mappe

1. **Grafo su carta** (1 giorno) — nodi, archi, tempi target, verifica delle regole §5.1.
2. **Blockout grigio** (3 giorni) — solo collisioni, niente arte.
3. **Bot-soak** (automatico, 1 notte) — 500 partite di bot per estrarre heatmap, distribuzione delle distanze di ingaggio, tempi reali di percorrenza. *È il nostro maggiore moltiplicatore di produttività: la maggior parte degli errori di mappa emerge qui, senza costo umano.*
4. **Playtest umano** (2 sessioni da 8 giocatori) — leggibilità, divertimento, "dove mi sono sentito fregato".
5. **Iterazione sul blockout** — media di 4 cicli. **Nessuna arte prima che il blockout sia congelato.**
6. **Art pass + ottimizzazione** (10–14 giorni) — budget: ≤ 220 draw call, ≤ 350k triangoli visibili, 1 atlas texture per zona.

Costo medio per mappa: **28 giorni-persona** dopo la terza (le prime due costano il doppio perché costruiscono la libreria di moduli).

## 5.5 Sistema di spawn

Un punteggio, valutato su tutti i punti di spawn a ogni morte:

```
S = 3,0·min(t_viaggio_nemico_più_vicino, 8)
  − 4,0·(numero di nemici con linea di vista sul punto)
  − 2,5·(punto usato negli ultimi 10 s)
  − 1,5·(un nemico ha > 150 hp+armatura entro 4 s di viaggio)
  + 1,0·(distanza dal luogo della tua morte, normalizzata)
```
Si sceglie casualmente tra i 3 punteggi migliori, per evitare che lo spawn diventi prevedibile e quindi camperabile. **Regola dura:** mai uno spawn entro 2,5 s di viaggio da un nemico armato e sano. Una morte allo spawn viola il pilastro P4 e va trattata come un bug di severità alta, non come sfortuna.

## 5.6 Leggibilità su schermo piccolo — checklist obbligatoria per ogni mappa

- Contrasto silhouette nemico / sfondo ≥ 4,5:1 in **ogni** area, verificato con strumento automatico che campiona 500 posizioni di camera.
- Nessun nemico può apparire davanti a uno sfondo con la stessa tinta del suo contorno rosso.
- Nessun elemento animato di sfondo entro 15° dal centro schermo.
- Ogni superficie calpestabile ha un materiale audio distinto (metallo, griglia, cemento, vetro): l'audio è larghezza di banda informativa gratuita su mobile.
- Test di leggibilità su device minimo, a 400 nit, con riflessi simulati.

---

# 6) POWER-UP E CONTROLLO DELLA MAPPA
*Owner: Lead Game Designer · Revisione: Esports Designer*

## 6.1 Il sistema di risorse

| Risorsa | Effetto | Respawn | Durata | Note |
|---|---|---|---|---|
| **Scheggia** (shard) | +5 armatura, fino a 200 | 20 s | permanente finché assorbe | Disseminata a gruppi di 3–5, pane quotidiano |
| **Armatura Leggera** | Porta l'armatura a 50 (non cumula sopra) | 25 s | — | L'oggetto di "manutenzione" |
| **Armatura Pesante** | Porta l'armatura a 100 | 25 s | — | **L'oggetto più importante del gioco competitivo** |
| **Cella Medica** | +25 hp fino a 100 | 15 s | — | |
| **Mega Cell** | Porta la vita a 200; decade di 1 hp/s fino a 100 | 35 s | ~100 s di valore decrescente | Il "grande premio" e la grande decisione |
| **SURGE** (haste) | +28% velocità, +20% accelerazione, +15% impulso di salto | 110 s | 22 s | Power-up *di movimento*: il più coerente con l'identità del gioco |
| **OVERLOAD** (quad) | ×2 danno inflitto | 130 s | 20 s | Suono e VFX visibili a tutta la mappa: è un *annuncio*, non un'imboscata |
| **PHASE** | Distorsione visiva + passi silenziati | 150 s | 18 s | **Solo FFA e casual.** Escluso dal competitivo: l'informazione nascosta danneggia la spettabilità e l'equità |
| **BEACON** (oggetto strategico) | Piazzabile; per 40 s rivela alla squadra il timer di respawn dell'oggetto più vicino e segnala i nemici che lo attraversano | 60 s | 40 s | Trasforma il controllo della mappa in **informazione condivisibile**. Distruggibile con 50 danni. |

L'armatura assorbe il **66%** del danno in entrata finché ha punti. 100 hp + 100 armatura = 250 danni effettivi assorbiti (vs 100 nudi): un giocatore "pieno" vale 2,5 giocatori vuoti. **Questo rapporto è il motore economico di tutto il gioco** e non va mai toccato senza rifare tutte le tabelle armi.

## 6.2 Tempi di respawn: perché questi numeri

- **25 s per le armature.** È il tempo in cui, sulle nostre mappe (diametro 9–14 s), un giocatore può fare *una* cosa in più tra un ciclo e l'altro: un duello, una rotazione, una negazione — ma non due. È la durata che rende ogni ciclo una scelta secca.
- **35 s per la Mega.** Coprimo con 25 rispetto ai cicli brevi: la coincidenza si ripete ogni 175 s, tre volte per partita. Quelle tre coincidenze sono i momenti di massima tensione e i professionisti le pianificano.
- **110–130 s per i power-up maggiori.** Abbastanza raro da essere un evento della partita (4 comparse in 8 minuti), abbastanza frequente da far parte del piano.
- **Respawn su timer fisso dalla raccolta, non a intervallo globale.** Così il timer è **informazione privata di chi ha raccolto**: chi controlla la risorsa controlla anche la conoscenza di quando tornerà. È questa asimmetria informativa a rendere il gioco profondo.

## 6.3 Rischio / rendimento

Ogni risorsa ha un prezzo progettato, espresso come secondi di esposizione richiesti:

| Risorsa | Valore (hp effettivi o equivalenti) | Prezzo (secondi esposto) | Rapporto |
|---|---|---|---|
| Scheggia ×4 | 20 | 0,5 | 40 |
| Armatura Leggera | 150 | 2,0 | 75 |
| Armatura Pesante | 300 | 5,5 | 55 |
| Mega Cell | 250 (decrescenti) | 7,0 | 36 |
| Surge | ~ (tattico) | 4,5 | — |
| Overload | ~ (tattico) | 6,0 | — |

Il rapporto scende all'aumentare del valore: **le risorse migliori sono i peggiori affari in termini assoluti.** Questo è deliberato ed è ciò che impedisce alla strategia ottimale di ridursi a "vai sempre all'oggetto migliore". La mossa corretta dipende dalla tua vita attuale, da dove sta l'avversario e dall'ora dell'orologio — cioè richiede giudizio.

## 6.4 Come si costruisce il "map control"

Il map control **non è occupare spazio**: è *possedere il calendario*. Si costruisce in quattro strati:

1. **Possesso del timer.** Prendere l'armatura pesante ti dà 25 secondi di conoscenza esclusiva. Questa è la risorsa vera.
2. **Negazione.** Prendere un oggetto che non ti serve, per toglierlo. Costa tempo ed esposizione e va valutato: il novizio non lo fa mai, il medio lo fa sempre, il professionista lo fa solo quando l'avversario ha davvero bisogno di quell'oggetto.
3. **Pressione posizionale.** Stare *sul percorso* verso il prossimo respawn, non sull'oggetto. Un giocatore forte non aspetta sull'armatura: aspetta a 3 secondi dall'armatura, dove ha ancora tre opzioni.
4. **Controllo del tempo (stalling).** Quando sei avanti in risorse, il tempo lavora per te. Disimpegnarsi, girare, ricomparire con vantaggio. Questa è la meccanica che produce partite "non simmetriche" e narrativamente interessanti — e il gioco deve renderla visibile allo spettatore (§8.4) o sembrerà solo che i giocatori stiano scappando.

**Lo spirale di controllo (e come lo spezziamo).** Chi controlla gli oggetti tende a controllarli sempre di più: è la dinamica che rende il duel di Quake brutale per i principianti. Due valvole:
- **Il Beacon** dà a chi è indietro un modo *economico* di comprare informazione, che è la merce che gli manca.
- **I power-up maggiori annunciati a tutta la mappa** (110–130 s) creano tre o quattro momenti in cui chi è dietro sa esattamente dove sarà chi è avanti, e può giocarci contro. Senza annuncio globale, il quad damage amplificherebbe soltanto il vantaggio.

## 6.5 Il "Circuit Clock" — assistenza al timing (decisione controversa)

L'item timing è l'abilità più profonda del genere **e la sua barriera d'ingresso più alta**. Su mobile, chiedere a un giocatore di tenere a mente tre orologi mentali mentre disegna archi col pollice è irrealistico, e ci costerebbe la maggior parte dei nuovi utenti.

La nostra soluzione, a tre livelli:

| Livello | Regola | Dove |
|---|---|---|
| **Aperto** | Tutti i timer visibili sempre, a tutti | Casual, FFA, allenamento |
| **Guadagnato** | Vedi il timer **solo degli oggetti che hai raccolto tu**, e solo per un ciclo | **Ranked — il nostro default competitivo** |
| **Cieco** | Nessun timer | Torneo Pro, Duel Elite |

Il livello "Guadagnato" preserva integralmente la meccanica interessante (*contendere per ottenere informazione*) e rimuove quella noiosa (*ricordare a memoria un numero*). È lo stesso principio per cui gli scacchi competitivi hanno un orologio visibile: la sfida è la decisione, non la contabilità.

**Rischio dichiarato:** i puristi lo leggeranno come un'assistenza. La mitigazione è la trasparenza — tre regolamenti pubblici, dichiarati, con il livello Cieco usato nelle finali. Se la telemetria mostrasse che la fascia Elite gioca comunque a memoria, promuoveremo "Cieco" a default della fascia alta.

---
# 7) MODALITÀ DI GIOCO
*Owner: Lead Game Designer + Esports Designer*

Principio di scoping: **cinque modalità al lancio, non nove.** Ogni modalità in più divide la popolazione di matchmaking, e su mobile la coda vuota è la prima causa di abbandono. Ogni modalità deve giustificare il proprio costo in tempi di attesa.

## 7.1 DEATHMATCH (FFA)
**Regole.** 6–8 giocatori, tutti contro tutti, 6 minuti o 30 frag. Suicidi −1. Respawn 1,5 s.

**Strategie avanzate.** Il DM libero non è un gioco di mira: è un gioco di **posizionamento relativo a più avversari**. Il professionista (a) non ingaggia mai un duello con un terzo giocatore entro 4 s di viaggio, (b) fa "spawn-read" — sa dove il giocatore che ha appena ucciso può rinascere e ci arriva prima, (c) tiene la Mega come ancora del proprio ciclo di rotazione e accetta di perdere le armature.

**Esport.** Scarso: troppo caotico da commentare, vantaggi di kill-steal, poca leggibilità. **Resta fuori dal circuito competitivo** ed è la nostra modalità di ingresso e di divertimento puro.

## 7.2 TEAM DEATHMATCH
**Regole.** 4v4, 8 minuti o 60 frag. Respawn a ondate ogni 5 s (**scelta importante**: le ondate creano gruppi e quindi combattimenti di squadra, invece di un flusso continuo di rinforzi singoli che vengono uccisi uno alla volta).

**Strategie avanzate.** Divisione dei ruoli non imposta ma emergente: chi tiene il Railcoil gioca l'alto e l'informazione; due giocatori "pieni" (armatura pesante) fanno da punta; uno tiene il ciclo della Mega. La rotazione a onde crea un ritmo di 25–35 s in cui una squadra è forte e l'altra sta ricostruendo: **vincere un fight non basta, bisogna convertirlo in risorse prima dell'onda successiva.**

**Esport.** Buono ma non eccezionale: leggibile, ma il punteggio a frag rende le rimonte poco drammatiche. Presente nel circuito come formato secondario.

## 7.3 DUEL 1v1
**Regole.** 10 minuti, chi ha più frag. Nessun power-up maggiore. Ruleset Cieco (nessun timer). Mappa: Gantry o la mappa della stagione. Pareggio → overtime a morte improvvisa con tutti gli oggetti rimossi tranne le armature.

**Strategie avanzate.** È il formato più profondo e il più didattico. Concetti chiave: *stack management* (quando sei a 190 hp l'obiettivo non è uccidere ma restare pieno), *negazione dei cicli*, *forced fight* (quando l'avversario è in vantaggio di risorse, imporre lo scontro prima che la sua armatura ritorni), *sound denial* (muoversi su superfici silenziose per rompere la lettura audio avversaria).

**Esport.** **Il nostro formato di punta.** Costo di produzione minimo (due giocatori, due telecamere, una mappa), narrazione immediata (una faccia contro una faccia), lunghezza televisiva perfetta (10 minuti, best-of-5 in un'ora). Storicamente il duel di Quake è stato l'esport più longevo del genere. Su mobile ha un ulteriore vantaggio: **è trasmissibile in verticale** e quindi nativamente adatto a TikTok/Reels/Shorts, dove sta il nostro pubblico.

## 7.4 CAPTURE THE FLAG
**Regole.** 4v4 su Kiln, 10 minuti, 5 catture. La bandiera va portata alla propria base **solo se la propria bandiera è in sede**. Il portatore: non può sparare, mantiene *tutte* le capacità di movimento, e **guadagna +10% di velocità massima** (per rendere la corsa spettacolare e non un'agonia di occultamento). La bandiera caduta torna in sede dopo 12 s.

**Strategie avanzate.** Ruoli espliciti (portatore, scorta, controllo corsia, difesa). Il concetto centrale è la **pressione sull'armatura di mezzo campo** (§5.3): chi ha il vantaggio di squadra deve allontanarsi dalla propria base per mantenerlo, creando naturalmente le finestre offensive avversarie. Il gioco di corsia alta/bassa è una scommessa ripetuta a somma quasi zero: il ponte è veloce ma battuto dai due Railcoil.

**Esport.** Molto spettacolare, ma costoso da commentare (quattro azioni simultanee) e con partite che possono finire 0-0. Al lancio lo teniamo **fuori dal circuito principale**, come formato d'esibizione. Rivalutazione dopo la Stagione 3 sui dati di visione.

## 7.5 SURGE — *la modalità nuova*
*(Owner: Lead Game Designer + Esports Designer · Prototipo obbligatorio al mese 2)*

### L'idea
In ogni shooter a obiettivi del mercato, l'obiettivo **premia lo stare fermi**: si difende un punto, si piazza una bomba, si presidia un'area. Il risultato è che ogni modalità a obiettivi tende alla staticità, cioè all'opposto di ciò che siamo.

Surge inverte la regola: **l'obiettivo produce punti solo mentre si muove velocemente.**

### Regole
- 4v4 su Verge (mappa ad anello), 3 round da 3 minuti, si vince al meglio dei 3.
- Al centro della mappa compare il **Core**. Chi lo raccoglie diventa il **Conduttore**.
- Il Conduttore **non può sparare**. Mantiene tutte le tecniche di movimento e riceve un unico strumento: **Phase Pulse**, un'onda di spinta a 360° (nessun danno, forte knockback, ricarica 6 s).
- Il Core **accumula carica proporzionalmente alla velocità del Conduttore**, ma solo sopra una soglia:

```
carica/s = k · max(0, v − 14 m/s)     con k = 0,9
→ a 14 m/s: 0/s    a 22 m/s: 7,2/s    a 30 m/s: 14,4/s
```
- Il Conduttore **scarica** la carica toccando uno dei tre **Piloni**. Il pilone attivo ruota ogni 25 s (annunciato 5 s prima), quindi il percorso ottimale cambia continuamente.
- La carica massima trasportabile è 100. A 100 il Core diventa **instabile**: il Conduttore perde 4 hp/s finché non scarica. *(Impedisce il farming infinito e forza la decisione.)*
- Se il Conduttore viene ucciso, il Core cade **conservando il 60% della carica**: chiunque lo raccolga eredita quel bottino. Una rubata a 90 di carica ribalta un round.
- Punteggio del round: primo a 250 di carica scaricata, o il più alto allo scadere.

### Perché funziona
- **Ogni pilastro di design converge.** Il movimento è il punteggio. La mappa è un circuito. Il combattimento serve a controllare *qualcun altro*, non a fare frag.
- **Ruoli nitidi ed emergenti**: il Conduttore (il migliore nel movimento), due scorte che aprono la strada e spingono via i bloccanti, un intercettore che si posiziona sul prossimo pilone. Nessun ruolo è imposto dal sistema, tutti emergono dalle regole.
- **Il contro-gioco è ricco e non frustrante:** non devi uccidere il Conduttore, ti basta **rallentarlo**. Il Tether attraverso la pista, un razzo che spezza un arco, il tuo corpo su una curva. "Negare un tetto di velocità" è un obiettivo di gioco che non esiste in nessun altro shooter e produce interazioni nuove.
- **Il volume di azioni non letali cresce**, il che è un enorme vantaggio per i giocatori di livello medio: puoi contribuire in modo decisivo senza vincere duelli di mira. **Questo è probabilmente il singolo fattore di retention più importante del gioco per l'utente mobile medio.**
- **Anti-stallo strutturale:** non esiste una posizione da difendere. Una squadra che si trincera perde per definizione.

### Perché funziona nell'esport
- **Una sola barra da guardare.** Lo spettatore capisce chi sta vincendo in mezzo secondo. La carica è una tensione crescente con un momento di rilascio — la struttura narrativa di base di ogni sport.
- **La rubata è il momento da highlight** e produce rimonte legittime senza meccaniche di *rubber banding* artificiale.
- **È visivamente unico.** Un torneo di Surge non assomiglia a nessun'altra trasmissione esport esistente. Per un gioco nuovo, la differenziazione visiva del broadcast vale quanto il gioco stesso.
- **Round da 3 minuti**: formato perfetto per clip verticali e per una diretta mobile.

### Rischi individuati (e mitigazioni)
| Rischio | Mitigazione |
|---|---|
| Il Conduttore migliore vince da solo (il *carry* totale) | Phase Pulse non fa danno; senza scorta un Conduttore viene bloccato su ogni curva. Da validare nei test: se il winrate della squadra col miglior Conduttore supera il 70%, riduciamo `k` e aumentiamo il valore della scarica |
| Non sparare è frustrante per il Conduttore | Il Core dà accesso a tutte le tecniche + una corsia di velocità aumentata: playtest su 40 utenti al mese 2; se l'indice di divertimento del ruolo Conduttore è sotto la media della modalità, aggiungiamo un'arma debolissima di puro disturbo |
| Troppo complesso da capire in 30 secondi | Tutorial di 60 s + colore singolo per la barra + l'annunciatore che dice letteralmente "vai più veloce" |

**Surge è la ragione per cui questo pitch non è "Quake su telefono". È la feature che difende il progetto in una riunione di greenlight**, e per questo deve essere prototipata nel mese 2, prima di ogni altra modalità a obiettivi.

---

# 8) SISTEMA DI RANKING ED ESPORT
*Owner: Esports Designer · Contributi: Multiplayer Network Engineer, Data Scientist*

## 8.1 Classificazione dei giocatori

Sistema bayesiano **OpenSkill (modello Plackett-Luce)**, non Elo. Ragioni tecniche:
- Gestisce nativamente FFA e squadre con un solo modello.
- Traccia l'**incertezza** (σ), quindi i nuovi giocatori convergono in 8–12 partite invece di 40. Su mobile, con retention D7 intorno al 20%, un piazzamento lungo 40 partite significa che la maggior parte dei giocatori non arriva mai al proprio rango reale — errore fatale.
- Computazionalmente banale, aggiornabile in linea.

**MMR nascosto** (il numero reale) + **Rango visibile** (una funzione monotona, smorzata, che non scende mai più di un livello per partita). Il rango è comunicazione emotiva, l'MMR è la verità matematica. Non devono essere la stessa cosa.

| Fascia | Percentile | Note |
|---|---|---|
| Iron / Ferro | 0–20 | |
| Copper / Rame | 20–45 | |
| Steel / Acciaio | 45–70 | |
| Plasma | 70–88 | |
| Void | 88–97 | |
| **Circuit** | 97–99,7 | Nome pubblico, leaderboard regionale |
| **Apex 500** | top 500 per regione | Classifica nominale, ingresso al circuito esport |

Stagioni di 10 settimane, reset parziale (`μ' = 0,7·μ + 0,3·μ_medio`, σ riaperta): abbastanza da dare un nuovo inizio, non tanto da buttare via 10 settimane di calibrazione.

## 8.2 Cosa misura l'MMR (oltre alla vittoria)

Solo l'esito conta per l'MMR — **le statistiche individuali non entrano nel calcolo del rango**, perché qualunque metrica premiata diventa immediatamente una metrica farmata, e perché i sistemi che premiano le statistiche personali distruggono il gioco di squadra.

Le statistiche servono altrove (feedback, allenamento, rilevamento cheat, scouting) e sono ricchissime:

`Danno per risorsa · Efficienza di controllo armature (% di cicli vinti) · Velocità media e mediana · % di tempo sopra 15 m/s · Energia conservata per salto · Accuratezza per arma e per fascia di distanza · Danno subito evitabile (esposizione a linee note) · Tempo alla prima risorsa dopo respawn · Frazione di ingaggi iniziati in vantaggio di risorse`

L'ultima metrica è la migliore correlazione singola con l'abilità reale che abbiamo trovato nei prototipi (r ≈ 0,71 con MMR), e diventa il cuore del sistema di allenamento.

## 8.3 Matchmaking

Priorità in ordine, non negoziabile:

1. **Latenza.** Bucket per data center, mai un match sopra i 70 ms di RTT stimato per il giocatore peggiore. La latenza rovina il gioco più di qualunque squilibrio di abilità, e in un arena shooter a proiettili è direttamente visibile.
2. **Pool di input.** Tre pool separati: **Touch**, **Controller**, **Mouse+Tastiera / Emulatore**. Detect lato server dalla firma dell'input (vedi §10.6). Mescolare touch e mouse è la singola decisione che più rapidamente ucciderebbe la fiducia competitiva.
3. **Abilità.** Finestra iniziale ±0,6σ, allargata a scaglioni: +0,2σ ogni 10 s fino a un tetto di ±2,0σ.
4. **Comportamento.** I giocatori con behavior score basso si incontrano preferibilmente tra loro.

**Tetto d'attesa: 45 secondi.** Oltre, si completa con bot marcati esplicitamente (§9) in casual, e in ranked si allarga la finestra di abilità dichiarandolo nella schermata di match. La coda infinita è un abbandono certo; una partita imperfetta ma dichiarata è recuperabile.

**Anti-smurf.** Un nuovo account con prestazioni oltre il 95° percentile nelle prime 3 partite ha σ triplicata e salta direttamente in fascia alta entro 5 partite. Inoltre incrociamo device fingerprint + firma di movimento (l'Arc Chain di un giocatore esperto è statisticamente riconoscibile): un account nuovo su un dispositivo con storico Elite parte pre-calibrato. È imperfetto per progetto — l'obiettivo è rendere lo smurfing *lento e noioso*, non impossibile.

## 8.4 Spettatore

- **Observer server dedicato**, ritardo obbligatorio di 45 s in torneo (anti-ghosting).
- Telecamera libera, aggancio giocatore, **camera automatica diretta da euristica** (punteggio di "interesse" = risorse in gioco + prossimità + velocità + carica del Core) — la regia automatica è indispensabile perché non avremo registi umani per la maggior parte dei match.
- Overlay competitivi: barre di vita/armatura per tutti, **timer di tutti gli oggetti**, vettori di velocità, mini-mappa con tracciati, scia di velocità colorata del Conduttore in Surge.
- **Modalità verticale per lo spettatore.** Non negoziabile: il nostro pubblico guarda in piedi, sul telefono. Interfaccia progettata per 9:16 fin dal primo giorno, non adattata dopo.

## 8.5 Replay e statistiche

**Non useremo replay deterministici.** Un replay deterministico richiede che la simulazione sia bit-identica per sempre, il che congela il codice di gioco e rompe ogni replay a ogni patch. Costo di manutenzione troppo alto per il nostro team.

Al suo posto: **registrazione a snapshot lato server**. Il server salva lo stato compresso a 20 Hz (posizioni, orientamenti, eventi, stato oggetti). Un match 4v4 di 8 minuti pesa **~3,5 MB**, compressi a ~900 KB con delta + quantizzazione. Il replay viene *ricostruito* con interpolazione, non risimulato.
- Conseguenza: fedeltà al 99% per l'analisi e lo spettacolo, non adatto a verifiche anti-cheat al colpo singolo (per quelle usiamo i log del server autoritativo, §10.6).
- Conservazione: 30 giorni per tutti, illimitata per i match di torneo e per chi ha il pass premium.
- Esportazione clip verticale 1080×1920, 15 s, 1 tap, con watermark. **È un canale di marketing, non una feature di comodità**, e va trattato come tale nelle priorità.

## 8.6 Tornei e struttura competitiva

| Livello | Frequenza | Struttura | Costo annuo |
|---|---|---|---|
| **Ladder in-client** | Continuo | Apex 500 per regione | ~0 |
| **Circuit Open** | Settimanale | Eliminazione a 256, Duel + Surge, ingresso libero da Void+ | 25 k$ |
| **Circuit Series** | Mensile | Top 32 degli Open, montepremi | 180 k$ |
| **World Circuit** | Annuale | 16 squadre / 16 duellanti, evento fisico piccolo | 600 k$ |

**Totale esport anno 1: ~1,1 M$** — deliberatamente modesto. L'esport non si compra: si coltiva. Spendere 8 M$ in un circuito per un gioco senza pubblico è l'errore più costoso e più documentato del settore (Battalion 1944, Rogue Company, e molti altri). Cresceremo il montepremi solo in proporzione alle ore di visione misurate.

**Supporto ai team via cosmetici (Club Items)**, con **split del 50%** dei ricavi alle organizzazioni. Allinea gli incentivi, finanzia le squadre senza toccare il nostro budget, ed è un modello già validato da Rocket League e CS.

## 8.7 Tossicità, abbandoni, equità

**Tossicità.**
- **Nessuna chat vocale o testuale aperta nelle code casuali.** Solo una ruota di ping contestuali (~14 voci: "nemico pieno", "armatura tra 5", "mi ritiro", "core rubato"...). La comunicazione tattica è quasi integralmente coperta; l'insulto no. Questo elimina alla radice la maggior parte degli abusi, a costo di un po' di coordinazione fine — un prezzo che paghiamo volentieri e che le squadre organizzate compensano con voce esterna o con il party.
- Voce disponibile **solo in party** con persone che hai scelto.
- **Behavior score** che influenza il matchmaking, non i permessi. Chi ha punteggio basso gioca con chi ha punteggio basso. È la sanzione più efficace e la meno contestabile.
- Elogi post-partita (3 categorie, senza voto negativo: le valutazioni negative vengono usate come arma contro chi gioca male, non contro chi si comporta male).
- Nomi e cosmetici moderati automaticamente, con revisione umana a campione.

**Abbandoni.**
- **Sostituzione con bot immediata**, con bot calibrato sull'MMR del giocatore uscito (§9). Questo è il motivo principale per cui investiamo nei bot: un 4v3 è una partita rovinata per sette persone.
- **Finestra di rientro di 120 s**; chi rientra riprende il controllo del proprio bot.
- **Perdono di MMR:** se un compagno abbandona entro i primi 90 s, la squadra rimanente perde 0 MMR in caso di sconfitta e ne guadagna pieno in caso di vittoria.
- Sanzioni progressive per abbandono: 5 min → 30 min → 2 h → 24 h, reset dopo 20 partite pulite.
- **Prevenzione:** partite corte (una partita da 6 minuti si abbandona molto meno di una da 30) e nessun punteggio individuale che renda "già perso" un match a metà.

**Matchmaking ingiusto.**
- Pubblichiamo mensilmente la **distribuzione degli squilibri** (differenza media di MMR per match, tempo medio d'attesa, percentuale di partite oltre ±1σ). La trasparenza sulle metriche di equità è economicamente conveniente: una community che può verificare smette di inventare teorie.
- Nessun *engagement-optimized matchmaking* (EOMM). Mai. È un moltiplicatore di ricavi a breve termine e un distruttore di community competitive a medio termine, ed è rilevabile dai giocatori esperti.
- Duo queue consentita fino a Void; da Circuit in su **solo solo-queue**, come nel resto del mondo competitivo serio.

## 8.8 Allenamento (il ponte verso l'abilità)
Sistema integrato, costo basso, impatto alto sulla retention:
- **Circuit Run** (§3.7): percorsi a tempo, fantasmi, classifica.
- **Drill di timing**: scenari da 45 s ("l'armatura torna tra 6 s, l'avversario ha 80 hp: cosa fai?").
- **Duel contro bot calibrati** al proprio MMR +1 fascia.
- **Revisione post-partita automatica**: tre schede generate dai dati ("hai iniziato il 62% degli scontri in svantaggio di risorse; la mediana della tua fascia è 48%"). Nessun consiglio generico: solo numeri con confronto e un drill collegato.

---
# 9) BOT CON INTELLIGENZA ARTIFICIALE
*Owner: AI Engineer · Revisione: Technical Director (costi), Lead Game Designer (sensazione di gioco)*

## 9.1 Perché i bot sono un sistema critico e non un contorno

Per un gioco mobile competitivo nuovo, i bot risolvono quattro problemi che valgono direttamente in retention:

1. **Riempimento della coda** nelle ore vuote e nelle regioni piccole (il problema che uccide la maggior parte degli shooter di nicchia: la coda da 4 minuti alle 3 del mattino in Brasile).
2. **Sostituzione di un abbandono**, salvando sette partite.
3. **Allenamento calibrato** a qualunque livello.
4. **QA automatizzato**: il bot-soak (§5.4) che produce heatmap e trova bug di navigazione senza costi umani.

## 9.2 Cosa deve fare un bot (e cosa no)

**Deve:** perdere in modo umano. Un bot che sbaglia in modo *alieno* (mira perfetta ma cammina in un muro) è peggio di un bot debole. Il requisito non è "essere forte", è **essere indistinguibile abbastanza a lungo**.

Target: in un test alla cieca, un giocatore di fascia Steel deve identificare il bot come bot in meno del **60%** delle partite entro 3 minuti (50% = caso).

**Non deve:** barare. Mai visione attraverso i muri, mai conoscenza istantanea dei timer non guadagnati, mai accelerazione sopra il cap del giocatore. Il bot usa **esattamente** l'API di input del giocatore e un modello di percezione esplicito. Se non lo facciamo, il bot smette di essere una fonte di dati valida per il bilanciamento — che è metà del suo valore.

## 9.3 Architettura a quattro strati

```
┌─────────────────────────────────────────────────────────────┐
│ L3 — MACRO POLICY (appresa)                                 │
│ "Dove andare, cosa contendere, quando disimpegnarsi"        │
│ Rete: MLP 3×256 + GRU(128) · ~1,9 MB int8 · 10 Hz           │
│ Input: 312 feature (risorse mie/stimate avversario,         │
│        timer noti, occupazione aree, posizione nel grafo)   │
│ Output: obiettivo su nodo del grafo + modalità d'ingaggio   │
├─────────────────────────────────────────────────────────────┤
│ L2 — TATTICA (utility AI, scritta a mano, ispezionabile)    │
│ Scelta arma, angolo d'approccio, copertura, uso Phase Pulse │
│ ~40 considerazioni con punteggio · 20 Hz                    │
├─────────────────────────────────────────────────────────────┤
│ L1 — NAVIGAZIONE E MOVIMENTO (deterministica)               │
│ Navmesh + link di salto/dash/blast precalcolati offline     │
│ Esecutore di Arc Chain e Ricochet con rumore parametrico    │
│ 60 Hz, identico al codice di movimento del giocatore        │
├─────────────────────────────────────────────────────────────┤
│ L0 — PERCEZIONE                                             │
│ Cono visivo + occlusione reale · modello udito con soglie   │
│ Memoria a decadimento delle posizioni nemiche (τ = 4 s)     │
│ Latenza di reazione campionata da distribuzione ex-gaussiana│
└─────────────────────────────────────────────────────────────┘
```

**La scelta chiave dell'architettura:** solo L3 è appresa. L2 e L1 sono codice scritto e leggibile. Motivi, in ordine di importanza pratica:
- **Debuggabilità.** Un bot end-to-end appreso che si comporta male è una settimana di indagine. Un bot con L2 esplicito è un breakpoint.
- **Costo.** Addestrare una policy end-to-end di movimento+mira richiede milioni di ore-partita simulate. Addestrare solo la macro-policy, che opera a 10 Hz su feature astratte, sta su **4 GPU per ~10 giorni** per mappa-famiglia.
- **Sicurezza di design.** Vogliamo poter *garantire* che il bot non barerà e non troverà strategie degenerate che rompono la percezione di equità.

## 9.4 Come impara

**Fase 1 — Behavior cloning.** I replay a snapshot (§8.5) sono già un dataset supervisionato: stato del mondo → obiettivo scelto dal giocatore umano. Con 200.000 match della beta otteniamo ~120 M di transizioni. Clonare i giocatori di fascia Void+ dà subito una policy "umana" che conosce le rotazioni sensate e non richiede alcun reward shaping. **Questa fase da sola copre l'80% del valore.**

**Fase 2 — Self-play con league.** PPO in una "lega" stile AlphaStar in miniatura:
- *Main agent* (gioca contro tutti)
- *Main exploiter* (cerca le debolezze del main)
- *League exploiters* (cercano debolezze di chiunque)
- Reward: differenziale di punteggio + **bonus denso su differenziale di risorse** (fondamentale: il segnale di vittoria da solo è troppo sparso su 8 minuti).
- Regolarizzazione KL verso la policy clonata, per impedire che il self-play derivi verso strategie ottimali ma non umane (stare fermi in un angolo matematicamente redditizio, per dire).

**Fase 3 — Distillazione e quantizzazione.** La policy finale viene distillata in una rete piccola (1,9 MB, int8) eseguibile via **NNAPI / TFLite** sul dispositivo per i bot offline e sul server per quelli in-match. Budget: **≤ 0,6 ms per bot per inferenza** (a 10 Hz, 4 bot: ~2,4% di un core).

**Fase 4 — Aggiornamento continuo.** Ri-addestramento ogni stagione sul nuovo pool di replay; così i bot seguono naturalmente l'evoluzione del meta, senza lavoro manuale. È l'argomento più forte a favore dell'approccio appreso: i bot scriptati invecchiano, questi no.

## 9.5 Simulare i livelli di abilità

**Regola:** la scala di abilità non passa *mai* per l'indebolimento della macro-policy. Un bot "facile" che non sa dov'è l'armatura non è facile, è stupido — e il giocatore lo sente.

Si modula lo strato L0/L1 con un **vettore di abilità** a 7 dimensioni:

| Dimensione | Facile | Medio | Elite |
|---|---|---|---|
| Latenza di reazione (ex-gauss µ/σ/τ ms) | 420/90/160 | 280/60/110 | 165/35/70 |
| Errore di mira (σ gradi a 20 m) | 4,2° | 2,1° | 0,75° |
| Rumore di tracking (Stream) | alto | medio | basso |
| Efficienza di movimento (energia conservata) | 38% | 65% | 91% |
| Orizzonte di predizione risorse | 1 ciclo | 2 cicli | 4 cicli |
| Precisione della memoria nemica (τ) | 1,5 s | 3 s | 6 s |
| Tasso di errore deliberato (scelte subottimali) | 22% | 9% | 2,5% |

L'errore di mira è modellato come **Ornstein-Uhlenbeck** sovrapposto al bersaglio (deriva lenta + correzioni), non come rumore gaussiano indipendente: il rumore bianco produce una mira che "trema" in modo riconoscibilmente artificiale, l'OU produce il tipico movimento umano di inseguimento e correzione. È un dettaglio piccolo con un impatto enorme sulla percezione.

**Personalità.** Tre archetipi con pesi di reward diversi in addestramento: *Aggressore* (sconta il rischio), *Controllore* (massimizza il differenziale di risorse), *Opportunista* (alta varianza, cerca le rubate in Surge). Danno varietà percepita a costo quasi zero, e servono per l'allenamento mirato.

## 9.6 Costi e piano di ripiego

| Componente | Costo | Mese |
|---|---|---|
| L0+L1+L2 (bot scriptato competente) | 2 ingegneri × 5 mesi | 3–8 |
| Pipeline dati replay → dataset | 1 ingegnere × 2 mesi | 10–12 |
| L3 behavior cloning | 1 ML eng × 3 mesi + ~15 k$ GPU | 13–16 |
| L3 self-play league | 1 ML eng × 5 mesi + ~90 k$ GPU | 16–21 |
| **Totale** | **~1,35 M$** | |

**Piano di ripiego, deciso in anticipo (questo è il punto):** L0–L2 da soli producono un bot già valido per riempimento coda, sostituzione abbandoni e allenamento fino alla fascia Plasma. Se al mese 14 il progetto è in ritardo o sotto pressione di budget, **L3 viene tagliato e rimandato all'anno 2 post-lancio**, senza impatto sul lancio. Questa è una delle poche feature del piano con un punto di uscita pulito, ed è progettata così di proposito.

---

# 10) TECNOLOGIA
*Owner: Technical Director · Contributi: Multiplayer Network Engineer*

## 10.1 Motore

**Unreal Engine 5.5, configurazione mobile forward renderer.** Disattivati: Nanite, Lumen, Virtual Shadow Maps, TSR. Attivi: illuminazione precalcolata (lightmap), distance field ambient occlusion selettiva, ASTC 6×6, instancing aggressivo.

**Perché UE5 e non Unity.**
- Tooling di livello per il level design e il networking replicato già pronto; il nostro collo di bottiglia è il tempo di iterazione sulle mappe, non il rendering.
- Accesso completo al sorgente C++: per un gioco competitivo dobbiamo poter **riscrivere il character movement e il modello di replicazione**, e non essere ostaggio del roadmap di un vendor.
- Bacino di assunzione più profondo per ingegneri gameplay/net senior.

**Perché non "UE5 così com'è".** UE5 è pensato per la fedeltà; noi ne useremo forse il 35%. Il rischio reale e documentato è il *peso morto*: dimensione APK, tempo di avvio, hitch da caricamento shader. Contromisure obbligatorie:
- **Budget APK: 380 MB** al primo download (Play Asset Delivery per il resto, cosmetici inclusi).
- **Avvio a partita in ≤ 11 s** su device minimo, misurato in CI a ogni build.
- **Precompilazione PSO completa**: nessun hitch al primo utilizzo di un effetto è accettabile in un gioco dove 200 ms decidono un duello.
- Un ingegnere dedicato a tempo pieno alla piattaforma Android per tutta la produzione. Non negoziabile: senza, il progetto scivolerà a 45 fps sui device medi e morirà per quello.

**Frame rate.** 60 fps sul device minimo, 120 sul target. **Il frame rate è una regola competitiva, non un'impostazione grafica:** il gioco riduce automaticamente risoluzione (fino a 0,65×) e qualità effetti per difendere i 60, e non offre mai un preset grafico che li metta a rischio. La qualità visiva è la variabile; la fluidità è la costante.

**Gestione termica.** Un telefono a 60 fps pieni scende in throttling tipicamente tra i 6 e i 12 minuti. Le nostre partite durano 6–8 minuti *per progetto*, anche per questo. In più: governatore termico che abbassa la risoluzione prima che il SoC tagli la frequenza, e modalità "Torneo" che parte già a impostazioni ridotte per garantire stabilità su una serie di match.

## 10.2 Server e tick rate

- **Server dedicati autoritativi**, Linux headless, nessuna modalità peer-to-peer. Mai.
- **Tick di simulazione 60 Hz.** Il movimento è il nostro gioco: a 30 Hz il Momentum Arc perde risoluzione e l'Edge Cancel (finestra 0,35 m) diventa una lotteria.
- **Frequenza di invio 30 Hz** con snapshot delta-compressi e interpolazione client. Sdoppiare simulazione e invio dimezza la banda senza toccare la fedeltà della fisica.
- **Banda:** ~38 kbps in downstream, ~14 kbps in upstream per giocatore. Su rete mobile è un requisito di progetto: molti dei nostri utenti giocheranno in 4G con piani dati limitati, e un gioco che consuma 200 MB/ora viene disinstallato.
- **Densità:** ~14 partite 4v4 per vCPU. Con costi cloud attuali, **~0,004 $ per giocatore-ora** — circa 340 k$/anno a 500 k DAU con 40 minuti medi. Sostenibile.
- **Perché non 128 Hz** (la richiesta che arriverà dalla community): moltiplicherebbe per ~2,1 il costo server e per ~2 la banda, a fronte di un miglioramento percepibile solo sopra un livello di precisione di input che il touch non raggiunge. **Ai clienti competitivi mostreremo i numeri.** Se la community esport crescerà, offriremo 120 Hz sui soli server di torneo, dove il costo è irrilevante.

## 10.3 Modello di netcode

```
CLIENT                                 SERVER
input (60 Hz, con numero di sequenza) ───────►
  ├ predizione locale immediata                simulazione autoritativa
  └ salva stato + input in ring buffer         lag compensation (rewind ≤ 200 ms)
                                        ◄─────── snapshot (30 Hz, delta)
riconciliazione:
  se |pos_server − pos_predetta| > 3 cm
     → riapplica tutti gli input dal tick confermato
  smorzamento visivo su 120 ms (mai teletrasporto della camera)
```

**Scelte e motivazioni.**
- **Predizione + riconciliazione** (non lock-step): l'unico modello che dà input immediato su reti mobili con jitter.
- **Lag compensation limitata a 200 ms**, e **solo per il Railcoil** (l'unica arma hitscan). Tutte le altre armi sono a proiettile e quindi **simulate lato server dal momento dello sparo**: il proiettile parte dove il server dice, e viaggia nel tempo reale di tutti. Questa scelta riduce drasticamente il "peeker's advantage" e il fenomeno del "sono morto dietro il muro" — il difetto di esperienza numero uno degli shooter online, e su reti mobili è amplificato. **È una delle ragioni forti per cui l'arsenale è a maggioranza proiettili.**
- **Jitter buffer adattivo** (60–140 ms) sul client, tarato in tempo reale sulla varianza dell'RTT: sul mobile la latenza media conta meno della sua deviazione standard.
- **Grazia su perdita di pacchetti:** fino al 5% di perdita senza degrado percepibile (ridondanza degli input sugli ultimi 3 tick — costa 3 KB/s, vale ogni byte).

## 10.4 Latenza accettabile

| RTT | Esperienza | Azione del sistema |
|---|---|---|
| < 35 ms | Ottimale | — |
| 35–70 ms | Competitivo pieno | Target del matchmaking |
| 70–120 ms | Giocabile, svantaggio percepibile | Avviso; ranked consentita con nota |
| 120–200 ms | Degradata | Solo casual, indicatore rosso visibile a tutti |
| > 200 ms | Non supportata | Rifiuto di entrata in ranked |

**Copertura data center al lancio:** Francoforte, Londra, Virginia, San Paolo, Mumbai, Singapore, Tokyo, Sydney, Bahrein. Nove regioni: meno sarebbe un errore di mercato (Brasile, India e SEA sono i nostri bacini di volume, non l'Europa).

## 10.5 Fisica

- **Fisica di gioco interamente custom e deterministica** (virgola fissa 16.16), nessun uso di Chaos per movimento, proiettili o collisioni dei giocatori. Chaos resta per detriti e cosmetici puramente visivi, non replicati.
- Collisione giocatore: **capsula**, nessuna collisione per-poligono. Prevedibile > realistico: in un gioco di velocità, restare impigliati in uno spigolo è la peggiore esperienza possibile.
- Hitbox: 4 volumi (testa, torso, arti, gambe) con moltiplicatori 1,25/1,0/0,85/0,85, leggermente generosi sul torso (+8%) per compensare il touch.
- Proiettili: raycast continuo per sotto-passo (nessun tunneling ad alta velocità), massimo 64 proiettili attivi per partita.

## 10.6 Anti-cheat

Modello a strati, con l'assunzione dichiarata che **il client è sempre ostile** — su Android, con dispositivi rootati ed emulatori diffusi, è l'unica assunzione professionale.

1. **Autorità del server su tutto** ciò che conta: posizione, danno, raccolta oggetti, munizioni, linea di vista. Un client modificato può mentire solo su ciò che il server ricalcola comunque — cioè quasi nulla.
2. **Niente informazione che il giocatore non dovrebbe avere.** Culling della replicazione server-side: le posizioni dei nemici non visibili **non vengono inviate** (con un margine di 350 ms per l'interpolazione). Questo rende un wallhack strutturalmente quasi inutile, ed è di gran lunga la difesa più efficace che esista. Costo: ~0,3 ms/tick di calcolo di visibilità. Vale la pena.
3. **Play Integrity API** (Google) per l'attestazione del dispositivo. I dispositivi che non passano vanno in una **coda separata**, non bannati: molti utenti legittimi in India e Brasile usano ROM alternative, e perderli sarebbe un errore di mercato.
4. **Rilevamento statistico lato server.** Distribuzione della velocità angolare del pollice, entropia dell'input, tempo di reazione, accuratezza per fascia di distanza. Il touch ha una firma cinematica molto riconoscibile: un aim-bot o un convertitore mouse-su-touch produce distribuzioni che si distinguono con alta confidenza. Un classificatore (gradient boosting su 60 feature per partita) segnala i sospetti; **nessun ban automatico** sopra una certa soglia senza revisione del replay.
5. **Rilevamento emulatori e convertitori** → spostamento nel pool Mouse+Tastiera, che è una soluzione *sportiva* e non punitiva.
6. **Ban a ondate**, non immediati: rende costoso per chi sviluppa cheat capire cosa è stato rilevato.
7. **Nessun anti-cheat kernel-level.** Su Android non è realistico, e le conseguenze reputazionali e di sicurezza superano il beneficio.

## 10.7 Audio

Sottovalutato in quasi tutti i pitch mobile, e per noi è un pilastro: su uno schermo piccolo **l'audio è metà della consapevolezza situazionale**.
- HRTF binaurale, ottimizzata per le cuffie (l'80% dei nostri giocatori competitivi) e con un mix alternativo per l'altoparlante del telefono (compressione forte, enfasi 2–4 kHz, passi sopra l'esplosione).
- Materiali di superficie distinti (§5.6), occlusione e riverbero per zona.
- **Priorità di mix competitiva:** passi e raccolta oggetti hanno priorità assoluta sugli effetti spettacolari. Se il mix è saturo, si taglia l'esplosione, non i passi dell'avversario.
- Segnali unici e non confondibili per: carica del Railcoil, avvio dell'Overload, comparsa dei power-up, scarica del Core.

## 10.8 Build, CI e telemetria

- CI su device farm reale (12 modelli, dal minimo al flagship): a ogni merge, benchmark di fps, tempo di avvio, dimensione APK, consumo batteria per 10 minuti. **Regressione > 5% = merge bloccato.**
- Test di soak automatici con bot: 500 match/notte per rilevare desync, crash, blocchi di navigazione.
- Telemetria: ogni match produce ~40 KB di eventi aggregati. Pipeline in ClickHouse; dashboard di bilanciamento aggiornata ogni ora. **Il Combat Designer deve poter vedere l'effetto di una patch entro 6 ore dal rilascio**, altrimenti il ciclo di bilanciamento è più lento del ciclo del meta.

---

# 11) MONETIZZAZIONE
*Owner: Publisher/Production · Revisione: Creative Director (veto sull'integrità competitiva)*

## 11.1 Modello

**Free-to-play, cosmetico puro, senza gacha.**

Il free-to-play non è una scelta di gusto: è aritmetica. Il nostro modello richiede una popolazione di matchmaking sufficiente in nove regioni, su tre pool di input, su cinque modalità. Un gioco a pagamento su Android a 6,99 $ venderebbe forse 150–400 k copie e produrrebbe code vuote entro 5 mesi in tutte le regioni tranne due. **La liquidità del matchmaking è un requisito di gameplay, e quindi il prezzo di ingresso deve essere zero.**

## 11.2 Cosa vendiamo

| Prodotto | Prezzo | Note |
|---|---|---|
| **Skin Atleta** | 4,99–14,99 $ | Silhouette invariata (vedi §11.4) |
| **Skin arma** | 2,99–9,99 $ | Modello e VFX conformi a regole rigide |
| **Scie di velocità** | 3,99–7,99 $ | Il cosmetico-firma del gioco: si vede quando sei veloce. **Alto valore percepito perché legato all'abilità** |
| **Pacchetti annunciatore** | 4,99 $ | |
| **Emblemi, banner, targhe di frag** | 0,99–3,99 $ | |
| **Battle Pass stagionale** | 9,99 $ / 10 settimane | Due tracce; la premium restituisce abbastanza valuta da pagare il pass successivo |
| **Pass Premium+** | 19,99 $ | Pass + 20 livelli, per chi ha poco tempo |
| **Club Items** (squadre esport) | 4,99 $ | **50% alle organizzazioni** |

**Stime prudenti** (e vanno presentate come tali): conversione a pagante 2,8%, ARPPU annuo 26 $, quindi ARPDAU ≈ **0,073 $**. A 400 k DAU stabili: ~10,6 M$/anno lordi, ~7,4 M$ netti dopo lo store. Sopra la soglia di sostenibilità (6,2 M$ di live ops), non un successo clamoroso. **Il caso base di questo progetto non è un jackpot: è un business di nicchia sano.** Chi lo approva deve volere questo.

## 11.3 Battle Pass — struttura

10 settimane, 100 livelli, progressione per **tempo giocato e obiettivi di abilità** (non per vittorie: premiare le vittorie incentiva lo smurfing e punisce i principianti). Gli obiettivi sono anche didattici: *"raggiungi 28 m/s cinque volte"*, *"vinci 20 cicli di armatura"*, *"scarica 150 di carica in un round"*. Il battle pass diventa così un percorso di apprendimento travestito da ricompensa — il miglior uso possibile del formato.

Completabile in ~45 h su 10 settimane (≈ 40 min/giorno): impegnativo ma non un secondo lavoro. Un pass che richiede 100 ore produce senso di obbligo, e l'obbligo è il contrario del divertimento che vendiamo.

## 11.4 Cosa NON venderemo mai

Questa lista entra nei termini di servizio ed è una promessa pubblica. Violarla anche una volta costa la community competitiva in modo irreversibile.

1. **Qualunque cosa che influenzi statistiche, danno, velocità, vita, munizioni, hitbox.**
2. **Armi, accessi a modalità, mappe.** L'arsenale e le arene sono identici per tutti, sempre.
3. **Vantaggi di matchmaking o di coda.** Nessuna coda prioritaria a pagamento.
4. **Progressione di potere.** Il livello account non sblocca nulla di funzionale.
5. **Skin che alterano la leggibilità.** Regole vincolanti: silhouette entro il ±3% del volume base; nessuna skin più scura del 20% di luminanza o che usi il rosso/ciano riservati al riconoscimento squadra; **in ranked e torneo le skin nemiche vengono forzate al modello uniforme rosso**. La leggibilità dell'avversario non è in vendita.
6. **VFX di arma che aumentino o riducano l'occlusione.** Tutti i VFX cosmetici hanno lo stesso ingombro schermo del modello base, verificato da uno strumento automatico.
7. **Loot box / gacha.** Ragioni: fiducia, regolamentazione crescente in UE e Brasile, e il fatto che il nostro pubblico target (giocatori competitivi adulti) le detesta attivamente.
8. **Vantaggi di informazione a pagamento.** I timer, le statistiche e le funzioni di replay analitico sono gratuite per tutti. *(L'unica differenza premium è la durata di conservazione dei replay — una comodità di archiviazione, non informazione.)*
9. **Aim assist migliorato.** L'assistenza di mira è identica per tutti, documentata pubblicamente nei suoi parametri, e regolata solo per pool di input.
10. **Nessun EOMM, nessuna manipolazione del matchmaking per incentivare l'acquisto.**

## 11.5 Contenuti stagionali (10 settimane)

Ogni stagione: **1 mappa nuova** (o una rielaborazione strutturale di una esistente), **1 ruleset o variante di modalità**, un battle pass, un capitolo di lore leggero, un aggiornamento di bilanciamento, una finestra di circuito esport.

**Deliberatamente assente: nuove armi a ogni stagione.** Il ritmo di rilascio di armi è la strada più rapida per distruggere il bilanciamento di un arena shooter. Obiettivo: **al massimo 2 armi nuove nei primi due anni**, e solo sostituendo o riprogettando qualcosa di esistente. Lo diremo pubblicamente fin dal lancio, perché è un tratto distintivo — l'arsenale stabile *è* una promessa competitiva, non una carenza di contenuto.

---

# 12) ANALISI CRITICA FINALE
*Scritta nel ruolo del publisher, con il mandato di dire no*

## 12.1 Cosa potrebbe farlo funzionare

1. **La categoria è davvero vuota.** Non esiste un arena shooter con movimento profondo su mobile. Non è un'asserzione di marketing: è verificabile in dieci minuti sullo store. Il rischio di essere schiacciati da un clone di CODM non esiste, perché nessuno sta costruendo in questa direzione.
2. **Il Momentum Arc è una soluzione reale a un problema reale.** Se funziona come descritto — e va dimostrato al mese 3, non discusso — è una tecnologia di input difendibile e brevettabile, che costituisce un fossato competitivo. Se non funziona, il progetto non ha ragione di esistere. **La cosa positiva è che lo si scopre in 90 giorni e con 600 k$**, non in 18 mesi.
3. **Surge è un formato esport nuovo e comprensibile.** Guardabile senza saper giocare, leggibile su schermo verticale, con una barra sola. È l'unica cosa nel documento che nessun concorrente può copiare rapidamente, perché richiede l'intero sistema di movimento sotto.
4. **Costi strutturalmente bassi.** Nessun eroe, nessuna abilità, nessuna campagna, nessuna progressione di potere, arsenale congelato. Il costo di live ops è una frazione di quello di un hero shooter, e il rischio di bilanciamento è una frazione. Questo gioco può **sopravvivere a 250 k DAU**, il che è una soglia raggiungibile.
5. **Genera clip per costruzione.** In un mercato con CPI a 4–8 $, un prodotto con coefficiente organico alto cambia l'intero modello finanziario.
6. **Il pubblico di riferimento esiste ed è mal servito.** Giocatori 25–40 che hanno smesso per mancanza di tempo, con capacità di spesa e nostalgia attiva. È un pubblico piccolo, ma è *raggiungibile a costo basso* perché è concentrato in community identificabili.

## 12.2 Cosa potrebbe farlo fallire

**In ordine decrescente di probabilità di uccidere il progetto.**

1. **Il touch potrebbe non reggere la profondità.** *Probabilità alta.* Se il Momentum Arc risulta impreciso, stancante o non gratificante, tutto il documento crolla: non abbiamo un piano B, perché senza movimento profondo siamo uno shooter mobile mediocre in un mercato saturo. **Mitigazione: gate di uccisione al mese 3** (§12.5). Nessuna spesa oltre il prototipo senza il superamento.
2. **Il genere è storicamente invendibile.** *Probabilità alta.* Quake Champions, Diabotical, Toxikk, Reflex: tutti falliti commercialmente, tutti con team competenti. L'ipotesi ottimistica "sono falliti perché erano su PC, dove il mercato è occupato" è plausibile ma **non dimostrata**. Mitigazione parziale: Surge e il posizionamento come "gioco di velocità" invece che "gioco di mira" — cioè vendere a un pubblico più ampio di quello degli arena shooter storici. Il pitch al giocatore non deve mai essere "è come Quake".
3. **Curva di apprendimento contro retention mobile.** *Probabilità alta.* Il mercato mobile ha D1 di ~35% e D30 di ~5%. Un gioco in cui si perde 25-8 per le prime dieci partite sarà brutale. Mitigazioni: le prime 15 partite in lobby di soli principianti e bot, Circuit Run come attività di successo garantito, nessuna statistica visibile prima del livello 5, e la revisione post-partita che trasforma la sconfitta in informazione. **Resta il rischio numero uno in termini di ricavi**, distinto dal rischio 1 che è esistenziale.
4. **Liquidità di matchmaking in nove regioni, tre pool di input, cinque modalità.** *Probabilità media-alta.* Il prodotto matematico è spaventoso. Un giocatore Touch, fascia Void, in Australia, che vuole giocare a CTF, potrebbe non trovare mai una partita. **Cambierei questo prima di sviluppare** (§12.4).
5. **Dipendenza da un singolo sistema tecnico.** *Media.* Il netcode deterministico a virgola fissa su un parco Android enorme è genuinamente difficile. Un bug di desync intermittente su un SoC specifico può costare due mesi.
6. **Costo di acquisizione.** *Media.* Se l'organico non parte, il CPI di 5 $ su un pubblico di nicchia rende l'LTV insufficiente. L'intero modello poggia su un fattore virale che al momento è un'ipotesi.
7. **Google Play.** *Bassa ma con impatto alto.* Un gioco da 380 MB, competitivo, senza gacha, riceve meno visibilità editoriale di un titolo che segue le convenzioni dello store.

## 12.3 Quali sistemi sono troppo costosi

| Sistema | Costo | Verdetto del publisher |
|---|---|---|
| **Bot con L3 appreso (self-play league)** | ~1,35 M$ + 105 k$ GPU | **Taglia L3 dalla v1.0.** L0–L2 bastano per coda, abbandoni e allenamento fino a Plasma. Sposta L3 all'anno 2 e finanzialo con i ricavi. Risparmio immediato: **~900 k$ e 3 mesi di calendario.** |
| **Nove regioni al lancio** | ~340 k$/anno + ops | **Riduci a cinque** (Francoforte, Virginia, San Paolo, Mumbai, Singapore). Aggiungi le altre quando il DAU regionale supera 15 k. Risparmio: ~180 k$/anno e una quantità significativa di complessità operativa. |
| **Cinque modalità al lancio** | ~4 mesi-team | **Quattro.** Vedi §12.4: CTF esce dal lancio. |
| **Esport 1,1 M$ anno 1** | 1,1 M$ | **Riduci a 450 k$**: solo Circuit Open settimanali e un evento finale. Un montepremi grande senza spettatori è denaro bruciato, e si può sempre aumentare. |
| **Replay a snapshot con conservazione 30 giorni per tutti** | ~90 k$/anno di storage | Accettabile. **Non tagliare**: alimenta bot, anti-cheat, allenamento e marketing. Uno dei pochi sistemi con quattro ritorni distinti. |
| **Ingegnere Android dedicato a tempo pieno** | ~180 k$/anno | **Non negoziabile. Aggiungine un secondo.** È l'assunzione con il miglior rapporto rischio/rendimento dell'intero piano. |

## 12.4 Cosa cambierei prima di iniziare lo sviluppo

Sette modifiche, da applicare al documento prima del kickoff:

1. **Tagliare CTF dal lancio.** È la modalità più costosa (una mappa dedicata, bilanciamento asimmetrico, pathing dei bot complesso), la meno adatta al mobile (10 minuti, coordinazione a 4), e quella con il pubblico più piccolo. Sposta a Stagione 3. **Kiln diventa una mappa TDM/Surge** e il risparmio finanzia un secondo ingegnere Android.
2. **Ridurre da 3 a 2 pool di input**: unire Controller e Mouse+Tastiera in un unico pool "Periferiche". Sono pochi giocatori, la differenza tra i due è minore della differenza con il touch, e la liquidità della coda vale più della purezza.
3. **Rendere Surge la modalità di punta del marketing, non il Duel.** Il Duel è il nostro formato esport migliore ma è il *peggiore* per acquisire nuovi utenti: è intimidatorio, solitario e comunica "sarai umiliato". Surge comunica velocità e squadra. **Duel resta l'anima competitiva, Surge diventa la faccia del prodotto.** Questa sola inversione di marketing potrebbe valere più di tutto il resto del piano di UA.
4. **Aggiungere un gate di uccisione esplicito e finanziato al mese 3.** Descritto in §12.5. Il consiglio di amministrazione deve avere il diritto contrattuale di chiudere a 600 k$ invece che a 4 M$.
5. **Alzare il device floor, non abbassarlo.** La tentazione di supportare 3 GB di RAM per raggiungere l'India a volume è forte ed è un errore: il gioco vive o muore sui 60 fps stabili, e un giocatore a 40 fps instabili perde partite e disinstalla, contando comunque nel CPI. Meglio un bacino più piccolo con un'esperienza integra.
6. **Congelare l'arsenale a 8 armi fino al mese 30** e comunicarlo pubblicamente come promessa. Trasforma un limite di budget in un valore di marca.
7. **Scrivere il piano di soft launch prima del piano di produzione.** Soft launch in Polonia, Filippine e Brasile al mese 18, con 150 k$ di spesa. I dati di quelle tre regioni decidono la data di lancio globale — non il contrario.

## 12.5 Il gate di uccisione (mese 3)

Il prototipo costa **~620 k$** (9 persone × 3 mesi + overhead). Deve superare **tutti** i criteri seguenti, misurati su un panel esterno di 120 tester reclutati in tre paesi. Ogni criterio non superato è un *no*, non un "discutiamone".

| # | Criterio | Soglia |
|---|---|---|
| 1 | Tempo mediano di sessione volontaria, build **senza armi** (solo movimento) | **≥ 7 minuti** |
| 2 | Tester che eseguono un'Arc Chain di 3+ salti entro 30 minuti | **≥ 55%** |
| 3 | Velocità mediana al minuto 60 di gioco rispetto al minuto 5 | **≥ +65%** (dimostra una curva di abilità reale) |
| 4 | D1 retention del panel | **≥ 42%** |
| 5 | Valutazione "il movimento è divertente" (1–7) | **mediana ≥ 5,5** |
| 6 | 60 fps stabili su Snapdragon 6 Gen 1, 8 giocatori, 10 minuti | **≥ 95% dei frame entro budget** |
| 7 | Parità Momentum Arc vs strafe con controller, guadagno di velocità | **entro ±5%** |

Se 5 su 7 passano, si itera per 6 settimane e si rimisura. Se meno di 5, **si chiude il progetto.** Averlo scritto qui è il motivo per cui questo pitch merita fiducia: il modo più costoso di fallire è scoprirlo al mese 20.

---

## 12.6 ROADMAP

### Fase 0 — Pre-produzione (mesi −2 → 0) · 9 persone · 180 k$
Documento di design congelato, team core assunto, pipeline tecnica scelta, blockout di Gantry su carta, prototipo di movimento in engine grezzo. **Deliverable: questo documento + una build che si muove.**

### Fase 1 — PROTOTIPO (mesi 1–3) · 9 persone · 620 k$
| Mese | Contenuto |
|---|---|
| 1 | Movimento completo (§3.2), Momentum Arc v1, Gantry blockout, client-server basilare a 60 Hz |
| 2 | 4 armi (Splitter, Nailer, Void, Railcoil), armature e timer, **prototipo di Surge in blockout**, bot L1 |
| 3 | Circuit Run, 12 percorsi, telemetria, **panel esterno di 120 tester** |

**Gate (§12.5). Go / no-go vincolante.**

### Fase 2 — PRODUZIONE / ALPHA (mesi 4–12) · scala a 38 persone · 5,4 M$
| Mesi | Contenuto |
|---|---|
| 4–6 | Arsenale completo (8 armi), Gantry + Solaris rifinite, bot L2 (utility AI), netcode di produzione con riconciliazione |
| 7–9 | Verge + Hollow, Surge completo e bilanciato, DM/TDM/Duel, matchmaking v1, OpenSkill, 2 regioni server |
| 10–12 | **Alpha chiusa: 5.000 giocatori su invito.** Anti-cheat v1, telemetria completa, sistema replay, 3 regioni, primo ciclo di bilanciamento sui dati |

**Gate alpha (mese 12):** D7 ≥ 22% · 60 fps su device minimo su tutte e 4 le mappe · < 0,4% di partite con desync · tempo mediano di coda < 30 s · distribuzione delle vette d'arma entro il modello di §4.3 su tutte le fasce.

### Fase 3 — BETA (mesi 13–18) · 48 persone · 4,1 M$
| Mesi | Contenuto |
|---|---|
| 13–15 | **Beta aperta globale.** Kiln, 5 regioni, monetizzazione impiantata ma disattivata, Play Integrity, pool di input separati, bot L3 in addestramento sui replay della beta |
| 16–18 | **Soft launch monetizzato** in Polonia / Filippine / Brasile (150 k$ di UA). Battle pass S0, negozio, Circuit Open settimanali di prova. Ottimizzazione termica e di dimensione APK |

**Gate beta (mese 18):** D30 ≥ 7% · ARPDAU ≥ 0,05 $ · CPI < 4,50 $ nelle regioni di soft launch · crash-free session ≥ 99,3% · coefficiente virale (K) misurato ≥ 0,15.

### Fase 4 — LANCIO (mesi 19–24) · 52 persone · 4,5 M$ + 10 M$ UA
| Mesi | Contenuto |
|---|---|
| 19–21 | Rifinitura su dati del soft launch, 5ª mappa, localizzazione (11 lingue), accessibilità, certificazione store |
| 22 | **Lancio globale Android.** Stagione 1 "Ignition". Push di UA. Lancio del Circuit Open |
| 23–24 | Stabilizzazione, prima grande patch di bilanciamento, primo Circuit Series |

### Fase 5 — ANNO 1 POST-LANCIO (mesi 25–36) · 34 persone · 6,2 M$
- Stagioni 2, 3, 4, 5 (una mappa e un pass ciascuna).
- **Mese 26:** bot L3 in produzione (se il gate finanziario lo consente).
- **Mese 28:** CTF rientra, con Kiln convertita.
- **Mese 30:** modalità spettatore verticale completa + regia automatica.
- **Mese 33:** **iOS**, se D30 ≥ 8% e il flusso di cassa è positivo. *(iOS è una ricompensa per aver funzionato, non un requisito di lancio: raddoppia i costi di QA e certificazione, e il nostro pubblico di volume — Brasile, India, SEA — è su Android.)*
- **Mese 36:** World Circuit 1, evento fisico contenuto.

### Fase 6 — ANNO 2 (mesi 37–48) · 30 persone · 5,8 M$
- Stagioni 6–9. Prima arma nuova in 2 anni (mese 40), preceduta da 6 settimane di test pubblico su server dedicato.
- Editor di mappe della community + curazione ufficiale (**mese 42**): la leva di longevità con il miglior rapporto costo/beneficio esistente per un arena shooter, e l'unica che ci permette di far crescere il catalogo di mappe più velocemente di quanto possiamo pagarlo.
- Modalità classificata a squadre con club persistenti.
- Valutazione di un porting PC/console, **solo** se la base esport lo richiede e con code rigorosamente separate.

### Riepilogo economico

| Voce | Importo |
|---|---|
| Sviluppo mesi −2 → 24 | **14,8 M$** |
| User acquisition anno 1 | **10,0 M$** |
| Live ops anno 1 (mesi 25–36) | **6,2 M$** |
| Live ops anno 2 | **5,8 M$** |
| Esport (2 anni, ridotto per §12.3) | **1,3 M$** |
| **Totale 4 anni** | **≈ 38,1 M$** |
| Pareggio stimato | **~480 k DAU sostenuti**, mese 31–34 |

---

## CONCLUSIONE DEL PUBLISHER

Il progetto è finanziabile **a condizioni precise**:

1. Approvazione a scaglioni, con il gate del mese 3 contrattualmente vincolante e il diritto di chiusura a 620 k$.
2. Applicazione delle sette modifiche di §12.4 prima del kickoff.
3. Taglio di L3 dai bot e riduzione a cinque regioni al lancio.
4. Surge come volto del prodotto, Duel come anima competitiva.
5. Riesame completo al mese 18, sui dati reali di soft launch e non sulle proiezioni contenute in questo documento.

Il rischio è reale e concentrato in un punto solo: **il Momentum Arc funziona o non funziona.** Il pregio di questo piano è che quella domanda costa 620 k$ e 90 giorni per avere risposta, mentre il premio, se la risposta è sì, è la proprietà esclusiva di una categoria vuota su una piattaforma da tre miliardi di dispositivi.

Si procede al prototipo.

---

*Documento redatto da: Creative Director, Lead Game Designer, Combat Designer, Level Designer competitivo, Multiplayer Network Engineer, AI Engineer, Technical Director, Esports Designer.*
*Revisione 0.9 — soggetto a modifica dopo il gate del mese 3.*
