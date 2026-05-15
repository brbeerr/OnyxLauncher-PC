# Onyx Launcher PC

A Minecraft Java Edition launcher for PC, based on [ZalithLauncher](https://github.com/MovTery/ZalithLauncher2).

## Features

- **Microsoft Account Login** - Full OAuth device code flow authentication
- **Offline Mode** - Play without internet connection  
- **Version Management** - Download and manage Minecraft versions
- **Mod Support** - Search and download mods from Modrinth
- **Modern UI** - Built with Kotlin Compose Desktop

## Building

### Requirements
- JDK 17+
- Gradle 8.5+

### Commands
```bash
./gradlew build    # Build
./gradlew run      # Run
./gradlew packageMsi   # Windows installer
./gradlew packageDmg   # macOS installer
./gradlew packageDeb   # Linux installer
```

## Project Structure
```
app/src/main/kotlin/com/onyx/launcher/
├── Main.kt                 # Entry point
├── data/                   # Data models (Account, Version)
├── game/                   # Game logic
│   ├── AccountsManager.kt
│   ├── VersionManager.kt
│   ├── auth/MicrosoftAuth.kt
│   ├── download/GameDownloader.kt
│   ├── launch/GameLauncher.kt
│   └── mods/ModsRepository.kt
├── network/HttpClient.kt
├── ui/                     # Compose UI
│   ├── App.kt
│   ├── theme/Theme.kt
│   └── screens/
└── utils/                  # Utilities
```

## Credits
- **ZalithLauncher** - Original Android launcher
- **PojavLauncher** - Original Minecraft Android launcher

## License
GNU GPLv3 - see LICENSE
