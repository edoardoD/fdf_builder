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
                implementation(project(":common"))
                implementation(compose.desktop.currentOs)

                // iText7 Core + html2pdf
                implementation(libs.itext.kernel)
                implementation(libs.itext.layout)
                implementation(libs.itext.forms)
                implementation(libs.itext.html2pdf)

                // Serializzazione JSON
                implementation(libs.kotlinx.serialization.json)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "manutenzioni.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ManutenzioniMaker"
            packageVersion = "1.0.0"
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
