//
//  ShareViewController.swift
//  Share Extension di VoceNota
//
//  Riceve l'audio condiviso da un'altra app e riapre VoceNota passandole il
//  file attraverso il gruppo condiviso (App Group).
//
//  Il target non è incluso nel progetto Xcode versionato: va creato una
//  volta sola seguendo docs/IOS.md, poi questi file vanno collegati al
//  nuovo target.
//

import receive_sharing_intent

class ShareViewController: RSIShareViewController {

    /// Nessuna finestra di composizione: l'audio viene passato subito
    /// all'app principale, che mostra l'anteprima e avvia la trascrizione.
    override func shouldAutoRedirect() -> Bool {
        return true
    }
}
