#!/usr/bin/env python3
"""
CSV → JSON Converter per Manutenzioni Maker
Legge i file CSV nella directory corrente e genera dizionari JSON
compatibili con il formato di produzione (manutenzioni_db.json).

Regole periodicità:
  - Valori con zero iniziale (01,02,03,...,06,...,0N) → tipo "M" (mesi), valore = int
  - Valori senza zero iniziale (1,2,3,5) → tipo "A" (anni), valore = int
  - "NP" → attività non presente, viene ignorata
"""

import csv
import json
import io
import os
import re
import sys

# ─── CONFIGURAZIONE ────────────────────────────────────────────────────────
CSV_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_JSON = os.path.join(os.path.dirname(CSV_DIR), "src", "desktopMain", "resources", "manutenzioni_db.json")
REPORT_FILE = os.path.join(CSV_DIR, "conversion_report.md")

# Mappa codice file → codIntervento (derivata da PARAMETRI.csv e nomi file)
FILE_TO_CODE = {
    "CAB MT.csv": "CAB",
    "UPS.csv": "UPS",
    "QMT.csv": "QMT",
    "Q.csv": "Q",
    "PEM.csv": "PEM",
    "EM.csv": "EM",
    "IS.csv": "IS",
    "IE.csv": "IE",
    "RI.csv": "RI",
    "TRFS.csv": "TRFS",
    "TRFO.csv": "TRFO",
    "FTV.csv": "FTV",
    "SPD.csv": "SPD",
    "GE.csv": "GE",
    "RIF.csv": "RIF",
    "BACS.csv": "BACS",
    "DEG.csv": "DEG",
    "AMB.csv": "AMB",
    "DS.csv": "DS",
    "RIG.csv": "RIG",
}

# Normative standard per tipo di impianto (basate su contesto tecnico e premesse)
NORMATIVE_MAP = {
    "CAB": [
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
        {"codNormativa": "CEI 0-16", "descrizione": "Regola tecnica di riferimento per la connessione di utenti attivi e passivi alle reti AT e MT"},
    ],
    "UPS": [
        {"codNormativa": "CEI EN 62040", "descrizione": "Sistemi statici di continuità (UPS)"},
    ],
    "QMT": [
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
        {"codNormativa": "CEI 0-16", "descrizione": "Regola tecnica di riferimento per la connessione di utenti attivi e passivi alle reti AT e MT"},
    ],
    "Q": [
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
    ],
    "PEM": [
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
        {"codNormativa": "DM 37/08", "descrizione": "Attività di installazione degli impianti"},
    ],
    "EM": [
        {"codNormativa": "UNI EN 1838", "descrizione": "Illuminazione di emergenza"},
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
    ],
    "IS": [
        {"codNormativa": "UNI EN 1838", "descrizione": "Illuminazione di emergenza"},
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
    ],
    "IE": [
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
    ],
    "RI": [
        {"codNormativa": "UNI 9795", "descrizione": "Sistemi fissi automatici di rivelazione e di segnalazione allarme d'incendio"},
        {"codNormativa": "UNI 11224", "descrizione": "Controllo iniziale e manutenzione dei sistemi di rivelazione incendi"},
    ],
    "TRFS": [
        {"codNormativa": "CEI 14-4", "descrizione": "Trasformatori di potenza"},
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
    ],
    "TRFO": [
        {"codNormativa": "CEI 14-4", "descrizione": "Trasformatori di potenza"},
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
    ],
    "FTV": [
        {"codNormativa": "CEI 82-25", "descrizione": "Guida alla realizzazione di sistemi di generazione fotovoltaica"},
        {"codNormativa": "CEI 11-20", "descrizione": "Impianti di produzione di energia elettrica"},
    ],
    "SPD": [
        {"codNormativa": "CEI EN 61643", "descrizione": "Dispositivi di protezione contro le sovratensioni"},
    ],
    "GE": [
        {"codNormativa": "CEI 11-20", "descrizione": "Impianti di produzione di energia elettrica"},
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
    ],
    "RIF": [
        {"codNormativa": "CEI 33-7", "descrizione": "Condensatori di potenza per impianti a corrente alternata"},
    ],
    "BACS": [
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
        {"codNormativa": "UNI EN ISO 16484", "descrizione": "Building automation and control systems (BACS)"},
    ],
    "DEG": [
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
    ],
    "AMB": [
        {"codNormativa": "CEI 64-8", "descrizione": "Impianti elettrici utilizzatori"},
        {"codNormativa": "CEI 11-27", "descrizione": "Lavori su impianti elettrici"},
    ],
    "DS": [
        {"codNormativa": "CEI 100-55", "descrizione": "Sistemi elettroacustici applicati ai servizi di emergenza"},
    ],
    "RIG": [
        {"codNormativa": "UNI EN 50194", "descrizione": "Apparecchi elettrici per la rivelazione di gas combustibili in locali ad uso domestico"},
        {"codNormativa": "UNI 11224", "descrizione": "Controllo iniziale e manutenzione dei sistemi di rivelazione incendi"},
    ],
}

