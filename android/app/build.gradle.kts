import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun loadProperties(file: File): Properties = Properties().apply {
    if (file.isFile) file.inputStream().use(::load)
}

val signingBootstrapFile = rootProject.file("keystore.properties")
val signingBootstrap = loadProperties(signingBootstrapFile)
val signingPropertiesFile = signingBootstrap.getProperty("propertiesFile")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let { rootProject.file(it).canonicalFile }
    ?: signingBootstrapFile
val signingProperties = if (signingPropertiesFile == signingBootstrapFile) {
    signingBootstrap
} else {
    loadProperties(signingPropertiesFile)
}

fun signingValue(gradleName: String, environmentName: String, localName: String) =
    providers.gradleProperty(gradleName)
        .orElse(providers.environmentVariable(environmentName))
        .orElse(providers.provider { signingProperties.getProperty(localName).orEmpty() })

val releaseStoreFile = signingValue("COMICPLUS_RELEASE_STORE_FILE", "COMICPLUS_RELEASE_STORE_FILE", "storeFile")
    .map { path ->
        val configured = File(path)
        if (configured.isAbsolute) configured.canonicalPath
        else signingPropertiesFile.parentFile.resolve(configured).canonicalPath
    }
val releaseStorePassword = signingValue("COMICPLUS_RELEASE_STORE_PASSWORD", "COMICPLUS_RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("COMICPLUS_RELEASE_KEY_ALIAS", "COMICPLUS_RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("COMICPLUS_RELEASE_KEY_PASSWORD", "COMICPLUS_RELEASE_KEY_PASSWORD", "keyPassword")
val releaseSigningConfigured = listOf(
    releaseStoreFile.orNull,
    releaseStorePassword.orNull,
    releaseKeyAlias.orNull,
    releaseKeyPassword.orNull,
).all { !it.isNullOrBlank() } && releaseStoreFile.orNull?.let(::File)?.isFile == true

android {
    namespace = "com.comicplus.pure"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.comicplus.pure"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.2"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
