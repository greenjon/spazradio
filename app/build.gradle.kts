import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "llm.slop.spazradio"
    compileSdk = 35

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro"
            )
        }
    }

    defaultConfig {
        applicationId = "llm.slop.spazradio"
        minSdk = 24
        targetSdk = 35
        versionCode = 131
        versionName = "1.3.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf(
                "**/baseline.prof",
                "**/baseline.profm",
                "**/*.baseline.prof",
                "**/*.baseline.profm",
                "assets/dexopt/*"
            )
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

configurations.all {
    resolutionStrategy {
        // Force stable versions that do NOT require SDK 36 / XR
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
        force("androidx.activity:activity:1.9.3")
        force("androidx.activity:activity-compose:1.9.3")
        force("androidx.activity:activity-ktx:1.9.3")
        force("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
        force("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
        
        // Block androidx.tracing from mismatched versions
        force("androidx.tracing:tracing:1.2.0")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)
    implementation(libs.paho)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

androidComponents {
    onVariants { variant ->
        val capName = variant.name.replaceFirstChar { it.uppercase() }
        tasks.matching { it.name.contains("ArtProfile") }.configureEach {
            enabled = false
        }
        tasks.matching {
            it.name.contains("merge${capName}Assets") ||
                    it.name.contains("package${capName}Resources") ||
                    it.name.contains("process${capName}JavaRes")
        }.configureEach {
            doLast {
                outputs.files.forEach { root ->
                    root.walkBottomUp().forEach { file ->
                        if (file.name == "baseline.prof" || file.name == "baseline.profm" || file.name == "dexopt") {
                            file.deleteRecursively()
                        }
                    }
                }
            }
        }
    }
}
