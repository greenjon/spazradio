import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = null
        }
    }

    defaultConfig {
        applicationId = "llm.slop.spazradio"
        minSdk = 24
        targetSdk = 35
        versionCode = 151
        versionName = "1.5.1"
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
                "assets/dexopt/*",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-Xjvm-default=all"
        )
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
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

dependencyLocking {
    lockAllConfigurations()
}

androidComponents {
    onVariants { variant ->
        val capName = variant.name.replaceFirstChar { it.uppercase() }
        tasks.matching { it.name.contains("ArtProfile") }.configureEach {
            enabled = false
        }
        tasks.matching {
            it.name == "merge${capName}Assets" ||
            it.name == "package${capName}Resources" ||
            it.name == "process${capName}JavaRes"
        }.configureEach {
            doLast {
                outputs.files.forEach { root ->
                    if (root.exists()) {
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
}
