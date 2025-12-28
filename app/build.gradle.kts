plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.cloudstream.extensions.dhakaflix"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cloudstream.extensions.dhakaflix"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // CloudStream API
    compileOnly("com.github.recloudstream:cloudstream:pre-release")
    
    implementation("org.jsoup:jsoup:1.17.2")
}
