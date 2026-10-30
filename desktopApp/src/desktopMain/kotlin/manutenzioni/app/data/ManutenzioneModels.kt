package manutenzioni.app.data

import kotlinx.serialization.Serializable
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto

/** Wrapper per la serializzazione del database JSON */
@Serializable
data class ManutenzioniDatabase(
    val impianti: List<Impianto>,
    val clienti: List<Cliente> = emptyList(),
    val cantieri: List<Cantiere> = emptyList(),
    val componenti: List<manutenzioni.domain.model.ComponenteStandard> = emptyList(),
    val catalogoApprovato: List<manutenzioni.domain.model.ComponenteApprovato> = emptyList(),
    val sessioniVerifica: List<manutenzioni.domain.model.SessioneVerifica> = emptyList(),
    val utenti: List<manutenzioni.domain.model.Utente> = emptyList(),
    val eventiManutenzione: List<manutenzioni.domain.model.EventoManutenzione> = emptyList()
)
