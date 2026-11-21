package manutenzioni.app.ui.components.spreadsheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ryinex.kotlin.datatable.data.DataTable
import com.ryinex.kotlin.datatable.data.DataTableCellConfig
import com.ryinex.kotlin.datatable.data.DataTableCellLocation
import com.ryinex.kotlin.datatable.data.DataTableColumnLayout
import com.ryinex.kotlin.datatable.data.DataTableConfig
import com.ryinex.kotlin.datatable.data.comparable
import com.ryinex.kotlin.datatable.data.composable
import com.ryinex.kotlin.datatable.data.setList
import com.ryinex.kotlin.datatable.views.DataTableView
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import manutenzioni.app.ui.theme.ErrorRed
import manutenzioni.app.ui.theme.GridBorderColor
import manutenzioni.app.ui.theme.GridErrorBg
import manutenzioni.app.ui.theme.GridErrorBorder
import manutenzioni.app.ui.theme.GridHeaderBg
import manutenzioni.app.ui.theme.LightSurface
import manutenzioni.app.ui.theme.PrimaryBlue
import manutenzioni.app.ui.theme.SlateBackground
import manutenzioni.app.ui.theme.SlateBorder
import manutenzioni.app.ui.theme.TextPrimary
import manutenzioni.app.ui.theme.TextSecondary
import manutenzioni.app.ui.theme.TextTertiary

private enum class PendingRowActionType {
    DUPLICATE,
    REMOVE
}

private data class PendingRowAction(
    val nonce: Int,
    val type: PendingRowActionType,
    val rowKey: Any,
    val fallbackIndex: Int
)

private class FocusManagerRef(var value: FocusManager)

private object DataTableFocusWorkaround {
    private val rowsField = runCatching {
        DataTable::class.java.getDeclaredField("rows").apply { isAccessible = true }
    }.getOrNull()

    private val currentFocusedCellField = runCatching {
        Class.forName("com.ryinex.kotlin.datatable.data.DataTableRows")
            .getDeclaredField("currentFocusedCell")
            .apply { isAccessible = true }
    }.getOrNull()

    fun clearFocusedCell(table: DataTable<*>) {
        runCatching {
            val rowsObj = rowsField?.get(table) ?: return
            currentFocusedCellField?.set(rowsObj, null)
        }
    }
}

/**
 * Dialog tabellare generico in stile foglio di calcolo per l'inserimento rapido multi-riga.
 * Basato su `com.ryinex.kotlin:compose-data-table` (`DataTable` + `DataTableView`) con
 * navigazione da tastiera (Tab / Shift+Tab / Frecce / Invio), ridimensionamento colonne,
 * header sticky, edit inline e colonna azioni.
 */
