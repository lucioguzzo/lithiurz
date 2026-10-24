package com.lucio.guzzobot.data.skills

import kotlinx.serialization.Serializable

enum class SkillCategory { SCRITTURA, STUDIO, CODICE, CASA, ANALISI }

/**
 * Una competenza: un modo di lavorare, con un nome, che si applica a una
 * conversazione.
 *
 * Non è un programma. In Claude una skill può portarsi dietro degli script;
 * qui no, e non per trascuratezza: un telefono non esegue codice arbitrario,
 * e fingere il contrario darebbe skill che sembrano funzionare e non fanno
 * niente. Quando serve eseguire davvero qualcosa, la strada è l'agente con la
 * CI di GitHub, che esiste già. Una skill qui è quindi istruzioni: la
 * differenza fra chiedere «correggi» e ottenere una correzione fatta come si
 * deve, su qualunque modello, anche gratuito, perché non richiede nulla di
 * speciale al modello.
 */
@Serializable
data class Skill(
    val id: String,
    val name: String,
    /** Quando conviene usarla: è la riga che si legge nell'elenco. */
    val description: String,
    /** Il corpo, che finisce nel prompt di sistema della conversazione. */
    val instructions: String,
    val category: SkillCategory = SkillCategory.SCRITTURA,
    val builtIn: Boolean = false,
    /** Se compare fra quelle proponibili. */
    val enabled: Boolean = true,
)

object Skills {

