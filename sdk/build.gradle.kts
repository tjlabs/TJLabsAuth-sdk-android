import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 9


android {
    namespace = "com.tjlabs.tjlabsauth_sdk_android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            consumerProguardFiles("consumer-rules.pro")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    libraryVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "app-release-auth.aar"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation ("androidx.security:security-crypto-ktx:1.1.0-alpha03")
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.tjlabs"
                artifactId = "TJLabsAuth-sdk-android"
                version = "$versionMajor.$versionMinor.$versionPatch"
            }
        }
    }
}
