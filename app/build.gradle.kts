plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val googleServicesFile = file("google-services.json")
val accountsEnabled = googleServicesFile.isFile
val googleWebClientId = readWebClientId(googleServicesFile)
if (!accountsEnabled) {
    logger.lifecycle("Accounts are disabled: app/google-services.json was not found. Sign-in stays off until you add that file and rebuild.")
}

val fallbackVersionName = "1.0.0"
val releaseVersionName = (project.findProperty("versionName") as String?) ?: fallbackVersionName
val derivedVersionCode = versionCodeOf(releaseVersionName)
val versionCodeProperty = project.findProperty("versionCode") as String?
if (!versionCodeProperty.isNullOrBlank()) {
    val parsedVersionCode = versionCodeProperty.toIntOrNull()
        ?: throw GradleException("versionCode must be an integer. Got \"$versionCodeProperty\".")
    if (parsedVersionCode != derivedVersionCode) {
        throw GradleException(
            "versionCode $parsedVersionCode does not match versionName $releaseVersionName. " +
                "Use $derivedVersionCode (major * 10000 + minor * 100 + patch).",
        )
    }
}

val keystorePath = System.getenv("KEYSTORE_FILE").orEmpty()
val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
val keyAliasEnv = System.getenv("KEY_ALIAS")
val keyPassword = System.getenv("KEY_PASSWORD")
val keystoreStore = keystorePath.takeIf { it.isNotBlank() }?.let { file(it) }
val releaseSigningReady = keystoreStore?.isFile == true &&
    !keystorePassword.isNullOrBlank() &&
    !keyAliasEnv.isNullOrBlank() &&
    !keyPassword.isNullOrBlank()

android {
    namespace = "com.worldtv.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.worldtv.app"
        minSdk = 24
        targetSdk = 36
        versionCode = derivedVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        val datasetBase = (project.findProperty("datasetBase") as String?)
            ?: "https://raw.githubusercontent.com/Valijonzoda/famelack-data/main/"
        buildConfigField("String", "DATASET_BASE", "\"$datasetBase\"")
        buildConfigField("boolean", "ACCOUNTS_ENABLED", accountsEnabled.toString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        if (releaseSigningReady) {
            val store = keystoreStore!!
            val storePass = keystorePassword!!
            val alias = keyAliasEnv!!
            val keyPass = keyPassword!!
            create("release") {
                storeFile = store
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

if (accountsEnabled) {
    apply(plugin = "com.google.gms.google-services")
}

gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { task ->
        task.project == project && task.name in setOf(
            "assembleRelease",
            "bundleRelease",
            "packageRelease",
            "signRelease",
            "signReleaseBundle",
            "packageReleaseBundle",
        )
    }
    if (!wantsRelease) return@whenReady
    val problems = releaseSigningProblems(keystorePath, keystoreStore, keystorePassword, keyAliasEnv, keyPassword)
    if (problems.isNotEmpty()) {
        throw GradleException(releaseSigningMessage(problems))
    }
    val configured = android.buildTypes.getByName("release").signingConfig
    if (configured?.name != "release") {
        throw GradleException(
            "The release build type is not using the upload signing config. " +
                "Refusing to package a release with another certificate.",
        )
    }
}

tasks.configureEach {
    if (name !in setOf(
            "assembleRelease",
            "bundleRelease",
            "packageRelease",
            "signRelease",
            "signReleaseBundle",
            "packageReleaseBundle",
        )
    ) {
        return@configureEach
    }
    doFirst {
        val problems = releaseSigningProblems(keystorePath, keystoreStore, keystorePassword, keyAliasEnv, keyPassword)
        if (problems.isNotEmpty()) {
            throw GradleException(releaseSigningMessage(problems))
        }
    }
}

fun versionCodeOf(versionName: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(versionName)
        ?: throw GradleException("versionName must look like 1.2.3. Got \"$versionName\".")
    val major = match.groupValues[1].toInt()
    val minor = match.groupValues[2].toInt()
    val patch = match.groupValues[3].toInt()
    if (minor !in 0..99 || patch !in 0..99) {
        throw GradleException(
            "versionName $versionName is outside the supported range. " +
                "minor and patch must be 0..99 so versionCode = major * 10000 + minor * 100 + patch stays unique and increasing.",
        )
    }
    return major * 10000 + minor * 100 + patch
}

fun releaseSigningProblems(
    path: String,
    store: java.io.File?,
    storePassword: String?,
    alias: String?,
    password: String?,
): List<String> {
    val problems = mutableListOf<String>()
    if (path.isBlank()) {
        problems += "KEYSTORE_FILE is missing"
    } else if (store?.isFile != true) {
        problems += "KEYSTORE_FILE does not point to a file ($path)"
    }
    if (storePassword.isNullOrBlank()) problems += "KEYSTORE_PASSWORD is missing"
    if (alias.isNullOrBlank()) problems += "KEY_ALIAS is missing"
    if (password.isNullOrBlank()) problems += "KEY_PASSWORD is missing"
    return problems
}

fun releaseSigningMessage(problems: List<String>): String = buildString {
    appendLine("Release signing is incomplete:")
    problems.forEach { appendLine("- $it") }
    appendLine("Set KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD to the upload key.")
    append("This release build stops before packaging. Android rejects an update when the certificate changes (\"conflicts with an existing package\").")
}

fun readWebClientId(servicesFile: java.io.File): String {
    if (!servicesFile.isFile) return ""
    val text = servicesFile.readText()
    val patterns = listOf(
        Regex(""""client_id"\s*:\s*"([^"]+)"\s*,\s*"client_type"\s*:\s*3"""),
        Regex(""""client_type"\s*:\s*3\s*,\s*"client_id"\s*:\s*"([^"]+)""""),
    )
    return patterns.firstNotNullOfOrNull { pattern -> pattern.find(text)?.groupValues?.get(1) }.orEmpty()
}
