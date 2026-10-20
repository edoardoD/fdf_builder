package manutenzioni.app.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.AppSection

@Composable
fun MainScaffold(
    currentSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    isAdmin: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 1.dp
            ) {
                NavigationRailItem(
                    selected = currentSection == AppSection.OPERATIVITA,
                    onClick = { onSectionSelected(AppSection.OPERATIVITA) },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Operatività") },
                    label = { Text("Operatività") },
                    selectedContentColor = MaterialTheme.colors.primary,
                    unselectedContentColor = MaterialTheme.colors.secondary
                )
                if (isAdmin) {
                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Database") },
                        label = { Text("Database", fontSize = 10.sp) },
                        selected = currentSection == AppSection.DATABASE,
                        onClick = { onSectionSelected(AppSection.DATABASE) },
                        selectedContentColor = MaterialTheme.colors.primary,
                        unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
        }
    }
}
