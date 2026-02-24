package manutenzioni.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.app.ui.App
import manutenzioni.app.ui.ManutenzioniViewModel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

fun main() {
    try {
        startApp()
    } catch (e: Throwable) {
        saveCrashLog(e)
        throw e
    }
}

private fun saveCrashLog(e: Throwable) {
    val sw = StringWriter()
    e.printStackTrace(PrintWriter(sw))
    val userHome = System.getProperty("user.home")
    val desktop = File(userHome, "Desktop")
    val logFile = File(desktop, "manutenzioni_maker_crash.log")
    logFile.writeText("CRASH RILEVATO ALL'AVVIO\n\n\${sw.toString()}")
}

fun startApp() = application {
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
