package manutenzioni.app.ui.features.calendario

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.domain.model.CantiereScadenzaInMese
import manutenzioni.domain.model.MeseScadenzaGlobaleGroup
import manutenzioni.domain.model.ScadenzaImpiantoItem

/**
 * Vista Globale di primo livello ("Calendario") nella barra laterale principale.
 *
 * Mostra per ogni mese pianificato (es. "Maggio 2026"):
 * - Per quali cantieri e clienti sono previste manutenzioni in quel mese;
 * - Quali impianti e periodicità sono in scadenza;
 * - Quali verifiche / attività / modifiche specifiche vanno eseguite su ciascun impianto;
 * - Azioni rapide per aprire direttamente il cantiere in Operatività con gli impianti del mese già
 *   selezionati oppure stampare il Foglio di Riepilogo Generale PDF (R27, R28).
 */
@Composable
fun CalendarioGlobaleScreen(
    state: ManutenzioniUiState,
    onOrizzonteChanged: (Int) -> Unit,
    onSelectMese: (String?) -> Unit,
    onApriCantiereDaCalendario: (CantiereScadenzaInMese) -> Unit,
    onExportRiepilogoCantierePdf: (CantiereScadenzaInMese) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val orizzontiDisponibili = listOf(12 to "1 Anno (12M)", 24 to "2 Anni (24M)", 36 to "3 Anni (36M)", 60 to "5 Anni (60M)")

    val gruppiFiltrati = remember(state.calendarioGlobale, state.meseCalendarioSelezionato, searchQuery) {
        val base = if (state.meseCalendarioSelezionato != null) {
            state.calendarioGlobale.filter { it.annoMese == state.meseCalendarioSelezionato }
        } else {
            state.calendarioGlobale
        }
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            base
        } else {
            base.mapNotNull { group ->
                val matchingCantieri = group.cantieriInScadenza.filter { item ->
                    item.cantiere.nome.lowercase().contains(query) ||
                        (item.cliente?.nome?.lowercase()?.contains(query) == true) ||
                        item.etichettaContains(group.etichettaMese, query) ||
                        item.scadenzeImpianti.any { imp ->
                            imp.codIntervento.lowercase().contains(query) ||
                                imp.descrizioneImpianto.lowercase().contains(query)
                        }
                }
                if (matchingCantieri.isNotEmpty()) group.copy(cantieriInScadenza = matchingCantieri) else null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header principale + selettore orizzonte + ricerca
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(10.dp),
            backgroundColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(10.dp).size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Calendario Globale Manutenzioni — Tutti i Cantieri",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Seleziona un mese (es. Maggio) per vedere tutti i cantieri in scadenza, gli impianti coinvolti e le verifiche/modifiche da eseguire.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Orizzonte:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        orizzontiDisponibili.forEach { (mesi, label) ->
                            val isSelected = state.orizzonteScadenzeMesi == mesi
                            OutlinedButton(
                                onClick = { onOrizzonteChanged(mesi) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    backgroundColor = if (isSelected) MaterialTheme.colors.primary else Color.White,
                                    contentColor = if (isSelected) Color.White else Color(0xFF334155)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Divider(color = Color(0xFFF1F5F9))

                // Barra filtro ricerca + barra orizzontale selezione rapida Mese
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cerca cantiere, cliente, impianto o mese (es. Maggio)...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.width(340.dp).height(48.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = state.meseCalendarioSelezionato == null
                        Surface(
                            color = if (allSelected) Color(0xFF1E3A8A) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (allSelected) Color(0xFF1E3A8A) else Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable { onSelectMese(null) }
                        ) {
                            Text(
                                text = "Tutti i mesi (${state.calendarioGlobale.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (allSelected) Color.White else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        state.calendarioGlobale.forEach { group ->
                            val isSelected = state.meseCalendarioSelezionato == group.annoMese
                            Surface(
                                color = if (isSelected) MaterialTheme.colors.primary else Color.White,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier.clickable {
                                    onSelectMese(if (isSelected) null else group.annoMese)
                                }
                            ) {
                                Text(
                                    text = "${group.etichettaMese} (${group.totaleCantieri} cant.)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF1E293B),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Contenuto principale: elenco mesi e cantieri in scadenza
        if (gruppiFiltrati.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                elevation = 0.dp,
                backgroundColor = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF64748B))
                    Text(
                        text = if (state.tuttiCantieri.isEmpty()) {
                            "Nessun cantiere registrato nel database. Aggiungi un cliente e un cantiere con impianti per visualizzare la pianificazione globale."
                        } else {
                            "Nessuna scadenza trovata per i filtri selezionati."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(gruppiFiltrati, key = { it.annoMese }) { group ->
                    MeseGlobaleSectionCard(
                        group = group,
                        onApriCantiere = onApriCantiereDaCalendario,
                        onExportRiepilogoPdf = onExportRiepilogoCantierePdf
                    )
                }
            }
        }
    }
}

private fun CantiereScadenzaInMese.etichettaContains(etichettaMese: String, query: String): Boolean =
    etichettaMese.lowercase().contains(query) || dataPrevista.lowercase().contains(query)

@Composable
private fun MeseGlobaleSectionCard(
    group: MeseScadenzaGlobaleGroup,
    onApriCantiere: (CantiereScadenzaInMese) -> Unit,
    onExportRiepilogoPdf: (CantiereScadenzaInMese) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Intestazione del mese (es. "Maggio 2026")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E3A8A))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📅 ${group.etichettaMese.uppercase()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Surface(
                        color = Color(0xFF3B82F6),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${group.totaleCantieri} cantieri da manutenere",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "${group.totaleImpianti} impianti in scadenza • ${group.totaleAttivita} verifiche/modifiche totali",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFDBEAFE)
                )
            }

            // Lista dei cantieri che hanno manutenzioni nel mese
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                group.cantieriInScadenza.forEach { cantiereScadenza ->
                    CantiereScadenzaCard(
                        item = cantiereScadenza,
                        etichettaMese = group.etichettaMese,
                        onApriCantiere = { onApriCantiere(cantiereScadenza) },
                        onExportRiepilogoPdf = { onExportRiepilogoPdf(cantiereScadenza) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CantiereScadenzaCard(
    item: CantiereScadenzaInMese,
    etichettaMese: String,
    onApriCantiere: () -> Unit,
    onExportRiepilogoPdf: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Riga Superiore: Info Cantiere/Cliente + Azioni rapide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.cliente?.nome ?: "Cliente",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = item.cantiere.nome,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        if (!item.cantiere.indirizzo.isNullOrBlank()) {
                            Text(
                                text = "— ${item.cantiere.indirizzo}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Data prevista: ${item.dataPrevista} (Mese +${item.indiceMeseDalContratto} da inizio contratto) • " +
                            "Manutentore: ${item.cantiere.nomeManutentore ?: "Non assegnato"} • " +
                            "${item.totaleImpiantiInScadenza} impianti (${item.totaleAttivitaInScadenza} attività da eseguire)",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onExportRiepilogoPdf,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Riepilogo PDF", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onApriCantiere,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prepara in Operatività ($etichettaMese) →", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Divider(color = Color(0xFFE2E8F0))

            // Elenco impianti in scadenza e relative verifiche/modifiche
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.scadenzeImpianti.forEach { scadenzaImp ->
                    ImpiantoScadenzaRow(scadenzaImp)
                }
            }
        }
    }
}

@Composable
private fun ImpiantoScadenzaRow(scadenzaImp: ScadenzaImpiantoItem) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (scadenzaImp.attivitaInScadenza.isNotEmpty()) expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Text(
                            text = scadenzaImp.codIntervento,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = scadenzaImp.descrizioneImpianto,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    scadenzaImp.frequenzeIncluse.forEach { freq ->
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Text(
                                text = freq.label(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "${scadenzaImp.numeroAttivita} verifiche da fare",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colors.primary
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (expanded && scadenzaImp.attivitaInScadenza.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFF1F5F9))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Verifiche, controlli e interventi previsti in scadenza:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    scadenzaImp.attivitaInScadenza.forEach { attDesc ->
                        Text(
                            text = "• $attDesc",
                            fontSize = 11.sp,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }
    }
}
