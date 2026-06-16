// Plugin de compilación de aplicaciones Android
plugins {
    alias(libs.plugins.android.application)
    // Plugin de Kotlin para Android (habilita código Kotlin en el proyecto)
    alias(libs.plugins.kotlin.android)
    // Plugin de serialización de Kotlin, necesario para @Serializable
    // y para que kotlinx.serialization funcione con Supabase
    alias(libs.plugins.kotlin.serialization)
}

android {
    // Identificador único del paquete de la aplicación
    namespace = "com.example.fodmanager"

    // API de Android usada para compilar; 36 = Android 16
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // ID único de la app en Google Play y en el dispositivo
        applicationId = "com.example.fodmanager"

        // API mínima soportada: 24 = Android 7.0 (Nougat)
        // Cubre la gran mayoría de dispositivos Android en uso
        minSdk = 24

        // API objetivo para la que se optimiza la app
        targetSdk = 36

        // Código de versión interno (entero incremental para el sistema)
        versionCode = 1
        // Cadena de versión visible al usuario
        versionName = "1.0"

        // Runner de tests de instrumentación estándar de AndroidX
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Minificación desactivada en release; activar en producción real
            // para reducir el tamaño del APK y ofuscar el código
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Java 11 como versión de compatibilidad de código fuente y bytecode
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // Core Library Desugaring: permite usar APIs de Java modernas (java.time.*)
        // en dispositivos con minSdk < 26, necesario para LocalDateTime,
        // OffsetDateTime y Duration usados en el cálculo de duraciones de incidencias
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        // JVM target alineado con compileOptions
        jvmTarget = "11"
    }

    packaging {
        resources {
            // Excluye ficheros de licencia duplicados de las dependencias
            // que causarían conflictos al empaquetar el APK
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── AndroidX Core ────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)          // Extensiones Kotlin para Android
    implementation(libs.androidx.appcompat)          // Compatibilidad hacia atrás para ActionBar, etc.
    implementation(libs.material)                    // Componentes Material Design (CardView, FAB, TextInputLayout…)
    implementation(libs.androidx.activity)           // Soporte para ActivityResultContracts y callbacks
    implementation(libs.androidx.constraintlayout)   // Layout de restricciones (no usado activamente, incluido por defecto)
    implementation(libs.androidx.datastore.core)     // DataStore (incluido por defecto; no se usa en el proyecto actual)

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)                          // Tests unitarios JUnit 4
    androidTestImplementation(libs.androidx.junit)          // Tests de instrumentación AndroidX
    androidTestImplementation(libs.androidx.espresso.core)  // Tests de UI con Espresso

    // ── Supabase SDK ──────────────────────────────────────────────────────────
    // Versión 2.6.1 con Ktor 2.3.12 (v3.0.2 causó errores de resolución de dependencias)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)  // Consultas SQL: SELECT, INSERT, UPDATE, DELETE
    implementation(libs.supabase.auth)       // Autenticación: signInWith, signUpWith, gestión de sesiones
    implementation(libs.supabase.storage)    // Almacenamiento de imágenes FOD en el bucket "fod-images"



    // Cliente HTTP de Ktor para Android, requerido por el SDK de Supabase
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.utils)

    // ── Serialización ─────────────────────────────────────────────────────────
    // Necesario para las clases @Serializable usadas en las peticiones a Supabase
    implementation(libs.kotlinx.serialization.json)

    // ── Carga de imágenes ─────────────────────────────────────────────────────
    // Glide: carga y cachea las imágenes de incidencias FOD desde Supabase Storage
    implementation(libs.glide)

    // ── Desugaring ────────────────────────────────────────────────────────────
    // Biblioteca de soporte para APIs Java modernas en dispositivos con minSdk < 26.
    // Imprescindible para java.time.* (LocalDateTime, OffsetDateTime, Duration)
    // que se usan extensamente en el cálculo de fechas y duraciones de incidencias
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    dependencies {
        // Librería PhotoView para permitir zoom con dedos sobre imágenes
        implementation("io.getstream:photoview:1.0.0")
    }

    implementation("com.onesignal:OneSignal:5.6.1")
}