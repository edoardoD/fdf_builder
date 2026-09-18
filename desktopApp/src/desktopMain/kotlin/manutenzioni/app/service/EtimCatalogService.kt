package manutenzioni.app.service

import manutenzioni.domain.model.ComponentCandidate
import manutenzioni.domain.model.VarianteProdotto
import manutenzioni.domain.service.ProductSearchApi

/**
 * Motore di ricerca catalogo ETIM per componenti di quadri elettrici BT.
 *
 * Mappa le query di ricerca (codici articolo o linguaggio naturale tecnico)
 * sulle classi standard ETIM (es. EC000042, EC000003, EC000228, EC000216)
 * e genera automaticamente le varianti commerciali equivalenti per i principali
 * produttori del mercato italiano/europeo (BTicino, ABB, Schneider, Siemens, Gewiss, Dehn).
 */
class EtimCatalogService : ProductSearchApi {

    override suspend fun search(query: String): List<ComponentCandidate> {
        val raw = query.trim()
        if (raw.isBlank()) return emptyList()

        // 1. Normalizzazione stringa query
        val q = normalizeQuery(raw)

        val results = mutableListOf<ComponentCandidate>()

        // 2. Riconoscimento parametri tecnici universali
        val poli = extractPoli(q)
        val curva = extractCurva(q)
        val corrente = parseCorrente(q) ?: 16
        val potereInterruzione = extractPotereInterruzione(q)
        val diffIdn = extractDiffIdn(q)
        val diffTipo = extractDiffTipo(q)

        val isAbbS250 = q.contains("s251") || q.contains("s250") || q.contains("ef 070") || q.contains("ef070") || q.contains("ef07")

        // 3. Riconoscimento categoria primaria del dispositivo
        val isMccb = q.contains("scatolato") || q.contains("mccb") || q.contains("tmax") || 
                     q.contains("xt1") || q.contains("xt2") || q.contains("xt3") || q.contains("xt4") || 
                     q.contains("nsx") || q.contains("megatiker") || q.contains("t714") || q.contains("lv429")

        val isSpd = q.contains("scaricatore") || q.contains("spd") || q.contains("sovratensione") || 
                    q.contains("dehn") || Regex("""\bovr\b""").containsMatchIn(q) || q.contains("a9l") || q.contains("iprd")

        val isSez = q.contains("sezionatore") || q.contains("manovra-sezionatore") || q.contains("isw") || 
                    q.contains("a9s") || Regex("""\be20\d\b""").containsMatchIn(q) || Regex("""\bf8\d\b""").containsMatchIn(q)

        val isRcbo = q.contains("magnetotermico differenziale") || q.contains("differenziale magnetotermico") || 
                     q.contains("rcbo") || q.contains("ds201") || q.contains("ds901") || 
                     q.contains("gc8813") || q.contains("gn8813") || q.contains("gn8843") || 
                     q.contains("a9d") || q.contains("5su1") ||
                     (q.contains("differenziale") && (q.contains("curva") || q.contains("ka") || q.contains("magnetotermico") || 
                      Regex("""\bc\d{1,2}\b""").containsMatchIn(q)))

        val isDiffPuro = !isRcbo && (q.contains("differenziale") || q.contains("salvavita") || q.contains("rccb") || 
                         q.contains("f202") || q.contains("f204") || q.contains("g723") || q.contains("g724") || 
                         q.contains("a9r") || q.contains("5sv3") || q.contains("gw946") || q.contains("gw94"))

        // 4. Costruzione del candidato primario
        when {
            // A. SPD Scaricatore di sovratensione (EC000228)
            isSpd -> {
                val specs = mapOf(
                    "Tipo SPD" to "Tipo 2",
                    "Poli" to poli,
                    "Tensione max continuativa (Uc)" to "275V",
                    "Corrente di scarica nominale (In)" to "20kA",
                    "Corrente di scarica max (Imax)" to "40kA"
                )
                results.add(
                    ComponentCandidate(
                        etimClassId = "EC000228",
                        etimClassName = "Scaricatore di sovratensione",
                        descrizioneStandard = "Scaricatore SPD Tipo 2 $poli In=20kA Imax=40kA",
                        caratteristicheTecniche = specs,
                        variantiDisponibili = listOf(
                            VarianteProdotto(
                                produttore = "Dehn",
                                codice = "952110",
                                serie = "DEHNguard",
                                descrizione = "DEHNguard M TT 275 Scaricatore modulare T2",
                                prezzoListino = 115.00
                            ),
                            VarianteProdotto(
                                produttore = "ABB",
                                codice = "OVR T2 1N 40-275 P",
                                serie = "OVR",
                                descrizione = "OVR T2 Scaricatore di sovratensione 1P+N 40kA",
                                prezzoListino = 108.00
                            ),
                            VarianteProdotto(
                                produttore = "Schneider",
                                codice = "A9L16292",
                                serie = "Acti9 iPRD",
                                descrizione = "iPRD40r Scaricatore modulare T2 1P+N 40kA",
                                prezzoListino = 112.00
                            ),
                            VarianteProdotto(
                                produttore = "BTicino",
                                codice = "F10AP2",
                                serie = "Btdin",
                                descrizione = "Btdin SPD autoprotetto T2 1P+N",
                                prezzoListino = 98.00
                            )
                        )
                    )
                )
            }

            // B. Sezionatore di manovra (EC000216)
            isSez -> {
                val sezPoli = if (poli == "1P+N") "2P" else poli
                val sezIn = corrente.coerceAtLeast(32)
                val specs = mapOf(
                    "Poli" to sezPoli,
                    "Corrente nominale (In)" to "${sezIn}A",
                    "Tensione nominale" to "400V"
                )
                results.add(
                    ComponentCandidate(
                        etimClassId = "EC000216",
                        etimClassName = "Interruttore di manovra-sezionatore",
                        descrizioneStandard = "Interruttore sezionatore modulare $sezPoli ${sezIn}A",
                        caratteristicheTecniche = specs,
                        variantiDisponibili = listOf(
                            VarianteProdotto(
                                produttore = "ABB",
                                codice = if (sezPoli == "4P") "E204/${sezIn}G" else "E202/${sezIn}G",
                                serie = "E200",
                                descrizione = "E200 Sezionatore modulare $sezPoli ${sezIn}A",
                                prezzoListino = 18.20
                            ),
                            VarianteProdotto(
                                produttore = "BTicino",
                                codice = if (sezPoli == "4P") "F84/$sezIn" else "F82/$sezIn",
                                serie = "Btdin",
                                descrizione = "Btdin Sezionatore $sezPoli ${sezIn}A",
                                prezzoListino = 17.50
                            ),
                            VarianteProdotto(
                                produttore = "Schneider",
                                codice = if (sezPoli == "4P") "A9S654$sezIn" else "A9S652$sezIn",
                                serie = "Acti9 iSW",
                                descrizione = "iSW Sezionatore $sezPoli ${sezIn}A",
                                prezzoListino = 19.10
                            )
                        )
                    )
                )
            }

            // C. Interruttore Scatolato Industriale (MCCB - EC000228)
            isMccb -> {
                val mccbIn = corrente.coerceAtLeast(63)
                val specs = mapOf(
                    "Tipo" to "Scatolato Industriale (MCCB)",
                    "Poli" to (if (poli == "1P+N") "3P" else poli),
                    "Corrente nominale (In)" to "${mccbIn}A",
                    "Potere di interruzione (Icu)" to (if (q.contains("16ka")) "16kA" else if (q.contains("36ka")) "36kA" else "25kA")
                )
                results.add(
                    ComponentCandidate(
                        etimClassId = "EC000228",
                        etimClassName = "Interruttore scatolato (MCCB)",
                        descrizioneStandard = "Interruttore scatolato $poli ${mccbIn}A",
                        caratteristicheTecniche = specs,
                        variantiDisponibili = listOf(
                            VarianteProdotto(
                                produttore = "ABB",
                                codice = "XT1B ${mccbIn} TMD ${mccbIn}-${mccbIn * 10}",
                                serie = "Tmax XT",
                                descrizione = "Tmax XT1B Interruttore scatolato ${mccbIn}A",
                                prezzoListino = 145.00
                            ),
                            VarianteProdotto(
                                produttore = "Schneider",
                                codice = "LV42963$mccbIn",
                                serie = "ComPact NSX",
                                descrizione = "ComPact NSX100B Interruttore scatolato ${mccbIn}A",
                                prezzoListino = 152.00
                            ),
                            VarianteProdotto(
                                produttore = "BTicino",
                                codice = "T714E$mccbIn",
                                serie = "Megatiker",
                                descrizione = "Megatiker M1 160 Interruttore scatolato ${mccbIn}A",
                                prezzoListino = 138.00
                            )
                        )
                    )
                )
            }

            // D. Differenziale Magnetotermico (RCBO - EC000905)
            isRcbo -> {
                val specs = mapOf(
                    "Poli" to poli,
                    "Corrente nominale (In)" to "${corrente}A",
                    "Curva di intervento" to curva,
                    "Potere di interruzione (Icn)" to potereInterruzione,
                    "Sensibilità differenziale" to diffIdn,
                    "Tipo differenziale" to diffTipo
                )

                val bticinoCode = when {
                    poli == "4P" -> "GN8843AC$corrente"
                    q.contains("gn8813a") && !q.contains("ac") -> "GN8813A$corrente"
                    q.contains("gc8813") -> "GC8813AC$corrente"
                    potereInterruzione == "6kA" -> "GN8813AC$corrente"
                    else -> "GC8813AC$corrente"
                }

                results.add(
                    ComponentCandidate(
                        etimClassId = "EC000905",
                        etimClassName = "Interruttore magnetotermico differenziale",
                        descrizioneStandard = "Magnetotermico Diff. $poli ${corrente}A Curva $curva $potereInterruzione $diffIdn",
                        caratteristicheTecniche = specs,
                        variantiDisponibili = listOf(
                            VarianteProdotto(
                                produttore = "BTicino",
                                codice = bticinoCode,
                                serie = if (potereInterruzione == "6kA") "Btdin60" else "Btdin45",
                                descrizione = "Int. magnetotermico differenziale $poli $corrente A $curva $potereInterruzione $diffIdn",
                                prezzoListino = 38.50
                            ),
                            VarianteProdotto(
                                produttore = "ABB",
                                codice = if (poli == "4P") "DS204 C$corrente AC30" else if (q.contains("ds901")) "DS901L C$corrente 30mA" else "DS201 C$corrente AC30",
                                serie = if (q.contains("ds901")) "DS901L" else "DS201",
                                descrizione = "DS201 Int. magnetotermico differenziale $poli $corrente A C $potereInterruzione $diffIdn",
                                prezzoListino = 39.80
                            ),
                            VarianteProdotto(
                                produttore = "Schneider",
                                codice = if (poli == "4P") "A9D346$corrente" else "A9D316$corrente",
                                serie = "Acti9 iDPN Vigi",
                                descrizione = "iDPN N Vigi Int. magnetotermico diff. $poli $corrente A $curva $potereInterruzione $diffIdn",
                                prezzoListino = 41.00
                            ),
                            VarianteProdotto(
                                produttore = "Siemens",
                                codice = "5SU1353-7KK$corrente",
                                serie = "SENTRON",
                                descrizione = "5SU1 RCBO Int. magnetotermico diff. $poli $corrente A $curva $potereInterruzione $diffIdn",
                                prezzoListino = 37.90
                            )
                        )
                    )
                )
            }

            // E. Differenziale Puro (RCCB - EC000003)
            isDiffPuro -> {
                val is4P = poli == "4P" || q.contains("4p") || q.contains("tetrapolare") || q.contains("3p+n")
                val diffIn = corrente.coerceAtLeast(25)
                val actualPoli = if (is4P) "4P" else poli
                val specs = mapOf(
                    "Poli" to actualPoli,
                    "Corrente nominale (In)" to "${diffIn}A",
                    "Sensibilità differenziale (Idn)" to diffIdn,
                    "Tipo differenziale" to diffTipo,
                    "Tensione nominale" to (if (is4P) "400V" else "230V")
                )
                results.add(
                    ComponentCandidate(
                        etimClassId = "EC000003",
                        etimClassName = "Interruttore differenziale puro",
                        descrizioneStandard = "Differenziale puro $actualPoli ${diffIn}A $diffIdn Tipo $diffTipo",
                        caratteristicheTecniche = specs,
                        variantiDisponibili = listOf(
                            VarianteProdotto(
                                produttore = "BTicino",
                                codice = if (is4P) "G724AC$diffIn" else "G723AC$diffIn",
                                serie = "Btdin",
                                descrizione = "Differenziale puro $actualPoli ${diffIn}A $diffIdn AC",
                                prezzoListino = if (is4P) 68.00 else 42.50
                            ),
                            VarianteProdotto(
                                produttore = "ABB",
                                codice = if (is4P) "F204 AC-${diffIn}/${if (diffIdn == "300mA") "0.3" else "0.03"}" else "F202 AC-${diffIn}/${if (diffIdn == "300mA") "0.3" else "0.03"}",
                                serie = "F200",
                                descrizione = "Differenziale puro $actualPoli ${diffIn}A $diffIdn AC",
                                prezzoListino = if (is4P) 72.00 else 44.00
                            ),
                            VarianteProdotto(
                                produttore = "Schneider",
                                codice = if (is4P) "A9R414$diffIn" else "A9R412$diffIn",
                                serie = "Acti9 iID",
                                descrizione = "iID Differenziale puro $actualPoli ${diffIn}A $diffIdn",
                                prezzoListino = if (is4P) 75.00 else 45.20
                            ),
                            VarianteProdotto(
                                produttore = "Siemens",
                                codice = if (is4P) "5SV3344-6" else "5SV3314-6",
                                serie = "SENTRON",
                                descrizione = "5SV Differenziale puro $actualPoli ${diffIn}A $diffIdn",
                                prezzoListino = if (is4P) 66.00 else 41.80
                            ),
                            VarianteProdotto(
                                produttore = "Gewiss",
                                codice = if (is4P) "GW94627" else "GW94617",
                                serie = "90 RCD",
                                descrizione = "Differenziale puro $actualPoli ${diffIn}A $diffIdn",
                                prezzoListino = if (is4P) 62.00 else 38.90
                            )
                        )
                    )
                )
            }

            // F. Default: Interruttore Magnetotermico standard (MCB - EC000042)
            else -> {
                val specs = mapOf(
                    "Poli" to poli,
                    "Corrente nominale (In)" to "${corrente}A",
                    "Curva di intervento" to curva,
                    "Potere di interruzione (Icn)" to potereInterruzione,
                    "Moduli DIN" to (if (poli == "1P+N") "1" else if (poli == "2P") "2" else if (poli == "3P") "3" else "4")
                )

                // Costruzione varianti ABB
                val abbVarianti = mutableListOf<VarianteProdotto>()
                if (isAbbS250) {
                    abbVarianti.add(
                        VarianteProdotto(
                            produttore = "ABB",
                            codice = "S251 NA C $corrente (EF 070 $corrente)",
                            serie = "System pro M (S250)",
                            descrizione = "S251 NA C$corrente Int. magnetotermico 1P+N 6kA (Cod. ABB EF 070 $corrente)",
                            prezzoListino = 19.50
                        )
                    )
                    abbVarianti.add(
                        VarianteProdotto(
                            produttore = "ABB",
                            codice = "SN201-L-C$corrente",
                            serie = "System pro M compact (Sostitutivo)",
                            descrizione = "SN201 Int. magnetotermico moderno equivalente $poli ${corrente}A Curva $curva",
                            prezzoListino = 17.20
                        )
                    )
                } else {
                    val abbCode = when {
                        q.contains("s201") -> "S201-C$corrente"
                        q.contains("s202") -> "S202-C$corrente"
                        q.contains("s203") -> "S203-C$corrente"
                        q.contains("s204") -> "S204-C$corrente"
                        q.contains("sn201") -> "SN201 L C$corrente"
                        poli == "1P+N" -> "SN201-L-C$corrente"
                        poli == "2P" -> "S202-C$corrente"
                        poli == "3P" -> "S203-C$corrente"
                        poli == "4P" -> "S204-C$corrente"
                        else -> "S201-C$corrente"
                    }
                    abbVarianti.add(
                        VarianteProdotto(
                            produttore = "ABB",
                            codice = abbCode,
                            serie = "System pro M compact",
                            descrizione = "System pro M Int. magnetotermico $poli ${corrente}A Curva $curva",
                            prezzoListino = 17.20
                        )
                    )
                }

                // BTicino
                val bticinoCode = when {
                    q.contains("fn81") -> "FN81NC$corrente"
                    q.contains("fn82") -> "FN82C$corrente"
                    q.contains("fn84") -> "FN84C$corrente"
                    q.contains("g8823") -> "G8823A$corrente"
                    q.contains("g8843") -> "G8843A$corrente"
                    q.contains("gn8813") -> "GN8813AC$corrente"
                    q.contains("gn8823") -> "GN8823AC$corrente"
                    poli == "1P+N" && potereInterruzione == "6kA" -> "GN8813AC$corrente"
                    poli == "1P+N" -> "FN81NC$corrente"
                    poli == "2P" && potereInterruzione == "6kA" -> "GN8823AC$corrente"
                    poli == "2P" -> "G8823A$corrente"
                    poli == "4P" -> "G8843A$corrente"
                    else -> "FN81NC$corrente"
                }

                // Schneider
                val schneiderCode = when {
                    q.contains("a9n21556") -> "A9N21556"
                    q.contains("a9n21555") -> "A9N21555"
                    poli == "1P+N" && corrente == 6 -> "A9N21556"
                    poli == "1P+N" -> "A9N2155$corrente"
                    poli == "2P" -> "A9F742$corrente"
                    poli == "3P" -> "A9F743$corrente"
                    poli == "4P" -> "A9F744$corrente"
                    else -> "A9F741$corrente"
                }

                // Siemens
                val siemensCode = when {
                    q.contains("5sl6506") -> "5SL6506-7"
                    q.contains("5sl6516") -> "5SL6516-7"
                    poli == "1P+N" -> "5SL65${if (corrente < 10) "0$corrente" else "$corrente"}-7"
                    poli == "2P" -> "5SL62$corrente-7"
                    poli == "3P" -> "5SL63$corrente-7"
                    poli == "4P" -> "5SL64$corrente-7"
                    else -> "5SL61$corrente-7"
                }

                // Gewiss
                val gewissCode = when {
                    q.contains("gw90026") -> "GW90026"
                    poli == "1P+N" -> "GW9002$corrente"
                    poli == "2P" -> "GW9004$corrente"
                    poli == "4P" -> "GW9006$corrente"
                    else -> "GW9000$corrente"
                }

                val allVariants = mutableListOf<VarianteProdotto>()
                allVariants.addAll(abbVarianti)
                allVariants.add(
                    VarianteProdotto(
                        produttore = "BTicino",
                        codice = bticinoCode,
                        serie = if (potereInterruzione == "6kA") "Btdin60" else "Btdin45",
                        descrizione = "Btdin Int. magnetotermico $poli ${corrente}A Curva $curva",
                        prezzoListino = 16.50
                    )
                )
                allVariants.add(
                    VarianteProdotto(
                        produttore = "Schneider",
                        codice = schneiderCode,
                        serie = "Acti9 / Prodis",
                        descrizione = "Acti9 Int. magnetotermico $poli ${corrente}A Curva $curva",
                        prezzoListino = 18.00
                    )
                )
                allVariants.add(
                    VarianteProdotto(
                        produttore = "Siemens",
                        codice = siemensCode,
                        serie = "SENTRON 5SL",
                        descrizione = "5SL Int. magnetotermico $poli ${corrente}A Curva $curva",
                        prezzoListino = 16.90
                    )
                )
                allVariants.add(
                    VarianteProdotto(
                        produttore = "Gewiss",
                        codice = gewissCode,
                        serie = "90 MCB",
                        descrizione = "90 MCB Int. magnetotermico $poli ${corrente}A Curva $curva",
                        prezzoListino = 15.30
                    )
                )

                results.add(
                    ComponentCandidate(
                        etimClassId = "EC000042",
                        etimClassName = "Interruttore magnetotermico",
                        descrizioneStandard = "Interruttore magnetotermico $poli ${corrente}A Curva $curva $potereInterruzione",
                        caratteristicheTecniche = specs,
                        variantiDisponibili = allVariants
                    )
                )
            }
        }

        // 5. Ordinamento intelligente: porta in cima il produttore ricercato
        val matchingBrand = detectBrand(q)
        if (matchingBrand != null) {
            return results.map { candidate ->
                val sorted = candidate.variantiDisponibili.sortedByDescending { it.produttore.equals(matchingBrand, ignoreCase = true) }
                candidate.copy(variantiDisponibili = sorted)
            }
        }

        return results
    }

