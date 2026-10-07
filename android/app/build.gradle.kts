import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ir.pocora"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        // From android/version.properties, which make publish writes for each release.
        val release = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
        versionCode = release.getProperty("versionCode").toInt()
        versionName = release.getProperty("versionName")
    }

    // The release key comes from the environment, so it never sits in the repo. Without it the release build is
    // left unsigned, which Android will not install.
    val keystore = System.getenv("POCORA_KEYSTORE_FILE")
    if (keystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("POCORA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("POCORA_KEY_ALIAS")
                keyPassword = System.getenv("POCORA_KEY_PASSWORD")
            }
        }
    }

    // One codebase, two apps. The role is the only difference between them.
    flavorDimensions += "role"
    productFlavors {
        create("child") {
            dimension = "role"
            applicationId = "ir.pocora.child"
            manifestPlaceholders["appLabel"] = "@string/app_name_child"
        }
        create("parent") {
            dimension = "role"
            applicationId = "ir.pocora.parent"
            manifestPlaceholders["appLabel"] = "@string/app_name_parent"
        }
    }

    sourceSets {
        getByName("child") { manifest.srcFile("manifests/child.xml") }
        getByName("parent") { manifest.srcFile("manifests/parent.xml") }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Phones only. The x86 copies of the scanner's native code serve emulators on Intel computers.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Writes the role as a Kotlin constant. BuildConfig would do the same, but it is generated as Java,
// and this project is kept free of Java sources so it builds with only the Kotlin compiler.
abstract class GenerateRoleTask : DefaultTask() {
    @get:Input
    abstract val role: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDirectory.get().file("ir/pocora/BuildRole.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package ir.pocora\n\ninternal const val BUILD_ROLE = \"${role.get()}\"\n")
    }
}

androidComponents {
    // The parent app shows the pairing code and never reads one, so it leaves out the scanner's native code.
    onVariants(selector().withFlavor("role" to "parent")) { variant ->
        variant.packaging.jniLibs.excludes
            .add("**/libbarhopper_v3.so")
    }

    onVariants { variant ->
        val taskName = "generate" + variant.name.replaceFirstChar { it.uppercase() } + "Role"
        val task =
            tasks.register<GenerateRoleTask>(taskName) {
                role.set(variant.productFlavors.first().second)
            }
        variant.sources.kotlin?.addGeneratedSourceDirectory(task, GenerateRoleTask::outputDirectory)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // Icons for the feature tiles and the kinds of apps. R8 keeps only the ones used.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Draws the pairing code. The scanner below only reads codes.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
}
