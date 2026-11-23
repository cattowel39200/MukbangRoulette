import java.util.Properties

plugins {
    id("com.android.application")
}

// 비밀값은 저장소에 두지 않는다: local.properties(커밋 안 됨) → 환경변수 순으로 읽음
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String? =
    localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("RELEASE_STORE_FILE") ?: "release.jks"
val releaseStorePassword = secret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secret("RELEASE_KEY_ALIAS") ?: "mukbang-roulette"
val releaseKeyPassword = secret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStorePassword != null && releaseKeyPassword != null &&
    file(releaseStoreFile).exists()

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
        versionCode = 8
        versionName = "2.10.0"
        val kakaoKey = secret("KAKAO_API_KEY") ?: ""
        if (kakaoKey.isEmpty()) {
            logger.warn("KAKAO_API_KEY가 없습니다. local.properties 또는 환경변수에 설정하세요 (검색이 동작하지 않음).")
        }
        buildConfigField("String", "KAKAO_API_KEY", "\"$kakaoKey\"")
    }

    // github: GitHub Pages APK 배포용 (앱 내 업데이트 안내 사용)
    // play:   Google Play 제출용 (스토어 정책상 자체 업데이트 안내 비활성화)
    flavorDimensions += "dist"
    productFlavors {
        create("github") {
            dimension = "dist"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("릴리스 서명 정보가 없어 서명되지 않은 릴리스가 만들어집니다 (RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD).")
            }
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
