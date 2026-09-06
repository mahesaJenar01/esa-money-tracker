import java.util.Properties

/**
 * Reads one of the project's `.properties` files, or comes back empty.
 *
 * Empty is a normal answer, not a failure: the version file has defaults worth
 * falling back on, and a machine without the signing key must still be able to
 * build and run the app.
 */
fun projectProperties(name: String): Properties = Properties().apply {
    val file = rootProject.file(name)
    if (file.exists()) file.inputStream().use { load(it) }
}

val versionProperties = projectProperties("version.properties")

/**
 * What the phone compares when deciding whether an APK is an update.
 *
 * It has to go up on every release or the install is refused, which is why it
 * lives in a file that `run.bat release` bumps rather than in a number here
 * that is easy to forget.
 */
val appVersionCode = versionProperties.getProperty("versionCode")?.toIntOrNull() ?: 1
val appVersionName = versionProperties.getProperty("versionName") ?: "1.0"

/**
 * The signing key, when this machine has one.
 *
 * A release APK signed with a different key than the copy already on the phone
 * is not an update — Android treats it as a different app and refuses to
 * install it over the top. Keeping one key for the life of the app is the whole
 * reason updates can be a download rather than a reinstall.
 */
val keystoreProperties = projectProperties("keystore.properties")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.esa.moneytracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.esa.moneytracker"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        val storePath = keystoreProperties.getProperty("storeFile")
        if (!storePath.isNullOrBlank()) {
            create("release") {
                // Absolute paths pass straight through; anything else is read
                // relative to the project folder.
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Null on a machine with no key: the build still succeeds, it just
            // produces an APK no phone will install. `run.bat release` says so
            // rather than leaving it to be discovered on the phone.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Room schemas are exported so the storage format stays inspectable and
    // migrations (and future data exports) can be reasoned about over time.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    // The balance arithmetic is plain Kotlin with no Android in it, which is
    // what makes "money is only ever moved, never created" testable at all.
    testImplementation(libs.junit)
}
