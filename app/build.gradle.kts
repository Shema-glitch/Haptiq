plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.roborazzi)
}

// Internal build fingerprint — branch codename + short SHA baked into every build,
// so "which commit do we revert to?" is answered by the About row, not archaeology.
fun git(vararg args: String): String = providers.exec {
  commandLine("git", *args)
}.standardOutput.asText.get().trim()

val gitBranch = runCatching { git("rev-parse", "--abbrev-ref", "HEAD") }.getOrDefault("unknown")
// ".dirty" marks builds made from uncommitted changes — without it, every build
// between commits carries the same SHA and becomes indistinguishable.
val gitDirty = runCatching { git("status", "--porcelain").isNotEmpty() }.getOrDefault(false)
val gitSha = runCatching { git("rev-parse", "--short", "HEAD") }.getOrDefault("nogit") +
  if (gitDirty) ".dirty" else ""

android {
  namespace = "com.haptiq.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.haptiq.app"
    minSdk = 28
    targetSdk = 36
    // Versioning convention: every change batch bumps versionName by 0.01
    // (1.01 → 1.02 → …) and versionCode by 1. Majors reset the minor (2.00).
    versionCode = 15
    versionName = "1.13"
    // Public release codename, Android-dessert style: alphabetical, haptic-themed.
    // 1.0 "Aftershock" → next majors continue B, C, D… (Bassline? Crossfade?)
    buildConfigField("String", "RELEASE_CODENAME", "\"Aftershock\"")
    buildConfigField("String", "BUILD_CODENAME", "\"$gitBranch\"")
    buildConfigField("String", "GIT_SHA", "\"$gitSha\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      versionNameSuffix = "-$gitBranch+$gitSha"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }

}

// Self-describing APK names: Haptiq-1.05-ui-tinkering+abc1234.dirty-debug.apk
// instead of app-debug.apk, so sideloaded/uploaded builds stay tellable-apart.
androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      (output as? com.android.build.api.variant.impl.VariantOutputImpl)?.let { impl ->
        impl.outputFileName.set(
          impl.versionName.map { "Haptiq-$it-${variant.buildType}.apk" }
        )
      }
    }
  }
}

dependencies {
  // Compose BOM
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.ui.text.googlefonts)

  // Core
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Room
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  // Image loading
  implementation(libs.coil.compose)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Hilt DI
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  // Hilt 2.60 generated code uses error_prone annotations
  implementation("com.google.errorprone:error_prone_annotations:2.36.0")

  // Media3 ExoPlayer
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  // Testing
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // KSP
  "ksp"(libs.androidx.room.compiler)
}