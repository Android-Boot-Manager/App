plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin")
}

android {
    namespace = "org.andbootmgr.app"
    compileSdk = 34
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        dex {
            useLegacyPackaging = false
        }
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "META-INF/*.version"
        }
    }
    defaultConfig {
        applicationId = "org.andbootmgr.app"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 32
        versionCode = 3001
        versionName = "0.3.0-m0"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }
    signingConfigs {
        register("release") {
            if (project.hasProperty("ABM_RELEASE_KEY_ALIAS")) {
                storeFile = file(project.properties["ABM_RELEASE_STORE_FILE"].toString())
                storePassword = project.properties["ABM_RELEASE_STORE_PASSWORD"].toString()
                keyAlias = project.properties["ABM_RELEASE_KEY_ALIAS"].toString()
                keyPassword = project.properties["ABM_RELEASE_KEY_PASSWORD"].toString()
            }
        }
        getByName("debug") {
            if (project.hasProperty("ABM_DEBUG_KEY_ALIAS")) {
                storeFile = file(project.properties["ABM_DEBUG_STORE_FILE"].toString())
                storePassword = project.properties["ABM_DEBUG_STORE_PASSWORD"].toString()
                keyAlias = project.properties["ABM_DEBUG_KEY_ALIAS"].toString()
                keyPassword = project.properties["ABM_DEBUG_KEY_PASSWORD"].toString()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (project.hasProperty("ABM_RELEASE_KEY_ALIAS")) {
                signingConfig = signingConfigs["release"]
            }
            buildConfigField("boolean", "DEFAULT_NOOB_MODE", "false") // Noob mode default
        }
        debug {
            if (project.hasProperty("ABM_DEBUG_KEY_ALIAS")) {
                signingConfig = signingConfigs["debug"]
            } else if (project.hasProperty("ABM_RELEASE_KEY_ALIAS")) {
                signingConfig = signingConfigs["release"]
            }
            buildConfigField("boolean", "DEFAULT_NOOB_MODE", "false") // Noob mode default
        }
    }
    java {
        compileOptions {
            toolchain {
                languageVersion = JavaLanguageVersion.of(17)
            }
        }
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs = listOf(
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions"
            )
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.1.1"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    applicationVariants.configureEach {
        tasks["merge${name.replaceFirstChar(Char::titlecase)}Assets"].dependsOn(tasks["setAssetTs"])
    }
}

tasks.register("setAssetTs", Task::class) {
    doLast {
        File("$rootDir/app/src/main/assets/cp/_ts").writeText((System.currentTimeMillis() / 1000L).toString())
    }
}

dependencies {
	implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.compose.ui:ui:1.6.8")
    // Tooling support (Previews, etc.)
    implementation("androidx.compose.ui:ui-tooling:1.6.8")
    // Foundation (Border, Background, Box, Image, Scroll, shapes, animations, etc.)
    implementation("androidx.compose.foundation:foundation:1.6.8")
    // Material Design
    implementation("androidx.compose.material3:material3:1.2.1")
    // Material design icons
    implementation("androidx.compose.material:material-icons-core:1.6.8")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    // Integration with activities
    implementation("androidx.activity:activity-compose:1.9.1")
    // Integration with ViewModels
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // Integration with observables
    implementation("androidx.compose.runtime:runtime-livedata:1.6.8")
    implementation("androidx.compose.runtime:runtime-rxjava2:1.6.8")

    implementation("androidx.navigation:navigation-compose:2.7.7")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
	implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
	androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
	debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
    implementation("com.github.skydoves:colorpicker-compose:1.1.2")


	val libsuVersion = "5.0.1"
    implementation("com.github.topjohnwu.libsu:core:${libsuVersion}")
    implementation("com.github.topjohnwu.libsu:io:${libsuVersion}")
    implementation("com.mikepenz:aboutlibraries:11.2.2")

    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")

}
