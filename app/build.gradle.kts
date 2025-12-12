import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "llm.slop.spazradio"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "llm.slop.spazradio" // Verify this is your ID
        minSdk = 24 // Or whatever you have
        targetSdk = 35 // Or whatever you have

        // --- AUTOMATIC VERSIONING LOGIC ---
        val gitCommitCount = try {
            val stdout = ByteArrayOutputStream()
            exec {
                commandLine = listOf("git", "rev-list", "--count", "HEAD")
                standardOutput = stdout
            }
            stdout.toString().trim().toInt()
        } catch (e: Exception) {
            1 // Fallback if git fails (e.g. during a fresh sync)
        }

        // Version Code = Total number of commits (e.g., 154)
        versionCode = gitCommitCount
        // Version Name = 1.0.154
        versionName = "1.0.$gitCommitCount"
        // ----------------------------------

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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)
    implementation("com.google.errorprone:error_prone_annotations:2.45.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}