plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)              // Room ke compiler ke liye
    alias(libs.plugins.google.services)  // google-services.json padhta hai (FCM ke liye)
}

android {
    namespace = "com.blackbox.jalrakshak"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blackbox.jalrakshak"

        // minSdk 26 (Android 8.0) — KYUN:
        //  - Notification channels Android 8 se hi hain, aur FCM notification ke liye
        //    channel zaroori hai. 26 rakhne se purane API ka alag code likhna nahi padta.
        //  - Assam mein 2016 se purana phone practically kam hi hai.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        /**
         * Sirf do ABI rakhte hain.
         *
         * KYUN: MapLibre ka native lib (libmaplibre.so) har ABI ke liye ~18 MB hai.
         * Chaaron (armeabi-v7a, arm64-v8a, x86, x86_64) rakhne se APK 95 MB ho gaya tha.
         *   arm64-v8a -> aaj ke saare asli Android phone
         *   x86_64    -> emulator (testing ke liye zaroori)
         * armeabi-v7a (2016 se purane 32-bit phone) aur x86 chhod diye — hamara
         * minSdk 26 waise bhi 2017+ hai.
         */
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            // Demo/prototype hai — minify off rakha taaki APK banane mein koi
            // ProGuard/R8 surprise na aaye (Room + Retrofit ko rules chahiye hote hain).
            isMinifyEnabled = false
        }
        debug {
            // Emulator pe testing ke liye — SETUP.md ka flow yahi hai.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG chahiye — Network.kt sirf debug build mein HTTP logging
        // on karta hai (release mein citizen ka SOS/location logcat mein na jaaye).
        // AGP 8 se ye by default OFF hai, isliye explicitly on karna padta hai.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        /**
         * .pmtiles ko APK mein COMPRESS mat karo.
         *
         * KYUN ZAROORI HAI: PMTiles random-access format hai — MapLibre file ke andar
         * seek karke sirf zaroori tile padhta hai. Agar asset DEFLATE ho jaaye to
         * random access possible hi nahi rehta aur map blank aata hai.
         * (Waise bhi andar ki tiles already gzip hain, to compress karne ka fayda bhi nahi.)
         */
        noCompress += "pmtiles"
    }
}

// Room ka generated schema kahan jaaye — version history ke liye achha rehta hai.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM — saare compose artifacts ka version ek saath manage hota hai.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Tabler-style line icons ke sabse kareeb Material ke "outlined" icons hain.
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    // Offline cache (BUILD_PLAN section 2 — locked feature)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Chuna hua gaon + bhasha yaad rakhne ke liye
    implementation(libs.androidx.datastore.preferences)

    // Laravel API
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Push
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // SOS ke saath exact location bhejne ke liye
    implementation(libs.play.services.location)
    implementation(libs.accompanist.permissions)
    implementation(libs.coroutines.play.services)

    // Offline map (B: offline maps)
    implementation(libs.maplibre)
}
