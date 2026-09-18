plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.calendarremember"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calendarremember"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // El motor de la palabra clave trae librerias nativas para cada tipo
        // de procesador. Los moviles de verdad son todos ARM: dejar fuera los
        // x86 de los emuladores ahorra muchos megas en el APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        // El APK que se instala a mano sale de aqui. Sin minificar: no merece
        // la pena pelearse con ProGuard por una app que no pasa por la tienda.
        release {
            isMinifyEnabled = false
            // Se firma con la clave de depuracion para que el APK de Releases
            // se pueda instalar directamente. No va a Google Play.
            signingConfig = signingConfigs.getByName("debug")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    // Vosk: reconocimiento de voz sin conexion para escuchar "Nebula".
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation("junit:junit:4.13.2")
}
