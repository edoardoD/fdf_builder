import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
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
                
                // iText7 dependencies
                implementation(libs.itext.kernel)
                implementation(libs.itext.layout)
                implementation(libs.itext.forms)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "FdfBuilder"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.example.fdfbuilder"
                dockName = "FDF Builder"
                // No signing for now as requested
            }

            windows {
                menu = true
                shortcut = true
                // No certificate for now as requested
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