# ─── FILES DA IGNORARE (non contengono dati impianto) ─────────────────────
IGNORED_FILES = {
    "CALENDARIO.csv",     # Cronoprogramma, non è un impianto
    "ELENCO DIFF.06.csv", # Elenco interruttori differenziali (dati inventario, non attività)
    "ELENCO DIFF.1.csv",  # Elenco interruttori differenziali (dati inventario, non attività)
    "ELENCO EM.csv",      # Elenco corpi illuminanti emergenza (inventario)
    "ELENCO RIL.csv",     # Elenco componenti rivelazione incendi (inventario)
    "Foglio1.csv",        # Vuoto
    "Foglio2.csv",        # Mappatura schede, utile per referenza ma non impianto
    "Foglio3.csv",        # Vuoto
    "DISP2.csv",          # Vuoto
    "DISP3.csv",          # Vuoto
    "DISP4.csv",          # Vuoto
    "DISP5.csv",          # Vuoto
    "Inizio.csv",         # Solo intestazione data/tecnico
    "PARAMETRI.csv",      # Parametri di configurazione, non impianto
    "start.csv",          # Layout stampa QMT (duplicato del formato complesso)
    "R.01.csv",           # Riepilogo interventi (template vuoto)
    "R.02.csv",           # Riepilogo interventi (template vuoto)
    "R.03.csv",           # Riepilogo interventi (template vuoto)
    "R.04.csv",           # Riepilogo interventi (template vuoto)
    "R.05.csv",           # Riepilogo interventi (template vuoto)
    "R.06.csv",           # Riepilogo interventi (template vuoto)
    "R.1.csv",            # Riepilogo interventi (template)
    "R.2.csv",            # Riepilogo interventi (template)
    "R.3.csv",            # Riepilogo interventi (template)
    "R.4.csv",            # Riepilogo interventi (template)
    "R.5.csv",            # Riepilogo interventi (template)
    "R.6.csv",            # Riepilogo interventi (template)
}


