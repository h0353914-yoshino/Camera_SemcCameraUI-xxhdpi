plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    // 30 是目前上限：android-34/35 的 android.jar 含 MODULE attribute，
    // 會讓固定使用的 JDK 8 javac 直接拋 AssertionError。
    compileSdkVersion(30)

    defaultConfig {
        applicationId = "com.sonyericsson.android.camera"
        minSdkVersion(28)
        targetSdkVersion(28)
        versionCode = 4472847
        versionName = "2.2.2.A.0.15"
        // 原始 APK 的 BuildConfig 含 FLAVOR = ""（AGP 只在有 productFlavors 時自動生成）
        buildConfigField("String", "FLAVOR", "\"\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
        }

        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lintOptions {
        isAbortOnError = false
        isCheckReleaseBuilds = false
    }

    aaptOptions {
        additionalParameters(
            "--stable-ids",
            file("stable-ids.txt").absolutePath
        )
        ignoreAssetsPattern =
            "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
        noCompress("apk", "so")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "src/main/kotlin")
            // 將原始 APK 中的非程式碼檔案打包（org/apache/.../rgb.txt 等資料檔案）
            resources.srcDir("src/main")
            resources.include("org/**")
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packagingOptions {
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        pickFirst("AndroidManifest.xml")
        doNotStrip("**/*.so")
    }
}

// 使用 JDK 8 編譯 Java（匹配原始 APK 的位元組碼版本）
tasks.withType(JavaCompile::class.java) {
    options.isFork = true
    options.forkOptions.javaHome =
        file("/home/h/lineageos/prebuilts/jdk/jdk8/linux-x86")
}

// Kotlin module 名稱修正（匹配原始 APK 的模組名）
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
    kotlinOptions.freeCompilerArgs =
        listOf("-module-name", "SemcCameraUI_release")
}

dependencies {
    // ── 編譯用 stubs（不打包進 APK）──────────────
    compileOnly("com.google.protobuf:protobuf-javalite:3.8.0")
    compileOnly(files("libs/protobuf-generated-registry-stub.jar"))
    compileOnly(files("libs/java-awt-stubs.jar"))
    compileOnly(files("libs/extra-stubs.jar"))
    compileOnly(files("libs/api-stubs.jar"))

    // design 僅 compileOnly（避免多餘 class 被打包）
    compileOnly("com.android.support:design:28.0.0")

    // ── 執行時依賴（打包進 APK）─────────────────
    // Kotlin stdlib（與原始 APK 對齊 1.2.60）
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.2.60")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.2.60")

    // Support Library 28
    implementation("com.android.support:appcompat-v7:28.0.0")
    implementation("com.android.support:support-v4:28.0.0")
    implementation("com.android.support:recyclerview-v7:28.0.0")
    implementation("com.android.support:coordinatorlayout:28.0.0")
    implementation("com.android.support:preference-v7:28.0.0")
    implementation("com.android.support:preference-v14:28.0.0")
    implementation("com.android.support:support-annotations:28.0.0")

    // Gson（與 smali 分析版本對齊）
    implementation("com.google.code.gson:gson:2.2.4")

    // Architecture Components
    implementation("android.arch.lifecycle:livedata-core:1.1.1")

    // QR Code 偵測（取代 2.9.2.A.0.10 內嵌的 ZXing core smali，改用官方 Maven 套件）
    implementation("com.google.zxing:core:3.5.3")
}

configurations.all {
    resolutionStrategy {
        force("com.android.support:support-annotations:28.0.0")
    }
    // 排除完整 livedata 模組（僅保留 livedata-core）
    exclude(group = "android.arch.lifecycle", module = "livedata")
}