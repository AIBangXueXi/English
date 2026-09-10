import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 从 local.properties 读取密钥（该文件不入库，见 local.properties.example）
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String): String = "\"${localProperties.getProperty(name, "")}\""

android {
    namespace = "com.example.english"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.english"
        minSdk = 24
        targetSdk = 36
        versionCode = 48
        versionName = "1.48.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ALIYUN_ACCESS_KEY_ID", secret("ALIYUN_ACCESS_KEY_ID"))
        buildConfigField("String", "ALIYUN_ACCESS_KEY_SECRET", secret("ALIYUN_ACCESS_KEY_SECRET"))
        buildConfigField("String", "DEEPSEEK_API_KEY", secret("DEEPSEEK_API_KEY"))
        buildConfigField("String", "ASR_APP_KEY_DEFAULT", secret("ASR_APP_KEY_DEFAULT"))
        buildConfigField("String", "ASR_APP_KEY_ENGLISH", secret("ASR_APP_KEY_ENGLISH"))
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.evaluating) {
        // evaluating 的 POM 带了 sample-app 的 appcompat/constraintlayout，
        // 实际 SDK 类只用 androidx.annotation / androidx.core（本工程已有），排除避免拉旧依赖。
        exclude(group = "androidx.appcompat")
        exclude(group = "androidx.constraintlayout")
    }
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}