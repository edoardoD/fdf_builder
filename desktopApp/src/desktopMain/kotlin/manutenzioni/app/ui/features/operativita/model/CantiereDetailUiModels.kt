package manutenzioni.app.ui.features.operativita.model

import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT

data class InterruttoreRowUiModel(
    val id: String,
    val quadroSigla: String,
    val siglaCircuito: String,
    val datiInterruttore: String,
    val taratura: String,
    val quantita: Int,
    val originalQuadro: QuadroBT,
    val originalInterruttore: InterruttoreBT
)

data class ElencoInterruttoriTabUiState(
    val quadriCount: Int,
    val totalInterruttoriCount: Int,
    val rows: List<InterruttoreRowUiModel>
)

data class QuadriBulkFrequenzeUiModel(
    val quadriAttivi: List<QuadroBT>,
    val frequenzeDisponibili: List<Periodo>
)

fun extractTaraturaFromInterruttore(interruttore: InterruttoreBT): String {
    val car = interruttore.caratteristicheTecniche
    if (car.isEmpty()) return ""

    val directKey = car["taratura"] ?: car["Idn"] ?: car["IΔn"] ?: car["taraturaDiff"] ?: car["sensibilita"]
    if (!directKey.isNullOrBlank()) return directKey

    val fuzzy = car.entries.firstOrNull { (k, v) ->
        (k.contains("taratura", ignoreCase = true) ||
         k.contains("diff", ignoreCase = true) ||
         k.contains("idn", ignoreCase = true)) && v.isNotBlank()
    }?.value
    if (!fuzzy.isNullOrBlank()) return fuzzy

    return car.values.filter { it.isNotBlank() }.joinToString(" - ")
}

fun buildElencoInterruttoriTabUiState(quadri: List<QuadroBT>): ElencoInterruttoriTabUiState {
    val quadriConInterruttori = quadri.filter { it.listaInterruttori.isNotEmpty() }
    val totRigheSrotolate = quadriConInterruttori.sumOf { q -> q.listaInterruttori.sumOf { it.quantita.coerceAtLeast(1) } }

    val rows = mutableListOf<InterruttoreRowUiModel>()
    quadriConInterruttori.forEach { quadro ->
        val siglaQuadro = if (quadro.sigla.isNotBlank()) quadro.sigla else quadro.codIntervento
        quadro.listaInterruttori.forEachIndexed { idx, interruttore ->
            val datiComp = buildString {
                if (!interruttore.produttore.isNullOrBlank()) append(interruttore.produttore).append(" ")
                if (!interruttore.codiceArticolo.isNullOrBlank()) append(interruttore.codiceArticolo).append(" — ")
                append(interruttore.nome)
            }
            val taratura = extractTaraturaFromInterruttore(interruttore).ifBlank { "—" }
            val siglaCircuito = interruttore.siglaCircuito?.takeIf { it.isNotBlank() } ?: "QF${idx + 1}"

            rows.add(
                InterruttoreRowUiModel(
                    id = "${quadro.id}_${interruttore.id}_$idx",
                    quadroSigla = siglaQuadro,
                    siglaCircuito = siglaCircuito,
                    datiInterruttore = datiComp,
                    taratura = taratura,
                    quantita = interruttore.quantita,
                    originalQuadro = quadro,
                    originalInterruttore = interruttore
                )
            )
        }
    }

    return ElencoInterruttoriTabUiState(
        quadriCount = quadri.size,
        totalInterruttoriCount = totRigheSrotolate,
        rows = rows
    )
}
