import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun String.escapeForBuildConfig(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val authClientSecret: String = providers.gradleProperty("AUTH_CLIENT_SECRET").orNull
    ?: localProperties.getProperty("AUTH_CLIENT_SECRET", "")
val authAccessKey: String = providers.gradleProperty("AUTH_ACCESS_KEY").orNull
    ?: localProperties.getProperty("AUTH_ACCESS_KEY", "")
val authSecretAccessKey: String = providers.gradleProperty("AUTH_SECRET_ACCESS_KEY").orNull
    ?: localProperties.getProperty("AUTH_SECRET_ACCESS_KEY", "")

android {
    namespace = "com.tjlabs.sdk_sample_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tjlabs.sdk_sample_app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField(
            "String",
            "AUTH_CLIENT_SECRET",
            "\"${authClientSecret.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "AUTH_ACCESS_KEY",
            "\"${authAccessKey.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "AUTH_SECRET_ACCESS_KEY",
            "\"${authSecretAccessKey.escapeForBuildConfig()}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
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
    implementation(project(":sdk"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
}
