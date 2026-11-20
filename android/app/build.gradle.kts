plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.voicedrop.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voicedrop.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "1.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Vosk On-Device Offline Speech Recognition
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
}

tasks.register("downloadVoskModel") {
    val modelDir = file("src/main/assets/model-ru")
    val marker = file("src/main/assets/model-ru/am/final.mdl")
    outputs.file(marker)
    doLast {
        if (!marker.exists()) {
            println("Downloading Vosk Russian model (~45MB)...")
            modelDir.mkdirs()
            val tempZip = File(buildDir, "vosk-model-ru.zip")
            tempZip.parentFile?.mkdirs()
            java.net.URL("https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip")
                .openStream().use { input ->
                    tempZip.outputStream().use { output -> input.copyTo(output) }
                }
            println("Unpacking Vosk Russian model...")
            copy {
                from(zipTree(tempZip))
                into(buildDir)
            }
            val extracted = buildDir.listFiles()?.find { it.name.startsWith("vosk-model-small-ru") }
            if (extracted != null) {
                copy {
                    from(extracted)
                    into(modelDir)
                }
                extracted.deleteRecursively()
            }
            tempZip.delete()
            println("Vosk Russian model ready.")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadVoskModel")
}

