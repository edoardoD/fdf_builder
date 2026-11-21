package manutenzioni.app.ui.components.spreadsheet

import kotlin.reflect.KClass

/**
 * Wrapper non-nullable per soddisfare il bound `DATA : Any` di `DataTable<DATA>`
 * preservando al contempo la firma generica non vincolata `SpreadsheetCreationDialog<Row>`.
 * Contiene inoltre [originalIndex], [key] e gli stati di errore precalcolati [cellErrors]
 * per invalidare le cache di `DataTableColumn` su errori cross-campo/cross-riga e zebra striping.
 */
internal data class TableRowWrapper<Row>(
    val originalIndex: Int,
    val key: Any,
    val row: Row,
    val cellErrors: List<Boolean>
)

internal data class ColumnSchemaKey(
    val header: String,
    val columnKind: KClass<out SpreadsheetColumn<*>>,
    val weight: Float,
    val isResizable: Boolean,
    val uppercase: Boolean? = null,
    val range: IntRange? = null
)

internal data class TableSchemaKey(
    val columns: List<ColumnSchemaKey>,
    val hasDuplicateAction: Boolean
)

internal fun <Row> computeColumnSchemaKey(
    columns: List<SpreadsheetColumn<Row>>,
    hasDuplicateAction: Boolean
): TableSchemaKey = TableSchemaKey(
    columns = columns.map { col ->
        ColumnSchemaKey(
            header = col.header,
            columnKind = col::class,
            weight = col.weight,
            isResizable = col.isResizable,
            uppercase = (col as? SpreadsheetColumn.Text<*>)?.uppercase,
            range = (col as? SpreadsheetColumn.NumericInt<*>)?.range
        )
    },
    hasDuplicateAction = hasDuplicateAction
)

/**
 * Valore `Comparable` per celle `table.comparable` di `compose-data-table`.
 * Include [isError], [isEvenRow] e [rowKey] in `equals`/`hashCode` affinché `DataTableColumn`
 * invalidi `remember(index, cell.state.value)` quando cambiano errori cross-campo/cross-riga,
 * striping zebra o identità della riga, mantenendo l'ordinamento basato esclusivamente su
 * [numericSortKey] o [text].
 */
internal data class SpreadsheetCellValue(
    val text: String,
    val numericSortKey: Int? = null,
    val isError: Boolean = false,
    val isEvenRow: Boolean = true,
    val rowKey: Any? = null
) : Comparable<SpreadsheetCellValue> {

    override fun compareTo(other: SpreadsheetCellValue): Int {
        if (numericSortKey != null || other.numericSortKey != null) {
            return when {
                numericSortKey == null -> 1
                other.numericSortKey == null -> -1
                else -> numericSortKey.compareTo(other.numericSortKey)
            }
        }
        val ignoreCaseCmp = text.compareTo(other.text, ignoreCase = true)
        return if (ignoreCaseCmp != 0) ignoreCaseCmp else text.compareTo(other.text)
    }
}

/**
 * Coordina la cella attualmente focalizzata in [SpreadsheetCreationDialog] affinché
 * qualsiasi azione esterna (Salva, Duplica, Rimuovi, Aggiungi, ordinamento header, cambio sezione)
 * possa committare in modo sincrono l'eventuale bozza in corso prima di leggere o mutare `rows`.
 */
internal class ActiveCellEditController {
    private var activeCellId: Any? = null
    private var activeCommitter: (() -> Unit)? = null

    fun registerFocused(cellId: Any, onCommit: () -> Unit) {
        activeCellId = cellId
        activeCommitter = onCommit
    }

    fun unregisterIfActive(cellId: Any) {
        if (activeCellId == cellId) {
            activeCellId = null
            activeCommitter = null
        }
    }

    fun commitActiveEdit() {
        activeCommitter?.invoke()
    }

    fun hasActiveEdit(): Boolean = activeCommitter != null
}

internal fun normalizeTextCellInput(raw: String, uppercase: Boolean): String =
    if (uppercase) raw.uppercase() else raw

internal fun filterNumericIntInput(raw: String, allowNegative: Boolean): String = buildString(raw.length) {
    raw.forEachIndexed { index, ch ->
        when {
            ch in '0'..'9' -> append(ch)
            allowNegative && ch == '-' && index == 0 && isEmpty() -> append(ch)
        }
    }
}

internal data class NumericParseResult(
    val accepted: Boolean,
    val value: Int?
)

internal fun parseNumericIntCellInput(raw: String, range: IntRange): NumericParseResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return NumericParseResult(accepted = true, value = null)
    }
    val digitsPortion = if (trimmed.startsWith('-') || trimmed.startsWith('+')) {
        trimmed.substring(1)
    } else {
        trimmed
    }
    if (digitsPortion.isEmpty() || digitsPortion.any { it !in '0'..'9' }) {
        return NumericParseResult(accepted = false, value = null)
    }
    val parsed = trimmed.toIntOrNull()
        ?: return NumericParseResult(accepted = false, value = null)
    return if (parsed in range) {
        NumericParseResult(accepted = true, value = parsed)
    } else {
        NumericParseResult(accepted = false, value = null)
    }
}

/**
 * Risolve l'indice corrente nella lista sorgente [rows] tramite [key] (stabile anche quando
 * l'utente ordina la `DataTable` tramite doppio click sull'header).
 * Restituisce `null` se la chiave non è presente in [rows] (evitando che callback asincroni
 * obsoleti su una riga eliminata mutino una riga adiacente traslata).
 */
@Suppress("UNUSED_PARAMETER")
internal fun <Row> resolveLiveRowIndex(
    rows: List<Row>,
    key: Any,
    fallbackIndex: Int = -1,
    rowKey: (Int, Row) -> Any
): Int? = rows.indices.firstOrNull { idx -> rowKey(idx, rows[idx]) == key }

internal fun <Row> buildTableRowWrappers(
    rows: List<Row>,
    columns: List<SpreadsheetColumn<Row>>,
    rowKey: (Int, Row) -> Any
): List<TableRowWrapper<Row>> = rows.mapIndexed { idx, row ->
    TableRowWrapper(
        originalIndex = idx,
        key = rowKey(idx, row),
        row = row,
        cellErrors = columns.map { col ->
            when (col) {
                is SpreadsheetColumn.Text -> col.isError(row)
                is SpreadsheetColumn.NumericInt -> col.isError(row)
                is SpreadsheetColumn.Dropdown<Row, *>, is SpreadsheetColumn.Custom -> false
            }
        }
    )
}
