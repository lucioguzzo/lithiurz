package com.lucio.guzzobot.data.model

import kotlinx.serialization.Serializable

enum class WorkspaceMode {
    /** Nessuna area di lavoro: il modello risponde e basta. */
    NONE,

    /** Una cartella del telefono, scelta dall'utente. */
    LOCAL,

    /** Un repository GitHub, via API. */
    GITHUB,
}

/** Dove GUZZOBOT può leggere e scrivere file. */
@Serializable
data class WorkspaceConfig(
    val mode: WorkspaceMode = WorkspaceMode.NONE,
    /** URI dell'albero SAF concesso dall'utente. */
    val localTreeUri: String? = null,
    val localLabel: String? = null,
    val owner: String? = null,
    val repo: String? = null,
    val branch: String = "main",
) {
    val isConfigured: Boolean
        get() = when (mode) {
            WorkspaceMode.NONE -> false
            WorkspaceMode.LOCAL -> !localTreeUri.isNullOrBlank()
            WorkspaceMode.GITHUB -> !owner.isNullOrBlank() && !repo.isNullOrBlank()
        }

    val label: String
        get() = when (mode) {
            WorkspaceMode.NONE -> ""
            WorkspaceMode.LOCAL -> localLabel ?: "Cartella locale"
            WorkspaceMode.GITHUB -> "$owner/$repo"
        }

    companion object {
        /** Chiave con cui il token GitHub viene custodito, cifrato, nel SecretStore. */
        const val GITHUB_SECRET = "workspace_github"
    }
}
