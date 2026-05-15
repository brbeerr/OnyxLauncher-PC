import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Ktor (HTTP клиент)
    val ktorVersion = "2.3.8"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    
    // Утилиты
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("commons-io:commons-io:2.15.1")
    implementation("org.apache.commons:commons-compress:1.26.0")
    
    // Логирование
    implementation("org.slf4j:slf4j-simple:2.0.12")
}

compose.desktop {
    application {
        mainClass = "com.onyx.launcher.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OnyxLauncher"
            packageVersion = "1.0.0"
            
            windows {
                menuGroup = "OnyxLauncher"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
            
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
            
            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
            }
        }
    }
}
