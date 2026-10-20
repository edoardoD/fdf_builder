package manutenzioni.app.cli

import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.app.service.HtmlService
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo
import manutenzioni.domain.service.CrossPlantAttivitaResolver
import java.io.File
import java.util.Scanner

/**
 * Entrypoint CLI interattivo e non-interattivo per generare e ispezionare da terminale
 * il codice HTML della scheda PDF, utilizzando il backend reale (Repository, Resolver, HtmlService).
 */
object GenerateHtmlCli {

    data class CliOptions(
        val cantiereQuery: String? = null,
        val impiantoQuery: String? = null,
        val frequenzaQuery: String? = null,
        val clienteNome: String? = null,
        val cantiereNome: String? = null,
        val dataIntervento: String? = null,
        val nomeManutentore: String? = null,
        val outputPath: String? = null
    )

    fun parseArgs(args: Array<String>): CliOptions {
        var cantiere: String? = null
        var impianto: String? = null
        var frequenza: String? = null
        var cliente: String? = null
        var sito: String? = null
        var data: String? = null
        var manutentore: String? = null
        var output: String? = null

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--cantiere", "-c" -> if (i + 1 < args.size) cantiere = args[++i]
                "--impianto", "-i" -> if (i + 1 < args.size) impianto = args[++i]
                "--frequenza", "-f" -> if (i + 1 < args.size) frequenza = args[++i]
                "--cliente" -> if (i + 1 < args.size) cliente = args[++i]
                "--sito" -> if (i + 1 < args.size) sito = args[++i]
                "--data", "-d" -> if (i + 1 < args.size) data = args[++i]
                "--manutentore", "-m" -> if (i + 1 < args.size) manutentore = args[++i]
                "--output", "-o" -> if (i + 1 < args.size) output = args[++i]
            }
            i++
        }
        return CliOptions(
            cantiereQuery = cantiere,
            impiantoQuery = impianto,
            frequenzaQuery = frequenza,
            clienteNome = cliente,
            cantiereNome = sito,
            dataIntervento = data,
            nomeManutentore = manutentore,
            outputPath = output
        )
    }

    fun execute(
        args: Array<String> = emptyArray(),
        repository: ManutenzioneRepository = JsonManutenzioneRepository(),
        htmlService: HtmlService = HtmlService(),
        inputScanner: Scanner = Scanner(System.`in`)
    ): String = runBlocking {
        val options = parseArgs(args)
        val tuttiImpianti = repository.caricaImpianti()
        val clienti = repository.caricaClienti()
        val tuttiCantieri = clienti.flatMap { cli ->
            repository.getCantieriForCliente(cli.id).map { cant -> cant to cli }
        }

        val isNonInteractive = options.impiantoQuery != null

        // 1. Selezione Cantiere (o Template Globali se nessun cantiere presente/scelto)
        var selectedCantiere: Cantiere? = null
        var selectedCliente: Cliente? = null

        if (options.cantiereQuery != null) {
            val match = tuttiCantieri.firstOrNull { (cant, _) ->
                cant.id.equals(options.cantiereQuery, ignoreCase = true) ||
                    cant.nome.contains(options.cantiereQuery, ignoreCase = true)
            }
            if (match != null) {
                selectedCantiere = match.first
                selectedCliente = match.second
            }
        } else if (!isNonInteractive) {
            System.err.println("=== MANUTENZIONI MAKER — GENERATORE HTML DA TERMINALE ===")
            if (tuttiCantieri.isNotEmpty()) {
                System.err.println("Seleziona Cantiere:")
                tuttiCantieri.forEachIndexed { idx, (cant, cli) ->
                    System.err.println("  [${idx + 1}] ${cant.nome} (Cliente: ${cli.nome})")
                }
                System.err.println("  [${tuttiCantieri.size + 1}] [Catalogo Template Globali]")
                System.err.print("Scelta [1]: ")
                val line = if (inputScanner.hasNextLine()) inputScanner.nextLine().trim() else ""
                val choice = line.toIntOrNull() ?: 1
                if (choice in 1..tuttiCantieri.size) {
                    val pair = tuttiCantieri[choice - 1]
                    selectedCantiere = pair.first
                    selectedCliente = pair.second
                }
            } else {
                System.err.println("Nessun cantiere configurato: utilizzo [Catalogo Template Globali].")
                System.err.println("  [1] Catalogo Template Globali")
                System.err.print("Scelta [1]: ")
                if (inputScanner.hasNextLine()) inputScanner.nextLine()
            }
        }

        // 2. Lista impianti disponibili per il contesto scelto
        val impiantiContesto: List<Impianto> = if (selectedCantiere != null) {
            val delCantiere = repository.getImpiantiForCantiere(selectedCantiere.id)
            if (delCantiere.isNotEmpty()) delCantiere else tuttiImpianti.filter { it.cantiereId == null }
        } else {
            val globali = tuttiImpianti.filter { it.cantiereId == null }
            if (globali.isNotEmpty()) globali else tuttiImpianti
        }

        check(impiantiContesto.isNotEmpty()) { "Nessun impianto trovato nel database." }

        // 3. Selezione Impianto
        val selectedImpianto: Impianto = if (options.impiantoQuery != null) {
            impiantiContesto.firstOrNull {
                it.codIntervento.equals(options.impiantoQuery, ignoreCase = true) ||
                    it.id.equals(options.impiantoQuery, ignoreCase = true) ||
                    it.nomeCompleto.contains(options.impiantoQuery, ignoreCase = true)
            } ?: impiantiContesto.first()
        } else {
            System.err.println("\nSeleziona Impianto:")
            impiantiContesto.forEachIndexed { idx, imp ->
                System.err.println("  [${idx + 1}] ${imp.codIntervento} — ${imp.nomeCompleto} (${imp.listaAttivita.size} attività)")
            }
            System.err.print("Scelta [1]: ")
            val line = if (inputScanner.hasNextLine()) inputScanner.nextLine().trim() else ""
            val choice = (line.toIntOrNull() ?: 1).coerceIn(1, impiantiContesto.size)
            impiantiContesto[choice - 1]
        }

        // 4. Selezione Frequenza
        val frequenzeDisp = CrossPlantAttivitaResolver.resolveFrequenze(selectedImpianto, impiantiContesto)
            .ifEmpty { listOf(Periodo(TipoPeriodo.A, 1)) }

        val selectedFrequenza: Periodo = if (options.frequenzaQuery != null) {
            parsePeriodoQuery(options.frequenzaQuery, frequenzeDisp)
        } else if (!isNonInteractive) {
            System.err.println("\nSeleziona Frequenza per ${selectedImpianto.codIntervento}:")
            frequenzeDisp.forEachIndexed { idx, per ->
                System.err.println("  [${idx + 1}] ${per.label()} [cartiglio: ${per.codiceCartiglio()}]")
            }
            System.err.print("Scelta [${frequenzeDisp.size}]: ")
            val line = if (inputScanner.hasNextLine()) inputScanner.nextLine().trim() else ""
            val choice = (line.toIntOrNull() ?: frequenzeDisp.size).coerceIn(1, frequenzeDisp.size)
            frequenzeDisp[choice - 1]
        } else {
            frequenzeDisp.last()
        }

        // 5. Dati opzionali (Data intervento e Manutentore)
        val defaultData = options.dataIntervento
            ?: selectedCantiere?.effectiveDataIntervento
            ?: ""
        val finalDataIntervento = if (!isNonInteractive && options.dataIntervento == null) {
            System.err.print("\nData intervento [$defaultData]: ")
            val line = if (inputScanner.hasNextLine()) inputScanner.nextLine().trim() else ""
            line.ifBlank { defaultData }
        } else {
            defaultData
        }

        val defaultManutentore = options.nomeManutentore
            ?: selectedCantiere?.nomeManutentore
            ?: ""
        val finalManutentore = if (!isNonInteractive && options.nomeManutentore == null) {
            System.err.print("Nome Verificatore/Manutentore [$defaultManutentore]: ")
            val line = if (inputScanner.hasNextLine()) inputScanner.nextLine().trim() else ""
            line.ifBlank { defaultManutentore }
        } else {
            defaultManutentore
        }

        // 6. Risoluzione attività e generazione HTML tramite HtmlService
        val attivitaRisolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = selectedImpianto,
            impiantiNelCantiere = impiantiContesto,
            frequenza = selectedFrequenza
        )

        val clienteLabel = options.clienteNome ?: selectedCliente?.nome
        val cantiereLabel = options.cantiereNome ?: selectedCantiere?.nome

        val generatedHtml = htmlService.buildHtml(
            impianto = selectedImpianto,
            attivitaFiltrate = attivitaRisolte,
            frequenza = selectedFrequenza,
            clienteNome = clienteLabel,
            cantiereNome = cantiereLabel,
            dataInizioIntervento = finalDataIntervento.ifBlank { null },
            nomeManutentore = finalManutentore.ifBlank { null },
            firmaManutentore = selectedCantiere?.firmaManutentore,
            dataInizioContratto = selectedCantiere?.dataInizioContratto,
            dataIntervento = finalDataIntervento.ifBlank { null },
            dataInterventoPrecedente = selectedCantiere?.dataInterventoPrecedente
        )

        // 7. Salvataggio su file build/output_anteprima.html
        val targetFiles = mutableListOf<File>()
        if (options.outputPath != null) {
            targetFiles.add(File(options.outputPath))
        } else {
            val cwd = File(System.getProperty("user.dir"))
            targetFiles.add(File(cwd, "build/output_anteprima.html"))
            if (cwd.name == "desktopApp" && cwd.parentFile != null) {
                targetFiles.add(File(cwd.parentFile, "build/output_anteprima.html"))
            } else if (File(cwd, "desktopApp").isDirectory) {
                targetFiles.add(File(cwd, "desktopApp/build/output_anteprima.html"))
            }
        }

        targetFiles.forEach { outFile ->
            outFile.parentFile?.mkdirs()
            outFile.writeText(generatedHtml, Charsets.UTF_8)
        }

        val primaryOut = targetFiles.first()
        System.err.println("\n✓ HTML generato con successo in: ${primaryOut.absolutePath}")
        println(generatedHtml)

        generatedHtml
    }

    private fun parsePeriodoQuery(query: String, disponibili: List<Periodo>): Periodo {
        val q = query.trim().uppercase()
        disponibili.firstOrNull {
            it.codiceCartiglio().equals(q, ignoreCase = true) ||
                it.label().equals(q, ignoreCase = true) ||
                "${it.tipo}${it.valore}".equals(q, ignoreCase = true) ||
                "${it.valore}${it.tipo}".equals(q, ignoreCase = true)
        }?.let { return it }

        if (q.startsWith("M") && q.drop(1).toIntOrNull() != null) {
            return Periodo(TipoPeriodo.M, q.drop(1).toInt())
        }
        if (q.startsWith("A") && q.drop(1).toIntOrNull() != null) {
            return Periodo(TipoPeriodo.A, q.drop(1).toInt())
        }
        if (q.length == 2 && q.startsWith("0") && q.toIntOrNull() != null) {
            return Periodo(TipoPeriodo.M, q.toInt())
        }
        if (q.toIntOrNull() != null) {
            return Periodo(TipoPeriodo.A, q.toInt())
        }
        return disponibili.last()
    }
}

fun main(args: Array<String>) {
    GenerateHtmlCli.execute(args)
}
