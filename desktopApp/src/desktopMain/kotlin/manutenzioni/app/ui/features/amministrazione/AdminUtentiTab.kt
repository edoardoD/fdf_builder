package manutenzioni.app.ui.features.amministrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.theme.SlateBorder
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.RuoloUtente
import manutenzioni.domain.model.Utente

/**
 * Tab Amministrazione per la Gestione Utenti e Permessi RBAC (Wave 4: R31).
 */
@Composable
fun AdminUtentiTab(
    utenti: List<Utente>,
    utenteAttivo: Utente,
    clienti: List<Cliente>,
    cantieriDisponibili: List<Cantiere>,
    onSelectUtenteAttivo: (Utente) -> Unit,
    onSaveUtente: (Utente) -> Unit,
    onDeleteUtente: (String) -> Unit
) {
    var utenteInEditing by remember { mutableStateOf<Utente?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        UtenteRbacDialog(
            initialUtente = utenteInEditing,
            cantieriDisponibili = cantieriDisponibili,
            clienti = clienti,
            onDismiss = {
                showDialog = false
                utenteInEditing = null
            },
            onConfirm = { salvato ->
                onSaveUtente(salvato)
                showDialog = false
                utenteInEditing = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gestione Utenti e Controllo Accessi (RBAC)",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configura i profili Admin (accesso completo) e Tecnico (accesso limitato ai soli cantieri assegnati).",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.secondary
                )
            }

            Button(
                onClick = {
                    utenteInEditing = null
                    showDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nuovo Utente", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(utenti, key = { it.id }) { utente ->
                val isCurrent = utente.id == utenteAttivo.id
                val isAdminRole = utente.ruolo == RuoloUtente.ADMIN

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 0.dp,
                    border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) MaterialTheme.colors.primary else SlateBorder),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isAdminRole) MaterialTheme.colors.primary else Color(0xFF2E7D32),
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = utente.nomeCompleto,
                                        style = MaterialTheme.typography.subtitle1,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isAdminRole) Color(0xFFEFF6FF) else Color(0xFFE8F5E9),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = utente.ruolo.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAdminRole) MaterialTheme.colors.primary else Color(0xFF2E7D32)
                                        )
                                    }
                                    if (isCurrent) {
                                        Text(
                                            text = "● PROFILO ATTIVO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colors.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAdminRole) {
                                        "@${utente.username} • Accesso globale a tutte le anagrafiche, cantieri e master"
                                    } else {
                                        "@${utente.username} • Cantieri assegnati: ${utente.cantieriAssegnatiIds.size}"
                                    },
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.secondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isCurrent) {
                                OutlinedButton(onClick = { onSelectUtenteAttivo(utente) }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Imposta come Attivo", fontSize = 12.sp)
                                }
                            }
                            IconButton(onClick = {
                                utenteInEditing = utente
                                showDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = MaterialTheme.colors.primary)
                            }
                            if (utenti.size > 1) {
                                IconButton(onClick = { onDeleteUtente(utente.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colors.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtenteRbacDialog(
    initialUtente: Utente?,
    cantieriDisponibili: List<Cantiere>,
    clienti: List<Cliente>,
    onDismiss: () -> Unit,
    onConfirm: (Utente) -> Unit
) {
    var username by remember { mutableStateOf(initialUtente?.username ?: "") }
    var nomeCompleto by remember { mutableStateOf(initialUtente?.nomeCompleto ?: "") }
    var ruolo by remember { mutableStateOf(initialUtente?.ruolo ?: RuoloUtente.TECNICO) }
    var cantieriAssegnati by remember { mutableStateOf(initialUtente?.cantieriAssegnatiIds ?: emptySet()) }

    val clientiMap = remember(clienti) { clienti.associateBy { it.id } }

    AlertDialog(
        modifier = Modifier.width(540.dp),
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialUtente == null) "Nuovo Utente RBAC" else "Modifica Utente RBAC",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nomeCompleto,
                    onValueChange = { nomeCompleto = it },
                    label = { Text("Nome e Cognome Completo *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Codice Operatore *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Ruolo Utente:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RuoloUtente.values().forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { ruolo = r }
                        ) {
                            RadioButton(
                                selected = ruolo == r,
                                onClick = { ruolo = r },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                            )
                            Text(r.label, fontSize = 13.sp)
                        }
                    }
                }

                if (ruolo == RuoloUtente.TECNICO) {
                    Divider(color = SlateBorder)
                    Text(
                        text = "Cantieri Assegnati al Tecnico (${cantieriAssegnati.size}):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if (cantieriDisponibili.isEmpty()) {
                        Text(
                            text = "Seleziona prima un cliente nella tab Cantieri o in Operatività per visualizzare i cantieri assegnabili.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colors.secondary
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                            items(cantieriDisponibili, key = { it.id }) { cantiere ->
                                val isChecked = cantiere.id in cantieriAssegnati
                                val clienteNome = clientiMap[cantiere.clienteId]?.nome ?: ""
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            cantieriAssegnati = if (isChecked) cantieriAssegnati - cantiere.id else cantieriAssegnati + cantiere.id
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = {
                                            cantieriAssegnati = if (isChecked) cantieriAssegnati - cantiere.id else cantieriAssegnati + cantiere.id
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                    )
                                    Text(
                                        text = if (clienteNome.isNotBlank()) "${cantiere.nome} ($clienteNome)" else cantiere.nome,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = Utente(
                        id = initialUtente?.id ?: java.util.UUID.randomUUID().toString(),
                        username = username.trim(),
                        nomeCompleto = nomeCompleto.trim(),
                        ruolo = ruolo,
                        cantieriAssegnatiIds = if (ruolo == RuoloUtente.ADMIN) emptySet() else cantieriAssegnati
                    )
                    onConfirm(result)
                },
                enabled = nomeCompleto.isNotBlank() && username.isNotBlank()
            ) {
                Text("Salva Utente")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
