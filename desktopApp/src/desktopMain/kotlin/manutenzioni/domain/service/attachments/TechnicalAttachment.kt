package manutenzioni.domain.service.attachments

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo

/**
 * Allineamento del testo all'interno di una cella della tabella tecnica.
 */
enum class CellTextAlign {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Cella tipizzata all'interno di una riga della tabella tecnica.
 */
sealed interface TechnicalCell {

    data class Text(
        val text: String,
        val align: CellTextAlign = CellTextAlign.CENTER,
        val bold: Boolean = false,
        val fontSizePt: Double = 6.0
    ) : TechnicalCell

    data class Radio(
        val groupName: String,
        val optionValue: String,
        val label: String
    ) : TechnicalCell

    data class TextInput(
        val fieldName: String,
        val placeholder: String = "",
        val fontSizePt: Double = 7.5,
        val label: String,
        val align: CellTextAlign = CellTextAlign.LEFT
    ) : TechnicalCell

    object Empty : TechnicalCell
}

/**
 * Definizione di una sotto-colonna (es. opzioni di verifica "OK" o "NON OK").
 */
data class SubColumn(
    val title: String,
    val widthCm: Double
)

/**
 * Definizione di una colonna della tabella tecnica.
 * Può essere a livello singolo (con [widthCm]) o raggruppare più [subColumns].
 */
data class TableColumn(
    val title: String,
    val widthCm: Double = 0.0,
    val subColumns: List<SubColumn> = emptyList()
) {
    val totalWidthCm: Double
        get() = if (subColumns.isNotEmpty()) subColumns.sumOf { it.widthCm } else widthCm
}

/**
 * Riga di dati per la tabella tecnica.
 */
data class TechnicalRow(
    val heightCm: Double = 0.48,
    val cells: List<TechnicalCell>
)

/**
 * Modello dati dichiarativo per un intero foglio allegato tecnico.
 */
data class TechnicalSheetData(
    val codScheda: String,
    val oggetto: String,
    val titoloBanner: String,
    val sottotitoloBanner: String,
    val impiantoLabel: String,
    val sitoLabel: String? = null,
    val normative: String,
    val campoAcroFormPrefix: String, // es. "diff" o "emerg"
    val columns: List<TableColumn>,
    val rows: List<TechnicalRow>,
    val footerDocTitle: String
)

/**
 * Contratto per i provider di allegati tecnici (es. Quadri BT, Illuminazione Emergenza, ecc.).
 * Separa la logica dei componenti specifici dell'impianto dal layout e dal rendering PDF/HTML.
 */
interface TechnicalAttachmentProvider {
    /** Determina se questo provider gestisce l'impianto specificato */
    fun canHandle(impianto: Impianto): Boolean

    /** Genera la sintesi testuale dei componenti da iniettare nella premessa del Foglio 1 */
    fun buildPremessaSummary(impianto: Impianto): String?

    /** Genera i dati strutturati per il foglio allegato (Foglio 2+) */
    fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData?
}
