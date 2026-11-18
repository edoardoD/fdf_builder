package manutenzioni.app.ui.features.operativita.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import manutenzioni.app.ui.theme.SlateBorder
import java.io.File

@Composable
fun CantiereActionBar(
    isEnabled: Boolean,
    isLoading: Boolean,
    batchProgress: String? = null,
    pdfFile: File? = null,
    onGeneraPdf: () -> Unit,
    onOpenPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = 0.dp,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "2. Generazione Schede di Verifica",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGeneraPdf,
                enabled = isEnabled && !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isLoading) "Generazione in corso..." else "Genera Schede di Verifica",
                    fontWeight = FontWeight.Bold
                )
            }

            if (batchProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = batchProgress,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }

            if (pdfFile != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onOpenPdf,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apri PDF Creato")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Percorso: ${pdfFile.name}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }
        }
    }
}
