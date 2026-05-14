# GuardianSpace

Android Virtual Space application for running apps (including GameGuardian) in an isolated environment without requiring device root.

## Features

- **BlackBox Virtualization Engine**: Run apps in an isolated virtual environment
- **Fake Root Emulation**: GameGuardian believes the device is rooted
- **Native Hooks**: mprotect/ptrace bypass via PLT hooking
- **Speed Hack Support**: Delta-based time hook (no crash)
- **Anti-Detection**: Device spoofed as Google Pixel 5
- **32-bit Support**: armeabi-v7a for GameGuardian compatibility

## Architecture

```
GuardianSpace/
├── app/                    # Main application module
│   ├── java/com/tools/vspace/
│   │   ├── MainActivity.kt        # UI entry point
│   │   ├── VirtualCore.kt         # Central controller
│   │   ├── ui/                    # Fragments (Home, AppList)
│   │   ├── engine/                # Virtualization engine
│   │   │   ├── BlackBoxCore.java  # Build spoofing
│   │   │   ├── VEnvironment.java  # Virtual filesystem
│   │   │   └── patch/
│   │   │       ├── GG_Patcher.java  # GameGuardian patches
│   │   │       └── RootEmu.java     # Root emulation
│   │   └── receiver/              # Install broadcast receiver
│   └── cpp/                 # Native hooks
│       ├── CMakeLists.txt
│       ├── hook/
│       │   ├── xhook.c       # PLT hooking framework
│       │   └── gg_bypass.c   # GG-specific bypasses
│       └── main.cpp          # JNI bridge
└── blackbox-core/          # Virtualization engine module
```

## Build Requirements

- Android Studio Arctic Fox or later
- JDK 11
- Android SDK 30
- NDK 25.1+
- CMake 3.22.1+

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Usage

1. Install GuardianSpace on your device
2. Tap the **+** FAB to add an APK from storage
3. Apps appear in the Home and Apps tabs
4. GameGuardian is auto-detected and patched
5. Launch apps from within virtual space

## Technical Details

### Root Emulation
- `RootEmu.java` creates virtual `/system/bin/su` and `/system/xbin/su`
- Intercepts `exec("su")` calls at the native level
- Provides fake root shell responses

### Speed Hack Fix
- Uses delta-based time calculation instead of blocking `gettimeofday()`
- `gg_bypass.c` implements `apply_speed_delta()` for smooth speed changes
- Prevents crashes caused by completely blocking time syscalls

### Anti-Detection
- Build fields spoofed as Google Pixel 5 (redfin)
- Virtual packages hidden from PackageManager queries
- `getOpPackageName` overridden for Android 11+ compatibility

### 32-bit Support
- NDK abiFilters set to `armeabi-v7a` and `arm64-v8a`
- GameGuardian (32-bit) runs on 64-bit hosts via translation

## Package Name

`com.tools.vspace`

## License

For educational and research purposes only.
