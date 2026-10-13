package manutenzioni.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente Composable unificato per la gestione dei componenti strumentali e registri tecnici (Foglio 2+)
 * (es. Interruttori differenziali QuadroBT, Registro lampade emergenza UNI 11222, Sensori e moduli Antincendio Loop 1..4).
 *
 * Basato sul benchmark UX di `EmergenzaAttachmentProvider`.
 */
@Composable
fun <T> TechnicalAttachmentSection(
    title: String,
    subtitle: String,
    items: List<T>,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String,
    badgeText: (T) -> String,
    modifier: Modifier = Modifier,
    extraBadges: @Composable RowScope.(T) -> Unit = {},
    customActions: @Composable RowScope.(T) -> Unit = {},
    onAddItem: () -> Unit,
    onEditItem: (T) -> Unit,
    onCloneItem: ((T) -> Unit)? = null,
    onDeleteItem: (T) -> Unit,
    onClearAll: (() -> Unit)? = null,
    onExportStandalonePdf: (() -> Unit)? = null,
    addButtonLabel: String = "Aggiungi",
    emptyMessage: String = "Nessun elemento registrato."
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Titolo, Badge Conteggio, e Azioni Principali
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${items.size}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsante Standalone PDF Foglio 2
                    if (onExportStandalonePdf != null && items.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onExportStandalonePdf,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, Color(0xFF2563EB))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Esporta Foglio Tecnico PDF",
                                modifier = Modifier.size(15.dp),
                                tint = Color(0xFF2563EB)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Esporta Foglio 2 PDF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }

                    // Pulsante Svuota Lista
                    if (onClearAll != null && items.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Svuota (${items.size})",
                                color = Color(0xFFDC2626),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Pulsante Aggiungi
                    Button(
                        onClick = onAddItem,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = addButtonLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Contenuto: Elenco Elementi o Stato Vuoto
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyMessage,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditItem(item) },
                            backgroundColor = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Badge Sigla / Identificatore Principale
                                val badge = badgeText(item)
                                if (badge.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colors.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                }

                                // Contenuto Principale: Titolo, Dettagli e Badge Ausiliari
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val titleText = itemTitle(item)
                                        if (titleText.isNotBlank()) {
                                            Text(
                                                text = titleText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        val subtitleText = itemSubtitle(item)
                                        if (subtitleText.isNotBlank()) {
                                            Text(
                                                text = subtitleText,
                                                fontSize = 12.sp,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                    }

                                    // Badge e Metadati Secondari
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        extraBadges(item)
                                    }
                                }

                                // Slot per Azioni Custom (es. Menu Sostituzione Marca ETIM)
                                customActions(item)

                                // Azione: Modifica (Matita)
                                IconButton(
                                    onClick = { onEditItem(item) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Modifica",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colors.primary
                                    )
                                }

                                // Azione: Clona Rapido (Duplica)
                                if (onCloneItem != null) {
                                    Spacer(Modifier.width(2.dp))
                                    IconButton(
                                        onClick = { onCloneItem(item) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Clona con sigla successiva",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF0284C7)
                                        )
                                    }
                                }

                                // Azione: Elimina (Cestino)
                                Spacer(Modifier.width(2.dp))
                                IconButton(
                                    onClick = { onDeleteItem(item) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Rimuovi",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFFDC2626)
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
