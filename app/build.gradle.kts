import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keyPropertiesFile = rootProject.file("key.properties")
val keyProperties = Properties()
if (keyPropertiesFile.exists()) {
    FileInputStream(keyPropertiesFile).use { keyProperties.load(it) }
}

android {
    namespace = "llm.slop.spazradio"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val isMasterRequested = project.hasProperty("useMasterKey") || project.findProperty("target") == "standalone"

            val envStorePath = System.getenv("KEYSTORE_PATH")
            val storePath = when {
                !envStorePath.isNullOrEmpty() -> envStorePath
                isMasterRequested && keyProperties.getProperty("masterStoreFile") != null -> keyProperties.getProperty("masterStoreFile")
                !isMasterRequested && keyProperties.getProperty("playStoreFile") != null -> keyProperties.getProperty("playStoreFile")
                keyProperties.getProperty("storeFile") != null -> keyProperties.getProperty("storeFile")
                else -> null
            }

            val storePass = System.getenv("KEYSTORE_PASSWORD")
                ?: (if (isMasterRequested) keyProperties.getProperty("masterStorePassword") else keyProperties.getProperty("playStorePassword"))
                ?: keyProperties.getProperty("storePassword")

            val aliasName = System.getenv("KEY_ALIAS")
                ?: (if (isMasterRequested) keyProperties.getProperty("masterKeyAlias") else keyProperties.getProperty("playKeyAlias"))
                ?: keyProperties.getProperty("keyAlias")

            val keyPass = System.getenv("KEY_PASSWORD")
                ?: (if (isMasterRequested) keyProperties.getProperty("masterKeyPassword") else keyProperties.getProperty("playKeyPassword"))
                ?: keyProperties.getProperty("keyPassword")

            if (storePath != null && File(storePath).exists() && !storePass.isNullOrEmpty() && !aliasName.isNullOrEmpty()) {
                storeFile = File(storePath)
                storePassword = storePass
                keyAlias = aliasName
                keyPassword = keyPass ?: storePass
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildFeatures {
        compose = true
    }

configurations.all {
    resolutionStrategy {
        exclude(group = "androidx.compose.compiler", module = "compiler")
    }
}

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    defaultConfig {
        applicationId = "llm.slop.spazradio"
        minSdk = 24
        targetSdk = 36
        versionCode = 163
        versionName = "1.6.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
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
