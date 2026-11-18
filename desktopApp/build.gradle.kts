import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    sourceSets {
        val desktopMain by getting {

            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.materialIconsExtended)

                // iText7 Core + html2pdf
                implementation(libs.itext.kernel)
                implementation(libs.itext.layout)
                implementation(libs.itext.forms)
                implementation(libs.itext.html2pdf)

                // Serializzazione JSON
                implementation(libs.kotlinx.serialization.json)

                // MongoDB
                implementation(libs.mongodb.driver.kotlin.coroutine)
                implementation(libs.bson.kotlinx)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = (project.findProperty("mainClass") as? String) ?: "manutenzioni.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ManutenzioniMaker"
            packageVersion = "4.0.0"
            description = "Generatore schede di manutenzione periodica con PDF AcroForm"
            vendor = "Manutenzioni Maker"

            macOS {
                bundleID = "com.example.manutenzionimaker"
                dockName = "Manutenzioni Maker"
            }

            windows {
                menu = true
                shortcut = true
                menuGroup = "Manutenzioni Maker"
            }

            linux {
                packageName = "manutenzioni-maker"
                debMaintainer = "dev@example.com"
            }
        }
    }
}

tasks.register("createDmg") {
    dependsOn("packageDmg")
}


tasks.register("createMsi") {
    dependsOn("packageMsi")
}

tasks.register<JavaExec>("runHtmlCli") {
    group = "application"
    description = "Genera e stampa il codice HTML compilato della scheda PDF tramite CLI o menu interattivo"
    mainClass.set("manutenzioni.app.cli.GenerateHtmlCliKt")
    val desktopMainCompilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = files(desktopMainCompilation.output.allOutputs, desktopMainCompilation.runtimeDependencyFiles)
    standardInput = System.`in`
}
