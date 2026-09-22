#!/usr/bin/env python3
"""
Carica i template degli impianti da manutenzioni_db.json verso MongoDB (database 'manutenzioni_db', collection 'impianti').
Aggiunge il discriminatore polimorfico '_t' compatibile con le classi Kotlin:
 - 'QuadroBT' per codIntervento == 'Q'
 - 'ImpiantoStandard' per tutti gli altri
Assicura la presenza di 'id' UUID, cantiereId=None e quantita=1.
"""

import json
import os
import sys
import uuid

try:
    from pymongo import MongoClient
except ImportError:
    print("pymongo non installato. Installalo con: pip3 install pymongo")
    sys.exit(1)

MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017/?directConnection=true")
DB_NAME = "manutenzioni_db"
COLLECTION_NAME = "impianti"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.path.join(SCRIPT_DIR, "desktopApp", "src", "desktopMain", "resources", "manutenzioni_db.json")

def main():
    if not os.path.exists(JSON_PATH):
        print(f"File non trovato: {JSON_PATH}")
        sys.exit(1)

    print(f"Lettura file: {JSON_PATH}")
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    impianti = data.get("impianti", [])
    print(f"Trovati {len(impianti)} impianti nel JSON.")

    print(f"Connessione a {MONGO_URI}...")
    client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
    db = client[DB_NAME]
    col = db[COLLECTION_NAME]

    inserted = 0
    updated = 0

    for imp in impianti:
        cod = imp.get("codIntervento")
        if not cod:
            continue

        tipo = "QuadroBT" if cod == "Q" else "ImpiantoStandard"
        
        doc_set = {
            "_t": tipo,
            "codIntervento": cod,
            "nomeCompleto": imp.get("nomeCompleto", ""),
            "premessa": imp.get("premessa"),
            "listaAttivita": imp.get("listaAttivita", []),
            "listaNormative": imp.get("listaNormative", []),
            "cantiereId": None,
            "quantita": imp.get("quantita", 1),
            "noteSpecifiche": imp.get("noteSpecifiche")
        }

        if tipo == "QuadroBT":
            doc_set.setdefault("sigla", "")
            doc_set.setdefault("descrizioneQuadro", "")
            doc_set.setdefault("listaInterruttori", [])
            doc_set.setdefault("componenti", [])

        filter_query = {
            "codIntervento": cod,
            "cantiereId": None
        }

        res = col.update_one(
            filter_query,
            {
                "$set": doc_set,
                "$setOnInsert": {
                    "id": imp.get("id", str(uuid.uuid4()))
                }
            },
            upsert=True
        )

        if res.upserted_id:
            inserted += 1
            print(f"  [+] Inserito {cod} ({imp.get('nomeCompleto')})")
        else:
            updated += 1
            print(f"  [*] Aggiornato {cod} ({imp.get('nomeCompleto')})")

    print(f"\nOperazione completata: {inserted} inseriti, {updated} aggiornati su MongoDB.")

if __name__ == "__main__":
    main()
