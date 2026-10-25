import java.util.Properties

/**
 * Credenziali della chiave di firma: da keystore.properties nella radice del
 * progetto, o da variabili d'ambiente (utili in CI, dove la chiave arriva da un
 * segreto invece che dal repository).
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, variable: String): String? =
    (System.getenv(variable) ?: keystoreProperties.getProperty(key))?.takeIf { it.isNotBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lucio.guzzobot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lucio.guzzobot"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "5.4"
    }

    signingConfigs {
        create("release") {
            val path = signingValue("storeFile", "GUZZOBOT_STORE_FILE")
                ?: "keystore/guzzobot-release.jks"
            val keystore = rootProject.file(path)
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = signingValue("storePassword", "GUZZOBOT_STORE_PASSWORD").orEmpty()
                keyAlias = signingValue("keyAlias", "GUZZOBOT_KEY_ALIAS") ?: "guzzobot"
                keyPassword = signingValue("keyPassword", "GUZZOBOT_KEY_PASSWORD").orEmpty()
            }
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            // R8 resta spento: PDFBox, Room e kotlinx.serialization usano la
            // reflection e qui non c'e' un dispositivo su cui verificare lo
            // shrinking. Meglio un APK piu' grande che una sorpresa a runtime.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Chiave di release se disponibile, altrimenti quella di debug: cosi'
            // il progetto compila anche per chi clona senza la chiave.
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work)
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
