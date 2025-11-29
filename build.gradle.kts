
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
//    id("com.android.application") version "8.0.2" // Asegúrate de usar la última versión estable
//    kotlin("android") version "1.9.0"
    kotlin("jvm") version "1.9.0" apply false

}

// build.gradle (Project)
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.gradle.v813)
        //classpath(libs.gradle) // Verifica la versión aquí
        classpath(libs.kotlin.gradle.plugin) // Verifica la versión aquí
    }
}




