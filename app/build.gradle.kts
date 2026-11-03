import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val releaseKeystoreFile = rootProject.file("keystore.properties")
val releaseKeystore = Properties().apply {
    if (releaseKeystoreFile.exists()) {
        releaseKeystoreFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.balsaroja.yapecompanion"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.balsaroja.yapecompanion"
        // Android 6.0+; las APIs posteriores se invocan con comprobación de SDK.
        minSdk = 23
        targetSdk = 37
        versionCode = 159
        versionName = "1.5.9"
    }

    signingConfigs {
        if (releaseKeystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseKeystore.getProperty("storeFile")))
                storePassword = requireNotNull(releaseKeystore.getProperty("storePassword"))
                keyAlias = requireNotNull(releaseKeystore.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseKeystore.getProperty("keyPassword"))
                // V1 permite instalar en Android 6; V2/V3 protegen equipos modernos.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
}
