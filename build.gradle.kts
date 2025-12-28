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

task("make") {
    dependsOn(":app:assembleRelease")
    doLast {
        val buildsDir = file("builds")
        buildsDir.mkdirs()
        
        val apkFile = file("app/build/outputs/apk/release/app-release-unsigned.apk")
        if (!apkFile.exists()) {
            // Try signed if unsigned doesn't exist
            val signedApk = file("app/build/outputs/apk/release/app-release.apk")
            if (signedApk.exists()) {
                signedApk.copyTo(file(buildsDir, "BDIX.cst"), true)
            }
        } else {
            apkFile.copyTo(file(buildsDir, "BDIX.cst"), true)
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
        file(buildsDir, "plugins.json").writeText(pluginsJson)
    }
}

task("clean", Delete::class) {
    delete(rootProject.buildDir)
}
