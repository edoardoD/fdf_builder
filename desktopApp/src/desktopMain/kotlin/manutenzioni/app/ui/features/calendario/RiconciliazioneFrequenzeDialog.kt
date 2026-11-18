package manutenzioni.app.ui.features.calendario

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.service.DiscrepanzaFrequenzaReport

/**
 * Modale di riconciliazione delle discrepanze di frequenza.
 * Offre 3 opzioni:
 * 1. Frequenze Differenziate per Impianto (Consigliata)
 * 2. Allinea al Minimo Comune
 * 3. Forza Frequenza Selezionata
 */
@Composable
internal fun RiconciliazioneFrequenzeDialog(
    report: DiscrepanzaFrequenzaReport,
    frequenzaScelta: Periodo,
    onConfermaDifferenziata: (Map<String, Periodo>) -> Unit,
    onConfermaMinima: (Periodo) -> Unit,
    onConfermaForzata: (Periodo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(620.dp).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color.White,
            elevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.padding(8.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Riconciliazione Frequenze Impianti",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Rilevata discrepanza tra la periodicità scelta e i vincoli normativi.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color(0xFF64748B))
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                val scrollState = rememberScrollState()
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Spiegazione
                        Text(
                            text = "Hai selezionato una frequenza di intervento di ${frequenzaScelta.label()} (${frequenzaScelta.inMesi()} mesi), tuttavia i seguenti impianti hanno una frequenza massima inferiore:",
                            fontSize = 11.sp,
                            color = Color(0xFF334155),
                            lineHeight = 16.sp
                        )

                        // Box Impianti in Discrepanza
                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                report.discrepanze.forEach { discr ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = Color(0xFFEFF6FF),
                                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = discr.codIntervento,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1D4ED8),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = discr.nomeCompleto,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFFFEE2E2),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Max: ${discr.frequenzaMassima.label()}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Come desideri procedere?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        // OPZIONE 1: Differenziata (Consigliata)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colors.primary),
                            backgroundColor = Color(0xFFF0F7FF),
                            elevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Opzione A: Frequenze Differenziate (Consigliata)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colors.primary
                                    )
                                    Surface(
                                        color = MaterialTheme.colors.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "CONSIGLIATA",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Assegna a ciascun impianto la propria frequenza massima ammissibile (es. ${report.discrepanze.map { "${it.codIntervento} a ${it.frequenzaMassima.label()}" }.joinToString(", ")}, altri a ${frequenzaScelta.label()}). Genera automaticamente eventi indipendenti e scadenze contestuali per ogni singolo impianto.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF334155),
                                    lineHeight = 15.sp
                                )
                                Button(
                                    onClick = { onConfermaDifferenziata(report.frequenzeConsigliatePerImpianto) },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.primary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Applica Frequenze Differenziate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // OPZIONE 2: Allinea al Minimo
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            backgroundColor = Color.White,
                            elevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Opzione B: Allinea Tutto a ${report.frequenzaMinimaTraImpianti.label()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Uniforma l'intero intervento e tutti gli impianti alla periodicità più stringente (${report.frequenzaMinimaTraImpianti.label()}). Tutti gli impianti verranno verificati con la medesima cadenza.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 15.sp
                                )
                                OutlinedButton(
                                    onClick = { onConfermaMinima(report.frequenzaMinimaTraImpianti) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Allinea a ${report.frequenzaMinimaTraImpianti.label()}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // OPZIONE 3: Forza Frequenza Scelta
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            backgroundColor = Color(0xFFFAFAFA),
                            elevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Opzione C: Forza ${frequenzaScelta.label()} per Tutti",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "Mantiene la periodicità scelta di ${frequenzaScelta.label()} per tutti gli impianti, forzando l'inclusione secondo le regole generali.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 15.sp
                                )
                                TextButton(
                                    onClick = { onConfermaForzata(frequenzaScelta) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Forza Frequenza Unica (${frequenzaScelta.label()})", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }

                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState)
                    )
                }

                Divider(color = Color(0xFFE2E8F0))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Torna alla Selezione", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
