plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(17)
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // common dependencies here
            }
        }
        val commonTest by getting {
            dependencies {
                // common test dependencies
            }
        }
        val desktopMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.itext.kernel)
                implementation(libs.itext.layout)
                implementation(libs.itext.forms)
            }
        }
    }
}