    val defaults: List<Skill> = listOf(
        Skill(
            id = "proofread",
            name = "Correttore di bozze",
            description = "Corregge senza riscrivere: ortografia, grammatica, punteggiatura",
            category = SkillCategory.SCRITTURA,
            builtIn = true,
            instructions = """
                Correggi il testo che ti viene dato intervenendo il meno possibile.
                Sistema ortografia, grammatica, punteggiatura, accordi e refusi;
                non cambiare lo stile, il registro, la lunghezza delle frasi né
                le scelte lessicali dell'autore, anche quando faresti diverso.

                Restituisci prima il testo corretto, pulito e pronto da copiare.
                Dopo, un elenco breve delle correzioni fatte, una per riga, nella
                forma «sbagliato → corretto», solo per quelle non ovvie.
                Se un passaggio è ambiguo e la correzione dipende da cosa
                l'autore intendeva, non scegliere al posto suo: segnalalo e
                chiedi.
            """.trimIndent(),
        ),
        Skill(
            id = "clearer",
            name = "Riscrivi più chiaro",
            description = "Stessa sostanza, metà delle parole, nessun burocratese",
            category = SkillCategory.SCRITTURA,
            builtIn = true,
            instructions = """
                Riscrivi il testo perché si capisca alla prima lettura.

                Togli il burocratese, le forme passive inutili, i doppioni e le
                formule di riempimento. Spezza le frasi lunghe. Usa parole
                concrete al posto di quelle astratte. Metti in evidenza, in
                apertura, la cosa che il lettore deve sapere per prima.

                Non togliere informazioni: se qualcosa non ti è chiaro, non
                tagliarlo e non inventare — chiedi. Conserva numeri, nomi,
                date e condizioni esattamente come sono.

                Dai il testo riscritto e, in fondo, una riga su cosa hai
                cambiato nella struttura e perché.
            """.trimIndent(),
        ),
        Skill(
            id = "email",
            name = "Email professionale",
            description = "Dall'appunto all'email pronta, nel tono che scegli",
            category = SkillCategory.SCRITTURA,
            builtIn = true,
            instructions = """
                Trasforma quello che ti viene detto in un'email pronta da mandare.

                Chiedi solo quello che davvero non puoi dedurre: destinatario,
                rapporto (formale o informale) e cosa vuoi ottenere. Se il tono
                non è indicato, usa un formale cortese e asciutto.

                Struttura: oggetto specifico (non «Richiesta informazioni»), una
                riga che dice subito perché scrivi, il necessario in mezzo, una
                chiusura che dice cosa ti aspetti e quando. Niente preamboli
                sulla speranza che il destinatario stia bene, niente
                ringraziamenti ripetuti.

                Alla fine proponi, in una riga, una versione alternativa più
                breve o più morbida, perché a volte serve quella.
            """.trimIndent(),
        ),
        Skill(
            id = "summary",
            name = "Riassunto strutturato",
            description = "Documenti lunghi: cosa dicono, cosa chiedono, cosa manca",
            category = SkillCategory.ANALISI,
            builtIn = true,
            instructions = """
                Riassumi il documento in modo che chi non lo ha letto possa
                decidere senza aprirlo.

                Nell'ordine: una frase su cosa è e a cosa serve; i punti
                principali in un elenco, ciascuno con il dato o la cifra che lo
                sostiene; cosa viene chiesto a chi legge, se qualcosa viene
                chiesto; le scadenze e le condizioni, riportate alla lettera.

                Chiudi con «Da verificare»: i punti vaghi, le affermazioni senza
                numeri, le cose che il documento dà per scontate. Non riempire
                i buchi con quello che probabilmente intendeva: dire che manca
                è più utile.

                Non usare mai un numero che non sia scritto nel documento.
            """.trimIndent(),
        ),
        Skill(
            id = "explain-simple",
            name = "Spiega da zero",
            description = "Un argomento difficile spiegato senza presupporre niente",
            category = SkillCategory.STUDIO,
            builtIn = true,
            instructions = """
                Spiega l'argomento a chi non ne sa niente e non vuole sentirsi
                stupido.

                Parti dal problema che quella cosa risolve, non dalla
                definizione. Usa un esempio concreto prima di ogni termine
                tecnico, e quando introduci un termine dillo chiaramente:
                «questo si chiama X». Un concetto per paragrafo.

                Non usare analogie che spostano il problema («è come un
                database»): se serve un paragone, prendilo dalla vita di tutti
                i giorni.

                Chiudi con le due o tre cose che si confondono più spesso, e
                con la differenza fra loro. Poi offriti di approfondire il punto
                che è rimasto più in ombra.
            """.trimIndent(),
        ),
        Skill(
            id = "flashcards",
            name = "Schede di studio",
            description = "Trasforma appunti o un capitolo in domande e risposte",
            category = SkillCategory.STUDIO,
            builtIn = true,
            instructions = """
                Ricava dal materiale un mazzo di schede domanda/risposta per
                ripassare.

                Una scheda per ogni fatto che va saputo, non per ogni frase.
                La domanda deve essere rispondibile da sola, senza il contesto
                del capitolo. La risposta sta in una o due righe.

                Evita le domande a cui si risponde sì o no, e quelle la cui
                risposta è contenuta nella domanda. Preferisci «perché» e
                «in che cosa differisce da» a «che cos'è».

                Formato: una riga per scheda, «D: … | R: …», così si possono
                incollare in qualunque app di ripetizione. In fondo, segnala
                i punti del materiale che non si prestano a una scheda perché
                vanno capiti e non memorizzati.
            """.trimIndent(),
        ),
        Skill(
            id = "translate",
            name = "Traduci e spiega",
            description = "Traduzione fedele, più le scelte difficili motivate",
            category = SkillCategory.STUDIO,
            builtIn = true,
            instructions = """
                Traduci nella lingua richiesta (se non è detta, in italiano).

                Prima la traduzione, scorrevole come se fosse stata scritta in
                quella lingua, non calcata sull'originale. Conserva il registro:
                un testo informale non diventa formale.

                Poi, solo per i punti davvero difficili, una nota per ciascuno:
                l'espressione originale, la resa scelta e perché. Includi i
                modi di dire, i giochi di parole, i termini tecnici con più
                traducenti e tutto quello che nella lingua d'arrivo non esiste.

                Se un passaggio è ambiguo nell'originale, non risolverlo di
                nascosto: traduci mantenendo l'ambiguità e dillo nella nota.
            """.trimIndent(),
        ),
        Skill(
            id = "code-review",
            name = "Revisione del codice",
            description = "Cerca i difetti veri, non lo stile",
            category = SkillCategory.CODICE,
            builtIn = true,
            instructions = """
                Rivedi il codice cercando quello che si rompe, in questo ordine:
                errori di correttezza, casi limite non gestiti, valori nulli,
                concorrenza, perdite di risorse, dati non validati.

                Per ogni problema: dove sta, cosa succede di sbagliato, e un
                caso concreto che lo scatena — input preciso, risultato
                sbagliato. Se non riesci a costruire quel caso, non è un
                problema accertato: dillo come sospetto, separato dagli altri.

                Proponi la correzione minima. Non riscrivere quello che
                funziona, non proporre architetture diverse se non è stato
                chiesto, non segnalare nomi di variabili e formattazione se non
                rendono il codice ambiguo.

                Se non trovi difetti, dillo invece di inventarne.
            """.trimIndent(),
        ),
        Skill(
            id = "explain-code",
            name = "Spiega questo codice",
            description = "Cosa fa, perché è scritto così, dove può rompersi",
            category = SkillCategory.CODICE,
            builtIn = true,
            instructions = """
                Spiega il codice a chi deve metterci le mani.

                Comincia da cosa fa nel complesso, in due righe. Poi segui il
                percorso dei dati: cosa entra, come viene trasformato, cosa
                esce. Nomina solo le parti che contano per capire, salta la
                carpenteria.

                Dedica un passaggio alle scelte non ovvie: perché quel
                controllo, perché quell'ordine, cosa succederebbe a togliere
                quella riga. Segnala le assunzioni implicite.

                Chiudi con i punti fragili: dove un dato inatteso farebbe
                danni, e cosa guardare per primo quando qualcosa non va.
                Se una parte non la capisci, dillo e indica quale.
            """.trimIndent(),
        ),
        Skill(
            id = "tests",
            name = "Scrivi i test",
            description = "Test che falliscono per i motivi giusti",
            category = SkillCategory.CODICE,
            builtIn = true,
            instructions = """
                Scrivi i test per il codice dato, nello stesso stile e con la
                stessa libreria dei test che già esistono nel progetto.

                Copri prima il comportamento normale, poi i casi limite: valori
                vuoti, zero, negativi, limiti degli intervalli, input malformato,
                ordine inatteso. Un test per comportamento, con un nome che dice
                cosa si aspetta.

                Niente test che verificano l'implementazione invece del
                risultato: se una riscrittura interna li fa fallire pur
                funzionando tutto, sono sbagliati.

                Se il codice non è testabile com'è, dillo e indica la modifica
                minima che lo renderebbe tale, invece di scrivere test
                tortuosi.
            """.trimIndent(),
        ),
        Skill(
            id = "commit",
            name = "Messaggio di commit",
            description = "Dal diff a un messaggio che spiega il perché",
            category = SkillCategory.CODICE,
            builtIn = true,
            instructions = """
                Scrivi il messaggio di commit per le modifiche date.

                Prima riga: fino a settanta caratteri, all'infinito o
                all'imperativo, dice cosa cambia il commit — non quali file
                tocca. Poi riga vuota.

                Nel corpo spiega il perché: qual era il problema, perché questa
                soluzione e non un'altra, cosa resta fuori. Il cosa si legge
                dal diff, il perché no.

                Se nel diff ci sono più cambiamenti indipendenti, non
                inventare un filo che li unisca: dillo e proponi come dividerli
                in più commit.

                Adeguati alle convenzioni dei commit già presenti nel progetto,
                se le vedi.
            """.trimIndent(),
        ),
        Skill(
            id = "documents",
            name = "Leggi questo documento",
            description = "Bollette, contratti, referti: cosa dicono in pratica",
            category = SkillCategory.CASA,
            builtIn = true,
            instructions = """
                Leggi il documento e spiega cosa significa per chi lo ha
                ricevuto.

                Comincia dalla cosa che conta: quanto si paga, entro quando,
                cosa si è impegnati a fare, cosa è stato deciso. Poi le voci
                una per una, con le cifre esatte come sono scritte.

                Segnala le clausole che cambiano le cose: rinnovi automatici,
                penali, scadenze per disdire, condizioni che si attivano dopo,
                costi che oggi non si vedono. E le cose insolite rispetto a
                documenti dello stesso tipo.

                Non dare consigli legali, fiscali o medici e non rassicurare:
                di' cosa c'è scritto, e quando una cosa richiede un
                professionista dillo chiaramente. Non riportare mai una cifra
                che non sia nel documento.
            """.trimIndent(),
        ),
        Skill(
            id = "shopping",
            name = "Dalla foto alla lista",
            description = "Foto di scaffali, frigo o scontrini → lista ordinata",
            category = SkillCategory.CASA,
            builtIn = true,
            instructions = """
                Guarda le immagini e ricava una lista pratica.

                Elenca quello che vedi con le quantità, raggruppato per reparto
                o per zona, così la lista si segue camminando. Dove la foto non
                basta a capire quantità o formato, scrivi «da verificare»
                invece di indovinare.

                Se si tratta di scontrini, riporta le voci con i prezzi e fai
                il totale, dicendo se coincide con quello stampato.

                Non aggiungere prodotti che non sono nelle immagini, anche se
                «di solito servono»: se vuoi suggerirne, mettili in un elenco
                separato, chiamato per quello che è.
            """.trimIndent(),
        ),
        Skill(
            id = "meeting",
            name = "Verbale di riunione",
            description = "Dalla registrazione o dagli appunti a decisioni e compiti",
            category = SkillCategory.CASA,
            builtIn = true,
            instructions = """
                Ricava dal materiale un verbale utile a chi non c'era.

                Tre parti, in quest'ordine. Decisioni: cosa è stato deciso,
                una riga per decisione. Compiti: chi fa cosa entro quando — se
                il responsabile o la scadenza non sono stati detti, scrivi
                «non assegnato» invece di attribuirlo a qualcuno. Questioni
                aperte: su cosa non si è deciso, e cosa serve per decidere.

                Lascia fuori le chiacchiere e le ripetizioni. Non trasformare
                un'opinione espressa in una decisione presa: è l'errore che
                rende i verbali dannosi.

                Se qualcosa nel materiale è inudibile o illeggibile, segnalalo
                al punto giusto.
            """.trimIndent(),
        ),
        Skill(
            id = "spreadsheet",
            name = "Analizza i dati",
            description = "Fogli di calcolo e tabelle: cosa dicono i numeri",
            category = SkillCategory.ANALISI,
            builtIn = true,
            instructions = """
                Analizza i dati dati.

                Prima descrivi cosa hai davanti: quante righe, quali colonne,
                che periodo coprono, e i problemi che vedi — celle vuote,
                doppioni, unità incoerenti, valori impossibili. Questo prima di
                ogni conclusione, perché decide quanto valgono.

                Poi i conti che servono: totali, medie, andamenti, differenze
                fra gruppi. Mostra il numero e come l'hai ottenuto.

                Distingui sempre quello che i dati dicono da quello che
                suggeriscono. Non attribuire una causa a una correlazione, non
                proiettare un andamento oltre i dati che hai, e dove il
                campione è troppo piccolo per concludere, dillo.
            """.trimIndent(),
        ),
    )
}
