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
 * produttori del mercato italiano/europeo (BTicino, ABB, Schneider, Siemens, Gewiss).
 */
class EtimCatalogService : ProductSearchApi {

    override suspend fun search(query: String): List<ComponentCandidate> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()

        val results = mutableListOf<ComponentCandidate>()

        // 1. Riconoscimento parametri tecnici universali
        val poli = when {
            q.contains("1p+n") || q.contains("1pn") || q.contains("1 polo + n") -> "1P+N"
            q.contains("2p") || q.contains("2 poli") || q.contains("bipolare") -> "2P"
            q.contains("3p+n") || q.contains("3pn") || q.contains("4p") || q.contains("tetrapolare") -> "4P"
            q.contains("3p") || q.contains("tripolare") -> "3P"
            else -> "1P+N"
        }

        val curva = when {
            q.contains("curva b") || Regex("""\bb\d{1,2}\b""").containsMatchIn(q) -> "B"
            q.contains("curva d") || Regex("""\bd\d{1,2}\b""").containsMatchIn(q) -> "D"
            else -> "C"
        }

        val corrente = parseCorrente(q) ?: 16

        val potereInterruzione = when {
            q.contains("6ka") || q.contains("6 ka") || q.contains("6000") -> "6kA"
            q.contains("10ka") || q.contains("10 ka") || q.contains("10000") -> "10kA"
            else -> "4.5kA"
        }

        val diffIdn = when {
            q.contains("10ma") || q.contains("0.01a") || q.contains("0,01a") -> "10mA"
            q.contains("300ma") || q.contains("0.3a") || q.contains("0,3a") -> "300mA"
            q.contains("500ma") || q.contains("0.5a") || q.contains("0,5a") -> "500mA"
            else -> "30mA"
        }

        val diffTipo = when {
            q.contains("tipo a") || q.contains("classe a") -> "A"
            q.contains("tipo f") || q.contains("classe f") -> "F"
            q.contains("tipo b") || q.contains("classe b") -> "B"
            else -> "AC"
        }

        // 2. Classificazione ETIM per tipo dispositivo

        // A. Differenziale Puro (RCCB - EC000003)
        if (q.contains("puro") || q.contains("differenziale puro") || q.contains("salvavita puro") || q.contains("rccb") || q.contains("f202") || q.contains("g723")) {
            val specs = mapOf(
                "Poli" to poli,
                "Corrente nominale (In)" to "${corrente}A",
                "Sensibilità differenziale (Idn)" to diffIdn,
                "Tipo differenziale" to diffTipo,
                "Tensione nominale" to (if (poli == "4P") "400V" else "230V")
            )
            results.add(
                ComponentCandidate(
                    etimClassId = "EC000003",
                    etimClassName = "Interruttore differenziale puro",
                    descrizioneStandard = "Differenziale puro $poli ${corrente}A $diffIdn Tipo $diffTipo",
                    caratteristicheTecniche = specs,
                    variantiDisponibili = listOf(
                        VarianteProdotto(
                            produttore = "BTicino",
                            codice = "G723AC$corrente",
                            serie = "Btdin",
                            descrizione = "Differenziale puro $poli ${corrente}A $diffIdn AC",
                            prezzoListino = 42.50
                        ),
                        VarianteProdotto(
                            produttore = "ABB",
                            codice = "F202AC-${corrente}/0.03",
                            serie = "F200",
                            descrizione = "Differenziale puro $poli ${corrente}A $diffIdn AC",
                            prezzoListino = 44.00
                        ),
                        VarianteProdotto(
                            produttore = "Schneider",
                            codice = "A9R412$corrente",
                            serie = "Acti9 iID",
                            descrizione = "iID Differenziale puro $poli ${corrente}A $diffIdn",
                            prezzoListino = 45.20
                        ),
                        VarianteProdotto(
                            produttore = "Siemens",
                            codice = "5SV3314-6",
                            serie = "SENTRON",
                            descrizione = "5SV Differenziale puro $poli ${corrente}A $diffIdn",
                            prezzoListino = 41.80
                        ),
                        VarianteProdotto(
                            produttore = "Gewiss",
                            codice = "GW94617",
                            serie = "90 RCD",
                            descrizione = "Differenziale puro $poli ${corrente}A $diffIdn",
                            prezzoListino = 38.90
                        )
                    )
                )
            )
        }

