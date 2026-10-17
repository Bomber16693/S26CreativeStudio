plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.bomber16693.s26creativestudio"
  compileSdk = 36
  ndkVersion = "27.2.12479018"
  defaultConfig {
    applicationId = "com.bomber16693.s26creativestudio"
    minSdk = 29
    targetSdk = 36
    versionCode = 5
    versionName = "0.4.0"
    ndk { abiFilters += listOf("arm64-v8a") }
    externalNativeBuild {
      cmake {
        arguments += listOf("-DGGML_NATIVE=OFF","-DGGML_OPENMP=OFF","-DGGML_BACKEND_DL=OFF","-DGGML_BLAS=OFF","-DGGML_VULKAN=OFF","-DCMAKE_POSITION_INDEPENDENT_CODE=ON")
        targets += listOf("yue2mobile")
      }
    }
  }
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/yue2/CMakeLists.txt")
      version = "3.22.1"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
