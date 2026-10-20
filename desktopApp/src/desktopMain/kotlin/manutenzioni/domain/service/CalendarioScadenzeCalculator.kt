package manutenzioni.domain.service

import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.CantiereScadenzaInMese
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.MeseScadenzaGlobaleGroup
import manutenzioni.domain.model.MeseScadenzaGroup
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.ScadenzaImpiantoItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Motore puro di dominio per il calcolo del Calendario Scadenze (R27), sia per singolo cantiere
 * sia aggregato a livello globale su tutti i cantieri.
 *
 * A partire dalla data di inizio contratto / inizio attività del cantiere e dalle frequenze
 * degli impianti configurati, proietta le scadenze future su un orizzonte di N mesi,
 * applicando la regola fondamentale di inclusione periodica:
 *   meseProgressivo % frequenza.inMesi() == 0
 */
object CalendarioScadenzeCalculator {

    private val NOMI_MESI = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )

    private val FORMATTER_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Effettua il parsing sicuro di una stringa data nei formati comuni ("dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd").
     */
    fun parseDataRiferimento(rawDate: String?, fallback: LocalDate = LocalDate.of(2026, 1, 1)): LocalDate {
        val cleaned = rawDate?.trim().orEmpty()
        if (cleaned.isEmpty()) return fallback

        val slashParts = cleaned.split("/", ".", "-")
        if (slashParts.size == 3) {
            val p0 = slashParts[0].toIntOrNull()
            val p1 = slashParts[1].toIntOrNull()
            val p2 = slashParts[2].toIntOrNull()
            if (p0 != null && p1 != null && p2 != null) {
                return try {
                    if (p0 > 1900) {
                        // Formato ISO: yyyy-MM-dd
                        LocalDate.of(p0, p1.coerceIn(1, 12), p2.coerceIn(1, 28))
                    } else {
                        // Formato IT: dd/MM/yyyy
                        val year = if (p2 < 100) 2000 + p2 else p2
                        LocalDate.of(year, p1.coerceIn(1, 12), p0.coerceIn(1, 28))
                    }
                } catch (_: Exception) {
                    fallback
                }
            }
        }
        return fallback
    }

    /**
     * Calcola le scadenze mensili raggruppate per un singolo cantiere su un orizzonte di [mesiOrizzonte] mesi.
     */
    fun calcolaScadenze(
        cantiere: Cantiere,
        impianti: List<Impianto>,
        mesiOrizzonte: Int = 12,
        fallbackDate: LocalDate = LocalDate.of(2026, 1, 1)
    ): List<MeseScadenzaGroup> {
        if (impianti.isEmpty() || mesiOrizzonte <= 0) return emptyList()

        val rawBaseDate = cantiere.dataInizioContratto?.takeIf { it.isNotBlank() }
            ?: cantiere.effectiveDataIntervento
        val baseDate = parseDataRiferimento(rawBaseDate, fallbackDate)

        val groups = mutableListOf<MeseScadenzaGroup>()

        for (meseOffset in 1..mesiOrizzonte) {
            val targetDate = baseDate.plusMonths(meseOffset.toLong())
            val scadenzeMese = mutableListOf<ScadenzaImpiantoItem>()

            for (impianto in impianti) {
                val frequenzeImpianto = CrossPlantAttivitaResolver.resolveFrequenze(impianto, impianti)
                if (frequenzeImpianto.isEmpty()) continue

                // Tutte le frequenze dell'impianto che cadono nel mese corrente (meseOffset % F.inMesi() == 0)
                val frequenzeInScadenza = frequenzeImpianto.filter { freq ->
                    val mesiFreq = freq.inMesi()
                    mesiFreq > 0 && meseOffset % mesiFreq == 0
                }.sortedBy { it.inMesi() }

                if (frequenzeInScadenza.isNotEmpty()) {
                    val freqMassima = frequenzeInScadenza.last()
                    val attivitaRisolte = CrossPlantAttivitaResolver.resolveAttivita(impianto, impianti, freqMassima)
                    val descrizione = when (impianto) {
                        is QuadroBT -> {
                            val siglaPart = impianto.sigla.trim()
                            val descPart = impianto.descrizioneQuadro.trim()
                            when {
                                descPart.isNotEmpty() && siglaPart.isNotEmpty() -> "$descPart ($siglaPart)"
                                siglaPart.isNotEmpty() -> "${impianto.nomeCompleto} ($siglaPart)"
                                else -> impianto.nomeCompleto
                            }
                        }
                        else -> impianto.nomeCompleto
                    }
                    val codDisplay = if (impianto is QuadroBT && impianto.sigla.isNotBlank()) {
                        "${impianto.codIntervento} - ${impianto.sigla}"
                    } else {
                        impianto.codIntervento
                    }

                    val attivitaFormattate = attivitaRisolte.mapIndexed { idx, att ->
                        val zonaLabel = att.zona?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
                        "${idx + 1}. ${att.tipoAttivita}$zonaLabel: ${att.descrizione} (${att.frequenza.label()})"
                    }

                    scadenzeMese.add(
                        ScadenzaImpiantoItem(
                            impiantoId = impianto.id,
                            codIntervento = codDisplay,
                            descrizioneImpianto = descrizione,
                            frequenzaMassima = freqMassima,
                            frequenzeIncluse = frequenzeInScadenza,
                            numeroAttivita = attivitaRisolte.size,
                            attivitaInScadenza = attivitaFormattate
                        )
                    )
                }
            }

            if (scadenzeMese.isNotEmpty()) {
                val meseNome = NOMI_MESI[(targetDate.monthValue - 1).coerceIn(0, 11)]
                val annoMese = "%04d-%02d".format(targetDate.year, targetDate.monthValue)
                groups.add(
                    MeseScadenzaGroup(
                        indiceMese = meseOffset,
                        annoMese = annoMese,
                        etichettaMese = "$meseNome ${targetDate.year}",
                        dataPrevista = targetDate.format(FORMATTER_IT),
                        scadenze = scadenzeMese
                    )
                )
            }
        }

        return groups
    }

    /**
     * Calcola il Calendario Globale Multi-Cantiere aggregando per mese (`annoMese`, es. `"2026-05"` / `"Maggio 2026"`)
     * tutti i cantieri che presentano impianti e attività in scadenza su un orizzonte di [mesiOrizzonte] mesi.
     */
    fun calcolaScadenzeGlobali(
        clienti: List<Cliente>,
        cantieri: List<Cantiere>,
        tuttiImpianti: List<Impianto>,
        mesiOrizzonte: Int = 12,
        fallbackDate: LocalDate = LocalDate.of(2026, 1, 1)
    ): List<MeseScadenzaGlobaleGroup> {
        if (cantieri.isEmpty() || mesiOrizzonte <= 0) return emptyList()

        val clientiById = clienti.associateBy { it.id }
        val impiantiByCantiere = tuttiImpianti
            .filter { !it.cantiereId.isNullOrBlank() }
            .groupBy { it.cantiereId!! }

        // Mappa ordinabile per annoMese ("2026-05") -> (etichettaMese, lista CantiereScadenzaInMese)
        val bucketPerMese = linkedMapOf<String, Pair<String, MutableList<CantiereScadenzaInMese>>>()

        for (cantiere in cantieri) {
            val impiantiCantiere = impiantiByCantiere[cantiere.id].orEmpty()
            if (impiantiCantiere.isEmpty()) continue

            val cliente = clientiById[cantiere.clienteId]
            val scadenzeCantiere = calcolaScadenze(
                cantiere = cantiere,
                impianti = impiantiCantiere,
                mesiOrizzonte = mesiOrizzonte,
                fallbackDate = fallbackDate
            )

            for (meseGroup in scadenzeCantiere) {
                val entry = bucketPerMese.getOrPut(meseGroup.annoMese) {
                    meseGroup.etichettaMese to mutableListOf()
                }
                entry.second.add(
                    CantiereScadenzaInMese(
                        cantiere = cantiere,
                        cliente = cliente,
                        dataPrevista = meseGroup.dataPrevista,
                        indiceMeseDalContratto = meseGroup.indiceMese,
                        scadenzeImpianti = meseGroup.scadenze
                    )
                )
            }
        }

        return bucketPerMese.entries
            .sortedBy { it.key }
            .map { (annoMese, pair) ->
                val (etichetta, cantieriList) = pair
                val sortedCantieri = cantieriList.sortedWith(
                    compareBy<CantiereScadenzaInMese> { it.cliente?.nome?.lowercase() ?: "" }
                        .thenBy { it.cantiere.nome.lowercase() }
                )
                MeseScadenzaGlobaleGroup(
                    annoMese = annoMese,
                    etichettaMese = etichetta,
                    cantieriInScadenza = sortedCantieri
                )
            }
    }
}
