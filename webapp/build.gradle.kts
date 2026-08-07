plugins {
    kotlin("multiplatform") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
}

group = "cz.mongecad"
version = "0.1.0"

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "mongecad-web"
        browser {
            commonWebpackConfig {
                outputFileName = "mongecad-web.js"

                // Dev server servíruje i kořen webu (aplikace-spustit.html, style.css, img/),
                // aby šla appka zkoušet v reálné stránce, ne jen samostatně.
                // Zároveň posílá u .wasm správný Content-Type – vestavěný server
                // IntelliJ ho neposílá a WebAssembly.instantiateStreaming pak selže.
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    static(rootProject.layout.projectDirectory.dir("..").asFile.canonicalPath)
                    open = false
                }
            }
        }
        binaries.executable()
    }

    compilerOptions {
        // expect/actual třídy (shimy v utils/, WebCursor, Settings) jsou v Kotlinu
        // označené jako Beta; jinak by každý build hlásil 6× totéž.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Celý wasmJs zdrojový set stojí na JS interopu (@JsFun, JsArray, Blob...),
        // takže opt-in řešíme jednou tady místo 44 anotací u jednotlivých volání.
        wasmJsMain { languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop") }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            // Desktopová appka staví na Material 2 (androidx.compose.material),
            // ne na Material 3 – držíme se jí, aby vzhled seděl 1:1.
            implementation(compose.material)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "mongecad.web.generated.resources"
    generateResClass = always
}

/**
 * Nasazení buildu do `app/` v kořeni webu, odkud ho načítá `aplikace-spustit.html`.
 * Vlastní index.html z distribuce se nekopíruje – stránku aplikace drží web.
 */
tasks.register<Sync>("deployToSite") {
    group = "mongecad"
    description = "Zkopíruje wasm build do app/ vedle stránek webu."
    dependsOn("wasmJsBrowserDistribution")

    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable")) {
        exclude("index.html")
    }
    into(rootProject.layout.projectDirectory.dir("../app"))
}
