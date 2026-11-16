import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  jacoco
}

val signingProperties = Properties().apply {
    val propertiesFile = rootProject.file("signing.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}
val releaseSigningValues = mapOf(
    "storeFile" to "NONOTI_KEYSTORE_PATH",
    "storePassword" to "NONOTI_STORE_PASSWORD",
    "keyAlias" to "NONOTI_KEY_ALIAS",
    "keyPassword" to "NONOTI_KEY_PASSWORD",
).mapValues { (property, environment) ->
    providers.environmentVariable(environment).orNull ?: signingProperties.getProperty(property)
}
val releaseKeystore = releaseSigningValues["storeFile"]
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    val missingProperties = releaseSigningValues.filterValues { it.isNullOrBlank() }.keys
    val keystore = releaseKeystore
    doLast {
        check(missingProperties.isEmpty()) {
            "Missing release signing configuration: ${missingProperties.joinToString()}. " +
                "See .github/RELEASING.md."
        }
        check(keystore?.isFile == true) { "Release keystore does not exist. See .github/RELEASING.md." }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}

android {
    namespace = "com.example.nonoti"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.nonoti"
        minSdk = 33
        targetSdk = 36
        versionCode = 9
        versionName = "0.1.8"
        testInstrumentationRunner = "com.example.nonoti.NonotiTestRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = releaseSigningValues["storePassword"]
            keyAlias = releaseSigningValues["keyAlias"]
            keyPassword = releaseSigningValues["keyPassword"]
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.google.hilt.android)
  ksp(libs.google.hilt.compiler)

  // Persistence
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  implementation(libs.haze)
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
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.google.hilt.android.testing)
  kspAndroidTest(libs.google.hilt.compiler)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
