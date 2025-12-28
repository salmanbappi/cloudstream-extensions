plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.cloudstream.extensions.dhakaflix"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // CloudStream API
    compileOnly("com.github.recloudstream:cloudstream:pre-release")
    
    implementation("org.jsoup:jsoup:1.17.2")
}
