import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.talotv.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.talotv.launcher"
        minSdk = 28
        targetSdk = 34
        versionCode = 6
        versionName = "1.0.5"
        val urls = (project.findProperty("bridgeUrls") as String? ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        buildConfigField("String[]", "BRIDGES", "{" + urls.joinToString(", ") { "\"" + it + "\"" } + "}")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

abstract class BundleTvWeb : DefaultTask() {
    @get:InputDirectory
    abstract val web: DirectoryProperty

    @get:InputFile
    abstract val configJson: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val dest = File(out, "www/tv")
        val src = web.get().asFile
        src.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(src).path
            if (rel == "mock.js" || rel.endsWith(".md") || f.name.startsWith(".")) return@forEach
            f.copyTo(File(dest, rel), overwrite = true)
        }
        File(dest, "config.js").writeText("window.TALO_CONFIG = " + JsonOutput.toJson(publicConfig(configJson.get().asFile)) + ";\nwindow.COUCH_CONFIG = window.TALO_CONFIG;\n")
    }

    private fun slug(s: Any?, fallback: String): String =
        Regex("[^a-z0-9]+").replace(s.toString().lowercase(), "-").trim('-').take(32).ifEmpty { fallback }

    @Suppress("UNCHECKED_CAST")
    private fun publicConfig(file: File): Map<String, Any?> {
        val c = JsonSlurper().parse(file) as Map<String, Any?>
        val people = mutableListOf<Map<String, Any?>>()
        for (p in (c["people"] as? List<Any?>) ?: emptyList<Any?>()) {
            val m: Map<String, Any?> = when (p) {
                is String -> mapOf("name" to p)
                is Map<*, *> -> p as Map<String, Any?>
                else -> continue
            }
            val name = m["name"]?.toString()?.trim().orEmpty()
            if (name.isEmpty() || people.size >= 12) continue
            val item = linkedMapOf<String, Any?>("id" to ((m["id"] as? String) ?: slug(name, "person")), "name" to name)
            (m["colour"] as? String)?.let { item["colour"] = it }
            people.add(item)
        }
        val tvs = (c["tvs"] as? List<Map<String, Any?>> ?: emptyList()).map { t ->
            mapOf("id" to t["id"], "name" to t["name"], "quick" to (t["quick"] as? List<Any?> ?: listOf("sleep")),
                "picture_modes" to emptyList<String>())
        }
        return mapOf(
            "people" to people,
            "weather" to c["weather"],
            "home" to (c["home"] ?: emptyMap<String, Any?>()),
            "tvs" to tvs,
            "default_tv" to (c["default_tv"] ?: tvs.firstOrNull()?.get("id")),
            "ask" to mapOf("enabled" to false),
            "phone_remote" to false,
        )
    }
}

val repoRoot = rootProject.projectDir.resolve("..")
val configFile: File = (project.findProperty("configJson") as String?)?.let { file(it) }
    ?: repoRoot.resolve("config.json").takeIf { it.isFile }
    ?: repoRoot.resolve("config.example.json")

val bundleTvWeb = tasks.register<BundleTvWeb>("bundleTvWeb") {
    web.set(rootProject.layout.projectDirectory.dir("../web"))
    configJson.set(configFile)
    outputDir.set(layout.buildDirectory.dir("generated/tvweb"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleTvWeb, BundleTvWeb::outputDir)
    }
}
