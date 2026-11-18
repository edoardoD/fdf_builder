package manutenzioni.app.ui.features.operativita.editor.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.MqtSwitchType

@Composable
fun QuadroMqtEditorSection(
    tipoInterruttore: MqtSwitchType,
    onTipoInterruttoreChange: (MqtSwitchType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Tipologia Interruttore Media Tensione (MT)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colors.primary
            )
            Text(
                text = "Seleziona la tecnologia dell'interruttore (SF6, ISV, IVOR). Le attività associate adegueranno automaticamente la colonna Zona/Impianto nel PDF.",
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MqtSwitchType.values().forEach { switchType ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onTipoInterruttoreChange(switchType) }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = tipoInterruttore == switchType,
                            onClick = { onTipoInterruttoreChange(switchType) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = switchType.label,
                            fontWeight = if (tipoInterruttore == switchType) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
