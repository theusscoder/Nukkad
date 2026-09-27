import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "in.nukkad"
    compileSdk = 35
    flavorDimensions += "audience"
    productFlavors {
        create("customer") {
            dimension = "audience"
            applicationIdSuffix = ".customer"
            versionNameSuffix = "-customer"
            resValue("string", "app_name", "Nukkad Customer")
        }
        create("merchant") {
            dimension = "audience"
            applicationIdSuffix = ".merchant"
            versionNameSuffix = "-merchant"
            resValue("string", "app_name", "Nukkad Merchant")
        }
        create("dev") {
            dimension = "audience"
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("nukkad-debug.keystore")
            storePassword = "android"
            keyAlias = "nukkad"
            keyPassword = "android"
        }
    }
    defaultConfig {
        applicationId = "in.nukkad"
        resValue("string", "app_name", "Nukkad Dev")
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.1-ui"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties") }
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    // Pinned LiteRT-LM runtime is customer-only; weights are imported separately and not bundled.
    add("customerImplementation", "com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    add("customerImplementation", "com.uber:h3-android:4.5.0")
    // Physical-world features are optional add-ons; customer requests/offers still work without permission.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    add("merchantImplementation", "androidx.camera:camera-camera2:1.5.3")
    add("merchantImplementation", "androidx.camera:camera-lifecycle:1.5.3")
    add("merchantImplementation", "androidx.camera:camera-view:1.5.3")
    add("merchantImplementation", "com.google.mlkit:text-recognition:16.0.1")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

// Opt-in network smoke test; ordinary unit tests remain offline.
tasks.withType<Test>().configureEach {
    systemProperty("nukkad.mqttSmoke", providers.gradleProperty("mqttSmoke").orElse("false").get())
}








// No Java source files; avoid unnecessary javac access to locked platform jars on Windows.




