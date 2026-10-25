// D:\ConanTCG\app\build.gradle.kts
// App-level Gradle file: apply each plugin ONCE.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Firebase Google Services
    id("com.google.gms.google-services")
}


android {
    namespace = "com.example.conantcg"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.conantcg"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Tools & Networking
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("dev.shreyaspatil:capturable:2.1.0")

    // Window size & translation cache
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // AI & Camera
    // Keep the old SDK temporarily until GoogleTranslator.kt migration is complete.
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("com.google.guava:guava:33.0.0-android")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.accompanist:accompanist-permissions:0.31.1-alpha")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Firebase AI Logic & App Check
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
}
