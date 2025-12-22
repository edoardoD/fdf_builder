-- MySQL dump 10.13  Distrib 8.4.7, for macos15 (arm64)
--
-- Host: localhost    Database: manutenzioni
-- ------------------------------------------------------
-- Server version	8.4.7

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `attivita`
--

DROP TABLE IF EXISTS `attivita`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attivita` (
  `cod_intervento` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cod_periodo` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `n_attivita` int NOT NULL,
  `tipo_attivita` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `descrizione` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`cod_intervento`,`cod_periodo`,`n_attivita`),
  KEY `idx_attivita_intervento` (`cod_intervento`),
  KEY `idx_attivita_periodo` (`cod_periodo`),
  CONSTRAINT `fk_attivita_intervento` FOREIGN KEY (`cod_intervento`) REFERENCES `intervento` (`cod_intervento`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_attivita_periodo` FOREIGN KEY (`cod_periodo`) REFERENCES `periodo` (`cod_periodo`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `attivita`
--

LOCK TABLES `attivita` WRITE;
/*!40000 ALTER TABLE `attivita` DISABLE KEYS */;
/*!40000 ALTER TABLE `attivita` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervento`
--

DROP TABLE IF EXISTS `intervento`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervento` (
  `cod_intervento` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nome_completo` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `premessa` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`cod_intervento`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervento`
--

LOCK TABLES `intervento` WRITE;
/*!40000 ALTER TABLE `intervento` DISABLE KEYS */;
INSERT INTO `intervento` VALUES ('AMB','Ambulatori','Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('BACS','Impianto domotico (BACS)','Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('CAB','Cabine (cabine elettriche)','Le attività sulla cabina elettrica devono essere svolte da personale addestrato ai lavori elettrici con qualifca PES come da Norma CEI 11-27, ed eseguiti in totale sicurezza. Prima di accedere ai locali è fondamentale avvertire i referenti del sito, ed avvertire il responsabile sulle attività che ci si appresta a svolgere. Nel caso l\'alimentazione alla cabina provenga dall\'ente distributore, occorre applicare tutte le precauzioni per la sicurezza delle persone e nel caso si rilevino problemi ai manufatti in uso all\'ente distributore, avvisare tempestivamente il suddetto ente. Prima di procedere alle prove e misure che possano mettere fuori tensione la cabina, concordare con i referenti del sito la possibilità e le modalità in cui svolgere queste attività.																									\r\n'),('DEG','Ambulatori (DEG)',NULL),('DS','impianto di diffusione sonora','Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('EM','Illuminazione di sicurezza','Prima di effettuare qualsiasi tipo di prova sugli apparecchi di illuminazione e segnalazione di sicurezza occorre verificare con i referenti del sito l\'effettiva possibilità di svolgere le prove.				\r\n				\r\n				\r\n				\r\n				\r\n				'),('FTV','Fotovoltaico','Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('GE','Gruppo Elettrogeno','Le verifiche periodiche sul gruppo elettrogeno devono essere supportate dalla consultazione del manuale d\'uso e manutenzione.'),('IE','Impianto Elettrico','Prima di effettuare qualsiasi tipo di prova occorre verificare con i referenti del sito la effettiva possibilità di svolgere le prove.I controlli a vista devono essere svolti senza influire sulle regolari attività lavorative del sito, mantenendo in ogni caso le condizioni di sicurezza per le persone.Le verifiche in quota devono essere svolte in condizioni di massima sicurezza per gli addetti e per le persone presenti nel sito, confinando le aree con opportune barriere e cartellonistica di avvertimento.				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('IS','Impianto elettrico ed equipotenziale','Prima di effettuare qualsiasi tipo di prova sugli apparecchi di illuminazione e segnalazione di sicurezza occorre verificare con i referenti del sito l\'effettiva possibilità di svolgere le prove.				\r\n				\r\n				\r\n				\r\n				\r\n				'),('PEM','Sgancio Generale Emergenza','Prima di effettuare qualsiasi tipo di prova sui dispositivi di sgancio di emergenza occorre verificare con i referenti del sito la effettiva possibilità di svolgere le prove.				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('Q','Quadro elettrico	','Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('QMT','Quadri elettrici Media Tensione','Le attività sul quadro elettrico di media tensione devono essere svolte da personale addestrato ai lavori elettrici con qualifca PES come da Norma CEI 11-27, ed eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione all\'interno del quadro è fondamentale sezionare la e le alimentazioni al quadro, e collegare le 3 fasi in franco cortocircuito fra loro e collegate anche verso terra. Nel caso l\'alimentazione al quadro provenga dall\'ente distributore, occorre preventivamente concordare il fuori tensione. Eseguire la procedura di fuori tensione svolgendo tutte le manovre necessarie per la messa in sicurezza del quadro, fra cui l\'interblocco meccanico (es. tramite chiavi anellate) o elettrico, e completare con l\'affissione della segnaletica di sicurezza ed avvertimento, e la modulistica per il passaggio delle consegne e delle responsabilità.'),('RI','Rivelazione Incendi','Il controllo periodico deve essere effettuato da tecnico qualificato. Prima di passare alla fase esecutiva delle prove, occorre controllare la presenza dei documenti riguardanti il controllo iniziale. Durante le operazioni di controllo periodico, deve essere eseguito un controllo funzionale sul sistema, e sul 50% di tutti i dispositivi e azionamenti presenti nel sistema, essendo le verifiche semestrali, raggiungendo il 100% dei dispositivi nell\'arco dei 12 mesi. Prima di procedere con le prove della parte di rivelazione degli impianti di rivelazione e spegnimento, porre le apparecchiature di comando dello spegnimento autotatico in sicurezza, per evitare l\'attivazione dello spegnimento a causa delle operazioni di controllo. In modo particolare assicurarsi che gli effetti delle prove (segnalazioni e comandi) non producano situazioni di pericolo o attuazioni indesiderate, pianificando ed organizzando le prove, ed i metodi di prova, con il responsabile della sicurezza e/o con il responsabile del servizio prevenzione e protezione competente.				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('RIF','Rifasamento','Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('RIG','Rilevazioine GAS','Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				\r\n				'),('SPD','Limitatori Sovratensione','Prima di effettuare qualsiasi tipo di prova occorre verificare con i referenti del sito la effettiva possibilità di svolgere le prove.I controlli a vista devono essere svolti senza influire sulle regolari attività lavorative del sito, mantenendo in ogni caso le condizioni di sicurezza per le persone.Le verifiche in quota devono essere svolte in condizioni di massima sicurezza per gli addetti e per le persone presenti nel sito, confinando le aree con opportune barriere e cartellonistica di avvertimento.				\r\n				\r\n				\r\n				\r\n				\r\n				'),('TRF','Impianti fotovoltaici',NULL),('TRFO','Trasformatore MT/BT in olio					','Le attività sul trasformatore MT/BT devono essere svolte da personale addestrato ai lavori elettrici con qualifca PES come da Norma CEI11-27, ed eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione è fondamentale sezionare la/le alimentazioni al trasformatore, e collegare le 3 fasi in franco cortocircuito fra loro e collegate anche verso terra.				\n				\n				\n				\n				\n				\n				\n				'),('TRFS','Trasformatori a secco','Le attività sul trasformatore MT/BT devono essere svolte da personale addestrato ai lavori elettrici con qualifca PES come da Norma CEI11-27, ed eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione è fondamentale sezionare la/le alimentazioni al trasformatore, e collegare le 3 fasi in franco cortocircuito fra loro e collegate anche verso terra.				\n				\n				\n				\n				\n				\n				\n				'),('UPS','Gruppi di continuità (UPS)','Il controllo periodico deve essere effettuato da tecnico qualificato. Prima di passare alla fase esecutiva delle prove, occorre controllare che gli effetti delle prove (black-out simulato) non producano situazioni di pericolo per le persone o di danno per l\'attività, pianificando ed organizzando\n	 le prove, con i responsabili dell\'attività.');
/*!40000 ALTER TABLE `intervento` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervento_normativa`
--

DROP TABLE IF EXISTS `intervento_normativa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervento_normativa` (
  `cod_intervento` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cod_normativa` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`cod_intervento`,`cod_normativa`),
  KEY `fk_int_norm_normativa` (`cod_normativa`),
  CONSTRAINT `fk_int_norm_intervento` FOREIGN KEY (`cod_intervento`) REFERENCES `intervento` (`cod_intervento`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_int_norm_normativa` FOREIGN KEY (`cod_normativa`) REFERENCES `normativa` (`cod_normativa`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervento_normativa`
--

LOCK TABLES `intervento_normativa` WRITE;
/*!40000 ALTER TABLE `intervento_normativa` DISABLE KEYS */;
/*!40000 ALTER TABLE `intervento_normativa` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `normativa`
--

DROP TABLE IF EXISTS `normativa`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `normativa` (
  `cod_normativa` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `descrizione` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`cod_normativa`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `normativa`
--

LOCK TABLES `normativa` WRITE;
/*!40000 ALTER TABLE `normativa` DISABLE KEYS */;
/*!40000 ALTER TABLE `normativa` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `periodo`
--

DROP TABLE IF EXISTS `periodo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `periodo` (
  `cod_periodo` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mesi` tinyint unsigned DEFAULT NULL,
  `anni` tinyint unsigned DEFAULT NULL,
  `tipo_periodo` enum('M','A') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`cod_periodo`),
  CONSTRAINT `chk_anni_validi` CHECK ((((`tipo_periodo` = _utf8mb4'A') and (`anni` between 1 and 6) and (`mesi` is null)) or ((`tipo_periodo` = _utf8mb4'M') and (`anni` is null)))),
  CONSTRAINT `chk_mesi_validi` CHECK ((((`tipo_periodo` = _utf8mb4'M') and (`mesi` between 1 and 6) and (`anni` is null)) or ((`tipo_periodo` = _utf8mb4'A') and (`mesi` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `periodo`
--

LOCK TABLES `periodo` WRITE;
/*!40000 ALTER TABLE `periodo` DISABLE KEYS */;
/*!40000 ALTER TABLE `periodo` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'manutenzioni'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-11-25 11:23:53