        // B. Differenziale Magnetotermico (RCBO - EC000905)
        if (q.contains("magnetotermico differenziale") || q.contains("mgb") || q.contains("rcbo") || q.contains("ds201") || q.contains("gc8813ac")) {
            val specs = mapOf(
                "Poli" to poli,
                "Corrente nominale (In)" to "${corrente}A",
                "Curva di intervento" to curva,
                "Potere di interruzione (Icn)" to potereInterruzione,
                "Sensibilità differenziale" to diffIdn,
                "Tipo differenziale" to diffTipo
            )
            results.add(
                ComponentCandidate(
                    etimClassId = "EC000905",
                    etimClassName = "Interruttore magnetotermico differenziale",
                    descrizioneStandard = "Magnetotermico Diff. $poli ${corrente}A Curva $curva $potereInterruzione $diffIdn",
                    caratteristicheTecniche = specs,
                    variantiDisponibili = listOf(
                        VarianteProdotto(
                            produttore = "BTicino",
                            codice = "GC8813AC$corrente",
                            serie = "Btdin45",
                            descrizione = "Btdin45 Int. magnetotermico differenziale 1P+N $corrente A $curva",
                            prezzoListino = 38.50
                        ),
                        VarianteProdotto(
                            produttore = "ABB",
                            codice = "DS201 C$corrente AC30",
                            serie = "DS201",
                            descrizione = "DS201 Int. magnetotermico differenziale 1P+N $corrente A C 4.5kA 30mA",
                            prezzoListino = 39.80
                        ),
                        VarianteProdotto(
                            produttore = "Schneider",
                            codice = "A9D316$corrente",
                            serie = "Acti9 iDPN H Vigi",
                            descrizione = "iDPN Vigi Int. magnetotermico differenziale 1P+N $corrente A",
                            prezzoListino = 42.00
                        ),
                        VarianteProdotto(
                            produttore = "Siemens",
                            codice = "5SU1353-7KK$corrente",
                            serie = "SENTRON",
                            descrizione = "5SU1 Magnetotermico differenziale 1P+N $corrente A C",
                            prezzoListino = 37.90
                        )
                    )
                )
            )
        }

