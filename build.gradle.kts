plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.diffplug.spotless") version "7.0.2"
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktfmt("0.53").kotlinlangStyle()
    }
}
