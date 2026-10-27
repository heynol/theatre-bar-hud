import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.heynol.barhud"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.heynol.barhud"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release key: a properties file (storeFile, storePassword, keyAlias, keyPassword) outside the repo,
    // from $HEYNOL_SIGNING_PROPERTIES or ~/Keystores/heynol-sideload.properties. Without it the release
    // build falls back to the debug key, so anyone can build an installable APK from source.
    val signingPropertiesFile = (System.getenv("HEYNOL_SIGNING_PROPERTIES")?.takeIf { it.isNotBlank() }
        ?.let(::File) ?: File(System.getProperty("user.home"), "Keystores/heynol-sideload.properties"))
        .takeIf { it.isFile }
    val signingProperties = signingPropertiesFile
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

    signingConfigs {
        if (signingProperties != null) {
            create("release") {
                // A relative storeFile is resolved next to the properties file.
                storeFile = signingPropertiesFile!!.parentFile.resolve(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (signingProperties != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("Theatre Bar HUD: no release signing properties found, signing the release build with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    packaging {
        // Only read by kotlin-reflect and the coroutines debug agent, neither of which ships.
        resources.excludes += setOf("kotlin/**", "DebugProbesKt.bin", "META-INF/*.version", "META-INF/*.kotlin_module")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
