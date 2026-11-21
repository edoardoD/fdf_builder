package manutenzioni.app.ui.components.spreadsheet

import androidx.compose.runtime.Composable

/**
 * Definizione dichiarativa di una colonna per [SpreadsheetCreationDialog].
 * Consente di costruire griglie tabellari stile foglio di calcolo per qualsiasi entità [Row]
 * senza duplicazione né hardcoding di layout.
 */
sealed interface SpreadsheetColumn<Row> {
    val header: String
    val weight: Float
    val isResizable: Boolean

    /**
     * Cella testuale editabile inline.
     */
    data class Text<Row>(
        override val header: String,
        override val weight: Float,
        val placeholder: String = "",
        val uppercase: Boolean = false,
        val getter: (Row) -> String,
        val setter: (Row, String) -> Row,
        val isError: (Row) -> Boolean = { false },
        override val isResizable: Boolean = true
    ) : SpreadsheetColumn<Row>

    /**
     * Cella numerica intera editabile inline con range di validazione (es. 1..150).
     */
    data class NumericInt<Row>(
        override val header: String,
        override val weight: Float,
        val range: IntRange,
        val placeholder: String = "",
        val getter: (Row) -> Int?,
        val setter: (Row, Int?) -> Row,
        val isError: (Row) -> Boolean = { false },
        override val isResizable: Boolean = true
    ) : SpreadsheetColumn<Row>

    /**
     * Cella con menu a tendina compatto inline.
     */
    data class Dropdown<Row, Option>(
        override val header: String,
        override val weight: Float,
        val options: List<Option>,
        val optionLabel: (Option) -> String,
        val getter: (Row) -> Option,
        val setter: (Row, Option) -> Row,
        override val isResizable: Boolean = true
    ) : SpreadsheetColumn<Row>

    /**
     * Cella personalizzata (es. selettore multi-componenti per quadri elettrici).
     */
    data class Custom<Row>(
        override val header: String,
        override val weight: Float,
        val content: @Composable (row: Row, onUpdate: (Row) -> Unit) -> Unit,
        override val isResizable: Boolean = true
    ) : SpreadsheetColumn<Row>
}
