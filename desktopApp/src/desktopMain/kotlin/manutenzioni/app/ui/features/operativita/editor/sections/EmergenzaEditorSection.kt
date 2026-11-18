package manutenzioni.app.ui.features.operativita.editor.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.components.SiglaCloneHelper
import manutenzioni.app.ui.components.TechnicalAttachmentSection
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.LampadaEmergenza
import java.util.UUID

@Composable
fun EmergenzaEditorSection(
    currentEmergenza: ImpiantoEmergenza,
    lampade: List<LampadaEmergenza>,
    onLampadeChange: (List<LampadaEmergenza>) -> Unit,
    codIntervento: String,
    onOpenAddDialog: () -> Unit,
    onEditLampada: (LampadaEmergenza) -> Unit,
    onAggiungiLampadeAEmergenza: ((ImpiantoEmergenza, List<LampadaEmergenza>) -> Unit)?,
    onAggiungiLampadaAEmergenza: ((ImpiantoEmergenza, LampadaEmergenza) -> Unit)?,
    onExportStandalonePdf: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isSicurezza = codIntervento.equals("IS", ignoreCase = true)
    val attachmentTitle = if (isSicurezza) {
        "Registro Lampade di Sicurezza (UNI 11222)"
    } else {
        "Registro Lampade di Emergenza (UNI 11222)"
    }

    TechnicalAttachmentSection(
        title = attachmentTitle,
        subtitle = "Apparecchi di illuminazione installati per verifiche e prove di autonomia (Foglio 2)",
        items = lampade,
        itemTitle = { lampada ->
            listOfNotNull(lampada.produttore, lampada.modello).joinToString(" ").ifBlank { lampada.sigla }
        },
        itemSubtitle = { lampada ->
            if (!lampada.posizione.isNullOrBlank()) "📍 ${lampada.posizione}" else ""
        },
        badgeText = { lampada ->
            lampada.sigla.ifBlank { "—" }
        },
        extraBadges = { lampada ->
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = "⏱ ${lampada.autonomia}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (!lampada.dataUltimoCambioBatteria.isNullOrBlank()) {
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = "🔋 ${lampada.dataUltimoCambioBatteria}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        },
        onAddItem = onOpenAddDialog,
        onEditItem = onEditLampada,
        onCloneItem = { lampada ->
            val nextSigla = SiglaCloneHelper.findNextSiglaForClone(
                baseSigla = lampada.sigla,
                existingSigle = lampade.map { it.sigla },
                defaultPrefix = codIntervento
            )
            val duplicata = lampada.copy(
                id = UUID.randomUUID().toString(),
                sigla = nextSigla
            )
            val nuovaLista = lampade + duplicata
            onLampadeChange(nuovaLista)
            if (onAggiungiLampadeAEmergenza != null) {
                onAggiungiLampadeAEmergenza.invoke(
                    currentEmergenza.copy(listaLampade = nuovaLista),
                    listOf(duplicata)
                )
            } else {
                onAggiungiLampadaAEmergenza?.invoke(
                    currentEmergenza.copy(listaLampade = nuovaLista),
                    duplicata
                )
            }
        },
        onDeleteItem = { lampada ->
            onLampadeChange(lampade - lampada)
        },
        onClearAll = { onLampadeChange(emptyList()) },
        onExportStandalonePdf = onExportStandalonePdf,
        addButtonLabel = "Aggiungi Lampade",
        emptyMessage = "Nessuna lampada registrata. Clicca 'Aggiungi Lampade' per iniziare.",
        modifier = modifier
    )
}