@Composable
fun <Row> SpreadsheetCreationDialog(
    title: String,
    subtitle: String? = null,
    rows: List<Row>,
    rowKey: (Int, Row) -> Any = { idx, _ -> idx },
    columns: List<SpreadsheetColumn<Row>>,
    onUpdateRow: (index: Int, Row) -> Unit,
    onAddRow: () -> Unit,
    onAddMultipleRows: ((count: Int) -> Unit)? = null,
    onDuplicateRow: ((index: Int, Row) -> Unit)? = null,
    onRemoveRow: (index: Int) -> Unit,
    headerControls: @Composable ColumnScope.() -> Unit = {},
    footerSummary: String? = null,
    validationError: String? = null,
    confirmLabel: String = "Salva",
    isConfirmEnabled: Boolean = true,
    emptyMessage: String = "Nessuna riga inserita. Clicca '+ Aggiungi Riga' per iniziare.",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val outerFocusManager = LocalFocusManager.current
    val focusManagerRef = remember { FocusManagerRef(outerFocusManager) }
    val activeEditController = remember { ActiveCellEditController() }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Snapshot strutturale per catturare mutazioni su SnapshotStateList (add/remove/set)
    val rowsSnapshot = rows.toList()
    val hasDuplicateAction = onDuplicateRow != null
    val schemaKey = computeColumnSchemaKey(columns, hasDuplicateAction)

    val latestRows = rememberUpdatedState(rowsSnapshot)
    val latestLiveRows = rememberUpdatedState(rows)
    val latestColumns = rememberUpdatedState(columns)
    val latestRowKey = rememberUpdatedState(rowKey)
    val latestOnUpdateRow = rememberUpdatedState(onUpdateRow)
    val latestOnDuplicateRow = rememberUpdatedState(onDuplicateRow)
    val latestOnRemoveRow = rememberUpdatedState(onRemoveRow)
    val latestOnAddRow = rememberUpdatedState(onAddRow)
    val latestOnAddMultipleRows = rememberUpdatedState(onAddMultipleRows)
    val latestOnConfirm = rememberUpdatedState(onConfirm)
    val latestIsConfirmEnabled = rememberUpdatedState(isConfirmEnabled)
    val latestValidationError = rememberUpdatedState(validationError)
    var isTableFocused by remember { mutableStateOf(false) }
    var pendingConfirmNonce by remember { mutableStateOf(0) }
    var isConfirmInFlight by remember { mutableStateOf(false) }
    var confirmAttemptRowsSnapshot by remember { mutableStateOf<List<Row>?>(null) }
    var isRowMutationInFlight by remember { mutableStateOf(false) }
    var rowMutationNonce by remember { mutableStateOf(0) }
    var pendingClearMutationNonce by remember { mutableStateOf<Int?>(null) }
    var rowActionCounter by remember { mutableStateOf(0) }
    var pendingRowAction by remember { mutableStateOf<PendingRowAction?>(null) }
    var pendingRemovedKeys by remember { mutableStateOf<Set<Any>>(emptySet()) }
    var pendingDuplicatedKeys by remember { mutableStateOf<Set<Any>>(emptySet()) }

    LaunchedEffect(schemaKey) {
        isTableFocused = false
    }

    LaunchedEffect(pendingRowAction) {
        val action = pendingRowAction ?: return@LaunchedEffect
        try {
            val clickRows = latestLiveRows.value.toList()
            val clickIdx = resolveLiveRowIndex(
                rows = clickRows,
                key = action.rowKey,
                fallbackIndex = action.fallbackIndex,
                rowKey = latestRowKey.value
            )
            if (clickIdx != null) {
                when (action.type) {
                    PendingRowActionType.DUPLICATE ->
                        latestOnDuplicateRow.value?.invoke(clickIdx, clickRows[clickIdx])
                    PendingRowActionType.REMOVE ->
                        latestOnRemoveRow.value(clickIdx)
                }
            }
        } finally {
            isRowMutationInFlight = false
            if (pendingRowAction?.nonce == action.nonce) {
                pendingRowAction = null
            }
            val nextNonce = rowMutationNonce + 1
            rowMutationNonce = nextNonce
            pendingClearMutationNonce = nextNonce
        }
    }

    LaunchedEffect(pendingConfirmNonce) {
        if (pendingConfirmNonce > 0) {
            if (!latestIsConfirmEnabled.value) {
                isConfirmInFlight = false
                confirmAttemptRowsSnapshot = null
            } else {
                try {
                    confirmAttemptRowsSnapshot = latestRows.value
                    latestOnConfirm.value()
                    yield()
                    Snapshot.sendApplyNotifications()
                    if (latestValidationError.value != null) {
                        isConfirmInFlight = false
                        confirmAttemptRowsSnapshot = null
                    }
                } catch (t: Throwable) {
                    isConfirmInFlight = false
                    confirmAttemptRowsSnapshot = null
                    throw t
                }
            }
        }
    }

    LaunchedEffect(validationError, rowsSnapshot) {
        if (isConfirmInFlight &&
            (validationError != null ||
                (confirmAttemptRowsSnapshot != null && rowsSnapshot != confirmAttemptRowsSnapshot))
        ) {
            isConfirmInFlight = false
            confirmAttemptRowsSnapshot = null
        }
    }

    val lazyState = remember(schemaKey) { LazyListState() }
    val table = remember(schemaKey) {
        val tableConfig = DataTableConfig.default(
            color = TextPrimary,
            backgroundColor = LightSurface,
            verticalSpacing = 0,
            horizontalSpacing = 0,
            isScrollable = true,
            isIndexed = true,
            isHeadered = true,
            isHeaderSticky = true,
            column = { colCfg ->
                colCfg.copy(
                    layout = DataTableColumnLayout.FixedWighted,
                    isResizable = true,
                    resizeHandleColor = SlateBorder,
                    cell = colCfg.cell.copy(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .border(0.5.dp, GridBorderColor),
                        color = TextTertiary,
                        backgroundColor = GridHeaderBg,
                        padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        textStyle = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextTertiary
                        ),
                        alignment = Alignment.CenterStart,
                        textAlign = TextAlign.Start
                    )
                )
            }
        )

        DataTable<TableRowWrapper<Row>>(
            config = tableConfig,
            scope = coroutineScope,
            lazyState = lazyState
        ).apply {
            columns.forEachIndexed { colIndex, templateCol ->
                when (templateCol) {
                    is SpreadsheetColumn.Text -> {
                        val uppercaseTransform = if (templateCol.uppercase) {
                            InputTransformation {
                                val current = asCharSequence().toString()
                                val upper = current.uppercase()
                                if (current != upper) {
                                    replace(0, length, upper)
                                }
                            }
                        } else {
                            null
                        }

                        comparable<SpreadsheetCellValue, TableRowWrapper<Row>>(
                            name = templateCol.header,
                            config = { colCfg ->
                                colCfg.copy(
                                    weight = templateCol.weight,
                                    isResizable = templateCol.isResizable
                                )
                            },
                            presentation = { location, cellValue, cellCfg ->
                                editableCellPresentation(location, cellValue, cellCfg)
                            },
                            value = { _, wrapper ->
                                val currentCol = latestColumns.value.getOrNull(colIndex)
                                    as? SpreadsheetColumn.Text<Row>
                                val textVal = currentCol?.getter?.invoke(wrapper.row).orEmpty()
                                val hasError = wrapper.cellErrors.getOrElse(colIndex) { false }
                                SpreadsheetCellValue(
                                    text = textVal,
                                    numericSortKey = null,
                                    isError = hasError,
                                    isEvenRow = wrapper.originalIndex % 2 == 0,
                                    rowKey = wrapper.key
                                )
                            }
                        ) { _, cellValue ->
                            val currentCol = latestColumns.value.getOrNull(colIndex)
                                as? SpreadsheetColumn.Text<Row>
                                ?: return@comparable
                            val cellTextStyle = TextStyle(
                                fontSize = 12.sp,
                                color = if (cellValue.isError) ErrorRed else TextPrimary
                            )
                            OneClickSpreadsheetCell(
                                rowKey = cellValue.rowKey ?: return@comparable,
                                colIndex = colIndex,
                                committedText = cellValue.text,
                                placeholder = currentCol.placeholder,
                                textStyle = cellTextStyle,
                                inputTransformation = uppercaseTransform,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                activeEditController = activeEditController,
                                focusManagerProvider = { focusManagerRef.value },
                                onCommitText = { rawText ->
                                    val liveCol = latestColumns.value.getOrNull(colIndex)
                                        as? SpreadsheetColumn.Text<Row>
                                        ?: return@OneClickSpreadsheetCell cellValue.text
                                    val normalized = normalizeTextCellInput(rawText, liveCol.uppercase)
                                    val rowsNow = latestLiveRows.value.toList()
                                    val liveIdx = resolveLiveRowIndex(
                                        rows = rowsNow,
                                        key = cellValue.rowKey,
                                        rowKey = latestRowKey.value
                                    ) ?: return@OneClickSpreadsheetCell cellValue.text
                                    val existingVal = liveCol.getter(rowsNow[liveIdx])
                                    if (normalized != existingVal) {
                                        latestOnUpdateRow.value(liveIdx, liveCol.setter(rowsNow[liveIdx], normalized))
                                    }
                                    normalized
                                }
                            )
                        }
                    }

                    is SpreadsheetColumn.NumericInt -> {
                        val allowNegative = templateCol.range.first < 0
                        val digitsOnlyTransform = InputTransformation {
                            val current = asCharSequence().toString()
                            val filtered = filterNumericIntInput(current, allowNegative = allowNegative)
                            if (current != filtered) {
                                replace(0, length, filtered)
                            }
                        }

                        comparable<SpreadsheetCellValue, TableRowWrapper<Row>>(
                            name = templateCol.header,
                            config = { colCfg ->
                                colCfg.copy(
                                    weight = templateCol.weight,
                                    isResizable = templateCol.isResizable
                                )
                            },
                            presentation = { location, cellValue, cellCfg ->
                                editableCellPresentation(
                                    location = location,
                                    cellValue = cellValue,
                                    cellCfg = cellCfg,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            value = { _, wrapper ->
                                val currentCol = latestColumns.value.getOrNull(colIndex)
                                    as? SpreadsheetColumn.NumericInt<Row>
                                val intVal = currentCol?.getter?.invoke(wrapper.row)
                                val hasError = wrapper.cellErrors.getOrElse(colIndex) { false }
                                SpreadsheetCellValue(
                                    text = intVal?.toString().orEmpty(),
                                    numericSortKey = intVal,
                                    isError = hasError,
                                    isEvenRow = wrapper.originalIndex % 2 == 0,
                                    rowKey = wrapper.key
                                )
                            }
                        ) { _, cellValue ->
                            val currentCol = latestColumns.value.getOrNull(colIndex)
                                as? SpreadsheetColumn.NumericInt<Row>
                                ?: return@comparable
                            val cellTextStyle = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cellValue.isError) ErrorRed else TextPrimary
                            )
                            OneClickSpreadsheetCell(
                                rowKey = cellValue.rowKey ?: return@comparable,
                                colIndex = colIndex,
                                committedText = cellValue.text,
                                placeholder = currentCol.placeholder,
                                textStyle = cellTextStyle,
                                inputTransformation = digitsOnlyTransform,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                activeEditController = activeEditController,
                                focusManagerProvider = { focusManagerRef.value },
                                onCommitText = { rawText ->
                                    val liveCol = latestColumns.value.getOrNull(colIndex)
                                        as? SpreadsheetColumn.NumericInt<Row>
                                        ?: return@OneClickSpreadsheetCell cellValue.text
                                    val rowsNow = latestLiveRows.value.toList()
                                    val liveIdx = resolveLiveRowIndex(
                                        rows = rowsNow,
                                        key = cellValue.rowKey,
                                        rowKey = latestRowKey.value
                                    ) ?: return@OneClickSpreadsheetCell cellValue.text
                                    val fallbackText = liveCol.getter(rowsNow[liveIdx])?.toString().orEmpty()
                                    val parsed = parseNumericIntCellInput(rawText, liveCol.range)
                                    if (!parsed.accepted) {
                                        return@OneClickSpreadsheetCell fallbackText
                                    }
                                    val existingVal = liveCol.getter(rowsNow[liveIdx])
                                    if (parsed.value != existingVal) {
                                        latestOnUpdateRow.value(liveIdx, liveCol.setter(rowsNow[liveIdx], parsed.value))
                                    }
                                    parsed.value?.toString().orEmpty()
                                }
                            )
                        }
                    }

                    is SpreadsheetColumn.Dropdown<Row, *> -> {
                        composable(
                            name = templateCol.header,
                            config = { colCfg ->
                                colCfg.copy(
                                    weight = templateCol.weight,
                                    isResizable = templateCol.isResizable
                                )
                            },
                            presentation = { location, cellCfg ->
                                val isEvenVisualRow = location.dataRelativeRowIndex % 2 == 0
                                cellCfg.copy(
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .border(0.5.dp, GridBorderColor),
                                    color = TextPrimary,
                                    backgroundColor = if (isEvenVisualRow) LightSurface else SlateBackground,
                                    padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    textStyle = TextStyle(fontSize = 11.sp, color = TextPrimary),
                                    alignment = Alignment.CenterStart,
                                    textAlign = TextAlign.Start
                                )
                            }
                        ) { _, wrapper ->
                            @Suppress("UNCHECKED_CAST")
                            val currentCol = latestColumns.value.getOrNull(colIndex)
                                as? SpreadsheetColumn.Dropdown<Row, Any?>
                                ?: return@composable
                            val rowsNow = latestRows.value
                            val liveIdx = resolveLiveRowIndex(
                                rows = rowsNow,
                                key = wrapper.key,
                                fallbackIndex = wrapper.originalIndex,
                                rowKey = latestRowKey.value
                            )
                            val liveRow = if (liveIdx != null) rowsNow[liveIdx] else wrapper.row
                            val currentOption = currentCol.getter(liveRow)
                            var expanded by remember(wrapper.key, colIndex) { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        focusManagerRef.value.clearFocus(force = true)
                                        expanded = true
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentCol.optionLabel(currentOption),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    currentCol.options.forEach { option ->
                                        DropdownMenuItem(
                                            onClick = {
                                                val clickRows = latestLiveRows.value.toList()
                                                val clickIdx = resolveLiveRowIndex(
                                                    rows = clickRows,
                                                    key = wrapper.key,
                                                    fallbackIndex = wrapper.originalIndex,
                                                    rowKey = latestRowKey.value
                                                )
                                                if (clickIdx != null) {
                                                    latestOnUpdateRow.value(
                                                        clickIdx,
                                                        currentCol.setter(clickRows[clickIdx], option)
                                                    )
                                                }
                                                expanded = false
                                            }
                                        ) {
                                            Text(
                                                text = currentCol.optionLabel(option),
                                                fontSize = 12.sp,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is SpreadsheetColumn.Custom -> {
                        composable(
                            name = templateCol.header,
                            config = { colCfg ->
                                colCfg.copy(
                                    weight = templateCol.weight,
                                    isResizable = templateCol.isResizable
                                )
                            },
                            presentation = { location, cellCfg ->
                                val isEvenVisualRow = location.dataRelativeRowIndex % 2 == 0
                                cellCfg.copy(
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .border(0.5.dp, GridBorderColor),
                                    color = TextPrimary,
                                    backgroundColor = if (isEvenVisualRow) LightSurface else SlateBackground,
                                    padding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    alignment = Alignment.CenterStart,
                                    textAlign = TextAlign.Start
                                )
                            }
                        ) { _, wrapper ->
                            @Suppress("UNCHECKED_CAST")
                            val currentCol = latestColumns.value.getOrNull(colIndex)
                                as? SpreadsheetColumn.Custom<Row>
                                ?: return@composable
                            val rowsNow = latestRows.value
                            val liveIdx = resolveLiveRowIndex(
                                rows = rowsNow,
                                key = wrapper.key,
                                fallbackIndex = wrapper.originalIndex,
                                rowKey = latestRowKey.value
                            )
                            val liveRow = if (liveIdx != null) rowsNow[liveIdx] else wrapper.row

                            currentCol.content(liveRow) { updated ->
                                val updateRows = latestLiveRows.value.toList()
                                val updateIdx = resolveLiveRowIndex(
                                    rows = updateRows,
                                    key = wrapper.key,
                                    fallbackIndex = wrapper.originalIndex,
                                    rowKey = latestRowKey.value
                                )
                                if (updateIdx != null) {
                                    latestOnUpdateRow.value(updateIdx, updated)
                                }
                            }
                        }
                    }
                }
            }

            composable(
                name = "Azioni",
                config = { colCfg ->
                    colCfg.copy(
                        layout = DataTableColumnLayout.ScrollableKeepInitial,
                        weight = 0f,
                        isResizable = false,
                        cell = colCfg.cell.copy(
                            modifier = Modifier
                                .width(if (hasDuplicateAction) 72.dp else 44.dp)
                                .heightIn(min = 36.dp)
                                .border(0.5.dp, GridBorderColor),
                            alignment = Alignment.Center,
                            textAlign = TextAlign.Center
                        )
                    )
                },
                presentation = { location, cellCfg ->
                    val isEvenVisualRow = location.dataRelativeRowIndex % 2 == 0
                    cellCfg.copy(
                        modifier = Modifier
                            .width(if (hasDuplicateAction) 72.dp else 44.dp)
                            .heightIn(min = 40.dp)
                            .border(0.5.dp, GridBorderColor),
                        backgroundColor = if (isEvenVisualRow) LightSurface else SlateBackground,
                        padding = PaddingValues(horizontal = 2.dp),
                        alignment = Alignment.Center,
                        textAlign = TextAlign.Center
                    )
                }
            ) { _, wrapper ->
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dupHandler = latestOnDuplicateRow.value
                    if (dupHandler != null) {
                        IconButton(
                            onClick = {
                                val rowKeySnapshot = wrapper.key
                                if (isRowMutationInFlight ||
                                    pendingRowAction != null ||
                                    rowKeySnapshot in pendingRemovedKeys ||
                                    rowKeySnapshot in pendingDuplicatedKeys
                                ) {
                                    return@IconButton
                                }
                                isRowMutationInFlight = true
                                pendingDuplicatedKeys = pendingDuplicatedKeys + rowKeySnapshot
                                val actionNonce = rowActionCounter + 1
                                rowActionCounter = actionNonce
                                coroutineScope.launch {
                                    var handedOffToEffect = false
                                    try {
                                        activeEditController.commitActiveEdit()
                                        focusManagerRef.value.clearFocus(force = true)
                                        yield()
                                        Snapshot.sendApplyNotifications()
                                        pendingRowAction = PendingRowAction(
                                            nonce = actionNonce,
                                            type = PendingRowActionType.DUPLICATE,
                                            rowKey = rowKeySnapshot,
                                            fallbackIndex = wrapper.originalIndex
                                        )
                                        handedOffToEffect = true
                                    } finally {
                                        if (!handedOffToEffect) {
                                            isRowMutationInFlight = false
                                            pendingDuplicatedKeys = pendingDuplicatedKeys - rowKeySnapshot
                                            val nextNonce = rowMutationNonce + 1
                                            rowMutationNonce = nextNonce
                                            pendingClearMutationNonce = nextNonce
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Duplica riga",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val rowKeySnapshot = wrapper.key
                            if (isRowMutationInFlight ||
                                pendingRowAction != null ||
                                rowKeySnapshot in pendingRemovedKeys ||
                                rowKeySnapshot in pendingDuplicatedKeys
                            ) {
                                return@IconButton
                            }
                            isRowMutationInFlight = true
                            pendingRemovedKeys = pendingRemovedKeys + rowKeySnapshot
                            val actionNonce = rowActionCounter + 1
                            rowActionCounter = actionNonce
                            coroutineScope.launch {
                                var handedOffToEffect = false
                                try {
                                    activeEditController.commitActiveEdit()
                                    focusManagerRef.value.clearFocus(force = true)
                                    yield()
                                    Snapshot.sendApplyNotifications()
                                    pendingRowAction = PendingRowAction(
                                        nonce = actionNonce,
                                        type = PendingRowActionType.REMOVE,
                                        rowKey = rowKeySnapshot,
                                        fallbackIndex = wrapper.originalIndex
                                    )
                                    handedOffToEffect = true
                                } finally {
                                    if (!handedOffToEffect) {
                                        isRowMutationInFlight = false
                                        pendingRemovedKeys = pendingRemovedKeys - rowKeySnapshot
                                        val nextNonce = rowMutationNonce + 1
                                        rowMutationNonce = nextNonce
                                        pendingClearMutationNonce = nextNonce
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Rimuovi riga",
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(table) {
        table.enableInteractions(true)
    }

    LaunchedEffect(table, isTableFocused) {
        if (!isTableFocused) {
            DataTableFocusWorkaround.clearFocusedCell(table)
        }
    }

    val wrappedRows = remember(rowsSnapshot, columns, rowKey) {
        buildTableRowWrappers(
            rows = rowsSnapshot,
            columns = columns,
            rowKey = rowKey
        )
    }

    val composedMutationNonce = rowMutationNonce
    LaunchedEffect(table, wrappedRows, rowMutationNonce) {
        table.setList(wrappedRows) { _, w -> w.key }
        if (!isRowMutationInFlight &&
            pendingRowAction == null &&
            (pendingClearMutationNonce == null || pendingClearMutationNonce == composedMutationNonce)
        ) {
            pendingRemovedKeys = emptySet()
            pendingDuplicatedKeys = emptySet()
            pendingClearMutationNonce = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogFocusManager = LocalFocusManager.current
        SideEffect {
            focusManagerRef.value = dialogFocusManager
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            elevation = 10.dp,
            color = LightSurface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(min = 780.dp, max = 1180.dp)
                .fillMaxHeight(0.86f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // Header Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                            color = TextTertiary
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.caption,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${rowsSnapshot.size} ${if (rowsSnapshot.size == 1) "riga" else "righe"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Slot controlli contestuali (es. selettore Loop / Comuni / Famiglia)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(activeEditController, dialogFocusManager) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        activeEditController.commitActiveEdit()
                                        dialogFocusManager.clearFocus(force = true)
                                    }
                                }
                            }
                        }
                ) {
                    headerControls()
                }

                // Barra strumenti foglio di calcolo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    activeEditController.commitActiveEdit()
                                    dialogFocusManager.clearFocus(force = true)
                                    yield()
                                    latestOnAddRow.value()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, PrimaryBlue)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aggiungi Riga", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (onAddMultipleRows != null) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        activeEditController.commitActiveEdit()
                                        dialogFocusManager.clearFocus(force = true)
                                        yield()
                                        latestOnAddMultipleRows.value?.invoke(5)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("+5 Righe", fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = "Suggerimento: click sulla cella per modificare; Tab / Invio / Frecce per spostarti; doppio click sull'intestazione per ordinare",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // Contenitore Griglia Tabellare con DataTableView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onFocusChanged { isTableFocused = it.hasFocus }
                        .pointerInput(table, lazyState, density) {
                            awaitPointerEventScope {
                                val fallbackHeaderHeightPx = with(density) { 36.dp.toPx() }
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        val headerBottomPx = lazyState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == 0 }
                                            ?.let { (it.offset + it.size).toFloat() }
                                            ?: fallbackHeaderHeightPx
                                        val pressedHeader = event.changes.any {
                                            it.position.y in 0f..headerBottomPx
                                        }
                                        if (pressedHeader) {
                                            activeEditController.commitActiveEdit()
                                            dialogFocusManager.clearFocus(force = true)
                                            dialogFocusManager.clearFocus(force = true)
                                            DataTableFocusWorkaround.clearFocusedCell(table)
                                        }
                                    }
                                }
                            }
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, GridBorderColor, RoundedCornerShape(6.dp))
                        .background(LightSurface)
                ) {
                    DataTableView(
                        modifier = Modifier.fillMaxSize(),
                        table = table
                    )

                    if (rowsSnapshot.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emptyMessage, color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                // Messaggio Errore / Riepilogo + Bottoni Conferma
                if (!validationError.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = validationError,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = footerSummary.orEmpty(),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text("Annulla")
                        }
                        Button(
                            onClick = {
                                if (isConfirmInFlight) return@Button
                                isConfirmInFlight = true
                                coroutineScope.launch {
                                    var handedOffToEffect = false
                                    try {
                                        activeEditController.commitActiveEdit()
                                        dialogFocusManager.clearFocus(force = true)
                                        yield()
                                        Snapshot.sendApplyNotifications()
                                        pendingConfirmNonce += 1
                                        handedOffToEffect = true
                                    } finally {
                                        if (!handedOffToEffect) {
                                            isConfirmInFlight = false
                                            confirmAttemptRowsSnapshot = null
                                        }
                                    }
                                }
                            },
                            enabled = !isConfirmInFlight && (isConfirmEnabled || (isTableFocused && rowsSnapshot.isNotEmpty())),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = PrimaryBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(confirmLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cella editabile one-click per [SpreadsheetCreationDialog]: un singolo click su qualsiasi
 * punto della cella (anche vuota) attiva immediatamente il cursore in scrittura, mostra
 * [placeholder] a cella vuota e committa in modo sincrono su perdita focus, `Invio`, `Frecce Su/Giù`
 * o su azioni di barra/salvataggio.
 */
@Composable
private fun OneClickSpreadsheetCell(
    rowKey: Any,
    colIndex: Int,
    committedText: String,
    placeholder: String,
    textStyle: TextStyle,
    inputTransformation: InputTransformation?,
    keyboardOptions: KeyboardOptions,
    activeEditController: ActiveCellEditController,
    focusManagerProvider: () -> FocusManager,
    onCommitText: (String) -> String
) {
    val cellId = remember(rowKey, colIndex) { rowKey to colIndex }
    val textFieldState = rememberTextFieldState(initialText = committedText)
    val cellFocusRequester = remember(rowKey, colIndex) { FocusRequester() }
    var isCellFocused by remember(rowKey, colIndex) { mutableStateOf(false) }
    var isEscaping by remember(rowKey, colIndex) { mutableStateOf(false) }
    var syncedCommittedText by remember(rowKey, colIndex) { mutableStateOf(committedText) }

    val latestOnCommitText = rememberUpdatedState(onCommitText)
    val latestFocusManager = rememberUpdatedState(focusManagerProvider)

    if (!isCellFocused && syncedCommittedText != committedText) {
        syncedCommittedText = committedText
        if (textFieldState.text.toString() != committedText) {
            textFieldState.setTextAndPlaceCursorAtEnd(committedText)
        }
    }

    fun commitEdit() {
        val raw = textFieldState.text.toString()
        val result = latestOnCommitText.value(raw)
        syncedCommittedText = result
        if (textFieldState.text.toString() != result) {
            textFieldState.setTextAndPlaceCursorAtEnd(result)
        }
    }

    DisposableEffect(cellId) {
        onDispose {
            activeEditController.unregisterIfActive(cellId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .pointerInput(cellFocusRequester) {
                detectTapGestures(onTap = { cellFocusRequester.requestFocus() })
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            state = textFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(cellFocusRequester)
                .onFocusChanged { focusState ->
                    val nowFocused = focusState.isFocused
                    if (nowFocused && !isCellFocused) {
                        isEscaping = false
                        activeEditController.registerFocused(cellId) { commitEdit() }
                    } else if (!nowFocused && isCellFocused) {
                        activeEditController.unregisterIfActive(cellId)
                        if (isEscaping) {
                            isEscaping = false
                            textFieldState.setTextAndPlaceCursorAtEnd(syncedCommittedText)
                        } else {
                            commitEdit()
                        }
                    }
                    isCellFocused = nowFocused
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.Escape -> {
                            isEscaping = true
                            textFieldState.setTextAndPlaceCursorAtEnd(syncedCommittedText)
                            activeEditController.unregisterIfActive(cellId)
                            latestFocusManager.value().clearFocus(force = true)
                            true
                        }
                        Key.DirectionUp -> {
                            commitEdit()
                            latestFocusManager.value().moveFocus(FocusDirection.Up)
                            true
                        }
                        Key.DirectionDown -> {
                            commitEdit()
                            latestFocusManager.value().moveFocus(FocusDirection.Down)
                            true
                        }
                        else -> false
                    }
                },
            textStyle = textStyle,
            inputTransformation = inputTransformation,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = KeyboardActionHandler {
                commitEdit()
                latestFocusManager.value().moveFocus(FocusDirection.Next)
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(PrimaryBlue),
            decorator = TextFieldDecorator { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (textFieldState.text.isEmpty() && !isCellFocused && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = TextSecondary.copy(alpha = 0.65f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Configura lo stile uniforme (bordo, zebra striping, stato errore e tipografia)
 * delle celle editabili one-click ([SpreadsheetColumn.Text] e [SpreadsheetColumn.NumericInt]).
 */
private fun editableCellPresentation(
    location: DataTableCellLocation,
    cellValue: SpreadsheetCellValue,
    cellCfg: DataTableCellConfig,
    fontWeight: FontWeight? = null
): DataTableCellConfig {
    val isEvenVisualRow = location.dataRelativeRowIndex % 2 == 0
    return cellCfg.copy(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .border(
                width = if (cellValue.isError) 1.dp else 0.5.dp,
                color = if (cellValue.isError) GridErrorBorder else GridBorderColor
            ),
        color = if (cellValue.isError) ErrorRed else TextPrimary,
        backgroundColor = when {
            cellValue.isError -> GridErrorBg
            isEvenVisualRow -> LightSurface
            else -> SlateBackground
        },
        padding = PaddingValues(0.dp),
        textStyle = TextStyle(
            fontSize = 12.sp,
            fontWeight = fontWeight,
            color = if (cellValue.isError) ErrorRed else TextPrimary
        ),
        alignment = Alignment.CenterStart,
        textAlign = TextAlign.Start
    )
}
