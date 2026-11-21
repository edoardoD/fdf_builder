package manutenzioni.app.ui.features.operativita.editor.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.components.SiglaCloneHelper
import manutenzioni.app.ui.components.TechnicalAttachmentSection
import manutenzioni.domain.model.*
import java.util.UUID

@Composable
fun AntincendioEditorSection(
    impianto: RilevazioneAntincendio,
    onImpiantoConfigChange: (RilevazioneAntincendio) -> Unit,
    dispositivi: List<DispositivoRilevazione>,
    onDispositiviChange: (List<DispositivoRilevazione>) -> Unit,
    onOpenAddDialog: () -> Unit,
    onEditDispositivo: (DispositivoRilevazione) -> Unit,
    onExportStandalonePdf: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ordinati = dispositivi.sortedWith(
        compareBy<DispositivoRilevazione>(
            {
                when (it) {
                    is DispositivoRilevazione.ComponenteComune -> 0
                    is DispositivoRilevazione.RivelatoreLoop -> it.loop * 10 + 1
                    is DispositivoRilevazione.ModuloLoop -> it.loop * 10 + 2
                }
            },
            {
                when (it) {
                    is DispositivoRilevazione.ComponenteComune -> 0
                    is DispositivoRilevazione.RivelatoreLoop -> it.numeroIndirizzo
                    is DispositivoRilevazione.ModuloLoop -> it.numeroIndirizzo
                }
            },
            { it.indirizzo }
        )
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // 1. CARD CONFIGURAZIONE IMPIANTO & LIBRETTO UNI 11224:2026 (SOPRA I LOOP)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Configurazione Impianto & Libretto Rilevazione Incendi (UNI 11224:2026)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = "Imposta le caratteristiche tecnologiche storicizzate nel database. Periodicità unica obbligatoria: 06 MESI (Semestrale).",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )
                    }
                    Surface(
                        color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "PERIODICITÀ: 06 MESI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Divider()

                // --- A. Tipologia Impianto Rivelazione Incendi: Convenzionale vs Indirizzato (Esclusivi) + ASD / Radio / EVAC (Coesistenti) ---
                Text(
                    text = "Tipologia Impianto Rivelazione Incendi (Convenzionale/Indirizzato esclusivi • ASD/Radio/EVAC coesistenti)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArchitetturaImpiantoRi.values().forEach { arch ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onImpiantoConfigChange(impianto.copy(architettura = arch)) }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = impianto.architettura == arch,
                                onClick = { onImpiantoConfigChange(impianto.copy(architettura = arch)) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = arch.label,
                                fontWeight = if (impianto.architettura == arch) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Text("|", color = Color.LightGray, fontSize = 16.sp)

                    // Checkbox coesistenti: ASD (Aspirazione), Radio, EVAC
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onImpiantoConfigChange(impianto.copy(hasAsd = !impianto.hasAsd)) }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = impianto.hasAsd,
                            onCheckedChange = { onImpiantoConfigChange(impianto.copy(hasAsd = it)) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "ASD (Aspirazione — include Prospetto A.4 / B.3)",
                            fontWeight = if (impianto.hasAsd) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onImpiantoConfigChange(impianto.copy(hasRadio = !impianto.hasRadio)) }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = impianto.hasRadio,
                            onCheckedChange = { onImpiantoConfigChange(impianto.copy(hasRadio = it)) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Radio",
                            fontWeight = if (impianto.hasRadio) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onImpiantoConfigChange(impianto.copy(hasEvac = !impianto.hasEvac)) }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = impianto.hasEvac,
                            onCheckedChange = { onImpiantoConfigChange(impianto.copy(hasEvac = it)) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "EVAC",
                            fontWeight = if (impianto.hasEvac) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }

                // --- B. Sezione Opzionale Parametri ASD (visibile solo quando ASD è spuntato) ---
                if (impianto.hasAsd) {
                    Surface(
                        color = Color(0xFFF0F7FF),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Parametri Prospetto A.4 / B.3 — Controlli Addizionali su Sistemi ASD (Aspirazione)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colors.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Classe di Sensibilità ASD:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(8.dp))
                                    ClasseSensibilitaAsd.values().forEach { classe ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable {
                                                    val next = if (impianto.classeSensibilitaAsd == classe) null else classe
                                                    onImpiantoConfigChange(impianto.copy(classeSensibilitaAsd = next))
                                                }
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = impianto.classeSensibilitaAsd == classe,
                                                onClick = {
                                                    val next = if (impianto.classeSensibilitaAsd == classe) null else classe
                                                    onImpiantoConfigChange(impianto.copy(classeSensibilitaAsd = next))
                                                }
                                            )
                                            Text(classe.label, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tempo Max Trasporto:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(8.dp))
                                    TempoTrasportoAsd.values().forEach { tempo ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable {
                                                    val next = if (impianto.tempoMaxTrasportoAsd == tempo) null else tempo
                                                    onImpiantoConfigChange(impianto.copy(tempoMaxTrasportoAsd = next))
                                                }
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            RadioButton(
                                                selected = impianto.tempoMaxTrasportoAsd == tempo,
                                                onClick = {
                                                    val next = if (impianto.tempoMaxTrasportoAsd == tempo) null else tempo
                                                    onImpiantoConfigChange(impianto.copy(tempoMaxTrasportoAsd = next))
                                                }
                                            )
                                            Text(tempo.label, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Divider()

                // --- C. Tipo di Libretto / Verbale (Appendice B Periodica vs Appendice A Iniziale/Presa in Carico) ---
                Text(
                    text = "Tipologia Libretto / Verbale UNI 11224:2026 da generare",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TipoVerbaleRi.values().forEach { verbale ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onImpiantoConfigChange(impianto.copy(tipoVerbale = verbale)) }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = impianto.tipoVerbale == verbale,
                                onClick = { onImpiantoConfigChange(impianto.copy(tipoVerbale = verbale)) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = verbale.label,
                                fontWeight = if (impianto.tipoVerbale == verbale) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // --- D. Esecuzione, Estensione Protezione e Responsabile Sistema ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Esecuzione Ambiente (A1 / B1):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TipoEsecuzioneRi.values().forEach { esec ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        onImpiantoConfigChange(impianto.copy(tipoEsecuzione = esec))
                                    }
                                ) {
                                    RadioButton(
                                        selected = impianto.tipoEsecuzione == esec,
                                        onClick = { onImpiantoConfigChange(impianto.copy(tipoEsecuzione = esec)) }
                                    )
                                    Text(esec.label, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Estensione Protezione:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        EstensioneProtezioneRi.values().forEach { est ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    onImpiantoConfigChange(impianto.copy(estensioneProtezione = est))
                                }
                            ) {
                                RadioButton(
                                    selected = impianto.estensioneProtezione == est,
                                    onClick = { onImpiantoConfigChange(impianto.copy(estensioneProtezione = est)) }
                                )
                                Text(est.label, fontSize = 12.sp)
                            }
                        }
                    }

                    if (impianto.estensioneProtezione == EstensioneProtezioneRi.PARZIALE) {
                        OutlinedTextField(
                            value = impianto.localiProtetti.orEmpty(),
                            onValueChange = {
                                onImpiantoConfigChange(impianto.copy(localiProtetti = it.ifBlank { null }))
                            },
                            label = { Text("Locali protetti", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = impianto.responsabileSistema.orEmpty(),
                        onValueChange = {
                            onImpiantoConfigChange(impianto.copy(responsabileSistema = it.ifBlank { null }))
                        },
                        label = { Text("Responsabile del Sistema / Datore di Lavoro", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // --- E. Sintesi Incrocio Dati Libretto ---
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statoListaComp = if (dispositivi.isNotEmpty()) {
                        "Sì — pre-spuntata [X] Sì in ${impianto.tipoVerbale.codiceSezioneAnagrafica} (${dispositivi.size} componenti allegati)"
                    } else {
                        "No (nessun componente Loop inserito)"
                    }
                    val statoAsd = if (impianto.hasAsd) {
                        val code = if (impianto.tipoVerbale == TipoVerbaleRi.VERIFICA_INIZIALE_A) "Prospetto A.4" else "Prospetto B.3"
                        "Incluso ($code — 14 controlli ASD)"
                    } else {
                        "Escluso"
                    }
                    Text(
                        text = "Incrocio Libretto attivo → Tipologia: ${impianto.subtipoLabel} • Lista di controllo dettagliata componenti: $statoListaComp • Tabella ASD: $statoAsd",
                        fontSize = 11.5.sp,
                        color = Color(0xFF334155),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // =========================================================================
        // 2. SEZIONE ELENCO COMPONENTI COMUNI E LOOP 1..8 (FOGLIO ALLEGATO B.1)
        // =========================================================================
        TechnicalAttachmentSection(
            title = "ELENCO COMPONENTI IMPIANTO RILEVAZIONE INCENDI",
            subtitle = "Componenti comuni e dispositivi su Loop 1..8 (Rivelatori e Moduli, ind. 1..150) — Lista di controllo allegata al Libretto",
            items = ordinati,
            itemTitle = { disp ->
                disp.descrizione.ifBlank { "Dispositivo ${disp.indirizzo}" }
            },
            itemSubtitle = { disp ->
                val parts = listOfNotNull(
                    disp.installazione?.let { "Installazione: $it" },
                    disp.zona?.let { "Zona: $it" },
                    disp.dataInstallazione?.let { "Data: $it" }
                )
                parts.joinToString(" • ")
            },
            badgeText = { disp -> disp.indirizzo },
            extraBadges = { disp ->
                Surface(
                    color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = disp.labelSezione,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                val famigliaLabel = when (disp) {
                    is DispositivoRilevazione.ComponenteComune -> "COMUNE"
                    is DispositivoRilevazione.RivelatoreLoop -> "RIVELATORE"
                    is DispositivoRilevazione.ModuloLoop -> "MODULO"
                }
                Surface(
                    color = when (disp) {
                        is DispositivoRilevazione.ComponenteComune -> Color(0xFFEDE9FE)
                        is DispositivoRilevazione.RivelatoreLoop -> Color(0xFFE0F2FE)
                        is DispositivoRilevazione.ModuloLoop -> Color(0xFFFEF3C7)
                    },
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = famigliaLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (disp) {
                            is DispositivoRilevazione.ComponenteComune -> Color(0xFF5B21B6)
                            is DispositivoRilevazione.RivelatoreLoop -> Color(0xFF0369A1)
                            is DispositivoRilevazione.ModuloLoop -> Color(0xFFB45309)
                        },
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = disp.labelTipo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            },
            onAddItem = onOpenAddDialog,
            onEditItem = onEditDispositivo,
            onCloneItem = { disp ->
                val duplicato: DispositivoRilevazione? = when (disp) {
                    is DispositivoRilevazione.ComponenteComune -> {
                        val existingInd = dispositivi
                            .filterIsInstance<DispositivoRilevazione.ComponenteComune>()
                            .map { it.indirizzo }
                        val nextInd = SiglaCloneHelper.findNextSiglaForClone(
                            baseSigla = disp.indirizzo,
                            existingSigle = existingInd,
                            defaultPrefix = "COM."
                        )
                        disp.copy(id = UUID.randomUUID().toString(), indirizzo = nextInd)
                    }

                    is DispositivoRilevazione.RivelatoreLoop -> {
                        val usati = dispositivi
                            .filterIsInstance<DispositivoRilevazione.RivelatoreLoop>()
                            .filter { it.loop == disp.loop }
                            .map { it.numeroIndirizzo }
                            .toSet()
                        val nextAddr = ((disp.numeroIndirizzo + 1)..150).firstOrNull { it !in usati }
                            ?: (1..150).firstOrNull { it !in usati }
                        nextAddr?.let {
                            disp.copy(id = UUID.randomUUID().toString(), numeroIndirizzo = it)
                        }
                    }

                    is DispositivoRilevazione.ModuloLoop -> {
                        val usati = dispositivi
                            .filterIsInstance<DispositivoRilevazione.ModuloLoop>()
                            .filter { it.loop == disp.loop }
                            .map { it.numeroIndirizzo }
                            .toSet()
                        val nextAddr = ((disp.numeroIndirizzo + 1)..150).firstOrNull { it !in usati }
                            ?: (1..150).firstOrNull { it !in usati }
                        nextAddr?.let {
                            disp.copy(id = UUID.randomUUID().toString(), numeroIndirizzo = it)
                        }
                    }
                }
                if (duplicato != null) {
                    onDispositiviChange(dispositivi + duplicato)
                }
            },
            onDeleteItem = { disp ->
                onDispositiviChange(dispositivi.filterNot { it.id == disp.id })
            },
            onClearAll = { onDispositiviChange(emptyList()) },
            onExportStandalonePdf = onExportStandalonePdf,
            addButtonLabel = "Aggiungi al Loop",
            emptyMessage = "Nessun componente registrato. Clicca 'Aggiungi al Loop' per inserire Componenti Comuni o dispositivi su Loop 1..8.",
            modifier = Modifier
        )
    }
}
