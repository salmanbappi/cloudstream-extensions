buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

tasks.register("make") {
    dependsOn(":app:assembleRelease")
    doLast {
        val buildsDir = File(projectDir, "builds")
        buildsDir.mkdirs()
        
        val apkFile = File(projectDir, "app/build/outputs/apk/release/app-release-unsigned.apk")
        val targetFile = File(buildsDir, "BDIX.cst")
        
        if (apkFile.exists()) {
            apkFile.copyTo(targetFile, true)
        } else {
            val signedApk = File(projectDir, "app/build/outputs/apk/release/app-release.apk")
            if (signedApk.exists()) {
                signedApk.copyTo(targetFile, true)
            } else {
                throw GradleException("Could not find generated APK file in app/build/outputs/apk/release/")
            }
        }
        
        // Generate plugins.json
        val pluginsJson = """
        [
          {
            "name": "BDIX",
            "filename": "BDIX",
            "version": 1,
            "description": "DhakaFlix and FtpBd providers for CloudStream",
            "author": "salmanbappi",
            "repository": "https://github.com/salmanbappi/cloudstream-extensions",
            "tvTypes": ["Movie", "TvSeries"],
            "language": "bn",
            "iconUrl": "https://raw.githubusercontent.com/salmanbappi/cloudstream-extensions/main/icon.png",
            "url": "https://raw.githubusercontent.com/salmanbappi/cloudstream-extensions/builds/BDIX.cst"
          }
        ]
        """.trimIndent()
        File(buildsDir, "plugins.json").writeText(pluginsJson)
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}