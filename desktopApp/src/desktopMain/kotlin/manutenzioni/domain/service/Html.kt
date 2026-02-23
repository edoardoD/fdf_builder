package manutenzioni.domain.service

interface Html {
    /**
     * Accetta una mappa di parametri, la chiave è l'id da cercare nel file
     * html e fillare mentre il value è il valore da mettere nel tag corrispondente alla chiave
     * dopo di che, si interroga il Nosql db per riempire il file delle righe corrispondenti*/
    fun fillHtml(map: Map<String, Any?>)

}