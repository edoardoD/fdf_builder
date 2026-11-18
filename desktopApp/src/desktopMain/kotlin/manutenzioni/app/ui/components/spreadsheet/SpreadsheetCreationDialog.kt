package manutenzioni.app.ui.components.spreadsheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val GridBorderColor = Color(0xFFCBD5E1)
private val GridHeaderBg = Color(0xFFF1F5F9)
private val GridZebraBg = Color(0xFFF8FAFC)
private val GridErrorBg = Color(0xFFFEF2F2)
private val GridErrorBorder = Color(0xFFEF4444)

/**
 * Dialog tabellare generico in stile foglio di calcolo per l'inserimento rapido multi-riga.
 * Supporta navigazione da tastiera (Tab / Shift+Tab / Invio), duplicazione riga, aggiunta rapida
 * multipla e slot opzionale [headerControls] per selettori contestuali (es. Loop 1..8, Comuni).
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
    val listState = rememberLazyListState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            elevation = 10.dp,
            color = MaterialTheme.colors.surface,
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
                            color = Color(0xFF0F172A)
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.caption,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${rows.size} ${if (rows.size == 1) "riga" else "righe"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color(0xFF64748B))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Slot controlli contestuali (es. selettore Loop / Comuni / Famiglia)
                headerControls()

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
                            onClick = onAddRow,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colors.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aggiungi Riga", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (onAddMultipleRows != null) {
                            OutlinedButton(
                                onClick = { onAddMultipleRows(5) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("+5 Righe", fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = "Suggerimento: usa Tab / Invio per spostarti tra le celle come in un foglio di calcolo",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Contenitore Griglia Tabellare con bordi Excel
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, GridBorderColor, RoundedCornerShape(6.dp))
                ) {
                    // Header Tabella Sticky
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(GridHeaderBg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, GridBorderColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("#", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        }

                        columns.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .weight(col.weight)
                                    .fillMaxHeight()
                                    .border(0.5.dp, GridBorderColor)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = col.header,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(if (onDuplicateRow != null) 72.dp else 40.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, GridBorderColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Azioni", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        }
                    }

                    // Corpo Righe Scrollabile
                    if (rows.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emptyMessage, color = Color(0xFF64748B), fontSize = 13.sp)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(end = 10.dp)
                            ) {
                                itemsIndexed(rows, key = { idx, row -> rowKey(idx, row) }) { rowIndex, row ->
                                    val rowBg = if (rowIndex % 2 == 0) Color.White else GridZebraBg
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .background(rowBg),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Gutter Numero Riga
                                        Box(
                                            modifier = Modifier
                                                .width(38.dp)
                                                .fillMaxHeight()
                                                .background(GridHeaderBg.copy(alpha = 0.6f))
                                                .border(0.5.dp, GridBorderColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${rowIndex + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF64748B)
                                            )
                                        }

                                        // Celle Colonne
                                        columns.forEach { col ->
                                            SpreadsheetCellBox(
                                                column = col,
                                                row = row,
                                                onUpdate = { updated -> onUpdateRow(rowIndex, updated) },
                                                modifier = Modifier.weight(col.weight).fillMaxHeight()
                                            )
                                        }

                                        // Gutter Azioni
                                        Row(
                                            modifier = Modifier
                                                .width(if (onDuplicateRow != null) 72.dp else 40.dp)
                                                .fillMaxHeight()
                                                .border(0.5.dp, GridBorderColor),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (onDuplicateRow != null) {
                                                IconButton(
                                                    onClick = { onDuplicateRow(rowIndex, row) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Duplica riga",
                                                        tint = MaterialTheme.colors.primary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onRemoveRow(rowIndex) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Rimuovi riga",
                                                    tint = Color(0xFFDC2626),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(listState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                            )
                        }
                    }
                }

                // Messaggio Errore / Riepilogo + Bottoni Conferma
                if (!validationError.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = validationError,
                        color = MaterialTheme.colors.error,
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
                        color = Color(0xFF475569)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text("Annulla")
                        }
                        Button(
                            onClick = onConfirm,
                            enabled = isConfirmEnabled,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary,
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

@Composable
private fun <Row> SpreadsheetCellBox(
    column: SpreadsheetColumn<Row>,
    row: Row,
    onUpdate: (Row) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    when (column) {
        is SpreadsheetColumn.Text -> {
            val value = column.getter(row)
            val hasError = column.isError(row)
            Box(
                modifier = modifier
                    .background(if (hasError) GridErrorBg else Color.Transparent)
                    .border(
                        width = if (hasError) 1.dp else 0.5.dp,
                        color = if (hasError) GridErrorBorder else GridBorderColor
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { raw ->
                        val next = if (column.uppercase) raw.uppercase() else raw
                        onUpdate(column.setter(row, next))
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF0F172A)),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Tab) {
                                focusManager.moveFocus(
                                    if (keyEvent.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                                )
                                true
                            } else false
                        },
                    decorationBox = { innerTextField ->
                        if (value.isEmpty() && column.placeholder.isNotEmpty()) {
                            Text(
                                text = column.placeholder,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        is SpreadsheetColumn.NumericInt -> {
            val currentInt = column.getter(row)
            var rawText by remember { mutableStateOf(currentInt?.toString().orEmpty()) }
            LaunchedEffect(currentInt) {
                if (rawText.toIntOrNull() != currentInt) {
                    rawText = currentInt?.toString().orEmpty()
                }
            }
            val hasError = column.isError(row) || (rawText.isNotBlank() && (rawText.toIntOrNull() !in column.range))

            Box(
                modifier = modifier
                    .background(if (hasError) GridErrorBg else Color.Transparent)
                    .border(
                        width = if (hasError) 1.dp else 0.5.dp,
                        color = if (hasError) GridErrorBorder else GridBorderColor
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = rawText,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }
                        rawText = digitsOnly
                        onUpdate(column.setter(row, digitsOnly.toIntOrNull()))
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasError) Color(0xFFB91C1C) else Color(0xFF0F172A)
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Tab) {
                                focusManager.moveFocus(
                                    if (keyEvent.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                                )
                                true
                            } else false
                        },
                    decorationBox = { innerTextField ->
                        if (rawText.isEmpty() && column.placeholder.isNotEmpty()) {
                            Text(
                                text = column.placeholder,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        is SpreadsheetColumn.Dropdown<Row, *> -> {
            @Suppress("UNCHECKED_CAST")
            val typedCol = column as SpreadsheetColumn.Dropdown<Row, Any?>
            var expanded by remember { mutableStateOf(false) }
            val currentOption = typedCol.getter(row)

            Box(
                modifier = modifier
                    .border(0.5.dp, GridBorderColor)
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = typedCol.optionLabel(currentOption),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    typedCol.options.forEach { option ->
                        DropdownMenuItem(
                            onClick = {
                                onUpdate(typedCol.setter(row, option))
                                expanded = false
                            }
                        ) {
                            Text(typedCol.optionLabel(option), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        is SpreadsheetColumn.Custom -> {
            Box(
                modifier = modifier
                    .border(0.5.dp, GridBorderColor)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                column.content(row, onUpdate)
            }
        }
    }
}