    private fun normalizeQuery(raw: String): String {
        return raw.lowercase()
            .replace("–", "-")
            .replace("—", "-")
            .replace("−", "-")
            .replace("automat.", "automatico")
            .replace("int.", "interruttore")
            .replace("diff.", "differenziale")
            .replace("sez.", "sezionatore")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun extractPoli(q: String): String {
        return when {
            // Espliciti 4P / Tetrapolari
            q.contains("4p") || q.contains("tetrapolare") || q.contains("4 poli") || q.contains("3p+n") || q.contains("3pn") ||
            Regex("""\b(?:s204|f204|e204|fn84|g8843|gn8843|g724|f84|a9f744|a9s654|a9r414|5sl64|5sv334|gw9462)""").containsMatchIn(q) -> "4P"

            // Espliciti 3P / Tripolari
            q.contains("3p") || q.contains("tripolare") || q.contains("3 poli") ||
            Regex("""\b(?:s203|e203|fn83|g8833|a9f743|5sl63)""").containsMatchIn(q) -> "3P"

            // Espliciti 2P / Bipolari (senza neutro o 2 poli protetti)
            (q.contains("2p") && !q.contains("1p+n")) || q.contains("2 poli") || q.contains("bipolare") ||
            Regex("""\b(?:s202|e202|fn82|g8823|gn8823|f82|a9f742|a9s652|5sl62|gw9004)""").containsMatchIn(q) -> "2P"

            // 1P / Unipolari
            (q.contains("1p") && !q.contains("1p+n") && !q.contains("1pn")) || q.contains("unipolare") || q.contains("1 polo") -> "1P"

            // 1P+N / Unipolari con neutro associato (standard modulare residenziale/terziario)
            else -> "1P+N"
        }
    }

    private fun extractCurva(q: String): String {
        return when {
            q.contains("curva b") || Regex("""\bb\s*\d{1,2}\b""").containsMatchIn(q) -> "B"
            q.contains("curva d") || Regex("""\bd\s*\d{1,2}\b""").containsMatchIn(q) -> "D"
            else -> "C"
        }
    }

    private fun extractPotereInterruzione(q: String): String {
        return when {
            q.contains("6ka") || q.contains("6 ka") || q.contains("6000") -> "6kA"
            q.contains("10ka") || q.contains("10 ka") || q.contains("10000") -> "10kA"
            q.contains("16ka") || q.contains("16 ka") -> "16kA"
            q.contains("25ka") || q.contains("25 ka") -> "25kA"
            else -> "4.5kA"
        }
    }

    private fun extractDiffIdn(q: String): String {
        return when {
            q.contains("10ma") || q.contains("0.01a") || q.contains("0,01a") -> "10mA"
            q.contains("300ma") || q.contains("0.3a") || q.contains("0,3a") -> "300mA"
            q.contains("500ma") || q.contains("0.5a") || q.contains("0,5a") -> "500mA"
            q.contains("1a") || q.contains("1 a") || q.contains("1000ma") -> "1A"
            else -> "30mA"
        }
    }

    private fun extractDiffTipo(q: String): String {
        return when {
            q.contains("tipo a") || q.contains("classe a") -> "A"
            q.contains("tipo f") || q.contains("classe f") -> "F"
            q.contains("tipo b") || q.contains("classe b") -> "B"
            else -> "AC"
        }
    }

    private fun detectBrand(q: String): String? {
        // 1. Nomi espliciti dei marchi (priorità assoluta)
        if (q.contains("dehn")) return "Dehn"
        if (q.contains("abb")) return "ABB"
        if (q.contains("bticino") || q.contains("b-ticino") || q.contains("ticino") || q.contains("legrand")) return "BTicino"
        if (q.contains("schneider") || q.contains("merlin gerin") || q.contains("merlin")) return "Schneider"
        if (q.contains("siemens")) return "Siemens"
        if (q.contains("gewiss")) return "Gewiss"

        // 2. Prefissi/codici serie univoci
        return when {
            q.contains("s25") || q.contains("s20") || q.contains("sn20") || 
            q.contains("f20") || q.contains("ds20") || q.contains("ds90") || 
            q.contains("xt1") || q.contains("xt2") || q.contains("xt3") || q.contains("xt4") || 
            q.contains("ef 070") || q.contains("ef070") || 
            Regex("""\be20\d\b""").containsMatchIn(q) || Regex("""\bovr\b""").containsMatchIn(q) -> "ABB"

            q.contains("btdin") || q.contains("fn8") || q.contains("gn8") || q.contains("gc8") || 
            q.contains("g88") || q.contains("g72") || Regex("""\bf8\d\b""").containsMatchIn(q) -> "BTicino"

            q.contains("acti9") || q.contains("multi9") || q.contains("ik60") || q.contains("ic60") || 
            q.contains("idpn") || q.contains("iprd") || q.contains("isw") || 
            Regex("""\ba9[a-z]\d""").containsMatchIn(q) -> "Schneider"

            q.contains("sentron") || q.contains("5sl") || q.contains("5sy") || q.contains("5sv") || q.contains("5su") -> "Siemens"

            q.contains("gw9") -> "Gewiss"

            else -> null
        }
    }

    private fun parseCorrente(q: String): Int? {
        // 1. Corrente esplicita con unità: es. "16a", "16 a", "16 amp"
        val regexUnit = Regex("""\b(\d{1,3})\s*(?:a|amp|ampere)\b""")
        val matchUnit = regexUnit.find(q)
        if (matchUnit != null) {
            val v = matchUnit.groupValues[1].toIntOrNull()
            if (v != null && v in 1..250) return v
        }

        // 2. Codice d'ordine ABB EF 070 X: l'ultima cifra è la corrente! es. "ef 070 6" -> 6A, "ef 070 16" -> 16A
        val regexEf = Regex("""ef\s*070\s*(\d{1,2})""")
        val matchEf = regexEf.find(q)
        if (matchEf != null) {
            val v = matchEf.groupValues[1].toIntOrNull()
            if (v != null) return v
        }

        // 3. Sezionatori con barra: es. "e204/63g", "f84/63", "e202/32g", "f82/32"
        val regexBarra = Regex("""/(?:(\d{1,3}))""")
        val matchBarra = regexBarra.find(q)
        if (matchBarra != null) {
            val v = matchBarra.groupValues[1].toIntOrNull()
            if (v != null && v in 1..250) return v
        }

        // 4. Siemens 5SL serie: es. "5sl6506-7" -> 6A, "5sl6516-7" -> 16A
        val regexSiemens = Regex("""5sl\d{2}0?([1-9]\d?)-""")
        val matchSiemens = regexSiemens.find(q)
        if (matchSiemens != null) {
            val v = matchSiemens.groupValues[1].toIntOrNull()
            if (v != null) return v
        }

        // 5. Schneider serie Prodis / Acti9 codici specifici
        if (q.contains("a9n21556")) return 6
        if (q.contains("a9n21555")) return 16
        val regexSchneider = Regex("""a9[a-z]\w*?(\d{2})\b""")
        val matchSchneider = regexSchneider.find(q)
        if (matchSchneider != null) {
            val v = matchSchneider.groupValues[1].toIntOrNull()
            if (v != null && v in listOf(6, 10, 16, 20, 25, 32, 40, 50, 63)) return v
        }

        // 6. Gewiss serie 90 codici: "gw90026" -> 6A
        if (q.contains("gw90026")) return 6

        // 7. BTicino con lettere prefisso curva/differenziale: es. "gn8813ac16", "fn84c32", "g8843a40", "g724ac63"
        val regexBticino = Regex("""(?:fn8\d|gn88\d{2}|gc88\d{2}|g88\d{2}|g72\d)[a-z]*?(\d{1,2})\b""")
        val matchBticino = regexBticino.find(q)
        if (matchBticino != null) {
            val v = matchBticino.groupValues[1].toIntOrNull()
            if (v != null && v in listOf(6, 10, 16, 20, 25, 32, 40, 50, 63)) return v
        }

        // 8. Differenziale puro tipo AC/A con trattino o senza: es. "ac-40/0.03", "ac-63/0.3", "ac-25", "ac40"
        val regexDiff = Regex("""ac-(\d{1,2})/""")
        val matchDiff = regexDiff.find(q)
        if (matchDiff != null) {
            val v = matchDiff.groupValues[1].toIntOrNull()
            if (v != null && v in 1..250) return v
        }

        // 9. Curva seguita da numero corrente: es. "c 6", "c6", "c16", "b10", "d32"
        val regexCurva = Regex("""\b[cbd]\s*(\d{1,3})\b""")
        val matchCurva = regexCurva.find(q)
        if (matchCurva != null) {
            val v = matchCurva.groupValues[1].toIntOrNull()
            if (v != null && v in 1..250) return v
        }

        // 10. Taglie standard commerciali
        val standardRatings = listOf(160, 125, 100, 80, 63, 50, 40, 32, 25, 20, 16, 10, 6)
        for (rating in standardRatings) {
            if (Regex("""\b$rating\b""").containsMatchIn(q) || q.contains("c$rating") || q.contains("b$rating") || q.contains("d$rating") || q.contains("ac$rating") || q.contains("a$rating")) {
                return rating
            }
        }
        return null
    }
}
