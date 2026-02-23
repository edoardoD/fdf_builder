package manutenzioni.domain

import manutenzioni.app.data.Impianto

interface ManutenzioneRepository {
    suspend fun salvaImpianto(impianto: Impianto)
    suspend fun caricaImpianti(): List<Impianto>
    suspend fun eliminaImpianto(codIntervento: String)
    suspend fun getImpianto(codIntervento: String): Impianto?

}