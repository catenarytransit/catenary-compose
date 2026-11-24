import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.catenarymaps.catenary.core.journey"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 27 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
}
kotlin { jvmToolchain(25) }
dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
