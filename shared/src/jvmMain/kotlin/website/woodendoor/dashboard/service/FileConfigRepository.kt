package website.woodendoor.dashboard.service

import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import website.woodendoor.dashboard.model.DashboardConfig
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceGroup
import website.woodendoor.dashboard.model.ServiceItem

class FileConfigRepository(
    val configFile: File = resolveDefaultConfigFile(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = defaultJson
) : ConfigRepository {

    private val _configUpdates = MutableSharedFlow<DashboardConfig>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    companion object {
        val defaultJson: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun resolveDefaultConfigFile(): File {
            // 1. System property or environment variable override
            val customPath = System.getProperty("dashboard.config.path")
                ?: System.getenv("DASHBOARD_CONFIG_PATH")
            if (!customPath.isNullOrBlank()) {
                return File(customPath)
            }

            // 2. Local directory (.config/services.json or config/services.json) for dev/portable mode
            val localDotConfig = File(".config/services.json")
            if (localDotConfig.exists()) return localDotConfig

            val localConfig = File("config/services.json")
            if (localConfig.exists()) return localConfig

            // 3. User standard configuration directory (~/.config/dashboard/services.json)
            val userHome = System.getProperty("user.home") ?: "."
            val os = System.getProperty("os.name", "").lowercase()

            val configDir = when {
                os.contains("win") -> {
                    val appData = System.getenv("APPDATA")
                    if (!appData.isNullOrBlank()) {
                        File(appData, "Dashboard")
                    } else {
                        File(userHome, "AppData/Roaming/Dashboard")
                    }
                }
                os.contains("mac") -> {
                    File(userHome, "Library/Application Support/Dashboard")
                }
                else -> {
                    val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
                    if (!xdgConfigHome.isNullOrBlank()) {
                        File(xdgConfigHome, "dashboard")
                    } else {
                        File(userHome, ".config/dashboard")
                    }
                }
            }

            return File(configDir, "services.json")
        }

        fun createDefaultConfig(): DashboardConfig {
            return DashboardConfig(
                version = 1,
                pollingIntervalSeconds = 5,
                groups = listOf(
                    ServiceGroup(
                        id = "web-apps",
                        name = "Web Applications",
                        services = listOf(
                            ServiceItem(
                                id = "frontend-dev",
                                name = "Frontend Web",
                                host = "127.0.0.1",
                                port = 3000,
                                openUrl = "http://localhost:3000",
                                logSource = LogSource.Command(
                                    workingDir = "/apps/frontend-web",
                                    startCommand = "npm run dev",
                                    stopCommand = "npm run stop"
                                ),
                                description = "Frontend Development Server",
                                tags = listOf("frontend", "ui")
                            ),
                            ServiceItem(
                                id = "backend-api",
                                name = "Backend API",
                                host = "127.0.0.1",
                                port = 8000,
                                openUrl = "http://localhost:8000",
                                logSource = LogSource.Docker(containerName = "backend-api"),
                                description = "Backend REST API Server",
                                tags = listOf("backend", "api")
                            )
                        )
                    ),
                    ServiceGroup(
                        id = "databases",
                        name = "Databases & Cache",
                        services = listOf(
                            ServiceItem(
                                id = "postgres-db",
                                name = "PostgreSQL",
                                host = "127.0.0.1",
                                port = 5432,
                                logSource = LogSource.Docker(containerName = "postgres-db"),
                                description = "Primary Relational Database",
                                tags = listOf("db", "sql")
                            ),
                            ServiceItem(
                                id = "redis-cache",
                                name = "Redis",
                                host = "127.0.0.1",
                                port = 6379,
                                logSource = LogSource.Docker(containerName = "redis-cache"),
                                description = "In-memory Cache & Queue",
                                tags = listOf("cache", "nosql")
                            )
                        )
                    )
                )
            )
        }
    }

    @Synchronized
    override fun loadConfig(): DashboardConfig {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            val defaultConfig = createDefaultConfig()
            saveConfig(defaultConfig)
            return defaultConfig
        }

        val content = configFile.readText(Charsets.UTF_8)
        return json.decodeFromString(content)
    }

    @Synchronized
    override fun saveConfig(config: DashboardConfig) {
        configFile.parentFile?.mkdirs()
        val content = json.encodeToString(config)
        configFile.writeText(content, Charsets.UTF_8)
        _configUpdates.tryEmit(config)
    }

    override fun observeConfig(): Flow<DashboardConfig> = callbackFlow {
        var lastEmittedConfig: DashboardConfig? = null
        try {
            val initial = loadConfig()
            lastEmittedConfig = initial
            trySend(initial)
        } catch (_: Exception) {}

        val inProcessJob = launch {
            _configUpdates.collect { updated ->
                if (updated != lastEmittedConfig) {
                    lastEmittedConfig = updated
                    trySend(updated)
                }
            }
        }

        val parentDir = configFile.absoluteFile.parentFile ?: File(".")
        parentDir.mkdirs()
        val path = parentDir.toPath()
        val targetFileName = configFile.name

        val watchService = FileSystems.getDefault().newWatchService()
        val watchKey = path.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        )

        val fsWatchJob = launch(dispatcher) {
            try {
                while (isActive) {
                    val key = withContext(Dispatchers.IO) {
                        try {
                            watchService.poll(200, TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            null
                        } catch (_: ClosedWatchServiceException) {
                            null
                        }
                    }

                    if (key != null) {
                        var changed = false
                        for (event in key.pollEvents()) {
                            val context = event.context() as? Path
                            if (context?.toString() == targetFileName) {
                                changed = true
                            }
                        }
                        val valid = key.reset()
                        if (!valid) break

                        if (changed) {
                            delay(50)
                            try {
                                val updated = loadConfig()
                                if (updated != lastEmittedConfig) {
                                    lastEmittedConfig = updated
                                    trySend(updated)
                                    _configUpdates.tryEmit(updated)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Cancelled
            } finally {
                try {
                    watchKey.cancel()
                    watchService.close()
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            inProcessJob.cancel()
            fsWatchJob.cancel()
            try {
                watchKey.cancel()
                watchService.close()
            } catch (_: Exception) {}
        }
    }
}
