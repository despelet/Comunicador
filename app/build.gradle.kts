import org.jetbrains.kotlin.storage.CacheResetOnProcessCanceled
import org.jetbrains.kotlin.storage.CacheResetOnProcessCanceled.enabled



plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.parcelize")

}


android {

    namespace = "com.comunic"
    compileSdk = 35

    //namespace = "mobile.template"
    testNamespace = "com.comunic.free"

    defaultConfig {
        applicationId = "com.comunic"
        minSdk = 21
        targetSdk = 35
        versionCode = 7
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/delfi/AndroidStudioProjects/Comunic/bicom.jks")  // Reemplaza con la ruta de tu archivo .jks
            storePassword = "bicom2024"    // Reemplaza con la contraseña de tu keystore
            keyAlias = "bicom_key"                       // Reemplaza con el alias de tu clave
            keyPassword = "bicom2024"         // Reemplaza con la contraseña de tu clave
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")  // Aquí estamos asociando la configuración de firma
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    packagingOptions {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout)
    // implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // para agregar la biblioteca RecyclerView
    implementation(libs.androidx.recyclerview)
    // para agregar Picasso para trabajar con imagenes
    implementation(libs.picasso)
    // para agregar temas
    //implementation(libs.androidx.appcompat.v161)
    implementation(libs.androidx.appcompat.v161)
    implementation(libs.material)
    //implementation(libs.glide)
    implementation(libs.play.services.auth) // Para autenticación
    implementation(libs.google.api.client.android) // Cliente API
    implementation(libs.google.api.services.drive) // API Drive
    implementation(libs.gms.play.services.auth.v2070)
    implementation(libs.google.api.client.android.v1332)
    implementation(libs.google.http.client.gson)
    implementation(libs.google.api.services.drive.vv3rev3051250)
    implementation(libs.zip4j) // Para trabajar con archivos zip

    configurations.all {
        resolutionStrategy.force("com.google.api-client:google-api-client-android:1.33.2")
    }

    implementation(libs.ucrop)
    implementation(libs.glide.v4160)
    //kapt(libs.compiler) // si usás anotaciones

    // ROOM: bases de datos
    val roomVersion = "2.6.1"
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
   //ksp(libs.androidx.room.compiler) // Recomendado
    kapt(libs.androidx.room.compiler) // Si usás kapt en lugar de KSP
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.material.v1110)

}


