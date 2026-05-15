plugins {
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
    id("org.jetbrains.compose") version "1.6.0" apply false
}

allprojects {
    group = "com.onyx.launcher"
    version = "1.0.0"
}
