import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
//    id("com.android.application")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 1


// local.properties 파일 읽기
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

val tjGroupId: String = localProperties.getProperty("GROUP_ID") ?: ""
val tjArtifactId: String = localProperties.getProperty("ARTIFACT_ID") ?: ""


android {
    namespace = "com.tjlabs.tjlabsauth_sdk_android"
    compileSdk = 35

    defaultConfig {
//        applicationId = "com.tjlabs.tjlabsauth_sdk_android"
//        versionCode = 1
//        versionName = "1.0"
        minSdk = 29
        targetSdk = 34


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

//    libraryVariants.all {
//        outputs.all {
//            (this as BaseVariantOutputImpl).outputFileName = "app-release-aegis.aar"
//        }
//    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation ("androidx.security:security-crypto-ktx:1.1.0-alpha03")
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.security.crypto.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.tjlabs"
                artifactId = "TJLabsAuth-sdk-android"
                version = "$versionMajor.$versionMinor.$versionPatch"
            }
        }
    }
}
