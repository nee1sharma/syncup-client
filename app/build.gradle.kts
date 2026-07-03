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

val versionFile = rootProject.file("version.properties")
val versionProperties = Properties().apply {
    versionFile.inputStream().use(::load)
}
val releaseBundleRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').equals("bundleRelease", ignoreCase = true)
}
var appVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: error("VERSION_CODE in version.properties must be an integer")
val appVersionName = versionProperties.getProperty("VERSION_NAME")
    ?.takeIf { it.isNotBlank() }
    ?: error("VERSION_NAME is missing from version.properties")

if (releaseBundleRequested) {
    appVersionCode += 1
    versionFile.writeText(
        "VERSION_CODE=$appVersionCode\n" +
            "VERSION_NAME=$appVersionName\n"
    )
}

base {
    archivesName.set("SyncUp")
}

android {
    namespace = "com.hitstudio.syncup.client"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hitstudio.syncup.client"
        minSdk = 34
        targetSdk = 36
        versionCode = appVersionCode
        versionName = "$appVersionName.$appVersionCode"

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
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
