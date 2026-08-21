import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "website.woodendoor.dashboard.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.AppImage
            )
            packageName = "website.woodendoor.dashboard"
            packageVersion = "1.0.0"
            description = "WoodenDoor Dashboard - Service & Log Observability"
            vendor = "WoodenDoor"
            linux {
                shortcut = true
                menuGroup = "Development"
                appCategory = "Development"
            }
        }
    }
}

tasks.register("installDesktopEntry") {
    group = "compose desktop"
    description = "Registers a .desktop shortcut in ~/.local/share/applications for createDistributable"
    dependsOn("createDistributable")

    val appDirProvider = layout.buildDirectory.dir("compose/binaries/main/app")

    doLast {
        val appBaseDir = appDirProvider.get().asFile
        val packageDir = File(appBaseDir, "website.woodendoor.dashboard")
        val execFile = File(packageDir, "bin/website.woodendoor.dashboard").takeIf { it.exists() }
            ?: File(packageDir, "website.woodendoor.dashboard").takeIf { it.exists() }
            ?: File(appBaseDir, "website.woodendoor.dashboard")
        val iconFile = File(packageDir, "lib/website.woodendoor.dashboard.png").takeIf { it.exists() }

        val userHome = System.getProperty("user.home") ?: "."
        val appsDir = File(userHome, ".local/share/applications")
        appsDir.mkdirs()

        val desktopFile = File(appsDir, "website.woodendoor.dashboard.desktop")
        val content = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=WoodenDoor Dashboard")
            appendLine("Comment=Service & Log Observability Dashboard")
            appendLine("Exec=\"${execFile.absolutePath}\"")
            appendLine("Path=${packageDir.absolutePath}")
            if (iconFile != null) {
                appendLine("Icon=${iconFile.absolutePath}")
            }
            appendLine("Terminal=false")
            appendLine("Categories=Development;Utility;")
            appendLine("StartupWMClass=website.woodendoor.dashboard.MainKt")
        }

        desktopFile.writeText(content)
        desktopFile.setExecutable(true)
        println("✅ .desktop shortcut registered at: ${desktopFile.absolutePath}")
        println("   Exec: ${execFile.absolutePath}")
        println("   Working Dir: ${packageDir.absolutePath}")
    }
}