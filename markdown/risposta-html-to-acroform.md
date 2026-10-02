Perfetto. Per implementare questa logica, devi:

1. **Creare una classe Java che rappresenti una riga di dati** - Una classe POJO (Plain Old Java Object) con i campi che corrispondo alle colonne della tabella (codice attività, tipo, impianto, attività, esiti P/PI/NA/NP/VN/B, nota).

2. **Eseguire la query al database** - Nel tuo `main()` o in un metodo separato, esegui la query e popola una `List<RowData>` con i risultati.

3. **Generare l'HTML dinamicamente** - Invece di avere le righe hardcoded, usa un loop per ogni tuple del database e genera le righe `<tr>` dinamicamente.

4. **Convertire le `<td>` in radio button HTML** - Per le colonne degli esiti (P/PI/NA/NP/VN/B), sostituisci il contenuto statico con `<input type="radio">` raggruppati per riga con lo stesso `name` (ad esempio `name="esito_1"`, `name="esito_2"`, ecc.). Ogni radio avrà un `value` diverso (P, PI, NA, ecc.).

5. **Parsare l'HTML generato con Jsoup** - Nel tuo `HtmlToPdfGenerator`, leggi l'HTML e per ogni `<input type="radio">` crea un radiobutton AcroForm nel PDF con il corretto `name` e `value`.

6. **Gestire i nomi dei campi in modo univoco** - Assegna nomi univoci tipo `esito_row_1_p`, `esito_row_1_pi`, ecc., così quando compili il PDF sai quale riga e quale esito è stato selezionato.

Vuoi che implementi questa soluzione completa?