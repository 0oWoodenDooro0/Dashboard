# WoodenDoor Dashboard

A cross-platform Desktop application for service health monitoring and log observability, built with **Kotlin Multiplatform** and **Compose Multiplatform (Desktop)**.

---

## 📁 Project Structure

* [`/shared`](./shared/src): Shared business logic, data models, state management, and Compose UI.
  - [`commonMain`](./shared/src/commonMain/kotlin): Platform-agnostic domain models, view models, and common UI components.
  - [`jvmMain`](./shared/src/jvmMain/kotlin): JVM/Desktop-specific implementations (e.g., file system watch, OS process execution, Docker integration).
* [`/desktopApp`](./desktopApp/src): Desktop application entry point and native packaging configuration.

---

## 🚀 Running the App

You can launch the application directly from IntelliJ IDEA / Android Studio, or via the command line:

- **Standard Run**:
  ```bash
  ./gradlew :desktopApp:run
  ```
- **Hot Reload (Compose Hot Run)**:
  ```bash
  ./gradlew :desktopApp:hotRun --auto
  ```

---

## 📦 Packaging into Executables & Installers (打包發行)

Compose Multiplatform allows packaging the application into standalone executables or native installers:

### 1. Standalone Portable Directory (免安裝綠色執行檔)
Generates an unpacked folder containing the application binary and bundled Java runtime (JRE) without needing an installer:
```bash
./gradlew :desktopApp:createDistributable
```
* **Output Path**: `desktopApp/build/compose/binaries/main/app/`
* **Run**: Execute `./website.woodendoor.dashboard` directly in the output directory.

#### 📌 Register Desktop Shortcut on Linux (.desktop)
To automatically create and register a desktop shortcut in `~/.local/share/applications/` for the portable build:
```bash
./gradlew :desktopApp:installDesktopEntry
```

### 2. Native Packages for Current OS (系統安裝檔)
Automatically builds packages tailored for your current host operating system:
```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

#### Platform-Specific Packaging Commands:
* **Linux**:
  - Debian/Ubuntu package (`.deb`):
    ```bash
    ./gradlew :desktopApp:packageDeb
    # Output: desktopApp/build/compose/binaries/main/deb/
    ```
  - AppImage single executable (`.AppImage`):
    ```bash
    ./gradlew :desktopApp:packageAppImage
    # Output: desktopApp/build/compose/binaries/main/appimage/
    ```
* **Windows**:
  - MSI installer (`.msi`): `./gradlew :desktopApp:packageMsi`
* **macOS**:
  - DMG disk image (`.dmg`): `./gradlew :desktopApp:packageDmg`

---

## ⚙️ Configuration (`services.json`)

The application automatically manages its configuration file with hot-reloading support (detects file changes in real-time).

### Resolution Priority & Location
When the application starts, it resolves the configuration file path in the following order:

1. **Environment Variable / System Property**:
   - Environment variable: `DASHBOARD_CONFIG_PATH=/path/to/services.json`
   - Java System Property: `-Ddashboard.config.path=/path/to/services.json`
2. **Local Directory (Dev / Portable Mode)**:
   - `./.config/services.json` or `./config/services.json` (if present in the current working directory).
3. **Standard User Configuration Directory (Default)**:
   - **Linux**: `~/.config/dashboard/services.json` (or `$XDG_CONFIG_HOME/dashboard/services.json`)
   - **macOS**: `~/Library/Application Support/Dashboard/services.json`
   - **Windows**: `%APPDATA%\Dashboard\services.json`

If no configuration file exists, a default template will be created automatically upon first run.

---

## 🧪 Running Tests

Run unit tests across shared modules and JVM targets:

```bash
./gradlew :shared:jvmTest
```

---

## 📚 Learn More

* [Compose Multiplatform Documentation](https://www.jetbrains.com/lp/compose-multiplatform/)
* [Kotlin Multiplatform Documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)