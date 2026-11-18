package manutenzioni.app.ui.features.operativita.editor.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.EmergenzaSiglaDoc

@Composable
fun GeneralEditorSection(
    codIntervento: String,
    onCodInterventoChange: (String) -> Unit,
    nomeCompleto: String,
    onNomeCompletoChange: (String) -> Unit,
    premessa: String,
    onPremessaChange: (String) -> Unit,
    siglaQuadro: String,
    onSiglaQuadroChange: (String) -> Unit,
    descrizioneQuadro: String,
    onDescrizioneQuadroChange: (String) -> Unit,
    isQuadro: Boolean,
    isEmergenza: Boolean,
    isNew: Boolean,
    isReadOnlyAdminFields: Boolean,
    codInterventoError: Boolean,
    nomeCompletoError: Boolean,
    propagaModifiche: Boolean,
    onPropagaModificheChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header con titolo e pulsanti Salva / Annulla
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isNew) "Nuovo Impianto" else "Modifica Impianto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = if (isNew) "Compila i dati e aggiungi le attività di verifica" else "Modifica le informazioni e le attività associate",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onCancelClick != null) {
                        OutlinedButton(
                            onClick = onCancelClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Annulla", fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = onSaveClick,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isNew) "Crea Impianto" else "Salva", fontSize = 13.sp)
                    }
                }
            }
        }

        // Campi principali compatti
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 1.dp,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = codIntervento,
                        onValueChange = onCodInterventoChange,
                        label = { Text("Codice *", fontSize = 11.sp) },
                        modifier = Modifier.weight(0.20f),
                        singleLine = true,
                        isError = codInterventoError,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            errorBorderColor = Color(0xFFD32F2F)
                        ),
                        placeholder = { Text("es. Q, EM, RI", fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    )

                    OutlinedTextField(
                        value = nomeCompleto,
                        onValueChange = onNomeCompletoChange,
                        label = { Text("Nome Completo *", fontSize = 11.sp) },
                        modifier = Modifier.weight(if (isQuadro || isEmergenza) 0.50f else 0.80f),
                        singleLine = true,
                        isError = nomeCompletoError,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            errorBorderColor = Color(0xFFD32F2F)
                        ),
                        placeholder = { Text("es. Quadro Elettrico Principale", fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    if (isQuadro) {
                        OutlinedTextField(
                            value = siglaQuadro,
                            onValueChange = onSiglaQuadroChange,
                            label = { Text("Sigla Quadro", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.30f),
                            singleLine = true,
                            placeholder = { Text("es. QG, Q1", fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    } else if (isEmergenza) {
                        val currentSigla = EmergenzaSiglaDoc.fromCod(codIntervento)
                        Row(
                            modifier = Modifier.weight(0.30f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Doc:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF475569))
                            EmergenzaSiglaDoc.values().forEach { sigla ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        onCodInterventoChange(sigla.codIntervento)
                                        if (nomeCompleto.isBlank() || EmergenzaSiglaDoc.values().any { it.nomeCompleto.equals(nomeCompleto.trim(), ignoreCase = true) }) {
                                            onNomeCompletoChange(sigla.nomeCompleto)
                                        }
                                    }
                                ) {
                                    RadioButton(
                                        selected = currentSigla == sigla,
                                        onClick = {
                                            onCodInterventoChange(sigla.codIntervento)
                                            if (nomeCompleto.isBlank() || EmergenzaSiglaDoc.values().any { it.nomeCompleto.equals(nomeCompleto.trim(), ignoreCase = true) }) {
                                                onNomeCompletoChange(sigla.nomeCompleto)
                                            }
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(sigla.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                if (codInterventoError || nomeCompletoError) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (codInterventoError) {
                            Text(
                                text = "Il codice intervento è obbligatorio",
                                color = Color(0xFFD32F2F),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        if (nomeCompletoError) {
                            Text(
                                text = "Il nome completo è obbligatorio",
                                color = Color(0xFFD32F2F),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                if (isQuadro) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = descrizioneQuadro,
                            onValueChange = onDescrizioneQuadroChange,
                            label = { Text("Descrizione / Ubicazione", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.50f),
                            singleLine = true,
                            placeholder = { Text("es. Piano Terra, Locale Tecnico", fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        OutlinedTextField(
                            value = premessa,
                            onValueChange = onPremessaChange,
                            label = { Text("Premessa", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.50f),
                            singleLine = true,
                            placeholder = { Text("Dicitura premessa nel verbale PDF", fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = premessa,
                        onValueChange = onPremessaChange,
                        label = { Text("Premessa", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Dicitura premessa nel verbale PDF", fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }

                if (!isReadOnlyAdminFields && !isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Checkbox(
                            checked = propagaModifiche,
                            onCheckedChange = onPropagaModificheChange,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Propaga modifiche (attività e normative) a TUTTI i cantieri con codice $codIntervento",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }
}