def fix_encoding(text: str) -> str:
    """Fix common latin1 → utf8 mojibake from CSV exported da Excel."""
    replacements = {
        "à": "à", "è": "è", "ì": "ì", "ò": "ò", "ù": "ù",
        "É": "É", "é": "é",
        "â€™": "'", "â€œ": '"', "â€": '"',
        "ï¿½": "",
        "\u00e0": "à", "\u00e8": "è", "\u00ec": "ì", "\u00f2": "ò", "\u00f9": "ù",
        "\u00c9": "É",
        "�": "",   # replacement char
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    # Fix common latin-1 encoding artifacts
    try:
        if any(ord(c) > 127 for c in text):
            # Try to decode as latin-1 re-encoded
            test = text.encode('latin-1', errors='ignore').decode('utf-8', errors='ignore')
            if test and len(test) > len(text) * 0.5:
                return test
    except Exception:
        pass
    return text


def parse_frequenza(raw: str) -> dict | None:
    """
    Converte il valore periodicità in {tipo, valore}.
    - Con zero iniziale (01,02,...,06) → tipo "M" (mesi)
    - Senza zero iniziale (1,2,3,5) → tipo "A" (anni)
    - "NP" → None (non presente)
    """
    raw = raw.strip()
    if not raw or raw.upper() == "NP":
        return None

    # Rimuovi eventuali spazi
    raw = raw.replace(" ", "")

    # Se ha zero iniziale → mesi
    if raw.startswith("0") and len(raw) >= 2:
        try:
            val = int(raw)
            return {"tipo": "M", "valore": val}
        except ValueError:
            return None

    # Se è un numero puro senza zero iniziale → anni
    try:
        val = int(raw)
        return {"tipo": "A", "valore": val}
    except ValueError:
        return None


def read_csv_file(filepath: str) -> str:
    """Read CSV trying multiple encodings. Mac OS Roman is the primary for these files."""
    for enc in ['mac_roman', 'utf-8', 'cp1252', 'latin-1']:
        try:
            with open(filepath, 'r', encoding=enc) as f:
                return f.read()
        except (UnicodeDecodeError, UnicodeError):
            continue
    # Fallback: binary with ignore
    with open(filepath, 'rb') as f:
        return f.read().decode('mac_roman', errors='replace')


def parse_standard_csv(filepath: str, filename: str, code: str) -> tuple[dict | None, dict]:
    """
    Parse un CSV nel formato standard:
    Riga 0: Nome completo impianto
    Riga 1: Header (Periodicità;Progressivo;Attività;Zona;Attività)
    Righe 2-N: Dati attività
    Dopo righe vuote: Premessa

    Restituisce (impianto_dict, report_info)
    """
    report = {
        "file": filename,
        "codIntervento": code,
        "colonne_usate": [],
        "anomalie": [],
        "attivita_trovate": 0,
        "attivita_scartate_NP": 0,
        "esempio": None,
    }

    raw_content = read_csv_file(filepath)
    lines = raw_content.strip().replace('\r\n', '\n').replace('\r', '\n').split('\n')

    if len(lines) < 3:
        report["anomalie"].append("File troppo corto, meno di 3 righe")
        return None, report

    # Riga 0: Nome completo
    nome_completo = lines[0].split(";")[0].strip()
    # Fix encoding
    nome_completo_clean = fix_mojibake(nome_completo)

    # Riga 1: Header - verifica la struttura
    header_parts = lines[1].split(";")
    report["colonne_usate"] = [h.strip() for h in header_parts if h.strip()]

    # Cerchiamo la premessa (dopo le righe vuote alla fine delle attività)
    premessa = ""
    premessa_marker = None
    for i, line in enumerate(lines):
        stripped = line.strip().rstrip(";").strip()
        if stripped.lower().startswith("premessa"):
            premessa_marker = i
            break

    if premessa_marker is not None:
        # La premessa è nelle righe successive (con contenuto)
        for j in range(premessa_marker + 1, len(lines)):
            line_content = lines[j].split(";")[0].strip()
            if not line_content:
                # Check if there's content in other columns
                all_parts = [p.strip() for p in lines[j].split(";") if p.strip()]
                if all_parts:
                    line_content = " ".join(all_parts)
            if line_content:
                premessa = fix_mojibake(line_content)
                break
    else:
        report["anomalie"].append("Premessa non trovata nel file")

    # Parse attività (partendo da riga 2)
    lista_attivita = []
    for i in range(2, len(lines)):
        line = lines[i].strip()
        if not line or line.replace(";", "").strip() == "":
            continue

        parts = line.split(";")
        if len(parts) < 2:
            continue

        periodicita_raw = parts[0].strip()
        progressivo_raw = parts[1].strip() if len(parts) > 1 else ""

        # Se non c'è progressivo numerico, skip
        if not progressivo_raw:
            continue
        try:
            n_attivita = int(progressivo_raw)
        except ValueError:
            continue

        # Frequenza
        freq = parse_frequenza(periodicita_raw)
        if freq is None:
            report["attivita_scartate_NP"] += 1
            continue

        # Tipo attività (colonna 2)
        tipo_attivita = parts[2].strip() if len(parts) > 2 else ""
        if not tipo_attivita:
            # Se manca il tipo ma c'è una descrizione, potrebbe essere un gap vuoto
            continue

        # Descrizione: colonna 4 (la vera descrizione) oppure colonna 2 se il formato è diverso
        descrizione = ""
        if len(parts) > 4 and parts[4].strip():
            descrizione = parts[4].strip()
        elif len(parts) > 3 and parts[3].strip():
            # Fallback: usa colonna 3
            descrizione = parts[3].strip()

        if not descrizione:
            report["anomalie"].append(f"Attività {n_attivita}: descrizione vuota, scartata")
            continue

        # Fix encoding artefatti
        tipo_attivita = fix_mojibake(tipo_attivita)
        descrizione = fix_mojibake(descrizione)

        # Gestisci descrizioni multi-linea (contenute tra virgolette)
        if descrizione.startswith('"') and not descrizione.endswith('"'):
            # Cerca la continuazione nelle righe successive
            j = i + 1
            while j < len(lines):
                next_line = lines[j].strip()
                descrizione += " " + next_line.split(";")[0].strip()
                if '"' in next_line:
                    break
                j += 1
            descrizione = descrizione.replace('"', '').strip()

        # Rimuovi virgolette residue
        descrizione = descrizione.strip('"').strip()

        attivita = {
            "nAttivita": n_attivita,
            "tipoAttivita": tipo_attivita,
            "descrizione": descrizione,
            "frequenza": freq,
        }
        lista_attivita.append(attivita)

        # Salva esempio per il report
        if report["esempio"] is None:
            report["esempio"] = {
                "riga_csv": line[:120] + ("..." if len(line) > 120 else ""),
                "json": attivita,
            }

    report["attivita_trovate"] = len(lista_attivita)

    if not lista_attivita:
        report["anomalie"].append("Nessuna attività valida trovata")
        return None, report

    impianto = {
        "codIntervento": code,
        "nomeCompleto": nome_completo_clean,
        "premessa": premessa,
        "listaAttivita": lista_attivita,
        "listaNormative": NORMATIVE_MAP.get(code, []),
    }

    return impianto, report


def fix_mojibake(text: str) -> str:
    """Clean up encoding artifacts from latin-1 CSV exports."""
    if not text:
        return text
    # Common patterns from latin-1 → utf-8 mis-read
    replacements = {
        '\x92': "'",
        '\x93': '"',
        '\x94': '"',
        '\x96': '–',
        '\x97': '—',
        '\xe0': 'à',
        '\xe8': 'è',
        '\xe9': 'é',
        '\xec': 'ì',
        '\xf2': 'ò',
        '\xf9': 'ù',
        '\xc0': 'À',
        '\xc8': 'È',
        '\xc9': 'É',
        '\xcc': 'Ì',
        '\xd2': 'Ò',
        '\xd9': 'Ù',
        '\xf1': 'ñ',
        '�': '',     # unicode replacement char
        '#N/D': '',  # Excel N/A
        '#RIF!': '', # Excel REF error
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    # Remove trailing whitespace and normalize spaces
    text = re.sub(r'\s+', ' ', text).strip()
    return text


def parse_qmt_csv(filepath: str) -> tuple[dict | None, dict]:
    """QMT ha 8 colonne (con SF6, ISV, IVOR) ma il formato base è lo stesso."""
    report = {
        "file": "QMT.csv",
        "codIntervento": "QMT",
        "colonne_usate": ["Periodicità", "Progressivo", "Attività", "Zona", "Attività", "SF6", "ISV", "IVOR"],
        "anomalie": [],
        "attivita_trovate": 0,
        "attivita_scartate_NP": 0,
        "esempio": None,
    }

    raw_content = read_csv_file(filepath)
    lines = raw_content.strip().replace('\r\n', '\n').replace('\r', '\n').split('\n')

    nome_completo = fix_mojibake(lines[0].split(";")[0].strip())

    # Premessa
    premessa = ""
    for i, line in enumerate(lines):
        stripped = line.strip().rstrip(";").strip()
        if stripped.lower().startswith("premessa"):
            for j in range(i + 1, len(lines)):
                line_content = lines[j].split(";")[0].strip()
                if line_content:
                    premessa = fix_mojibake(line_content)
                    break
            break

    lista_attivita = []
    for i in range(2, len(lines)):
        line = lines[i].strip()
        if not line or line.replace(";", "").strip() == "":
            continue

        parts = line.split(";")
        if len(parts) < 5:
            continue

        periodicita_raw = parts[0].strip()
        progressivo_raw = parts[1].strip()

        if not progressivo_raw:
            continue
        try:
            n_attivita = int(progressivo_raw)
        except ValueError:
            continue

        freq = parse_frequenza(periodicita_raw)
        if freq is None:
            report["attivita_scartate_NP"] += 1
            continue

        tipo_attivita = fix_mojibake(parts[2].strip()) if len(parts) > 2 else ""
        descrizione = fix_mojibake(parts[4].strip()) if len(parts) > 4 else ""

        if not tipo_attivita or not descrizione:
            continue

        # Gestisci riga 20-21 del QMT che ha descrizione split
        if descrizione.startswith("dei componenti"):
            # Skip, è continuazione della riga 20
            continue

        attivita = {
            "nAttivita": n_attivita,
            "tipoAttivita": tipo_attivita,
            "descrizione": descrizione,
            "frequenza": freq,
        }
        lista_attivita.append(attivita)

        if report["esempio"] is None:
            report["esempio"] = {
                "riga_csv": line[:120] + ("..." if len(line) > 120 else ""),
                "json": attivita,
            }

    report["attivita_trovate"] = len(lista_attivita)

    if not lista_attivita:
        report["anomalie"].append("Nessuna attività valida trovata")
        return None, report

    impianto = {
        "codIntervento": "QMT",
        "nomeCompleto": nome_completo,
        "premessa": premessa,
        "listaAttivita": lista_attivita,
        "listaNormative": NORMATIVE_MAP["QMT"],
    }

    return impianto, report


def main():
    reports = []
    impianti = []
    ignored_files_report = []
    anomaly_files = []

    csv_files = sorted([f for f in os.listdir(CSV_DIR) if f.endswith('.csv')])

    for filename in csv_files:
        filepath = os.path.join(CSV_DIR, filename)

        # Skip file ignorati
        if filename in IGNORED_FILES:
            ignored_files_report.append(filename)
            continue

        # Skip file non mappati
        if filename not in FILE_TO_CODE:
            ignored_files_report.append(f"{filename} (non mappato)")
            continue

        code = FILE_TO_CODE[filename]

        # Check file vuoto
        if os.path.getsize(filepath) == 0:
            ignored_files_report.append(f"{filename} (vuoto)")
            continue

        print(f"📄 Processing: {filename} → {code}")

        # QMT ha formato speciale con colonne extra
        if filename == "QMT.csv":
            impianto, report = parse_qmt_csv(filepath)
        else:
            impianto, report = parse_standard_csv(filepath, filename, code)

        reports.append(report)

        if impianto:
            impianti.append(impianto)
            print(f"   ✅ {report['attivita_trovate']} attività, {report['attivita_scartate_NP']} NP scartate")
            if report["anomalie"]:
                print(f"   ⚠️  Anomalie: {', '.join(report['anomalie'])}")
        else:
            anomaly_files.append(filename)
            print(f"   ❌ Nessun impianto generato")

    # ── Genera il JSON di output ────────────────────────────────────────────
    output = {"impianti": impianti}

    with open(OUTPUT_JSON, 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n✅ JSON scritto in: {OUTPUT_JSON}")
    print(f"   Totale impianti: {len(impianti)}")

    # ── Genera il Report ────────────────────────────────────────────────────
    generate_report(reports, impianti, ignored_files_report, anomaly_files)
    print(f"📋 Report scritto in: {REPORT_FILE}")


def generate_report(reports, impianti, ignored, anomaly_files):
    """Genera il report markdown dettagliato."""
    md = []
    md.append("# 📋 Report Conversione CSV → JSON\n")
    md.append(f"**Data generazione:** 2026-03-11\n")
    md.append(f"**Impianti generati:** {len(impianti)}\n")
    md.append(f"**File ignorati:** {len(ignored)}\n\n")

    md.append("---\n\n")

    # ── Sommario ─────────────────────────────────────────────────
    md.append("## 📊 Sommario\n\n")
    md.append("| # | File CSV | Cod. Intervento | Attività | NP Scartate | Anomalie |\n")
    md.append("|---|---------|----------------|----------|-------------|----------|\n")
    for i, r in enumerate(reports, 1):
        anom = len(r["anomalie"])
        md.append(f"| {i} | `{r['file']}` | **{r['codIntervento']}** | {r['attivita_trovate']} | {r['attivita_scartate_NP']} | {anom} |\n")
    md.append("\n")

    # ── File Ignorati ────────────────────────────────────────────
    md.append("## 🚫 File CSV Ignorati\n\n")
    md.append("I seguenti file sono stati esclusi perché non contengono dati utili per la costruzione dei dizionari impianto:\n\n")
    for f in sorted(ignored):
        md.append(f"- `{f}`\n")
    md.append("\n")

    # ── Dettaglio per ogni file ─────────────────────────────────
    md.append("## 📄 Dettaglio Conversione per File\n\n")
    for r in reports:
        md.append(f"### 🔧 {r['file']} → `{r['codIntervento']}`\n\n")
        md.append(f"- **Colonne utilizzate:** {', '.join(r['colonne_usate']) if r['colonne_usate'] else 'Standard (Periodicità, Progressivo, Attività, Zona, Attività)'}\n")
        md.append(f"- **Attività valide trovate:** {r['attivita_trovate']}\n")
        md.append(f"- **Attività NP scartate:** {r['attivita_scartate_NP']}\n")

        if r["anomalie"]:
            md.append(f"- **⚠️ Anomalie:**\n")
            for a in r["anomalie"]:
                md.append(f"  - {a}\n")

        if r["esempio"]:
            md.append(f"\n**Esempio conversione:**\n\n")
            md.append(f"CSV input:\n```\n{r['esempio']['riga_csv']}\n```\n\n")
            md.append(f"JSON output:\n```json\n{json.dumps(r['esempio']['json'], ensure_ascii=False, indent=2)}\n```\n\n")
        md.append("---\n\n")

    # ── Regole Periodicità ──────────────────────────────────────
    md.append("## ⏰ Regole Periodicità Applicate\n\n")
    md.append("| Valore CSV | Tipo | Valore JSON | Significato |\n")
    md.append("|-----------|------|-------------|-------------|\n")
    md.append("| `01` | `M` | 1 | Ogni 1 mese |\n")
    md.append("| `02` | `M` | 2 | Ogni 2 mesi |\n")
    md.append("| `04` | `M` | 4 | Ogni 4 mesi |\n")
    md.append("| `06` | `M` | 6 | Ogni 6 mesi |\n")
    md.append("| `1` | `A` | 1 | Ogni 1 anno |\n")
    md.append("| `2` | `A` | 2 | Ogni 2 anni |\n")
    md.append("| `3` | `A` | 3 | Ogni 3 anni |\n")
    md.append("| `5` | `A` | 5 | Ogni 5 anni |\n")
    md.append("| `NP` | — | — | Non Presente (scartata) |\n")
    md.append("\n")

    # ── Elenco completo impianti generati ────────────────────────
    md.append("## 🏭 Elenco Impianti Generati\n\n")
    md.append("| # | codIntervento | Nome Completo | N. Attività | N. Normative |\n")
    md.append("|---|--------------|---------------|-------------|---------------|\n")
    for i, imp in enumerate(impianti, 1):
        md.append(f"| {i} | **{imp['codIntervento']}** | {imp['nomeCompleto']} | {len(imp['listaAttivita'])} | {len(imp['listaNormative'])} |\n")
    md.append("\n")

    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write("".join(md))


if __name__ == "__main__":
    main()


