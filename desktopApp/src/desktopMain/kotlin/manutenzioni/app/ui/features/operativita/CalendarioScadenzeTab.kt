package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.theme.SlateBorder
import manutenzioni.domain.model.MeseScadenzaGroup

/**
 * Tab "Calendario Scadenze" e generazione "Foglio di Riepilogo Generale Intervento" (Wave 4: R27, R28).
 */
@Composable
fun CalendarioScadenzeTab(
    scadenze: List<MeseScadenzaGroup>,
    orizzonteMesi: Int,
    dataInizioRiferimento: String?,
    isLoading: Boolean,
    onOrizzonteMesiChanged: (Int) -> Unit,
    onExportRiepilogoPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orizzontiDisponibili = listOf(
        12 to "1 Anno (12M)",
        24 to "2 Anni (24M)",
        36 to "3 Anni (36M)",
        60 to "5 Anni (60M)"
    )

    Card(
        modifier = modifier,
        elevation = 0.dp,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Calendario Scadenze e Riepilogo Intervento",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Data base pianificazione: ${dataInizioRiferimento?.takeIf { it.isNotBlank() } ?: "01/01/2026 (default)"} • ${scadenze.size} scadenze rilevate nei prossimi $orizzonteMesi mesi",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }

                Button(
                    onClick = onExportRiepilogoPdf,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stampa Foglio di Riepilogo Generale (PDF)", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selettore orizzonte temporale
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Orizzonte di pianificazione:",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold
                )
                orizzontiDisponibili.forEach { (mesi, label) ->
                    val selected = orizzonteMesi == mesi
                    OutlinedButton(
                        onClick = { onOrizzonteMesiChanged(mesi) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else MaterialTheme.colors.surface,
                            contentColor = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
                        ),
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colors.primary else SlateBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            if (scadenze.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nessuna scadenza calcolata per questo cantiere.",
                            style = MaterialTheme.typography.body1,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Aggiungi almeno un'attività di verifica al cantiere per visualizzare il calendario.",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    scadenze.forEach { group ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 0.dp,
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colors.primary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Mese +${group.indiceMese}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colors.primary
                                            )
                                        }
                                        Text(
                                            text = group.etichettaMese,
                                            style = MaterialTheme.typography.subtitle2,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "📅 Data prevista: ${group.dataPrevista} • ${group.scadenze.size} moduli",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = SlateBorder.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(8.dp))

                                group.scadenze.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = item.codIntervento,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colors.primary
                                            )
                                            Text(
                                                text = item.descrizioneImpianto,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            item.frequenzeIncluse.forEach { freq ->
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = freq.label(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colors.primary
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "(${item.numeroAttivita} att.)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colors.secondary
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
}
