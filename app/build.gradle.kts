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
            isShrinkResources = false // Keep false for now as per your file
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
        // Force an older version that only requires API 34 or 35
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")

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

//// THE F-DROID FIX:
//// This hooks into the task graph to delete the profiles from the staging area
//// AFTER they are generated but BEFORE the APK is zipped.
//androidComponents {
//    onVariants { variant ->
//        val capName = variant.name.replaceFirstChar { it.uppercase() }
//
//        // Target the process that actually puts assets into the APK structure
//        tasks.matching { it.name.contains("package${capName}Resources") || it.name.contains("merge${capName}Assets") }
//            .configureEach {
//                doLast {
//                    outputs.files.forEach { file ->
//                        // Path 1: assets/dexopt/
//                        val dexoptDir = File(file, "assets/dexopt")
//                        if (dexoptDir.exists()) {
//                            println("F-Droid: Stripping ${dexoptDir.absolutePath}")
//                            dexoptDir.deleteRecursively()
//                        }
//                        // Path 2: flat baseline.prof in the root
//                        val rootProf = File(file, "baseline.prof")
//                        if (rootProf.exists()) rootProf.delete()
//                    }
//                }
//            }
//    }
//}
// Add this to the very bottom of /app/build.gradle.kts
androidComponents {onVariants { variant ->
    val capName = variant.name.replaceFirstChar { it.uppercase() }

    // 1. Disable the task that compiles the profiles into binary
    tasks.matching { it.name.contains("ArtProfile") }.configureEach {
        enabled = false
    }

    // 2. Brutally delete the dexopt directory from all possible intermediate outputs
    tasks.matching {
        it.name.contains("merge${capName}Assets") ||
                it.name.contains("package${capName}Resources") ||
                it.name.contains("process${capName}JavaRes")
    }.configureEach {
        doLast {
            outputs.files.forEach { root ->
                // Recursively look for any file named baseline.prof or any directory named dexopt
                root.walkBottomUp().forEach { file ->
                    if (file.name == "baseline.prof" || file.name == "baseline.profm" || file.name == "dexopt") {
                        println("F-Droid: Deleting non-deterministic file: ${file.absolutePath}")
                        file.deleteRecursively()
                    }
                }
            }
        }
    }
}
}
