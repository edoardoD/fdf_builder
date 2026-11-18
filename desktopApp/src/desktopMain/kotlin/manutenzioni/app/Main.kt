package manutenzioni.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import manutenzioni.app.ui.App
import manutenzioni.app.ui.ManutenzioniViewModel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import manutenzioni.app.data.AppConfigRepository
import manutenzioni.app.ui.features.config.ConnectionScreen

import kotlinx.coroutines.withTimeout

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
    logFile.writeText("CRASH RILEVATO ALL'AVVIO\n\n${sw.toString()}")
}

fun startApp() = application {
    var repository by remember { mutableStateOf<manutenzioni.app.data.MongoManutenzioneRepository?>(null) }
    var config by remember { mutableStateOf(AppConfigRepository.loadConfig()) }
    var isTryingToConnect by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        val currentConfig = config
        if (currentConfig != null && repository == null) {
            isTryingToConnect = true
            try {
                withTimeout(5000L) { // Timeout di 5 secondi per il tentativo automatico
                    val connectionString = AppConfigRepository.buildConnectionString(currentConfig)
                    val repo = withContext(Dispatchers.IO) {
                        manutenzioni.app.data.MongoManutenzioneRepository(connectionString, currentConfig.dbName)
                    }
                    withContext(Dispatchers.IO) {
                        repo.caricaClienti() // simple ping test
                    }
                    repository = repo
                }
            } catch (e: Exception) {
                e.printStackTrace()
                config = null
            } finally {
                isTryingToConnect = false
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Manutenzioni Maker",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        manutenzioni.app.ui.theme.ManutenzioniTheme {
            val currentRepo = repository
            if (isTryingToConnect) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Connessione al database in corso...")
                        }
                    }
                }
            } else if (currentRepo == null) {
                ConnectionScreen(
                    initialConfig = config,
                    onConnected = { repo, newConfig ->
                        config = newConfig
                        repository = repo
                    }
                )
            } else {
                val viewModel = remember(currentRepo) { ManutenzioniViewModel(currentRepo) }
                App(viewModel) {
                    repository = null
                    config = null
                }
            }
        }
    }
}
