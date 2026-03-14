plugins {
    id("com.android.application")
}

android {
    namespace = "com.mukbang.roulette"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.mukbang.roulette"
        minSdk = 21
        targetSdk = 35
        versionCode = 7
        versionName = "2.9.0"
        buildConfigField("String", "KAKAO_API_KEY", "\"5f998820ea219e73861b052ca67453db\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "mukbang2026!"
            keyAlias = "mukbang-roulette"
            keyPassword = "mukbang2026!"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.webkit:webkit:1.9.0")
}
