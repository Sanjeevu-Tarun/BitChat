plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.2.0"
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" // ✅ Required for Kotlin 2.0+ Compose
}

android {
    namespace = "com.bitchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bitchat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ✅ Kotlin 2.2 JVM target via compilerOptions DSL
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.androidx.appcompat)

    // Accompanist & Navigation
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.navigation.compose)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx.v270)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)

    // Jetpack Compose + Material3
    implementation(libs.androidx.activity.compose.v190)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.ui.tooling)

    // Material Design (legacy)
    implementation(libs.material)

    // Firebase
    implementation(libs.firebase.inappmessaging.ktx.v2041)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Room with KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Notifications, Background Work
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)

    // ✅ PullRefresh is now included in Material3 core library

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v115)
    androidTestImplementation(libs.androidx.espresso.core.v351)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
}