import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// compileSdk / targetSdk = latest *stable* platform installed in the SDK.
// Stable platforms live in platforms/android-<int>; previews and minor
// releases (android-36.1, android-Baklava, ...) are ignored on purpose.
// Override with -Pedgebar.sdk=NN or in gradle.properties.
// ---------------------------------------------------------------------------
fun sdkDirectory(): File? {
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        val props = Properties().apply { local.inputStream().use { load(it) } }
        props.getProperty("sdk.dir")?.let { return File(it) }
    }
    return (System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT"))?.let(::File)
}

fun latestStablePlatform(): Int? =
    sdkDirectory()?.resolve("platforms")?.listFiles()
        ?.mapNotNull { Regex("""android-(\d+)""").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        ?.filter { File(sdkDirectory(), "platforms/android-$it/android.jar").exists() }
        ?.maxOrNull()

val edgeBarSdk: Int = (findProperty("edgebar.sdk") as String?)?.toInt()
    ?: latestStablePlatform()
    ?: 36

// ---------------------------------------------------------------------------
// Release signing. keystore.properties is git-ignored and points at a key
// stored outside the repository (scripts/make-keystore.sh creates both).
// ---------------------------------------------------------------------------
val keystorePropsFile: File = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKey = keystorePropsFile.exists() &&
    keystoreProps.getProperty("storeFile")?.let { File(it).exists() } == true

android {
    namespace = "com.local.edgebar"
    compileSdk = edgeBarSdk

    defaultConfig {
        applicationId = "com.local.edgebar"
        minSdk = 33
        targetSdk = edgeBarSdk
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = File(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // A newer SDK than this AGP knows about is expected (latest installed SDK is used).
        disable += setOf("OldTargetApi", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        htmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Fail loudly instead of silently producing an unsigned release APK.
val checkReleaseSigning by tasks.registering {
    doLast {
        if (!hasReleaseKey) {
            throw GradleException(
                "Release signing key not found. Run scripts/make-keystore.sh first " +
                    "(it writes keystore.properties and a key under ~/.edgebar/).",
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
