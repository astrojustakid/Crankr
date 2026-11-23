import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()

if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    versionProps["VERSION_MAJOR"] = "1"
    versionProps["VERSION_MINOR"] = "0"
    versionProps["VERSION_CODE"] = "1"
    versionProps.store(FileOutputStream(versionPropsFile), "Initial Version")
}

var major = versionProps.getProperty("VERSION_MAJOR", "1").toInt()
var minor = versionProps.getProperty("VERSION_MINOR", "0").toInt()
var code = versionProps.getProperty("VERSION_CODE", "1").toInt()

val isAssemble = gradle.startParameter.taskNames.any {
    it.contains("assembleDebug", ignoreCase = true) ||
    it.contains("assembleRelease", ignoreCase = true) ||
    it.equals("assemble", ignoreCase = true)
}

if (isAssemble) {
    code += 1
    minor += 1
    if (minor > 99) {
        minor = 0
        major += 1
    }
    versionProps["VERSION_MAJOR"] = major.toString()
    versionProps["VERSION_MINOR"] = minor.toString()
    versionProps["VERSION_CODE"] = code.toString()
    versionProps.store(FileOutputStream(versionPropsFile), "Auto-incremented build version")
}

android {
    namespace = "com.example.spotifyclone"
    compileSdk = 36
    defaultConfig {
        applicationId = "fm.crankr.music"
        minSdk = 26
        targetSdk = 36
        versionCode = code
        versionName = "$major.$minor"
    }

    signingConfigs {
        create("release") {
            storeFile = file("crankr-release.jks")
            storePassword = "crankr2026release"
            keyAlias = "crankr_release_key"
            keyPassword = "crankr2026release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 & ExoPlayer for Music Streaming & Offline Caching
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.datasource)

  // Image Loading
  implementation(libs.coil.compose)

  // AndroidX Media for MediaSessionCompat & MediaStyle Notification
  implementation("androidx.media:media:1.7.0")

  // AndroidX Palette for dynamic cover color extraction
  implementation("androidx.palette:palette-ktx:1.0.0")

  // Material Icons Extended
  implementation(libs.androidx.compose.material.icons.extended)

  // Ktor WebSocket Client for Listen Together
  val ktorVersion = "3.0.3"
  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
  implementation("io.ktor:ktor-client-websockets:$ktorVersion")
}

val releaseOutputDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile

tasks.register("ensureCrankrApkName") {
    val targetDir = releaseOutputDir
    doLast {
        val appReleaseApk = File(targetDir, "app-release.apk")
        val crankrApk = File(targetDir, "crankr.apk")
        if (appReleaseApk.exists()) {
            appReleaseApk.copyTo(crankrApk, overwrite = true)
            appReleaseApk.delete()
        }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "assembleRelease" }.configureEach {
    finalizedBy("ensureCrankrApkName")
}
