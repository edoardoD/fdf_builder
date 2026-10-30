package manutenzioni.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.ImpiantoStandard
import manutenzioni.domain.model.MqtSwitchType
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroMQT
import manutenzioni.domain.model.TipoPeriodo
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 1 — Quick-Wins & Anagrafica Master.
 * Copre R2 (update completo cliente), R3 (indirizzo cantiere) e R6 (aggiunta multipla impianti).
 */
class Wave1AnagraficaEMultiSelezioneTest {

    private val testDbFileName = "test_wave1_db.json"
    private lateinit var repo: JsonManutenzioneRepository
    private lateinit var testDbFile: File
    private lateinit var viewModel: ManutenzioniViewModel

    @BeforeTest
    fun setup() {
        val userHome = System.getProperty("user.home")
        testDbFile = File(File(userHome, ".manutenzioni-maker"), testDbFileName)
        if (testDbFile.exists()) testDbFile.delete()
        repo = JsonManutenzioneRepository(testDbFileName)
        viewModel = ManutenzioniViewModel(repository = repo)
    }

    @AfterTest
    fun cleanup() {
        if (testDbFile.exists()) testDbFile.delete()
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        var elapsed = 0L
        while (!condition() && elapsed < timeoutMs) {
            delay(50)
            elapsed += 50
        }
    }

    @Test
    fun `AdminTab IMPIANTI_GLOBALI espone etichetta MASTER SCHEDE DI VERIFICA`() {
        assertEquals("MASTER SCHEDE DI VERIFICA", AdminTab.IMPIANTI_GLOBALI.label)
    }

    @Test
    fun `updateCliente persiste nome indirizzo e partita IVA`() = runBlocking {
        val cliente = Cliente(id = "cli-w1", nome = "ACME")
        repo.salvaCliente(cliente)

        viewModel.updateCliente(cliente.copy(nome = " ACME S.p.A. ", indirizzo = "Via Roma 1", partitaIva = "IT01234567890"))
        waitUntil { viewModel.uiState.value.statusMessage.contains("aggiornato") }

        val salvato = repo.caricaClienti().first { it.id == "cli-w1" }
        assertEquals("ACME S.p.A.", salvato.nome)
        assertEquals("Via Roma 1", salvato.indirizzo)
        assertEquals("IT01234567890", salvato.partitaIva)
    }

    @Test
    fun `updateCantiere aggiorna indirizzo preservando i dati intervento`() = runBlocking {
        val cliente = Cliente(id = "cli-w1b", nome = "Cliente B")
        repo.salvaCliente(cliente)
        val cantiere = Cantiere(
            id = "can-w1", nome = "Sede", clienteId = cliente.id,
            dataInizioIntervento = "01/10/2026", nomeManutentore = "Mario Rossi"
        )
        repo.salvaCantiere(cantiere)
        viewModel.selectCliente(cliente)
        waitUntil { viewModel.uiState.value.cantieriDisponibili.isNotEmpty() }

        viewModel.updateCantiere(cantiere.id, "Sede Centrale", "Via Milano 10, Torino")
        waitUntil { viewModel.uiState.value.cantieriDisponibili.any { it.indirizzo != null } }

        val salvato = repo.getCantieriForCliente(cliente.id).first()
        assertEquals("Sede Centrale", salvato.nome)
        assertEquals("Via Milano 10, Torino", salvato.indirizzo)
        assertEquals("01/10/2026", salvato.dataInizioIntervento)
        assertEquals("Mario Rossi", salvato.nomeManutentore)

        // Indirizzo vuoto viene normalizzato a null
        viewModel.updateCantiere(cantiere.id, "Sede Centrale", "   ")
        waitUntil { viewModel.uiState.value.cantieriDisponibili.all { it.indirizzo == null } }
        assertNull(repo.getCantieriForCliente(cliente.id).first().indirizzo)
    }

    @Test
    fun `aggiunta simultanea di 3 impianti al cantiere senza alterare i template globali`() = runBlocking {
        val cliente = Cliente(id = "cli-w1c", nome = "Cliente C")
        val cantiere = Cantiere(id = "can-w1c", nome = "Stabilimento", clienteId = cliente.id)
        repo.salvaCliente(cliente)
        repo.salvaCantiere(cantiere)

        val attivita = listOf(Attivita(nAttivita = 1, tipoAttivita = "Ispezione", descrizione = "Verifica generale", frequenza = Periodo(TipoPeriodo.A, 1)))
        val tplEm = ImpiantoEmergenza(id = "tpl-em", codIntervento = "EM", nomeCompleto = "Illuminazione Emergenza", premessa = null, listaAttivita = attivita)
        val tplGe = ImpiantoStandard(id = "tpl-ge", codIntervento = "GE", nomeCompleto = "Gruppo Elettrogeno", premessa = null, listaAttivita = attivita)
        val tplQmt = QuadroMQT(id = "tpl-qmt", codIntervento = "QMT", nomeCompleto = "Quadro Media Tensione", premessa = null, listaAttivita = attivita)
        listOf(tplEm, tplGe, tplQmt).forEach { repo.salvaImpianto(it) }

        viewModel.selectCliente(cliente)
        viewModel.selectCantiere(cantiere)
        waitUntil { viewModel.uiState.value.selectedCantiere?.id == cantiere.id }

        viewModel.addImpiantiFromTemplates(listOf(tplEm, tplGe, tplQmt))
        waitUntil { viewModel.uiState.value.impiantiDelCantiere.size == 3 }

        val delCantiere = repo.getImpiantiForCantiere(cantiere.id)
        assertEquals(3, delCantiere.size)
        assertEquals(setOf("EM", "GE", "QMT"), delCantiere.map { it.codIntervento }.toSet())
        delCantiere.forEach { assertEquals(cantiere.id, it.cantiereId) }
        assertTrue(delCantiere.none { it.id in setOf("tpl-em", "tpl-ge", "tpl-qmt") }, "Le istanze devono avere id nuovi")
        assertTrue(delCantiere.any { it is ImpiantoEmergenza }, "Il tipo polimorfico deve essere preservato")
        val qmt = delCantiere.filterIsInstance<QuadroMQT>().firstOrNull()
        assertNotNull(qmt)
        assertEquals(MqtSwitchType.SF6, qmt.tipoInterruttore)

        // I template globali restano intatti (cantiereId == null)
        val globali = repo.caricaImpianti().filter { it.id.startsWith("tpl-") }
        assertEquals(3, globali.size)
        globali.forEach { assertNull(it.cantiereId) }
        assertEquals(3, viewModel.uiState.value.impiantiDelCantiere.size)
    }

    @Test
    fun `instantiateTemplateForCantiere genera copia indipendente`() {
        val tpl = ImpiantoStandard(id = "tpl-x", codIntervento = "X", nomeCompleto = "X", premessa = null, listaAttivita = emptyList(), quantita = 5)
        val a = instantiateTemplateForCantiere(tpl, "can-1")
        val b = instantiateTemplateForCantiere(tpl, "can-1")
        assertNotEquals(tpl.id, a.id)
        assertNotEquals(a.id, b.id)
        assertEquals("can-1", a.cantiereId)
        assertEquals(1, a.quantita)
        assertNull(tpl.cantiereId)
    }
}
