package manutenzioni.app.ui.components.spreadsheet

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import manutenzioni.app.ui.features.operativita.MassiveDispositivoAntincendioDialog
import manutenzioni.app.ui.features.operativita.MassiveQuadroCreationDialog
import manutenzioni.app.ui.theme.ManutenzioniTheme
import manutenzioni.domain.model.DispositivoRilevazione
import manutenzioni.domain.model.QuadroBT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpreadsheetAdapterTest {

    private data class SampleRow(
        val id: String,
        val code: String = "",
        val address: Int? = null,
        val note: String = ""
    )

    @Test
    fun cellValueSortsNumericallyWithNullsLast() {
        val v2 = SpreadsheetCellValue(text = "2", numericSortKey = 2)
        val v10 = SpreadsheetCellValue(text = "10", numericSortKey = 10)
        val vEmpty = SpreadsheetCellValue(text = "", numericSortKey = null)
        val vEmptyOther = SpreadsheetCellValue(text = "", numericSortKey = null)

        val sorted = listOf(vEmpty, v10, v2).sorted()
        assertEquals(listOf(v2, v10, vEmpty), sorted)
        assertEquals(0, vEmpty.compareTo(vEmptyOther))
    }

    @Test
    fun cellValueSortsTextCaseInsensitivelyAndIgnoresErrorOrZebraFlags() {
        val b = SpreadsheetCellValue(text = "beta", isError = true, isEvenRow = false)
        val aUpper = SpreadsheetCellValue(text = "ALFA", isError = false, isEvenRow = true)
        val aErr = SpreadsheetCellValue(text = "ALFA", isError = true, isEvenRow = false)

        assertTrue(aUpper < b)
        assertEquals(0, aUpper.compareTo(aErr))
        assertNotEquals(aUpper, aErr)
    }

    @Test
    fun parseNumericIntAcceptsEmptyAndInRangeAndRejectsInvalid() {
        val range = 1..150

        val empty = parseNumericIntCellInput("", range)
        assertTrue(empty.accepted)
        assertNull(empty.value)

        val minBound = parseNumericIntCellInput("1", range)
        assertTrue(minBound.accepted)
        assertEquals(1, minBound.value)

        val maxBound = parseNumericIntCellInput(" 150 ", range)
        assertTrue(maxBound.accepted)
        assertEquals(150, maxBound.value)

        val belowMin = parseNumericIntCellInput("0", range)
        assertFalse(belowMin.accepted)
        assertNull(belowMin.value)

        val aboveMax = parseNumericIntCellInput("151", range)
        assertFalse(aboveMax.accepted)
        assertNull(aboveMax.value)

        val nonNumeric = parseNumericIntCellInput("12a", range)
        assertFalse(nonNumeric.accepted)
        assertNull(nonNumeric.value)
    }

    @Test
    fun normalizeTextCellInputRespectsUppercaseFlag() {
        assertEquals("QG-01", normalizeTextCellInput("qg-01", uppercase = true))
        assertEquals("Nota libera", normalizeTextCellInput("Nota libera", uppercase = false))
    }

    @Test
    fun schemaKeyRemainsStableOnListRecreationAndChangesOnDynamicSwitch() {
        fun buildColumns(
            tipoHeader: String,
            resizable: Boolean = true,
            uppercaseNote: Boolean = false,
            addressRange: IntRange = 1..150
        ): List<SpreadsheetColumn<SampleRow>> = listOf(
            SpreadsheetColumn.NumericInt(
                header = "Ind. (1..150) *",
                weight = 0.2f,
                range = addressRange,
                getter = { it.address },
                setter = { r, v -> r.copy(address = v) },
                isResizable = resizable
            ),
            SpreadsheetColumn.Dropdown(
                header = tipoHeader,
                weight = 0.3f,
                options = listOf("A", "B"),
                optionLabel = { it },
                getter = { it.code.ifEmpty { "A" } },
                setter = { r, v -> r.copy(code = v) }
            ),
            SpreadsheetColumn.Text(
                header = "Note",
                weight = 0.5f,
                uppercase = uppercaseNote,
                getter = { it.note },
                setter = { r, v -> r.copy(note = v) }
            )
        )

        val loop1Key = computeColumnSchemaKey(buildColumns("Tipo Rivelatore"), hasDuplicateAction = true)
        val loop2SameFamilyKey = computeColumnSchemaKey(buildColumns("Tipo Rivelatore"), hasDuplicateAction = true)
        val moduloSwitchKey = computeColumnSchemaKey(buildColumns("Tipo Modulo"), hasDuplicateAction = true)
        val noDuplicateKey = computeColumnSchemaKey(buildColumns("Tipo Rivelatore"), hasDuplicateAction = false)
        val nonResizableKey = computeColumnSchemaKey(buildColumns("Tipo Rivelatore", resizable = false), hasDuplicateAction = true)
        val uppercaseChangedKey = computeColumnSchemaKey(buildColumns("Tipo Rivelatore", uppercaseNote = true), hasDuplicateAction = true)
        val rangeChangedKey = computeColumnSchemaKey(buildColumns("Tipo Rivelatore", addressRange = 1..200), hasDuplicateAction = true)

        assertEquals(loop1Key, loop2SameFamilyKey)
        assertNotEquals(loop1Key, moduloSwitchKey)
        assertNotEquals(loop1Key, noDuplicateKey)
        assertNotEquals(loop1Key, nonResizableKey)
        assertNotEquals(loop1Key, uppercaseChangedKey)
        assertNotEquals(loop1Key, rangeChangedKey)
    }

    @Test
    fun filterNumericIntInputAcceptsAsciiDigitsAndOptionalLeadingMinusOnly() {
        assertEquals("123", filterNumericIntInput("12a3b", allowNegative = false))
        assertEquals("", filterNumericIntInput("\u0662\u0663", allowNegative = false))
        assertEquals("42", filterNumericIntInput("-42", allowNegative = false))
        assertEquals("-42", filterNumericIntInput("-42", allowNegative = true))
        assertEquals("-42", filterNumericIntInput("-4-2", allowNegative = true))
        assertEquals("42", filterNumericIntInput("4-2", allowNegative = true))

        val unicodeDigit = parseNumericIntCellInput("\u0662", 1..150)
        assertFalse(unicodeDigit.accepted)
        assertNull(unicodeDigit.value)
    }

    @Test
    fun resolveLiveRowIndexFindsCorrectRowAfterTableSortingAndReturnsNullForDeletedKey() {
        val originalRows = listOf(
            SampleRow(id = "r1", code = "Q2", address = 20),
            SampleRow(id = "r2", code = "Q1", address = 5),
            SampleRow(id = "r3", code = "Q3", address = 10)
        )

        // Simula una vista ordinata dove "r2" appare in posizione 0, mentre nella lista live è all'indice 1
        val resolved = resolveLiveRowIndex(
            rows = originalRows,
            key = "r2",
            fallbackIndex = 0,
            rowKey = { _, row -> row.id }
        )
        assertEquals(1, resolved)

        // Chiave eliminata su lista non vuota: DEVE restituire null invece di mutare la riga adiacente traslata
        val deletedKey = resolveLiveRowIndex(
            rows = originalRows,
            key = "deleted-row-id",
            fallbackIndex = 0,
            rowKey = { _, row -> row.id }
        )
        assertNull(deletedKey)

        val outOfBounds = resolveLiveRowIndex(
            rows = emptyList<SampleRow>(),
            key = "missing",
            fallbackIndex = 0,
            rowKey = { _, row -> row.id }
        )
        assertNull(outOfBounds)
    }

    @Test
    fun buildTableRowWrappersTracksCrossFieldAndCrossRowErrors() {
        val rows = listOf(
            SampleRow(id = "a", code = "", note = "Locale Caldaia", address = 10),
            SampleRow(id = "b", code = "Q1", note = "Uffici", address = 10)
        )
        val columns = listOf<SpreadsheetColumn<SampleRow>>(
            SpreadsheetColumn.Text(
                header = "Sigla *",
                weight = 0.4f,
                getter = { it.code },
                setter = { r, v -> r.copy(code = v) },
                isError = { it.code.isBlank() && it.note.isNotBlank() }
            ),
            SpreadsheetColumn.NumericInt(
                header = "Indirizzo",
                weight = 0.3f,
                range = 1..150,
                getter = { it.address },
                setter = { r, v -> r.copy(address = v) },
                isError = { row -> rows.count { it.address == row.address } > 1 }
            ),
            SpreadsheetColumn.Text(
                header = "Note",
                weight = 0.3f,
                getter = { it.note },
                setter = { r, v -> r.copy(note = v) }
            )
        )

        val wrappers = buildTableRowWrappers(rows, columns) { _, r -> r.id }
        assertEquals(2, wrappers.size)
        assertEquals(listOf(true, true, false), wrappers[0].cellErrors)
        assertEquals(listOf(false, true, false), wrappers[1].cellErrors)
        assertEquals(0, wrappers[0].originalIndex)
        assertEquals("b", wrappers[1].key)
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun confirmButtonCommitsValidInFlightInitialRowEditWithoutPressingEnter() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            val saveBefore = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Quadri") } == true
            }
            assertNotNull(saveBefore)
            assertTrue(
                saveBefore.config.contains(SemanticsProperties.Disabled),
                "Untouched initial dialog must keep Salva disabled before table focus"
            )

            // Single-click anywhere inside the "Sigla *" cell in row 0 immediately focuses inline TextField
            val siglaCellCenter = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCellCenter, "Could not locate Sigla cell bounds")
            click(scene, siglaCellCenter, timeMs = 100L)
            scene.render(200_000_000L)

            val editable = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable, "Single-clicking Sigla cell should focus inline TextField")

            // Type "qg" via SetText without pressing Enter or blurring focus
            editable.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("qg"))
            scene.render(300_000_000L)
            scene.render(320_000_000L)

            val saveDuringEdit = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Quadri") } == true
            }
            assertNotNull(saveDuringEdit)
            assertFalse(
                saveDuringEdit.config.contains(SemanticsProperties.Disabled),
                "Salva must become enabled while table cell edit is in flight on non-empty rows"
            )

            // Click Salva directly without Enter or prior blur
            val saveBounds = saveDuringEdit.boundsInRoot
            click(scene, Offset(saveBounds.center.x, saveBounds.center.y), timeMs = 400L)
            scene.render(500_000_000L)
            scene.render(520_000_000L)

            val result = savedQuadri
            assertNotNull(result, "Clicking Salva during valid in-flight initial-row edit must commit and invoke onSave")
            assertEquals(1, result.size)
            assertEquals("QG", result[0].sigla)
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun confirmButtonBlocksSubmissionWhenInFlightInitialRowEditIsBlank() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            val siglaCellCenter = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCellCenter)
            click(scene, siglaCellCenter, timeMs = 100L)
            scene.render(200_000_000L)

            val editable = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable)

            editable.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("   "))
            scene.render(300_000_000L)

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Quadri") } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 400L)
            scene.render(500_000_000L)

            assertNull(savedQuadri, "Blank in-flight edit must still NOT submit onSave")
            val saveAfter = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Quadri") } == true
            }
            assertNotNull(saveAfter)
            assertTrue(
                saveAfter.config.contains(SemanticsProperties.Disabled),
                "Salva must return to disabled after committing an invalid/blank edit"
            )
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun confirmButtonStaysDisabledWhenRowsAreEmptyEvenIfDialogAreaFocused() {
        var confirmed = false
        val rows = mutableStateListOf<SampleRow>()
        val cols = listOf(
            SpreadsheetColumn.Text<SampleRow>(
                header = "Codice",
                weight = 1f,
                getter = { it.code },
                setter = { r, v -> r.copy(code = v) }
            )
        )
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                SpreadsheetCreationDialog(
                    title = "Test Empty",
                    rows = rows,
                    rowKey = { _, r -> r.id },
                    columns = cols,
                    onUpdateRow = { _, _ -> },
                    onAddRow = {},
                    onRemoveRow = {},
                    confirmLabel = "Salva Test",
                    isConfirmEnabled = false,
                    onDismiss = {},
                    onConfirm = { confirmed = true }
                )
            }
        }
        try {
            scene.render(0L)
            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Test" } == true
            }
            assertNotNull(saveBtn)
            assertTrue(saveBtn.config.contains(SemanticsProperties.Disabled))
            click(scene, saveBtn.boundsInRoot.center, timeMs = 100L)
            scene.render(200_000_000L)
            assertFalse(confirmed)
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun confirmButtonCommitsValidInFlightDispositivoEditWithoutPressingEnter() {
        var savedBatch: List<DispositivoRilevazione>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveDispositivoAntincendioDialog(
                    dispositiviEsistenti = emptyList(),
                    cantiereNome = "Cantiere Test",
                    onDismiss = {},
                    onConfirmBatch = { savedBatch = it }
                )
            }
        }
        try {
            scene.render(0L)
            val saveBefore = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Componenti") } == true
            }
            assertNotNull(saveBefore)
            assertTrue(saveBefore.config.contains(SemanticsProperties.Disabled))

            val descHeader = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Descrizione / Ubicazione *") } == true
            }
            assertNotNull(descHeader, "Could not locate Descrizione / Ubicazione * header")
            val descCellCenter = Offset(descHeader.boundsInRoot.center.x, descHeader.boundsInRoot.bottom + 26f)
            click(scene, descCellCenter, timeMs = 100L)
            scene.render(200_000_000L)

            val editable = findFocusedEditableNode(scene)
            assertNotNull(editable, "Single-clicking Descrizione cell should focus inline TextField")

            editable.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("sensore hall"))
            scene.render(300_000_000L)
            scene.render(320_000_000L)

            val saveDuringEdit = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Componenti") } == true
            }
            assertNotNull(saveDuringEdit)
            assertFalse(saveDuringEdit.config.contains(SemanticsProperties.Disabled))

            click(scene, saveDuringEdit.boundsInRoot.center, timeMs = 400L)
            scene.render(500_000_000L)
            scene.render(520_000_000L)

            val result = savedBatch
            assertNotNull(result, "Clicking Salva Componenti during valid in-flight edit must commit and invoke onConfirmBatch")
            assertEquals(1, result.size)
            assertEquals("SENSORE HALL", result[0].descrizione)
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun confirmButtonBlocksSubmissionWhenValidRowIsClearedInFlight() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            val siglaCellCenter = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCellCenter)
            click(scene, siglaCellCenter, timeMs = 100L)
            scene.render(200_000_000L)

            val editable1 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable1)
            editable1.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("QG"))
            scene.render(300_000_000L)

            // Commit "QG" by clicking the adjacent Descrizione cell (triggers LEAVE_FOCUS) so isConfirmEnabled becomes true
            val descCellCenter = findDataCellCenter(scene, rowIndex = 0, colIndex = 2)
            assertNotNull(descCellCenter)
            click(scene, descCellCenter, timeMs = 350L)
            scene.render(450_000_000L)
            scene.render(470_000_000L)

            val saveBeforeClear = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (1)" } == true
            }
            assertNotNull(saveBeforeClear, "Salva Quadri (1) should be shown after committing QG")

            // Re-enter Sigla cell with single click and clear it to empty without pressing Enter, then click Salva
            click(scene, siglaCellCenter, timeMs = 550L)
            scene.render(650_000_000L)
            val editable2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable2)
            editable2.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(""))
            scene.render(700_000_000L)

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Quadri") } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 800L)
            scene.render(900_000_000L)
            scene.render(920_000_000L)

            assertNull(savedQuadri, "Clearing the only valid row in-flight must NOT submit stale true isConfirmEnabled")
            val saveAfter = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Salva Quadri") } == true
            }
            assertNotNull(saveAfter)
            assertTrue(saveAfter.config.contains(SemanticsProperties.Disabled))
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun rapidDoubleClickRemoveOnTwoRowDialogPreservesFirstRow() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            val siglaCell0 = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCell0)
            click(scene, siglaCell0, timeMs = 100L)
            scene.render(200_000_000L)

            val editable0 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable0)
            editable0.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("QG"))
            scene.render(250_000_000L)

            // Aggiungi seconda riga ("Q1") -> ["QG", "Q1"]
            val addBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Aggiungi Riga" } == true
            }
            assertNotNull(addBtn)
            click(scene, addBtn.boundsInRoot.center, timeMs = 300L)
            scene.render(400_000_000L)
            scene.render(420_000_000L)

            val deleteButtons = findSemanticsNodes(scene) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Rimuovi riga") == true
            }.sortedBy { it.boundsInRoot.top }
            assertEquals(2, deleteButtons.size, "Expected 2 Rimuovi riga buttons for [QG, Q1]")

            // Doppio click rapido su "Rimuovi riga" della riga 1 (Q1) prima della ricomposizione
            val row1DeleteCenter = deleteButtons[1].boundsInRoot.center
            click(scene, row1DeleteCenter, timeMs = 500L)
            click(scene, row1DeleteCenter, timeMs = 525L)

            repeat(4) { i ->
                scene.render(600_000_000L + (i * 20_000_000L))
            }

            val badge1 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "1 riga" } == true
            }
            assertNotNull(badge1, "Deleting row 1 twice rapidly must leave 1 riga, preserving row 0")

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (1)" } == true
            }
            assertNotNull(saveBtn, "Row 0 'QG' must remain intact and keep Salva Quadri (1) enabled")
            click(scene, saveBtn.boundsInRoot.center, timeMs = 800L)
            scene.render(900_000_000L)
            scene.render(920_000_000L)

            val result = savedQuadri
            assertNotNull(result)
            assertEquals(listOf("QG"), result.map { it.sigla })
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun rapidDoubleClickRemoveOnThreeRowDialogDoesNotThrowAndLeavesTwoRows() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            val siglaCell0 = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCell0)
            click(scene, siglaCell0, timeMs = 100L)
            scene.render(200_000_000L)

            val editable0 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable0)
            editable0.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("Q1"))
            scene.render(250_000_000L)

            val addBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Aggiungi Riga" } == true
            }
            assertNotNull(addBtn)
            click(scene, addBtn.boundsInRoot.center, timeMs = 300L)
            scene.render(400_000_000L)
            click(scene, addBtn.boundsInRoot.center, timeMs = 450L)
            scene.render(550_000_000L)
            scene.render(570_000_000L)

            val deleteButtons = findSemanticsNodes(scene) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Rimuovi riga") == true
            }.sortedBy { it.boundsInRoot.top }
            assertEquals(3, deleteButtons.size, "Expected 3 Rimuovi riga buttons for [Q1, Q2, Q3]")

            // Doppio click rapido su "Rimuovi riga" della riga 2 (Q3) prima della ricomposizione
            val row2DeleteCenter = deleteButtons[2].boundsInRoot.center
            click(scene, row2DeleteCenter, timeMs = 650L)
            click(scene, row2DeleteCenter, timeMs = 675L)

            repeat(4) { i ->
                scene.render(750_000_000L + (i * 20_000_000L))
            }

            val badge2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "2 righe" } == true
            }
            assertNotNull(badge2, "Deleting row 2 twice rapidly must leave 2 righe without IndexOutOfBoundsException")

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (2)" } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 900L)
            scene.render(1_000_000_000L)
            scene.render(1_020_000_000L)

            val result = savedQuadri
            assertNotNull(result)
            assertEquals(listOf("Q1", "Q2"), result.map { it.sigla })
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun rapidDoubleClickDuplicateRowDuplicatesOnlyOnceAndAllowsSubsequentClicks() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            val siglaCell0 = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCell0)
            click(scene, siglaCell0, timeMs = 100L)
            scene.render(200_000_000L)

            val editable0 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable0)
            editable0.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("Q1"))
            scene.render(250_000_000L)

            val descCell0 = findDataCellCenter(scene, rowIndex = 0, colIndex = 2)
            assertNotNull(descCell0)
            click(scene, descCell0, timeMs = 300L)
            scene.render(400_000_000L)
            scene.render(420_000_000L)

            val dupButtonsInitial = findSemanticsNodes(scene) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Duplica riga") == true
            }.sortedBy { it.boundsInRoot.top }
            assertEquals(1, dupButtonsInitial.size)

            // Doppio click rapido su "Duplica riga" prima della ricomposizione
            val row0DupCenter = dupButtonsInitial[0].boundsInRoot.center
            click(scene, row0DupCenter, timeMs = 500L)
            click(scene, row0DupCenter, timeMs = 525L)

            repeat(4) { i ->
                scene.render(600_000_000L + (i * 20_000_000L))
            }

            val badge2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "2 righe" } == true
            }
            assertNotNull(badge2, "Rapid double-click on Duplica riga must duplicate only once")

            // Un click singolo successivo dopo la ricomposizione deve funzionare regolarmente
            val dupButtonsAfter = findSemanticsNodes(scene) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Duplica riga") == true
            }.sortedBy { it.boundsInRoot.top }
            assertEquals(2, dupButtonsAfter.size)
            click(scene, dupButtonsAfter[1].boundsInRoot.center, timeMs = 750L)

            repeat(4) { i ->
                scene.render(850_000_000L + (i * 20_000_000L))
            }

            val badge3 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "3 righe" } == true
            }
            assertNotNull(badge3, "Subsequent single click on Duplica riga after recomposition must work")

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (3)" } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 1000L)
            scene.render(1_100_000_000L)
            scene.render(1_120_000_000L)

            val result = savedQuadri
            assertNotNull(result)
            assertEquals(listOf("Q1", "Q2", "Q3"), result.map { it.sigla })
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun crossFrameDoubleClickOnConfirmSubmitsOnceAndReEnablesOnValidationErrorOrRowEdit() {
        val rows = mutableStateListOf(SampleRow(id = "r1", code = "QG", note = "Iniziale"))
        val validationError = mutableStateOf<String?>(null)
        var confirmCalls = 0
        val cols = listOf(
            SpreadsheetColumn.Text<SampleRow>(
                header = "Sigla *",
                weight = 0.5f,
                uppercase = true,
                getter = { it.code },
                setter = { r, v -> r.copy(code = v) }
            ),
            SpreadsheetColumn.Text<SampleRow>(
                header = "Note",
                weight = 0.5f,
                getter = { it.note },
                setter = { r, v -> r.copy(note = v) }
            )
        )
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                SpreadsheetCreationDialog(
                    title = "Test Async Confirm",
                    rows = rows,
                    rowKey = { _, r -> r.id },
                    columns = cols,
                    onUpdateRow = { idx, updated ->
                        rows[idx] = updated
                        validationError.value = null
                    },
                    onAddRow = {},
                    onRemoveRow = {},
                    validationError = validationError.value,
                    confirmLabel = "Salva Test",
                    isConfirmEnabled = rows.any { it.code.isNotBlank() },
                    onDismiss = {},
                    onConfirm = { confirmCalls += 1 }
                )
            }
        }
        try {
            scene.render(0L)
            val saveInitial = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Test" } == true
            }
            assertNotNull(saveInitial)
            assertFalse(saveInitial.config.contains(SemanticsProperties.Disabled))

            // Doppio click cross-frame a 20ms di distanza con 1 render frame in mezzo e dialog ancora aperta
            val saveCenter = saveInitial.boundsInRoot.center
            click(scene, saveCenter, timeMs = 100L)
            scene.render(120_000_000L)
            click(scene, saveCenter, timeMs = 140L)
            repeat(4) { i ->
                scene.render(220_000_000L + (i * 20_000_000L))
            }

            assertEquals(1, confirmCalls, "Cross-frame rapid double-click on Salva must invoke onConfirm only once")
            val saveLocked = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Test" } == true
            }
            assertNotNull(saveLocked)
            assertTrue(saveLocked.config.contains(SemanticsProperties.Disabled))

            // Comparsa validationError -> Salva deve riabilitarsi
            validationError.value = "Errore di validazione simulato"
            repeat(3) { i ->
                scene.render(340_000_000L + (i * 20_000_000L))
            }
            val saveAfterError = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Test" } == true
            }
            assertNotNull(saveAfterError)
            assertFalse(
                saveAfterError.config.contains(SemanticsProperties.Disabled),
                "Salva must re-enable when validationError appears"
            )

            // Rimuovo validationError e verifico riabilitazione anche modificando una riga dopo Salva
            validationError.value = null
            scene.render(420_000_000L)
            click(scene, saveAfterError.boundsInRoot.center, timeMs = 450L)
            repeat(3) { i ->
                scene.render(520_000_000L + (i * 20_000_000L))
            }
            assertEquals(2, confirmCalls)

            val saveLockedAgain = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Test" } == true
            }
            assertNotNull(saveLockedAgain)
            assertTrue(saveLockedAgain.config.contains(SemanticsProperties.Disabled))

            // Modifica contenuto riga e commit su LEAVE_FOCUS -> Salva deve riabilitarsi
            val siglaCell = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCell)
            click(scene, siglaCell, timeMs = 600L)
            scene.render(700_000_000L)

            val editable = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable)
            editable.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("QG-2"))
            scene.render(750_000_000L)

            val noteCell = findDataCellCenter(scene, rowIndex = 0, colIndex = 2)
            assertNotNull(noteCell)
            click(scene, noteCell, timeMs = 800L)
            repeat(3) { i ->
                scene.render(880_000_000L + (i * 20_000_000L))
            }

            val saveAfterEdit = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Test" } == true
            }
            assertNotNull(saveAfterEdit)
            assertFalse(
                saveAfterEdit.config.contains(SemanticsProperties.Disabled),
                "Salva must re-enable after editing row content"
            )

            click(scene, saveAfterEdit.boundsInRoot.center, timeMs = 1000L)
            repeat(3) { i ->
                scene.render(1_100_000_000L + (i * 20_000_000L))
            }
            assertEquals(3, confirmCalls)
            assertEquals("QG-2", rows[0].code)
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun singleRowRemovalResetsRowAndClearsActionLockForSubsequentClicks() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            val siglaCell0 = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCell0)
            click(scene, siglaCell0, timeMs = 100L)
            scene.render(200_000_000L)

            val editable0 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable0)
            editable0.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("QG"))
            scene.render(250_000_000L)

            val descCell0 = findDataCellCenter(scene, rowIndex = 0, colIndex = 2)
            assertNotNull(descCell0)
            click(scene, descCell0, timeMs = 300L)
            scene.render(400_000_000L)

            // Elimina la riga 0 (unica riga): deve azzerarsi e rilasciare il lock per usi successivi
            val deleteButtons1 = findSemanticsNodes(scene) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Rimuovi riga") == true
            }
            assertEquals(1, deleteButtons1.size)
            click(scene, deleteButtons1[0].boundsInRoot.center, timeMs = 450L)
            repeat(4) { i ->
                scene.render(550_000_000L + (i * 20_000_000L))
            }

            val saveReset = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (0)" } == true
            }
            assertNotNull(saveReset)
            assertTrue(saveReset.config.contains(SemanticsProperties.Disabled))

            // Compila di nuovo riga 0 e verifica che "Rimuovi riga" e "Duplica riga" funzionino ancora
            click(scene, siglaCell0, timeMs = 700L)
            scene.render(800_000_000L)
            val editable1 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editable1)
            editable1.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("Q-NEW"))
            scene.render(850_000_000L)
            click(scene, descCell0, timeMs = 900L)
            scene.render(1_000_000_000L)

            val dupButtons = findSemanticsNodes(scene) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Duplica riga") == true
            }
            assertEquals(1, dupButtons.size)
            click(scene, dupButtons[0].boundsInRoot.center, timeMs = 1050L)
            repeat(4) { i ->
                scene.render(1_150_000_000L + (i * 20_000_000L))
            }

            val badge2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "2 righe" } == true
            }
            assertNotNull(badge2, "Action lock must clear after 1-row reset so subsequent row actions work")

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (2)" } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 1300L)
            scene.render(1_400_000_000L)
            scene.render(1_420_000_000L)

            val result = savedQuadri
            assertNotNull(result)
            assertEquals(listOf("Q-NEW", "Q-NEW-01"), result.map { it.sigla })
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun loopModeEditThenDuplicateWithoutBlurPreservesEditedRow() {
        var savedBatch: List<DispositivoRilevazione>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveDispositivoAntincendioDialog(
                    dispositiviEsistenti = emptyList(),
                    cantiereNome = "Cantiere Test",
                    onDismiss = {},
                    onConfirmBatch = { savedBatch = it }
                )
            }
        }
        try {
            scene.render(0L)

            // Popola prima Loop 2 così Loop 1 utilizza un sottoinsieme filtrato (activeRows != loopDrafts)
            val loop2Chip = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Loop 2" } == true
            }
            assertNotNull(loop2Chip, "Loop 2 chip must be present")
            click(scene, loop2Chip.boundsInRoot.center, timeMs = 50L)
            repeat(3) { i -> scene.render(100_000_000L + i * 20_000_000L) }

            val descHeaderLoop2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Descrizione / Ubicazione *") } == true
            }
            assertNotNull(descHeaderLoop2)
            val loop2DescCenter = Offset(descHeaderLoop2.boundsInRoot.center.x, descHeaderLoop2.boundsInRoot.bottom + 26f)
            click(scene, loop2DescCenter, timeMs = 200L)
            scene.render(300_000_000L)
            val editableLoop2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editableLoop2)
            editableLoop2.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("RIV-L2"))
            scene.render(350_000_000L)

            // Torna a Loop 1 (il blur su click di Loop 1 committa RIV-L2)
            val loop1Chip = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Loop 1" } == true
            }
            assertNotNull(loop1Chip)
            click(scene, loop1Chip.boundsInRoot.center, timeMs = 400L)
            repeat(3) { i -> scene.render(450_000_000L + i * 20_000_000L) }

            // Modifica riga 0 di Loop 1 ("Descrizione / Ubicazione *") a "RIV-INFLIGHT" SENZA fare blur
            val descHeader = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Descrizione / Ubicazione *") } == true
            }
            assertNotNull(descHeader)
            val descCellCenter = Offset(descHeader.boundsInRoot.center.x, descHeader.boundsInRoot.bottom + 26f)
            click(scene, descCellCenter, timeMs = 550L)
            scene.render(650_000_000L)

            val editableLoop1 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(editableLoop1, "Single-clicking Descrizione cell in Loop 1 must focus TextField")
            editableLoop1.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("RIV-INFLIGHT"))
            scene.render(700_000_000L)

            // Clicca direttamente "Duplica riga" sulla riga 0 mentre la cella è ancora focused
            val dupButtons = findSemanticsNodes(scene) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Duplica riga") == true
            }.sortedBy { it.boundsInRoot.top }
            assertEquals(1, dupButtons.size)
            click(scene, dupButtons[0].boundsInRoot.center, timeMs = 750L)

            repeat(5) { i -> scene.render(820_000_000L + i * 20_000_000L) }

            val badge2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "2 righe" } == true
            }
            assertNotNull(badge2, "Duplicating row 0 in Loop 1 must produce 2 righe")

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Componenti (3)" } == true
            }
            assertNotNull(saveBtn, "Expected both Loop 1 duplicates and Loop 2 row ready to save (3)")
            click(scene, saveBtn.boundsInRoot.center, timeMs = 950L)
            repeat(4) { i -> scene.render(1_020_000_000L + i * 20_000_000L) }

            val result = savedBatch
            assertNotNull(result, "onConfirmBatch must be invoked")
            val loop1Saved = result.filterIsInstance<DispositivoRilevazione.RivelatoreLoop>()
                .filter { it.loop == 1 }
            assertEquals(2, loop1Saved.size, "Both duplicated Loop 1 rows must be saved without blank descrizione loss")
            assertTrue(
                loop1Saved.all { it.descrizione == "RIV-INFLIGHT" },
                "Both Loop 1 rows must retain 'RIV-INFLIGHT', got: ${loop1Saved.map { it.descrizione }}"
            )
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun doubleClickHeaderSortWhileCellFocusedCommitsEditWithoutCrashAndSingleClickIsNoOp() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)

            // Crea 3 righe: QC, QA, QB
            suspendEditSiglaRow(scene, rowIndex = 0, text = "QC", startTimeMs = 100L)

            val addBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Aggiungi Riga" } == true
            }
            assertNotNull(addBtn)
            click(scene, addBtn.boundsInRoot.center, timeMs = 300L)
            repeat(3) { i -> scene.render(350_000_000L + i * 20_000_000L) }
            suspendEditSiglaRow(scene, rowIndex = 1, text = "QA", startTimeMs = 450L)

            click(scene, addBtn.boundsInRoot.center, timeMs = 650L)
            repeat(3) { i -> scene.render(700_000_000L + i * 20_000_000L) }
            suspendEditSiglaRow(scene, rowIndex = 2, text = "QB", startTimeMs = 800L)

            // Verifica che il click singolo sull'header NON ordini la tabella
            val siglaHeader = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sigla *") } == true
            }
            assertNotNull(siglaHeader)
            click(scene, siglaHeader.boundsInRoot.center, timeMs = 1000L)
            repeat(3) { i -> scene.render(1_050_000_000L + i * 20_000_000L) }
            assertEquals(listOf("QC", "QA", "QB"), readVisibleSiglaRows(scene))

            // Metti a fuoco riga 1 ("QA") con un click e inserisci "QA-LIVE" SENZA fare blur
            val row1SiglaCenter = findDataCellCenter(scene, rowIndex = 1, colIndex = 1)
            assertNotNull(row1SiglaCenter)
            click(scene, row1SiglaCenter, timeMs = 1200L)
            scene.render(1_300_000_000L)
            val focusedEditable = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true &&
                    node.config.contains(SemanticsActions.SetText)
            }
            assertNotNull(focusedEditable, "Row 1 Sigla cell must be focused")
            focusedEditable.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("QA-LIVE"))
            scene.render(1_350_000_000L)

            // Doppio click sull'header "Sigla *" mentre la cella è focused: NON deve crashare,
            // deve committare "QA-LIVE" e ordinare in ASC [QA-LIVE, QB, QC]
            val headerCenter = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sigla *") } == true
            }?.boundsInRoot?.center
            assertNotNull(headerCenter)
            doubleClick(scene, headerCenter, startTimeMs = 1450L)
            repeat(6) { i -> scene.render(1_560_000_000L + i * 20_000_000L) }

            assertEquals(listOf("QA-LIVE", "QB", "QC"), readVisibleSiglaRows(scene))

            // Secondo doppio click sull'header -> DESC [QC, QB, QA-LIVE]
            val headerCenter2 = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sigla *") } == true
            }?.boundsInRoot?.center
            assertNotNull(headerCenter2)
            doubleClick(scene, headerCenter2, startTimeMs = 1800L)
            repeat(6) { i -> scene.render(1_910_000_000L + i * 20_000_000L) }

            assertEquals(listOf("QC", "QB", "QA-LIVE"), readVisibleSiglaRows(scene))

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (3)" } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 2100L)
            repeat(4) { i -> scene.render(2_200_000_000L + i * 20_000_000L) }

            val result = savedQuadri
            assertNotNull(result)
            assertTrue(result.any { it.sigla == "QA-LIVE" }, "In-flight edit QA-LIVE must be committed and saved")
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun singleClickEmptyAreaFocusesCellAndTogglesPlaceholder() {
        val scene = ImageComposeScene(
            width = 1280,
            height = 720,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = {}
                )
            }
        }
        try {
            scene.render(0L)

            val placeholderBeforeFocus = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "es. QG, Q1" } == true
            }
            assertNotNull(placeholderBeforeFocus, "Placeholder must be visible when empty and unfocused")

            val headerNode = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sigla *") } == true
            }
            assertNotNull(headerNode)
            // Click su area vuota vicino al bordo inferiore destro della prima cella dati (fuori da BasicTextField, lontano dal resize handle)
            val emptyEdgeTarget = Offset(
                x = headerNode.boundsInRoot.right - 20f,
                y = headerNode.boundsInRoot.bottom + 36f
            )
            click(scene, emptyEdgeTarget, timeMs = 100L)
            scene.render(200_000_000L)

            val focused = findFocusedEditableNode(scene)
            assertNotNull(focused, "Single-clicking near cell edge must focus the cell TextField")

            val placeholderWhileFocused = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "es. QG, Q1" } == true
            }
            assertNull(placeholderWhileFocused, "Placeholder must disappear while the cell is focused")

            focused.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("Q-EDGE"))
            scene.render(260_000_000L)

            val descCell = findDataCellCenter(scene, rowIndex = 0, colIndex = 2)
            assertNotNull(descCell)
            click(scene, descCell, timeMs = 300L)
            scene.render(400_000_000L)

            val placeholderWithValue = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "es. QG, Q1" } == true
            }
            assertNull(placeholderWithValue, "Placeholder must stay hidden after committing non-empty text")
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun escapeKeyRevertsInFlightEditAndPreservesCommittedRow() {
        var savedQuadri: List<QuadroBT>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 720,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveQuadroCreationDialog(
                    cantiereId = "c-1",
                    templateQuadro = null,
                    onDismiss = {},
                    onSave = { savedQuadri = it }
                )
            }
        }
        try {
            scene.render(0L)
            suspendEditSiglaRow(scene, rowIndex = 0, text = "Q1", startTimeMs = 100L)
            assertEquals(listOf("Q1"), readVisibleSiglaRows(scene))

            val siglaCell = findDataCellCenter(scene, rowIndex = 0, colIndex = 1)
            assertNotNull(siglaCell)
            click(scene, siglaCell, timeMs = 400L)
            scene.render(500_000_000L)

            val focused = findFocusedEditableNode(scene)
            assertNotNull(focused)
            focused.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("Q-ABORT"))
            scene.render(550_000_000L)

            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
            repeat(4) { i -> scene.render(620_000_000L + i * 20_000_000L) }

            assertNull(findFocusedEditableNode(scene), "Escape must clear focus from active cell")
            assertEquals(listOf("Q1"), readVisibleSiglaRows(scene))

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Quadri (1)" } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 750L)
            repeat(3) { i -> scene.render(820_000_000L + i * 20_000_000L) }

            val result = savedQuadri
            assertNotNull(result)
            assertEquals(listOf("Q1"), result.map { it.sigla })
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun numericIntOutOfRangeRevertsToLastValidValueOnCommit() {
        var savedBatch: List<DispositivoRilevazione>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveDispositivoAntincendioDialog(
                    dispositiviEsistenti = emptyList(),
                    cantiereNome = "Cantiere Test",
                    onDismiss = {},
                    onConfirmBatch = { savedBatch = it }
                )
            }
        }
        try {
            scene.render(0L)

            val indHeader = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Ind. (1..150)") } == true
            }
            assertNotNull(indHeader)
            val indCell0 = Offset(indHeader.boundsInRoot.center.x, indHeader.boundsInRoot.bottom + 26f)
            click(scene, indCell0, timeMs = 100L)
            scene.render(200_000_000L)

            val indFocused = findFocusedEditableNode(scene)
            assertNotNull(indFocused, "Indirizzo cell must focus on single click")
            indFocused.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("999"))
            scene.render(250_000_000L)

            // Spostamento alla cella Descrizione -> commit con out-of-range revert al precedente valore valido (1)
            val descHeader = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Descrizione / Ubicazione *") } == true
            }
            assertNotNull(descHeader)
            val descCell0 = Offset(descHeader.boundsInRoot.center.x, descHeader.boundsInRoot.bottom + 26f)
            click(scene, descCell0, timeMs = 300L)
            scene.render(400_000_000L)

            val descFocused = findFocusedEditableNode(scene)
            assertNotNull(descFocused)
            descFocused.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("SENSORE TEST"))
            scene.render(450_000_000L)

            // Blur tornando su Indirizzo così Descrizione committa e abilita "Salva Componenti (1)"
            click(scene, indCell0, timeMs = 480L)
            repeat(3) { i -> scene.render(540_000_000L + i * 20_000_000L) }

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Componenti (1)" } == true
            }
            assertNotNull(saveBtn)
            click(scene, saveBtn.boundsInRoot.center, timeMs = 650L)
            repeat(4) { i -> scene.render(720_000_000L + i * 20_000_000L) }

            val result = savedBatch
            assertNotNull(result)
            val rivelatore = result.filterIsInstance<DispositivoRilevazione.RivelatoreLoop>().single()
            assertEquals(1, rivelatore.numeroIndirizzo, "Out-of-range 999 must revert to last valid value 1")
            assertEquals("SENSORE TEST", rivelatore.descrizione)
        } finally {
            scene.close()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun switchingLoopSectionAutoCommitsInFlightEdit() {
        var savedBatch: List<DispositivoRilevazione>? = null
        val scene = ImageComposeScene(
            width = 1280,
            height = 800,
            density = Density(1f)
        ) {
            ManutenzioniTheme {
                MassiveDispositivoAntincendioDialog(
                    dispositiviEsistenti = emptyList(),
                    cantiereNome = "Cantiere Test",
                    onDismiss = {},
                    onConfirmBatch = { savedBatch = it }
                )
            }
        }
        try {
            scene.render(0L)

            val loop2Chip = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Loop 2" } == true
            }
            assertNotNull(loop2Chip)
            click(scene, loop2Chip.boundsInRoot.center, timeMs = 50L)
            repeat(3) { i -> scene.render(100_000_000L + i * 20_000_000L) }

            val descHeader = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Descrizione / Ubicazione *") } == true
            }
            assertNotNull(descHeader)
            click(scene, Offset(descHeader.boundsInRoot.center.x, descHeader.boundsInRoot.bottom + 26f), timeMs = 200L)
            scene.render(300_000_000L)

            val focused = findFocusedEditableNode(scene)
            assertNotNull(focused)
            focused.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("RIV-L2-AUTO"))
            scene.render(340_000_000L)

            // Passa subito a Loop 1 senza blur esplicito: headerControls deve committare RIV-L2-AUTO prima dello switch
            val loop1Chip = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Loop 1" } == true
            }
            assertNotNull(loop1Chip)
            click(scene, loop1Chip.boundsInRoot.center, timeMs = 400L)
            repeat(4) { i -> scene.render(480_000_000L + i * 20_000_000L) }

            val saveBtn = findSemanticsNode(scene) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Salva Componenti (1)" } == true
            }
            assertNotNull(saveBtn, "Loop 2 edit must be auto-committed on section switch so Salva Componenti (1) is enabled")
            click(scene, saveBtn.boundsInRoot.center, timeMs = 600L)
            repeat(4) { i -> scene.render(680_000_000L + i * 20_000_000L) }

            val result = savedBatch
            assertNotNull(result)
            val savedL2 = result.filterIsInstance<DispositivoRilevazione.RivelatoreLoop>().single()
            assertEquals(2, savedL2.loop)
            assertEquals("RIV-L2-AUTO", savedL2.descrizione)
        } finally {
            scene.close()
        }
    }

    private fun suspendEditSiglaRow(
        scene: ImageComposeScene,
        rowIndex: Int,
        text: String,
        startTimeMs: Long
    ) {
        val cell = findDataCellCenter(scene, rowIndex = rowIndex, colIndex = 1)
        assertNotNull(cell)
        click(scene, cell, timeMs = startTimeMs)
        scene.render((startTimeMs + 100L) * 1_000_000L)
        val editable = findFocusedEditableNode(scene)
        assertNotNull(editable)
        editable.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(text))
        scene.render((startTimeMs + 120L) * 1_000_000L)
        val descCell = findDataCellCenter(scene, rowIndex = rowIndex, colIndex = 2)
        assertNotNull(descCell)
        click(scene, descCell, timeMs = startTimeMs + 140L)
        scene.render((startTimeMs + 180L) * 1_000_000L)
    }

    private fun readVisibleSiglaRows(scene: ImageComposeScene): List<String> {
        val headerNode = findSemanticsNode(scene) { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sigla *") } == true
        } ?: return emptyList()
        val headerBounds = headerNode.boundsInRoot
        val footerTop = findSemanticsNode(scene) { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text.contains("quadri validi") || it.text.contains("quadro valido")
            } == true
        }?.boundsInRoot?.top ?: Float.MAX_VALUE
        return findSemanticsNodes(scene) { node ->
            val bounds = node.boundsInRoot
            val text = node.config.getOrNull(SemanticsProperties.EditableText)?.text
                ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            !text.isNullOrEmpty() &&
                bounds.top >= headerBounds.bottom - 1f &&
                bounds.bottom <= footerTop - 1f &&
                bounds.left >= headerBounds.left - 12f &&
                bounds.left <= headerBounds.right + 12f
        }
            .sortedBy { it.boundsInRoot.top }
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.EditableText)?.text
                    ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            }
    }

    private fun collectSemanticsNodes(scene: ImageComposeScene): List<SemanticsNode> {
        val result = mutableListOf<SemanticsNode>()
        fun visit(node: SemanticsNode) {
            result.add(node)
            node.children.forEach(::visit)
        }
        val composeScene = ImageComposeScene::class.java.getDeclaredField("scene").apply {
            isAccessible = true
        }.get(scene)
        val owners = mutableListOf<Any>()
        composeScene.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }.get(composeScene)?.let {
            owners.add(it)
        }
        val layers = composeScene.javaClass.getDeclaredField("layers").apply { isAccessible = true }.get(composeScene) as? List<*>
        layers?.forEach { layer ->
            if (layer != null) {
                layer.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(layer)?.let {
                    owners.add(it)
                }
            }
        }
        owners.forEach { rootOwner ->
            val semOwner = rootOwner.javaClass.getMethod("getSemanticsOwner").invoke(rootOwner) as? SemanticsOwner
            semOwner?.rootSemanticsNode?.let(::visit)
        }
        return result
    }

    private fun findSemanticsNodes(
        scene: ImageComposeScene,
        predicate: (SemanticsNode) -> Boolean
    ): List<SemanticsNode> = collectSemanticsNodes(scene).filter(predicate)

    private fun findSemanticsNode(
        scene: ImageComposeScene,
        predicate: (SemanticsNode) -> Boolean
    ): SemanticsNode? = collectSemanticsNodes(scene).firstOrNull(predicate)

    private fun findFocusedEditableNode(scene: ImageComposeScene): SemanticsNode? =
        findSemanticsNode(scene) { node ->
            node.config.getOrNull(SemanticsProperties.Focused) == true &&
                node.config.contains(SemanticsActions.SetText)
        }

    private fun findDataCellCenter(
        scene: ImageComposeScene,
        rowIndex: Int,
        colIndex: Int
    ): Offset? {
        // Locate header cell "Sigla *" (with native sort indicator) or adjacent column header
        val siglaHeader = findSemanticsNode(scene) { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sigla *") } == true
        } ?: return null
        val targetHeader = if (colIndex <= 1) {
            siglaHeader
        } else {
            findSemanticsNode(scene) { node ->
                node.boundsInRoot.left > siglaHeader.boundsInRoot.right &&
                    kotlin.math.abs(node.boundsInRoot.top - siglaHeader.boundsInRoot.top) < 12f &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any {
                        it.text.startsWith("Descrizione") || it.text == "Note"
                    } == true
            } ?: siglaHeader
        }
        val headerBounds = targetHeader.boundsInRoot
        val x = if (targetHeader == siglaHeader && colIndex > 1) headerBounds.center.x + 320f else headerBounds.center.x
        val y = siglaHeader.boundsInRoot.bottom + 26f + (rowIndex * 40f)
        return Offset(x, y)
    }

    private fun click(scene: ImageComposeScene, position: Offset, timeMs: Long) {
        scene.sendPointerEvent(PointerEventType.Move, position = position, timeMillis = timeMs)
        scene.sendPointerEvent(PointerEventType.Press, position = position, timeMillis = timeMs + 10L)
        scene.sendPointerEvent(PointerEventType.Release, position = position, timeMillis = timeMs + 20L)
    }

    private fun doubleClick(scene: ImageComposeScene, position: Offset, startTimeMs: Long) {
        click(scene, position, startTimeMs)
        click(scene, position, startTimeMs + 60L)
    }
}
