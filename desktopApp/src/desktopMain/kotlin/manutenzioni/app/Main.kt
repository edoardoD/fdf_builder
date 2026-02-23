package manutenzioni.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.app.ui.App
import manutenzioni.app.ui.ManutenzioniViewModel

fun main() = application {
    val repository = JsonManutenzioneRepository()
    val viewModel = ManutenzioniViewModel(repository)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Manutenzioni Maker",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        App(viewModel)
    }
}
