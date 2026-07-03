import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val releaseKeystoreFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystoreFile.exists()) {
        releaseKeystoreFile.inputStream().use(::load)
    }
}

fun releaseProperty(name: String): String =
    releaseKeystoreProperties.getProperty(name)
        ?.takeIf { it.isNotBlank() }
        ?: error("Missing '$name' in keystore.properties")

base {
    archivesName.set("SyncUp")
}

android {
    namespace = "com.hitstudio.syncup"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hitstudio.syncup"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(releaseProperty("storeFile"))
                storePassword = releaseProperty("storePassword")
                keyAlias = releaseProperty("keyAlias")
                keyPassword = releaseProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
