package manutenzioni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.QuadroMQT

@Composable
fun ImpiantoSelectionList(
    impianti: List<Impianto>,
    selectionState: Map<String, Boolean>,
    onSelectionChanged: (String, Boolean) -> Unit,
    onEditImpianto: (Impianto) -> Unit,
    onDeleteImpianto: (Impianto) -> Unit,
    modifier: Modifier = Modifier,
    onBulkSelectionChanged: ((List<String>, Boolean) -> Unit)? = null
) {
    if (impianti.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Nessun impianto trovato per questo cantiere.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    val quadriBT = remember(impianti) { impianti.filterIsInstance<QuadroBT>() }
    val altriImpianti = remember(impianti) { impianti.filter { it !is QuadroBT } }
    var isQuadriExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Seleziona Impianti da Includere",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (quadriBT.isNotEmpty()) {
                item(key = "group_quadri_bt") {
                    val selectedQuadriCount = quadriBT.count { selectionState[it.id] == true }
                    val groupToggleState = when {
                        selectedQuadriCount == 0 -> ToggleableState.Off
                        selectedQuadriCount == quadriBT.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                    val onToggleAll = {
                        val shouldSelect = groupToggleState != ToggleableState.On
                        if (onBulkSelectionChanged != null) {
                            onBulkSelectionChanged(quadriBT.map { it.id }, shouldSelect)
                        } else {
                            quadriBT.forEach { onSelectionChanged(it.id, shouldSelect) }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 2.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TriStateCheckbox(
                                    state = groupToggleState,
                                    onClick = onToggleAll,
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Quadri Elettrici (${quadriBT.size})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.subtitle1
                                    )
                                    Text(
                                        text = "$selectedQuadriCount di ${quadriBT.size} selezionati",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { isQuadriExpanded = !isQuadriExpanded }) {
                                    Icon(
                                        imageVector = if (isQuadriExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isQuadriExpanded) "Comprimi quadri" else "Espandi quadri",
                                        tint = MaterialTheme.colors.primary
                                    )
                                }
                            }

                            if (isQuadriExpanded) {
                                Divider()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quadriBT.forEach { quadro ->
                                        val isSelected = selectionState[quadro.id] == true
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    onSelectionChanged(quadro.id, checked)
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (quadro.sigla.isNotBlank()) {
                                                        Text(
                                                            text = quadro.sigla,
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.body2,
                                                            modifier = Modifier.padding(end = 8.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = quadro.descrizioneQuadro.ifBlank { quadro.nomeCompleto },
                                                        style = MaterialTheme.typography.body2
                                                    )
                                                }
                                                Text(
                                                    text = "${quadro.codIntervento} • ${quadro.listaInterruttori.size} interruttori",
                                                    style = MaterialTheme.typography.caption,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                            Row {
                                                IconButton(onClick = { onEditImpianto(quadro) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Modifica quadro",
                                                        tint = MaterialTheme.colors.primary
                                                    )
                                                }
                                                IconButton(onClick = { onDeleteImpianto(quadro) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Elimina quadro",
                                                        tint = MaterialTheme.colors.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(altriImpianti, key = { it.id }) { impianto ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSelected = selectionState[impianto.id] == true
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                onSelectionChanged(impianto.id, checked)
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = impianto.nomeCompleto,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Codice: ${impianto.codIntervento}",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        if (impianto is QuadroMQT && impianto.tipoInterruttore != null) {
                            Text(
                                text = "Interruttore: ${impianto.tipoInterruttore.label}",
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }

                        Row {
                            IconButton(onClick = { onEditImpianto(impianto) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Modifica impianto",
                                    tint = MaterialTheme.colors.primary
                                )
                            }
                            IconButton(onClick = { onDeleteImpianto(impianto) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Elimina impianto",
                                    tint = MaterialTheme.colors.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
