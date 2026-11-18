package manutenzioni.app.ui.features.operativita.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.components.ItalianDateInputField
import manutenzioni.app.ui.theme.SlateBorder

@Composable
fun CantiereSessionHeaderCard(
    dataInizioContratto: String,
    dataInterventoPrecedente: String,
    dataIntervento: String,
    nomeManutentore: String,
    hasFirmaManutentore: Boolean,
    onDataInizioContrattoChange: (String) -> Unit,
    onDataInterventoPrecedenteChange: (String) -> Unit,
    onDataInterventoChange: (String) -> Unit,
    onNomeManutentoreChange: (String) -> Unit,
    onSelectFirmaManutentore: () -> Unit,
    onRemoveFirmaManutentore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = 0.dp,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Engineering,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dati Sessione Intervento",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Storicizzazione automatica nel database",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItalianDateInputField(
                    value = dataInizioContratto,
                    onValueChange = onDataInizioContrattoChange,
                    label = "Data inizio contratto",
                    placeholder = "GG/MM/AAAA",
                    modifier = Modifier.weight(1f)
                )

                ItalianDateInputField(
                    value = dataInterventoPrecedente,
                    onValueChange = onDataInterventoPrecedenteChange,
                    label = "Data intervento precedente",
                    placeholder = "GG/MM/AAAA",
                    modifier = Modifier.weight(1f)
                )

                ItalianDateInputField(
                    value = dataIntervento,
                    onValueChange = onDataInterventoChange,
                    label = "Data intervento",
                    placeholder = "GG/MM/AAAA",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nomeManutentore,
                    onValueChange = onNomeManutentoreChange,
                    label = { Text("Nome Manutentore / Tecnico") },
                    placeholder = { Text("Nome e Cognome (es. Mario Rossi)") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colors.primary)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = SlateBorder
                    )
                )

                Column(
                    modifier = Modifier.weight(0.8f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onSelectFirmaManutentore,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (hasFirmaManutentore) Color(0xFF2E7D32) else MaterialTheme.colors.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (hasFirmaManutentore) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasFirmaManutentore) "Firma Caricata (Cambia)" else "Carica Firma Tecnico",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (hasFirmaManutentore) {
                            TextButton(
                                onClick = onRemoveFirmaManutentore,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.error)
                            ) {
                                Text("Rimuovi", fontSize = 11.sp)
                            }
                        }
                    }
                    Text(
                        text = if (hasFirmaManutentore) "✓ Immagine firma pronta per il cartiglio PDF" else "Nessuna firma digitale associata (opzionale)",
                        fontSize = 10.sp,
                        color = if (hasFirmaManutentore) Color(0xFF2E7D32) else MaterialTheme.colors.secondary
                    )
                }
            }
        }
    }
}
