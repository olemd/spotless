plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from the environment (set by CI); without it we fall back to the debug key.
val releaseKeystore = System.getenv("SPOTLESS_KEYSTORE")?.let(::file)?.takeIf { it.isFile }

android {
    namespace = "work.silly.spotless"
    compileSdk = 35

    defaultConfig {
        applicationId = "work.silly.spotless"
        minSdk = 26
        targetSdk = 35
        // Overridable from the command line: -PversionCode=… -PversionName=…
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("SPOTLESS_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SPOTLESS_KEY_ALIAS")
            keyPassword = System.getenv("SPOTLESS_KEY_PASSWORD") ?: System.getenv("SPOTLESS_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Sideload build: signed with the debug key unless a release keystore is configured.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // org.json is in the Android runtime, but not on the JVM during unit tests.
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    systemProperty("live", System.getProperty("live") ?: "false")
    testLogging { showStandardStreams = true; events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
