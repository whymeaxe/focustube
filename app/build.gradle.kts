plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.himanshu.videos"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.himanshukhandelwal.focustube"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("BUILD_NUMBER") ?: "1")
        // Where the app looks for new versions (GitHub releases of this repo, tags "focustube-v<N>")
        buildConfigField("String", "UPDATE_REPO", "\"" + (System.getenv("UPDATE_REPO") ?: "") + "\"")
        buildConfigField("String", "UPDATE_TAG_PREFIX", "\"focustube-v\"")
        // Self-updating is not allowed on Google Play — set SELF_UPDATE=false for a Play build
        buildConfigField("boolean", "SELF_UPDATE", System.getenv("SELF_UPDATE") ?: "true")
    }

    signingConfigs {
        create("hv") {
            storeFile = rootProject.file("release.jks")
            // Real key + password come from GitHub secrets; never commit them.
            val pw = System.getenv("KEY_PASSWORD") ?: ""
            storePassword = pw
            keyAlias = System.getenv("KEY_ALIAS") ?: "focustube"
            keyPassword = pw
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("hv")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
