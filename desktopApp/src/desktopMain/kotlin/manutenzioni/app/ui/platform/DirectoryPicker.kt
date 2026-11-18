package manutenzioni.app.ui.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

interface DirectoryPicker {
    fun pickDirectory(title: String = "Seleziona o crea la cartella di destinazione"): File?
    fun pickFile(title: String, allowedExtensions: List<String> = emptyList()): File? = null
}

class AwtDirectoryPicker : DirectoryPicker {
    override fun pickDirectory(title: String): File? {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("mac")) {
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
                try {
                    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
                    dialog.isVisible = true
                    val dir = dialog.directory ?: return null
                    val file = dialog.file ?: return null
                    File(dir, file).also { if (!it.exists()) it.mkdirs() }
                } finally {
                    System.setProperty("apple.awt.fileDialogForDirectories", "false")
                }
            } else {
                val chooser = JFileChooser().apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    approveButtonText = "Salva qui"
                }
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
                chooser.selectedFile?.also { if (!it.exists()) it.mkdirs() }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun pickFile(title: String, allowedExtensions: List<String>): File? {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("mac")) {
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
                if (allowedExtensions.isNotEmpty()) {
                    dialog.setFilenameFilter { _, name ->
                        val lower = name.lowercase()
                        allowedExtensions.any { ext -> lower.endsWith(".${ext.lowercase()}") }
                    }
                }
                dialog.isVisible = true
                val dir = dialog.directory ?: return null
                val file = dialog.file ?: return null
                File(dir, file)
            } else {
                val chooser = JFileChooser().apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.FILES_ONLY
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
