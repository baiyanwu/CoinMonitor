import java.util.Properties

// 统一维护应用版本信息，避免版本展示与打包产物命名脱节。
val appArtifactName = "coinmonitor"
val appVersionName = "1.0.15"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Gradle 直接从外部配置文件按渠道读取版本代码；可用 -PdistributionConfigFile 指定文件。
val distributionConfigFile = rootProject.file(
    providers.gradleProperty("distributionConfigFile")
        .orElse("config/distribution.properties")
        .get()
)
val distributionProperties = Properties().apply {
    distributionConfigFile.inputStream().use(::load)
}
fun distributionVersionCode(channel: String): Int = distributionProperties.getProperty("$channel.versionCode")
    ?.trim()?.toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
    ?: error("$channel.versionCode must be an integer between 1 and 2100000000")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
    arg("room.incremental", "true")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun readSigningProperty(localKey: String, envKey: String): String? {
    return localProperties.getProperty(localKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
}

val releaseStoreFile = readSigningProperty("release.storeFile", "ANDROID_RELEASE_STORE_FILE")
val releaseStorePassword = readSigningProperty("release.storePassword", "ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = readSigningProperty("release.keyAlias", "ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = readSigningProperty("release.keyPassword", "ANDROID_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
if (providers.gradleProperty("requireReleaseSigning").orNull == "true") {
    require(hasReleaseSigning) {
        "Release signing is required. Configure the existing release keystore in local.properties or CI secrets."
    }
}

android {
    namespace = "io.baiyanwu.coinmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.baiyanwu.coinmonitor"
        minSdk = 26
        targetSdk = 36
        versionName = appVersionName
        // 让 APK / AAB 产物名自动携带版本号，便于发布与归档识别。
        setProperty("archivesBaseName", "$appArtifactName-v$appVersionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            versionCode = distributionVersionCode(name)
        }
        create("play") {
            dimension = "distribution"
            versionCode = distributionVersionCode(name)
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            // 统一 debug 签名，避免不同开发者编译的开发版需要重新覆盖安装
            signingConfig = signingConfigs.getByName("debug").apply {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.trustwallet.core)
    implementation(libs.trustwallet.web3.provider)
    implementation(libs.androidx.webkit)
    implementation(libs.zxing.core)
    implementation(project(":lib"))
    implementation(project(":third_party:lightweightlibrary"))

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-core:5.14.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
