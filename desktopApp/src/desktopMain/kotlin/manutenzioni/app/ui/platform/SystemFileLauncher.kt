package manutenzioni.app.ui.platform

import java.awt.Desktop
import java.io.File
import java.io.FileNotFoundException

interface SystemFileLauncher {
    fun open(file: File)
}

class AwtSystemFileLauncher : SystemFileLauncher {
    override fun open(file: File) {
        if (!file.exists()) {
            throw FileNotFoundException("File non trovato: ${file.absolutePath}")
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        } else {
            val os = System.getProperty("os.name", "").lowercase()
            val cmd = when {
                os.contains("mac") -> arrayOf("open", file.absolutePath)
                os.contains("win") -> arrayOf("explorer", file.absolutePath)
                else -> arrayOf("xdg-open", file.absolutePath)
            }
            ProcessBuilder(*cmd).start()
        }
    }
}
