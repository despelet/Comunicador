plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.comunic"
    compileSdk = 35

    testNamespace = "com.comunic.free"

    defaultConfig {
        applicationId = "com.comunic"
        minSdk = 23
        targetSdk = 35
        versionCode = 13
        versionName = "1.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/delfi/AndroidStudioProjects/Comunic/bicom.jks")
            storePassword = "bicom2024"
            keyAlias = "bicom_key"
            keyPassword = "bicom2024"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    useLibrary("org.apache.http.legacy")
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.recyclerview)
    implementation(libs.picasso)

    // ⚠️ Dejar SOLO una versión de material
    implementation(libs.material.v1110)

    // 🔥 Google APIs (LIMPIO + SIN HTTPCLIENT)
//    implementation(libs.google.api.client.android.v1332) {
//        exclude(group = "org.apache.httpcomponents", module = "httpclient")
//        exclude(group = "commons-logging", module = "commons-logging")
//    }

//    implementation(libs.google.api.services.drive.vv3rev3051250) {
//        exclude(group = "org.apache.httpcomponents", module = "httpclient")
//        exclude(group = "commons-logging", module = "commons-logging")
//    }
//
//    implementation(libs.google.http.client.gson) {
//        exclude(group = "org.apache.httpcomponents", module = "httpclient")
//        exclude(group = "commons-logging", module = "commons-logging")
//    }

    implementation(libs.gms.play.services.auth.v2070)

    // ✅ Networking moderno
    implementation(libs.okhttp)

    implementation(libs.zip4j)
    implementation(libs.ucrop)
    implementation(libs.glide.v4160)
    implementation(libs.flexbox)

    // ROOM
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.splashscreen.v101)
    implementation(libs.gson) // json para exportar listas

    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))

    implementation(libs.firebase.auth)
    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
 //   implementation(libs.firebase.analytics)


    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries

}




