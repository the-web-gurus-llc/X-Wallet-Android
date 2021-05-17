import x.BrdRelease
import x.Libs

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
}

apply(plugin = "io.sweers.redacted.redacted-plugin")

project.tasks.register<x.DownloadBundles>("downloadBundles")

android {
    compileSdkVersion(BrdRelease.ANDROID_COMPILE_SDK)
    buildToolsVersion(BrdRelease.ANDROID_BUILD_TOOLS)
    defaultConfig {
        minSdkVersion(BrdRelease.ANDROID_MINIMUM_SDK)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    androidExtensions {
        isExperimental = true
    }
}

dependencies {
    implementation(project(":theme"))
    implementation(Libs.Kotlin.StdLibJdk8)
    implementation(Libs.Coroutines.Core)
    api(Libs.WalletKit.CoreAndroid)

    implementation(Libs.Androidx.LifecycleExtensions)
    implementation(Libs.Androidx.AppCompat)
    implementation(Libs.Androidx.CardView)
    implementation(Libs.Androidx.CoreKtx)
    api(Libs.Androidx.ConstraintLayout)
    implementation(Libs.Androidx.GridLayout)
    implementation(Libs.Zxing.Core)

    implementation(Libs.ApacheCommons.IO)
    implementation(Libs.ApacheCommons.Compress)
    implementation(Libs.Redacted.Annotation)

    implementation(Libs.Firebase.Crashlytics)

    // Kodein DI
    implementation(Libs.Kodein.CoreErasedJvm)
    implementation(Libs.Kodein.FrameworkAndroidX)

    implementation(Libs.Jbsdiff.Core)

    // Google/Firebase
    implementation(Libs.Material.Core)
    implementation(Libs.Firebase.ConfigKtx)
    implementation(Libs.Firebase.Analytics)
    implementation(Libs.Firebase.Messaging)
    implementation(Libs.Firebase.Crashlytics)
    implementation(Libs.Guava.Core)
    implementation(Libs.Zxing.Core)
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation(platform("com.google.firebase:firebase-bom:26.6.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
}
