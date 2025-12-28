import com.lagradost.cloudstream3.gradle.CloudstreamExtension

version = 1

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.lagradost.cloudstream3.gradle")
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
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions"
        )
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

cloudstream {
    setRepo("https://github.com/salmanbappi/cloudstream-extensions")
    authors = listOf("salmanbappi")
    description = "DhakaFlix provider for CloudStream"
}

dependencies {
    val cloudstream by configurations
    val implementation by configurations

    cloudstream("com.github.recloudstream.cloudstream:-SNAPSHOT")
    
    implementation(kotlin("stdlib"))
    implementation("com.github.Blatzar:NiceHttp:0.4.13")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.github.recloudstream.cloudstream:library:-SNAPSHOT")
}