        // C. Scaricatore di sovratensione (SPD - EC000228)
        if (q.contains("scaricatore") || q.contains("spd") || q.contains("sovratensione") || q.contains("dehn") || q.contains("ovr")) {
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

        // D. Sezionatore di manovra (EC000216)
        if (q.contains("sezionatore") || q.contains("manovra-sezionatore") || q.contains("e200") || q.contains("isw")) {
            val sezPoli = if (poli == "1P+N") "2P" else poli
            val specs = mapOf(
                "Poli" to sezPoli,
                "Corrente nominale (In)" to "${corrente.coerceAtLeast(32)}A",
                "Tensione nominale" to "400V"
            )
            results.add(
                ComponentCandidate(
                    etimClassId = "EC000216",
                    etimClassName = "Interruttore di manovra-sezionatore",
                    descrizioneStandard = "Interruttore sezionatore modulare $sezPoli ${corrente.coerceAtLeast(32)}A",
                    caratteristicheTecniche = specs,
                    variantiDisponibili = listOf(
                        VarianteProdotto(
                            produttore = "ABB",
                            codice = "E202/${corrente.coerceAtLeast(32)}G",
                            serie = "E200",
                            descrizione = "E200 Sezionatore modulare $sezPoli ${corrente.coerceAtLeast(32)}A",
                            prezzoListino = 18.20
                        ),
                        VarianteProdotto(
                            produttore = "BTicino",
                            codice = "F82/${corrente.coerceAtLeast(32)}",
                            serie = "Btdin",
                            descrizione = "Btdin Sezionatore $sezPoli ${corrente.coerceAtLeast(32)}A",
                            prezzoListino = 17.50
                        ),
                        VarianteProdotto(
                            produttore = "Schneider",
                            codice = "A9S652${corrente.coerceAtLeast(32)}",
                            serie = "Acti9 iSW",
                            descrizione = "iSW Sezionatore $sezPoli ${corrente.coerceAtLeast(32)}A",
                            prezzoListino = 19.10
                        )
                    )
                )
            )
        }

        // E. Interruttore Magnetotermico standard (MCB - EC000042)
        // Se non è stato trovato nulla o la query menziona magnetotermico o codici tipo s201, gc88, btdin
        if (results.isEmpty() || q.contains("magnetotermico") || q.contains("mcb") || q.contains("s20") || q.contains("btdin") || q.contains("ic60") || q.contains("5sl")) {
            val specs = mapOf(
                "Poli" to poli,
                "Corrente nominale (In)" to "${corrente}A",
                "Curva di intervento" to curva,
                "Potere di interruzione (Icn)" to potereInterruzione,
                "Moduli DIN" to (if (poli == "1P+N") "1" else if (poli == "2P") "2" else "4")
            )
            val bticinoCode = when {
                poli == "1P+N" && potereInterruzione == "4.5kA" -> "GC8813AC$corrente"
                poli == "2P" -> "G8823A$corrente"
                poli == "4P" -> "G8843A$corrente"
                else -> "FN81NC$corrente"
            }
            val abbCode = when {
                poli == "1P+N" -> "SN201-L-C$corrente"
                poli == "2P" -> "S202-C$corrente"
                poli == "4P" -> "S204-C$corrente"
                else -> "S201-C$corrente"
            }
            val schneiderCode = when {
                poli == "1P+N" -> "A9N2155$corrente"
                poli == "2P" -> "A9F742$corrente"
                poli == "4P" -> "A9F744$corrente"
                else -> "A9F741$corrente"
            }
            val siemensCode = when {
                poli == "1P+N" -> "5SL65$corrente-7"
                poli == "2P" -> "5SL62$corrente-7"
                poli == "4P" -> "5SL64$corrente-7"
                else -> "5SL61$corrente-7"
            }
            val gewissCode = when {
                poli == "1P+N" -> "GW9002$corrente"
                poli == "2P" -> "GW9004$corrente"
                else -> "GW9000$corrente"
            }

            results.add(
                0,
                ComponentCandidate(
                    etimClassId = "EC000042",
                    etimClassName = "Interruttore magnetotermico",
                    descrizioneStandard = "Interruttore magnetotermico $poli ${corrente}A Curva $curva $potereInterruzione",
                    caratteristicheTecniche = specs,
                    variantiDisponibili = listOf(
                        VarianteProdotto(
                            produttore = "BTicino",
                            codice = bticinoCode,
                            serie = "Btdin45",
                            descrizione = "Btdin45 Int. magnetotermico $poli ${corrente}A Curva $curva $potereInterruzione",
                            prezzoListino = 16.50
                        ),
                        VarianteProdotto(
                            produttore = "ABB",
                            codice = abbCode,
                            serie = "System pro M compact",
                            descrizione = "System pro M Int. magnetotermico $poli ${corrente}A Curva $curva",
                            prezzoListino = 17.20
                        ),
                        VarianteProdotto(
                            produttore = "Schneider",
                            codice = schneiderCode,
                            serie = "Acti9 iK60N",
                            descrizione = "Acti9 iK60N Int. magnetotermico $poli ${corrente}A Curva $curva",
                            prezzoListino = 18.00
                        ),
                        VarianteProdotto(
                            produttore = "Siemens",
                            codice = siemensCode,
                            serie = "SENTRON 5SL",
                            descrizione = "5SL Int. magnetotermico $poli ${corrente}A Curva $curva",
                            prezzoListino = 16.90
                        ),
                        VarianteProdotto(
                            produttore = "Gewiss",
                            codice = gewissCode,
                            serie = "90 MCB",
                            descrizione = "90 MCB Int. magnetotermico $poli ${corrente}A Curva $curva",
                            prezzoListino = 15.30
                        )
                    )
                )
            )
        }

        // 3. Ordinamento intelligente: se la query menziona un produttore specifico (es. "abb" o "bticino"),
        // ordina le varianti mettendo quel produttore al primo posto
        val matchingBrand = when {
            q.contains("bticino") || q.contains("legrand") || q.startsWith("gc") || q.startsWith("g8") -> "BTicino"
            q.contains("abb") || q.startsWith("s20") || q.startsWith("sn20") || q.startsWith("f20") -> "ABB"
            q.contains("schneider") || q.contains("merlin") || q.startsWith("a9") -> "Schneider"
            q.contains("siemens") || q.startsWith("5s") -> "Siemens"
            q.contains("gewiss") || q.startsWith("gw") -> "Gewiss"
            q.contains("dehn") -> "Dehn"
            else -> null
        }

        if (matchingBrand != null) {
            return results.map { candidate ->
                val sorted = candidate.variantiDisponibili.sortedByDescending { it.produttore.equals(matchingBrand, ignoreCase = true) }
                candidate.copy(variantiDisponibili = sorted)
            }
        }

        return results
    }

    private fun parseCorrente(q: String): Int? {
        val regex = Regex("""\b(\d{1,3})\s*(?:a|amp|ampere)\b""")
        val match = regex.find(q)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
        // Match numeri tipici di taglie interruttori: 6, 10, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160
        val standardRatings = listOf(160, 125, 100, 80, 63, 50, 40, 32, 25, 20, 16, 10, 6)
        for (rating in standardRatings) {
            if (Regex("""\b$rating\b""").containsMatchIn(q) || q.contains("c$rating") || q.contains("b$rating") || q.contains("d$rating")) {
                return rating
            }
        }
        return null
    }
}
