package manutenzioni.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import manutenzioni.app.service.Pdf

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "FDF Builder") {
        var status by remember { mutableStateOf("Pronto per generare PDF") }
        val pdf = Pdf()

        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = status)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    try {
                        val home = System.getProperty("user.home")
                        // Ensure the path exists or use a safer one
                        val output = "output.pdf"
                        val path = "scheletro"
                        pdf.buildPdf(path,output)
                        status = "PDF generato in: $output"
                    } catch (e: Exception) {
                        status = "Errore: ${e.message}"
                        e.printStackTrace()
                    }
                }) {
                    Text("Genera PDF")
                }
            }
        }
    }
}
