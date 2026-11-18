package manutenzioni.app.data

import manutenzioni.app.data.dao.*
import manutenzioni.app.data.storage.JsonDatabaseStorage
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import java.io.File

/**
 * Facade ad alto livello che implementa ManutenzioneRepository delegando
 * alle DAO specializzate basate su JsonDatabaseStorage thread-safe.
 */
class JsonManutenzioneRepository(
    val storage: JsonDatabaseStorage,
    private val clienteDao: JsonClienteDao = JsonClienteDao(storage),
    private val cantiereDao: JsonCantiereDao = JsonCantiereDao(storage),
    private val impiantoDao: JsonImpiantoDao = JsonImpiantoDao(storage),
    private val componentiDao: JsonComponentiDao = JsonComponentiDao(storage),
    private val catalogoApprovatoDao: JsonCatalogoApprovatoDao = JsonCatalogoApprovatoDao(storage),
    private val sessioniVerificaDao: JsonSessioniVerificaDao = JsonSessioniVerificaDao(storage),
    private val utentiDao: JsonUtentiDao = JsonUtentiDao(storage),
    private val eventiManutenzioneDao: JsonEventiManutenzioneDao = JsonEventiManutenzioneDao(storage)
) : ManutenzioneRepository {

    /** Costruttore per compatibilità con nome file (default "manutenzioni_db.json") */
    constructor(fileName: String = "manutenzioni_db.json") : this(JsonDatabaseStorage(fileName))

    /** Costruttore per compatibilità con file personalizzato */
    constructor(dbFile: File) : this(JsonDatabaseStorage(dbFile))

    // === Impianti CRUD ===
    override suspend fun salvaImpianto(impianto: Impianto) = impiantoDao.salvaImpianto(impianto)
    override suspend fun caricaImpianti(): List<Impianto> = impiantoDao.caricaImpianti()
    override suspend fun eliminaImpianto(id: String) = impiantoDao.eliminaImpianto(id)
    override suspend fun getImpianto(codIntervento: String): Impianto? = impiantoDao.getImpianto(codIntervento)
    override suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto) = impiantoDao.aggiornaImpiantiGlobalmente(impiantoTemplate)

    // === Clienti CRUD ===
    override suspend fun caricaClienti(): List<Cliente> = clienteDao.caricaClienti()
    override suspend fun salvaCliente(cliente: Cliente) = clienteDao.salvaCliente(cliente)
    override suspend fun updateCliente(id: String, newName: String) = clienteDao.updateCliente(id, newName)
    override suspend fun eliminaCliente(id: String) = clienteDao.eliminaCliente(id)

    // === Cantieri CRUD ===
    override suspend fun caricaCantieri(): List<Cantiere> = cantiereDao.caricaCantieri()
    override suspend fun getCantieriForCliente(clienteId: String): List<Cantiere> = cantiereDao.getCantieriForCliente(clienteId)
    override suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> = impiantoDao.getImpiantiForCantiere(cantiereId)
    override suspend fun salvaCantiere(cantiere: Cantiere) = cantiereDao.salvaCantiere(cantiere)
    override suspend fun updateCantiere(id: String, newName: String) = cantiereDao.updateCantiere(id, newName)
    override suspend fun eliminaCantiere(id: String) = cantiereDao.eliminaCantiere(id)

    // === Componenti Standard ===
    override suspend fun caricaComponentiStandard(): List<ComponenteStandard> = componentiDao.caricaComponentiStandard()
    override suspend fun salvaComponenteStandard(componente: ComponenteStandard) = componentiDao.salvaComponenteStandard(componente)

    // === Catalogo Approvato (ETIM) ===
    override suspend fun caricaCatalogoApprovato(): List<ComponenteApprovato> = catalogoApprovatoDao.caricaCatalogoApprovato()
    override suspend fun salvaOAggiornaComponenteApprovato(approvato: ComponenteApprovato) = catalogoApprovatoDao.salvaOAggiornaComponenteApprovato(approvato)
    override suspend fun trovaEquivalentiApprovati(etimClassId: String): List<ComponenteApprovato> = catalogoApprovatoDao.trovaEquivalentiApprovati(etimClassId)

    // === Storicizzazione Sessioni di Verifica (Wave 4) ===
    override suspend fun caricaSessioniVerifica(): List<SessioneVerifica> = sessioniVerificaDao.caricaSessioniVerifica()
    override suspend fun salvaSessioneVerifica(sessione: SessioneVerifica) = sessioniVerificaDao.salvaSessioneVerifica(sessione)
    override suspend fun getSessioniPerCantiere(cantiereId: String): List<SessioneVerifica> = sessioniVerificaDao.getSessioniPerCantiere(cantiereId)
    override suspend fun eliminaSessioneVerifica(id: String) = sessioniVerificaDao.eliminaSessioneVerifica(id)

    // === Gestione Utenti & RBAC (Wave 4) ===
    override suspend fun caricaUtenti(): List<Utente> = utentiDao.caricaUtenti()
    override suspend fun salvaUtente(utente: Utente) = utentiDao.salvaUtente(utente)
    override suspend fun eliminaUtente(id: String) = utentiDao.eliminaUtente(id)

    // === Eventi di Manutenzione & Scheduler Ricorrenze ===
    override suspend fun salvaEventoManutenzione(evento: EventoManutenzione) = eventiManutenzioneDao.salvaEventoManutenzione(evento)
    override suspend fun salvaEventiManutenzioneBatch(eventi: List<EventoManutenzione>) = eventiManutenzioneDao.salvaEventiManutenzioneBatch(eventi)
    override suspend fun caricaEventiManutenzione(): List<EventoManutenzione> = eventiManutenzioneDao.caricaEventiManutenzione()
    override suspend fun caricaEventiPerMese(annoMese: String): List<EventoManutenzione> = eventiManutenzioneDao.caricaEventiPerMese(annoMese)
    override suspend fun caricaEventiPerCantiere(cantiereId: String): List<EventoManutenzione> = eventiManutenzioneDao.caricaEventiPerCantiere(cantiereId)
    override suspend fun eliminaEventoManutenzione(id: String, eliminaSerieFutura: Boolean) = eventiManutenzioneDao.eliminaEventoManutenzione(id, eliminaSerieFutura)
    override suspend fun aggiornaStatoEvento(id: String, nuovoStato: StatoEventoManutenzione, dataCompletamento: String?) =
        eventiManutenzioneDao.aggiornaStatoEvento(id, nuovoStato, dataCompletamento)
}
