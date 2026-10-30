import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.concept.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.concept.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 手机版 (phone) 与平板版 (tablet) 签名配置：
    // 1. 手机版默认沿用与 v1.0.0 ~ v1.1.0 相同的 debug 签名配置（确保老版本用户直接覆盖升级不触发签名冲突，
    //    若需切换为独立证书可在 keystore.properties 中设置 phone.useDedicatedKeystore=true）。
    // 2. 平板版默认使用独立的 app/keystore/breezemusic-tablet.jks (tabletSign) 签名，并配合独立包名后缀 .tablet，
    //    从签名与包名双维度彻底隔离，杜绝与手机版产生 INSTALL_FAILED_UPDATE_INCOMPATIBLE 签名冲突。
    signingConfigs {
        create("phoneSign") {
            val phoneStorePath = keystoreProps.getProperty("phone.storeFile")
                ?: System.getenv("PHONE_KEYSTORE_PATH")
                ?: "keystore/breezemusic-phone.jks"
            val resolvedPhoneStore = file(phoneStorePath)
            if (resolvedPhoneStore.exists()) {
                storeFile = resolvedPhoneStore
                storePassword = keystoreProps.getProperty("phone.storePassword")
                    ?: System.getenv("PHONE_KEYSTORE_PASSWORD")
                    ?: "breezephone2026"
                keyAlias = keystoreProps.getProperty("phone.keyAlias")
                    ?: System.getenv("PHONE_KEY_ALIAS")
                    ?: "breeze_phone"
                keyPassword = keystoreProps.getProperty("phone.keyPassword")
                    ?: System.getenv("PHONE_KEY_PASSWORD")
                    ?: "breezephone2026"
            }
        }
        create("tabletSign") {
            val tabletStorePath = keystoreProps.getProperty("tablet.storeFile")
                ?: System.getenv("TABLET_KEYSTORE_PATH")
                ?: "keystore/breezemusic-tablet.jks"
            val resolvedTabletStore = file(tabletStorePath)
            if (resolvedTabletStore.exists()) {
                storeFile = resolvedTabletStore
                storePassword = keystoreProps.getProperty("tablet.storePassword")
                    ?: System.getenv("TABLET_KEYSTORE_PASSWORD")
                    ?: "breezetablet2026"
                keyAlias = keystoreProps.getProperty("tablet.keyAlias")
                    ?: System.getenv("TABLET_KEY_ALIAS")
                    ?: "breeze_tablet"
                keyPassword = keystoreProps.getProperty("tablet.keyPassword")
                    ?: System.getenv("TABLET_KEY_PASSWORD")
                    ?: "breezetablet2026"
            }
        }
    }

    // 双端平台构建变体（Product Flavors）：
    // - phone：面向安卓手机构建（applicationId = com.concept.music），默认沿用与 v1.1.0 一致的签名以支持无缝覆盖升级
    // - tablet：面向安卓平板与大屏设备构建（applicationId = com.concept.music.tablet），绑定 tabletSign 独立签名证书
    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            val useDedicatedPhoneJks = keystoreProps.getProperty("phone.useDedicatedKeystore")?.toBoolean() == true ||
                System.getenv("USE_DEDICATED_PHONE_KEYSTORE")?.toBoolean() == true
            val phoneSignCfg = signingConfigs.getByName("phoneSign")
            signingConfig = if (useDedicatedPhoneJks && phoneSignCfg.storeFile?.exists() == true) {
                phoneSignCfg
            } else {
                signingConfigs.getByName("debug")
            }
            buildConfigField("String", "DEVICE_FLAVOR", "\"phone\"")
            buildConfigField("boolean", "IS_TABLET_FLAVOR", "false")
        }
        create("tablet") {
            dimension = "device"
            applicationIdSuffix = ".tablet"
            versionNameSuffix = "-tablet"
            val tabletSignCfg = signingConfigs.getByName("tabletSign")
            signingConfig = if (tabletSignCfg.storeFile?.exists() == true) {
                tabletSignCfg
            } else {
                signingConfigs.getByName("debug")
            }
            buildConfigField("String", "DEVICE_FLAVOR", "\"tablet\"")
            buildConfigField("boolean", "IS_TABLET_FLAVOR", "true")
        }
    }

    buildTypes {
        debug {
            // 置空 buildType 级默认签名，使 phoneDebug 与 tabletDebug 各自继承所属 Flavor 的签名
            signingConfig = null
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 置空 buildType 级默认签名，使 phoneRelease 与 tabletRelease 各自使用所属 Flavor 的签名
            signingConfig = null
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core Android & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation & ViewModel
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // Image loading with Coil (including animated GIF / WebP support for comment stickers & images)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    // AndroidX Media3 (ExoPlayer & Background MediaSession)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Network & Serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
