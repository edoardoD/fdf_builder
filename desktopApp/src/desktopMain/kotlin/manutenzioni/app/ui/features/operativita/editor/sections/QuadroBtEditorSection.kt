package manutenzioni.app.ui.features.operativita.editor.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.components.SiglaCloneHelper
import manutenzioni.app.ui.components.TechnicalAttachmentSection
import manutenzioni.domain.model.ComponenteApprovato
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.VarianteProdotto
import java.util.UUID

@Composable
fun QuadroBtEditorSection(
    quadro: QuadroBT,
    interruttori: List<InterruttoreBT>,
    onInterruttoriChange: (List<InterruttoreBT>) -> Unit,
    catalogoApprovato: List<ComponenteApprovato>,
    onOpenAddDialog: () -> Unit,
    onEditInterruttore: (InterruttoreBT) -> Unit,
    onSostituisciProduttore: ((QuadroBT, String, VarianteProdotto) -> Unit)?,
    onAggiungiComponenteAQuadro: ((QuadroBT, InterruttoreBT) -> Unit)?,
    onExportStandalonePdf: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    TechnicalAttachmentSection(
        title = "Interruttori Differenziali e Componenti",
        subtitle = "Componenti associati a questo quadro con verifiche strumentali (Foglio 2)",
        items = interruttori,
        itemTitle = { interruttore ->
            listOfNotNull(interruttore.produttore, interruttore.codiceArticolo).joinToString(" ").ifBlank { interruttore.nome }
        },
        itemSubtitle = { interruttore ->
            if (interruttore.nome.isNotBlank() && interruttore.nome != interruttore.codiceArticolo) interruttore.nome else ""
        },
        badgeText = { interruttore ->
            interruttore.siglaCircuito?.ifBlank { "${interruttore.quantita}x" } ?: "${interruttore.quantita}x"
        },
        extraBadges = { interruttore ->
            if (interruttore.quantita > 1 && !interruttore.siglaCircuito.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = "${interruttore.quantita}x",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            val taratura = interruttore.caratteristicheTecniche["taratura"]
                ?: interruttore.caratteristicheTecniche["Idn"]
                ?: interruttore.caratteristicheTecniche["IΔn"]
                ?: interruttore.caratteristicheTecniche["taraturaDiff"]
            if (!taratura.isNullOrBlank()) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = "⚡ $taratura",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            if (!interruttore.note.isNullOrBlank()) {
                Text(
                    text = interruttore.note,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        customActions = { interruttore ->
            if (!interruttore.etimClassId.isNullOrBlank()) {
                val approvato = catalogoApprovato.firstOrNull { it.etimClassId == interruttore.etimClassId }
                val alternative = approvato?.variantiProduttore?.values?.filter { it.produttore != interruttore.produttore } ?: emptyList()

                if (alternative.isNotEmpty()) {
                    var menuAlternativeExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { menuAlternativeExpanded = true },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("🔄 Cambia Marca", fontSize = 10.sp)
                        }
                        DropdownMenu(
                            expanded = menuAlternativeExpanded,
                            onDismissRequest = { menuAlternativeExpanded = false }
                        ) {
                            Text(
                                text = "Alternative approvate per ETIM ${interruttore.etimClassId}:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            Divider()
                            alternative.forEach { alt ->
                                DropdownMenuItem(
                                    onClick = {
                                        onSostituisciProduttore?.invoke(quadro, interruttore.id, alt)
                                        val idx = interruttori.indexOfFirst { it.id == interruttore.id }
                                        if (idx >= 0) {
                                            val mList = interruttori.toMutableList()
                                            mList[idx] = interruttore.copy(
                                                produttore = alt.produttore,
                                                codiceArticolo = alt.codice,
                                                note = alt.serie?.let { "Serie: $it" } ?: interruttore.note
                                            )
                                            onInterruttoriChange(mList)
                                        }
                                        menuAlternativeExpanded = false
                                    }
                                ) {
                                    Column {
                                        Text("${alt.produttore} — ${alt.codice}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        if (alt.prezzoListino != null) {
                                            Text("Listino indicativo: ${alt.prezzoListino} €", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
        },
        onAddItem = onOpenAddDialog,
        onEditItem = onEditInterruttore,
        onCloneItem = { interruttore ->
            val existingSigle = interruttori.mapNotNull { it.siglaCircuito }
            val nextSigla = SiglaCloneHelper.findNextSiglaForClone(
                baseSigla = interruttore.siglaCircuito ?: "QA1",
                existingSigle = existingSigle,
                defaultPrefix = "QA"
            )
            val duplicato = interruttore.copy(
                id = UUID.randomUUID().toString(),
                siglaCircuito = nextSigla
            )
            val nuovaLista = interruttori + duplicato
            onInterruttoriChange(nuovaLista)
            onAggiungiComponenteAQuadro?.invoke(quadro.copy(listaInterruttori = nuovaLista), duplicato)
        },
        onDeleteItem = { interruttore ->
            onInterruttoriChange(interruttori - interruttore)
        },
        onClearAll = { onInterruttoriChange(emptyList()) },
        onExportStandalonePdf = onExportStandalonePdf,
        addButtonLabel = "Aggiungi Componente",
        emptyMessage = "Nessun componente registrato per questo quadro. Clicca su 'Aggiungi Componente' per iniziare.",
        modifier = modifier
    )
}
