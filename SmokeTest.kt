import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.app.ui.ManutenzioniViewModel
import java.io.File
fun main() {
    println("Smoke Test: Inizializzazione Repository e ViewModel...")
    try {
        // Usa un file locale temporaneo per il test
        val repo = JsonManutenzioneRepository("test_db.json")
        val vm = ManutenzioniViewModel(repo)
        println("UiState iniziale: ${vm.uiState.value}")
        println("✓ Inizializzazione completata con successo.")
        // Pulizia
        File("test_db.json").delete()
    } catch (e: Exception) {
        println("❌ CRASH RILEVATO: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}
