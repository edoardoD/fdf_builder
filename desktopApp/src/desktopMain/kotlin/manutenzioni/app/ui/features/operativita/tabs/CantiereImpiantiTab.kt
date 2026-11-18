package manutenzioni.app.ui.features.operativita.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.ImpiantoSelectionList
import manutenzioni.app.ui.theme.SlateBorder
import manutenzioni.domain.model.*

@Composable
fun CantiereImpiantiTab(
    impiantiDelCantiere: List<Impianto>,
    impiantiSelezionati: Map<String, Boolean>,
    frequenzePerImpianto: Map<String, Periodo>,
    frequenzeSelezionatePerImpianto: Map<String, Set<Periodo>>,
    frequenzeDisponibiliMap: Map<String, List<Periodo>>,
    isAdmin: Boolean,
    onImpiantoSelectionChanged: (String, Boolean) -> Unit,
    onImpiantiSelectionChanged: ((List<String>, Boolean) -> Unit)?,
    onEditImpianto: (Impianto) -> Unit,
    onDeleteImpianto: (Impianto) -> Unit,
    onAddImpiantoClick: () -> Unit,
    onFrequenzaPerImpiantoToggled: (String, Periodo) -> Unit,
    onFrequenzaForImpiantiToggled: ((List<String>, Periodo) -> Unit)?,
    onUpdateMqtSwitchType: ((String, MqtSwitchType) -> Unit)?,
    onUpdateEmergenzaSiglaDocumentale: ((String, EmergenzaSiglaDoc) -> Unit)?,
    actionBarSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Colonna 1: Impianti del cantiere
        Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attività di verifica in questo cantiere",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold
                )
                if (isAdmin) {
                    Button(onClick = onAddImpiantoClick) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aggiungi Attività di Verifica")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ImpiantoSelectionList(
                impianti = impiantiDelCantiere,
                selectionState = impiantiSelezionati,
                onSelectionChanged = onImpiantoSelectionChanged,
                onEditImpianto = onEditImpianto,
                onDeleteImpianto = onDeleteImpianto,
                onBulkSelectionChanged = onImpiantiSelectionChanged,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        // Colonna 2: Frequenza e Generazione
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 0.dp,
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(8.dp),
                backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "1. Scegli Frequenze per Modulo",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Puoi spuntare più periodicità per lo stesso impianto (es. 6 Mesi + 1 Anno)",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val impiantiAttiviIds = impiantiSelezionati.filter { it.value }.keys
                    val impiantiAttivi = impiantiDelCantiere.filter { it.id in impiantiAttiviIds }

                    if (impiantiAttivi.isEmpty()) {
                        Text(
                            text = "Seleziona almeno un impianto dalla lista a sinistra.",
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body2
                        )
                    } else {
                        val quadriAttivi = impiantiAttivi.filterIsInstance<QuadroBT>()
                        val altriImpiantiAttivi = impiantiAttivi.filter { it !is QuadroBT }

                        if (quadriAttivi.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                elevation = 0.dp,
                                border = BorderStroke(1.dp, SlateBorder),
                                shape = RoundedCornerShape(8.dp),
                                backgroundColor = MaterialTheme.colors.surface
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Quadri Elettrici selezionati (${quadriAttivi.size})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.subtitle2,
                                        color = MaterialTheme.colors.primary
                                    )
                                    Text(
                                        text = "Imposta le periodicità per tutti i quadri attivi",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.secondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val frequenzeDisponibiliQuadri = quadriAttivi
                                        .flatMap { frequenzeDisponibiliMap[it.id] ?: emptyList() }
                                        .distinct()
                                        .sortedBy { it.inMesi() }

                                    if (frequenzeDisponibiliQuadri.isEmpty()) {
                                        Text(
                                            text = "Nessuna attività registrata per i quadri selezionati.",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.error
                                        )
                                    } else {
                                        frequenzeDisponibiliQuadri.forEach { periodo ->
                                            val matchingCount = quadriAttivi.count { quadro ->
                                                val selectedFreqs = frequenzeSelezionatePerImpianto[quadro.id]
                                                    ?: frequenzePerImpianto[quadro.id]?.let { setOf(it) }
                                                    ?: emptySet()
                                                periodo in selectedFreqs
                                            }
                                            val triState = when {
                                                matchingCount == 0 -> ToggleableState.Off
                                                matchingCount == quadriAttivi.size -> ToggleableState.On
                                                else -> ToggleableState.Indeterminate
                                            }

                                            val onToggleBulkFreq = {
                                                if (onFrequenzaForImpiantiToggled != null) {
                                                    onFrequenzaForImpiantiToggled(quadriAttivi.map { it.id }, periodo)
                                                } else {
                                                    quadriAttivi.forEach { onFrequenzaPerImpiantoToggled(it.id, periodo) }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onToggleBulkFreq() }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TriStateCheckbox(
                                                    state = triState,
                                                    onClick = onToggleBulkFreq,
                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = periodo.label(),
                                                    style = MaterialTheme.typography.body2
                                                )
                                                if (matchingCount > 0 && matchingCount < quadriAttivi.size) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "($matchingCount/${quadriAttivi.size})",
                                                        style = MaterialTheme.typography.caption,
                                                        color = MaterialTheme.colors.secondary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider()

                                    var expandPersonalizzaQuadri by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandPersonalizzaQuadri = !expandPersonalizzaQuadri }
                                            .padding(top = 8.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Personalizza singoli quadri (${quadriAttivi.size})",
                                            style = MaterialTheme.typography.caption,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colors.primary
                                        )
                                        Icon(
                                            imageVector = if (expandPersonalizzaQuadri) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colors.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (expandPersonalizzaQuadri) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, top = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            quadriAttivi.forEach { quadro ->
                                                val frequenzeDisp = frequenzeDisponibiliMap[quadro.id] ?: emptyList()
                                                val selectedFreqs = frequenzeSelezionatePerImpianto[quadro.id]
                                                    ?: frequenzePerImpianto[quadro.id]?.let { setOf(it) }
                                                    ?: emptySet()
                                                val displayLabel = if (quadro.sigla.isNotBlank()) "${quadro.codIntervento} - ${quadro.sigla}" else quadro.nomeCompleto
                                                Text(
                                                    text = displayLabel,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.caption,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )

                                                frequenzeDisp.forEach { periodo ->
                                                    val isChecked = periodo in selectedFreqs
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { onFrequenzaPerImpiantoToggled(quadro.id, periodo) }
                                                            .padding(vertical = 1.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked,
                                                            onCheckedChange = { onFrequenzaPerImpiantoToggled(quadro.id, periodo) },
                                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = periodo.label(),
                                                            style = MaterialTheme.typography.caption
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        altriImpiantiAttivi.forEach { impianto ->
                            val frequenzeDisp = frequenzeDisponibiliMap[impianto.id] ?: emptyList()
                            val selectedFreqs = frequenzeSelezionatePerImpianto[impianto.id]
                                ?: frequenzePerImpianto[impianto.id]?.let { setOf(it) }
                                ?: emptySet()
                            val displayLabel = if (impianto is QuadroBT && impianto.sigla.isNotBlank()) "${impianto.codIntervento} - ${impianto.sigla}" else impianto.codIntervento
                            Text(
                                text = displayLabel,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )

                            if (impianto is QuadroMQT) {
                                Text(
                                    text = "Tecnologia Interruttore MT:",
                                    style = MaterialTheme.typography.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colors.primary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MqtSwitchType.values().forEach { st ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                onUpdateMqtSwitchType?.invoke(impianto.id, st)
                                            }
                                        ) {
                                            RadioButton(
                                                selected = (impianto.tipoInterruttore ?: MqtSwitchType.SF6) == st,
                                                onClick = { onUpdateMqtSwitchType?.invoke(impianto.id, st) },
                                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Text(st.label, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            if (impianto is ImpiantoEmergenza) {
                                Text(
                                    text = "Sigla Documentale:",
                                    style = MaterialTheme.typography.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colors.primary,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EmergenzaSiglaDoc.values().forEach { sigla ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                onUpdateEmergenzaSiglaDocumentale?.invoke(impianto.id, sigla)
                                            }
                                        ) {
                                            RadioButton(
                                                selected = impianto.siglaDocumentale == sigla,
                                                onClick = { onUpdateEmergenzaSiglaDocumentale?.invoke(impianto.id, sigla) },
                                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Text(sigla.displayLabel, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            if (frequenzeDisp.isEmpty()) {
                                Text(
                                    text = "Nessuna attività registrata per questo modulo.",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.error
                                )
                            } else {
                                frequenzeDisp.forEach { periodo ->
                                    val isChecked = periodo in selectedFreqs
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onFrequenzaPerImpiantoToggled(impianto.id, periodo) }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { onFrequenzaPerImpiantoToggled(impianto.id, periodo) },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = periodo.label(),
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            actionBarSlot()
        }
    }
}
