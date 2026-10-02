# FDF Builder - Kotlin Multiplatform Desktop

Progetto Kotlin Multiplatform per Desktop (macOS + Windows) con Compose Multiplatform e iText7.

## Prerequisiti
- **JDK 17**: Assicurati di avere il JDK 17 installato.
- **VS Code Estensioni**:
  - `Kotlin` (JetBrains)
  - `Gradle for Java` (Microsoft)

## Istruzioni per VS Code

1. **Import del progetto**:
   - Apri la cartella del progetto in VS Code.
   - Attendi che le estensioni Gradle e Kotlin completino l'indicizzazione.
2. **Esecuzione**:
   - Apri il terminale integrato.
   - Esegui: `./gradlew :desktopApp:run`
3. **Packaging**:
   - **macOS**: `./gradlew :desktopApp:createDmg`
   - **Windows**: `./gradlew :desktopApp:createMsi`
4. **Debug**:
   - Crea un file `.vscode/launch.json` se non presente:
     ```json
     {
       "version": "0.2.0",
       "configurations": [
         {
           "type": "java",
           "name": "Debug DesktopApp",
           "request": "launch",
           "mainClass": "com.example.desktop.MainKt",
           "projectName": "fdf_builder.desktopApp.desktopMain"
         }
       ]
     }
     ```

## Note su iText7
- **Licenza**: iText7 è distribuito sotto licenza **AGPL**. Per uso commerciale chiuso è necessaria una licenza commerciale.
- **Integrazione**: La libreria è integrata solo nei source set JVM/Desktop.

## Note sul Packaging
- **macOS**: Per la distribuzione esterna è necessaria la firma e la notarizzazione tramite Apple Developer Program. Questo progetto esegue build non firmate per uso locale.
- **Windows**: Per evitare avvisi "SmartScreen", l'MSI dovrebbe essere firmato con un certificato di firma del codice.
