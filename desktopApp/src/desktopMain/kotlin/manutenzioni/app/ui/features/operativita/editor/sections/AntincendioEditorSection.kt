package manutenzioni.app.ui.features.operativita.editor.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
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
import manutenzioni.domain.model.DispositivoRilevazione
import java.util.UUID

@Composable
fun AntincendioEditorSection(
    dispositivi: List<DispositivoRilevazione>,
    onDispositiviChange: (List<DispositivoRilevazione>) -> Unit,
    onOpenAddDialog: () -> Unit,
    onEditDispositivo: (DispositivoRilevazione) -> Unit,
    onExportStandalonePdf: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ordinati = dispositivi.sortedWith(
        compareBy<DispositivoRilevazione>(
            {
                when (it) {
                    is DispositivoRilevazione.ComponenteComune -> 0
                    is DispositivoRilevazione.RivelatoreLoop -> it.loop * 10 + 1
                    is DispositivoRilevazione.ModuloLoop -> it.loop * 10 + 2
                }
            },
            {
                when (it) {
                    is DispositivoRilevazione.ComponenteComune -> 0
                    is DispositivoRilevazione.RivelatoreLoop -> it.numeroIndirizzo
                    is DispositivoRilevazione.ModuloLoop -> it.numeroIndirizzo
                }
            },
            { it.indirizzo }
        )
    )

    TechnicalAttachmentSection(
        title = "ELENCO COMPONENTI IMPIANTO RILEVAZIONE INCENDI",
        subtitle = "Componenti comuni e dispositivi su Loop 1..8 (Rivelatori e Moduli, ind. 1..150) — Foglio 2+",
        items = ordinati,
        itemTitle = { disp ->
            disp.descrizione.ifBlank { "Dispositivo ${disp.indirizzo}" }
        },
        itemSubtitle = { disp ->
            val parts = listOfNotNull(
                disp.installazione?.let { "Installazione: $it" },
                disp.zona?.let { "Zona: $it" },
                disp.dataInstallazione?.let { "Data: $it" }
            )
            parts.joinToString(" • ")
        },
        badgeText = { disp -> disp.indirizzo },
        extraBadges = { disp ->
            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = disp.labelSezione,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            val famigliaLabel = when (disp) {
                is DispositivoRilevazione.ComponenteComune -> "COMUNE"
                is DispositivoRilevazione.RivelatoreLoop -> "RIVELATORE"
                is DispositivoRilevazione.ModuloLoop -> "MODULO"
            }
            Surface(
                color = when (disp) {
                    is DispositivoRilevazione.ComponenteComune -> Color(0xFFEDE9FE)
                    is DispositivoRilevazione.RivelatoreLoop -> Color(0xFFE0F2FE)
                    is DispositivoRilevazione.ModuloLoop -> Color(0xFFFEF3C7)
                },
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = famigliaLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (disp) {
                        is DispositivoRilevazione.ComponenteComune -> Color(0xFF5B21B6)
                        is DispositivoRilevazione.RivelatoreLoop -> Color(0xFF0369A1)
                        is DispositivoRilevazione.ModuloLoop -> Color(0xFFB45309)
                    },
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = disp.labelTipo,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        },
        onAddItem = onOpenAddDialog,
        onEditItem = onEditDispositivo,
        onCloneItem = { disp ->
            val duplicato: DispositivoRilevazione? = when (disp) {
                is DispositivoRilevazione.ComponenteComune -> {
                    val existingInd = dispositivi
                        .filterIsInstance<DispositivoRilevazione.ComponenteComune>()
                        .map { it.indirizzo }
                    val nextInd = SiglaCloneHelper.findNextSiglaForClone(
                        baseSigla = disp.indirizzo,
                        existingSigle = existingInd,
                        defaultPrefix = "COM."
                    )
                    disp.copy(id = UUID.randomUUID().toString(), indirizzo = nextInd)
                }

                is DispositivoRilevazione.RivelatoreLoop -> {
                    val usati = dispositivi
                        .filterIsInstance<DispositivoRilevazione.RivelatoreLoop>()
                        .filter { it.loop == disp.loop }
                        .map { it.numeroIndirizzo }
                        .toSet()
                    val nextAddr = ((disp.numeroIndirizzo + 1)..150).firstOrNull { it !in usati }
                        ?: (1..150).firstOrNull { it !in usati }
                    nextAddr?.let {
                        disp.copy(id = UUID.randomUUID().toString(), numeroIndirizzo = it)
                    }
                }

                is DispositivoRilevazione.ModuloLoop -> {
                    val usati = dispositivi
                        .filterIsInstance<DispositivoRilevazione.ModuloLoop>()
                        .filter { it.loop == disp.loop }
                        .map { it.numeroIndirizzo }
                        .toSet()
                    val nextAddr = ((disp.numeroIndirizzo + 1)..150).firstOrNull { it !in usati }
                        ?: (1..150).firstOrNull { it !in usati }
                    nextAddr?.let {
                        disp.copy(id = UUID.randomUUID().toString(), numeroIndirizzo = it)
                    }
                }
            }
            if (duplicato != null) {
                onDispositiviChange(dispositivi + duplicato)
            }
        },
        onDeleteItem = { disp ->
            onDispositiviChange(dispositivi.filterNot { it.id == disp.id })
        },
        onClearAll = { onDispositiviChange(emptyList()) },
        onExportStandalonePdf = onExportStandalonePdf,
        addButtonLabel = "Aggiungi al Loop",
        emptyMessage = "Nessun componente registrato. Clicca 'Aggiungi al Loop' per inserire Componenti Comuni o dispositivi su Loop 1..8.",
        modifier = modifier
    )
}
