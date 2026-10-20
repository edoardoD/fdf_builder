package manutenzioni.domain.service

import manutenzioni.domain.model.EventoManutenzione
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.StatoEventoManutenzione
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Motore di calcolo per le ricorrenze periodiche degli eventi di manutenzione (Scheduler Engine).
 *
 * Calcola le date future successive applicando la frequenza selezionata
 * (es. +6 mesi, +1 anno) e gestendo correttamente il bound di fine mese
 * (es. 31/08 + 6 mesi -> 28/02 in anni non bisestili).
 */
object RicorrenzeManutenzioneEngine {

    private val FORMATTER_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Calcola la sequenza delle prossime date ricorrenti a partire da una [dataBase] stringa ("dd/MM/yyyy")
     * con il passo specificato da [frequenza] per un numero massimo di [numeroOccorrenze].
     */
    fun calcolaProssimeDateRicorrenti(
        dataBase: String,
        frequenza: Periodo,
        numeroOccorrenze: Int = 12
    ): List<String> {
        val baseDate = CalendarioScadenzeCalculator.parseDataRiferimento(dataBase)
        val mesiPasso = frequenza.inMesi().coerceAtLeast(1)
        if (numeroOccorrenze <= 0) return emptyList()

        return (1..numeroOccorrenze).map { step ->
            val target = baseDate.plusMonths((step * mesiPasso).toLong())
            target.format(FORMATTER_IT)
        }
    }

    /**
     * Genera la lista degli eventi futuri ricorrenti a partire da un [eventoBase]
     * su un orizzonte temporale specificato da [orizzonteMesi] (default 24 mesi).
     */
    fun generaRicorrenzeFuture(
        eventoBase: EventoManutenzione,
        orizzonteMesi: Int = 24
    ): List<EventoManutenzione> {
        if (eventoBase.impiantiPianificati.isEmpty() || orizzonteMesi <= 0) return emptyList()

        val freqMinima = eventoBase.impiantiPianificati
            .map { it.periodicitaScelta }
            .minByOrNull { it.inMesi() }
            ?: return emptyList()

        val mesiPasso = freqMinima.inMesi().coerceAtLeast(1)
        val occorrenze = (orizzonteMesi / mesiPasso).coerceIn(1, 60)
        val serieId = eventoBase.serieRicorrenzaId?.takeIf { it.isNotBlank() } ?: eventoBase.id

        val dateRicorrenti = calcolaProssimeDateRicorrenti(
            dataBase = eventoBase.dataPianificata,
            frequenza = freqMinima,
            numeroOccorrenze = occorrenze
        )

        return dateRicorrenti.map { dataRicorrente ->
            eventoBase.copy(
                id = UUID.randomUUID().toString(),
                dataPianificata = dataRicorrente,
                stato = StatoEventoManutenzione.PIANIFICATO,
                serieRicorrenzaId = serieId,
                dataCompletamentoEffettiva = null,
                sessioneVerificaId = null
            )
        }
    }
}
