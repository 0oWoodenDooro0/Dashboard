package website.woodendoor.dashboard.service

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import website.woodendoor.dashboard.model.DashboardConfig
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceGroup
import website.woodendoor.dashboard.model.ServiceItem

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var configFile: File
    private lateinit var repository: FileConfigRepository

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("dashboard-config-test").toFile()
        configFile = File(tempDir, "services.json")
        repository = FileConfigRepository(configFile = configFile)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testDefaultConfigGeneratedWhenFileNotFound() {
        // Given config file does not exist
        assertTrue(!configFile.exists())

        // When loadConfig() is invoked
        val config = repository.loadConfig()

        // Then config file is automatically generated on disk with empty service groups
        assertTrue(configFile.exists(), "Config file should be created on disk")
        assertTrue(config.groups.isEmpty(), "Default config should contain empty service groups")
        assertEquals(1, config.version)
        assertEquals(5, config.pollingIntervalSeconds)
    }

    @Test
    fun testAddServiceOnTopOfEmptyDefaultConfig() {
        // Given a newly generated default config
        val initialConfig = repository.loadConfig()
        assertTrue(initialConfig.groups.isEmpty(), "Initial config must have empty groups")

        // When adding a service on top of the empty default configuration
        val service = ServiceItem(
            id = "frontend-dev",
            name = "Frontend Web",
            host = "127.0.0.1",
            port = 3000
        )
        val updatedConfig = initialConfig.withServiceAdded(service, targetGroupIdOrName = "Web Applications")
        repository.saveConfig(updatedConfig)

        // Then reload config and verify group & service exist
        val reloaded = repository.loadConfig()
        assertEquals(1, reloaded.groups.size)
        assertEquals("Web Applications", reloaded.groups.first().name)
        assertEquals(1, reloaded.groups.first().services.size)
        assertEquals("frontend-dev", reloaded.groups.first().services.first().id)
    }

    @Test
    fun testSaveAndLoadRoundTripWithAllLogSources() {
        // Given a custom configuration containing Docker, LocalFile, and None log sources
        val customConfig = DashboardConfig(
            version = 2,
            pollingIntervalSeconds = 10,
            groups = listOf(
                ServiceGroup(
                    id = "group-1",
                    name = "Microservices",
                    services = listOf(
                        ServiceItem(
                            id = "srv-1",
                            name = "Auth Service",
                            host = "127.0.0.1",
                            port = 8080,
                            healthUrl = "http://127.0.0.1:8080/health",
                            openUrl = "http://localhost:8080",
                            logSource = LogSource.Docker(containerName = "auth-backend-1"),
                            description = "OAuth2 & JWT provider",
                            tags = listOf("auth", "security")
                        ),
                        ServiceItem(
                            id = "srv-2",
                            name = "Legacy Worker",
                            host = "localhost",
                            port = 9000,
                            logSource = LogSource.LocalFile(path = "/var/log/worker.log"),
                            description = "Background task worker",
                            tags = listOf("worker")
                        ),
                        ServiceItem(
                            id = "srv-3",
                            name = "Local Dev Server",
                            host = "127.0.0.1",
                            port = 3000,
                            logSource = LogSource.Command(
                                workingDir = "/home/user/project",
                                startCommand = "npm run dev",
                                stopCommand = "npm run stop",
                                environment = mapOf("NODE_ENV" to "development")
                            ),
                            description = "Frontend Vite dev server",
                            tags = listOf("vite", "frontend")
                        ),
                        ServiceItem(
                            id = "srv-4",
                            name = "External Dashboard",
                            host = "192.168.1.100",
                            port = 443,
                            openUrl = "https://internal.example.com",
                            logSource = LogSource.None,
                            description = null,
                            tags = emptyList()
                        )
                    )
                )
            )
        )

        // When saved and loaded through a fresh repository instance
        repository.saveConfig(customConfig)

        val freshRepository = FileConfigRepository(configFile = configFile)
        val loadedConfig = freshRepository.loadConfig()

        // Then loaded config matches original exactly
        assertEquals(customConfig, loadedConfig)
        assertEquals(2, loadedConfig.version)
        assertEquals(10, loadedConfig.pollingIntervalSeconds)
        assertEquals(1, loadedConfig.groups.size)

        val services = loadedConfig.groups.first().services
        assertEquals(4, services.size)
        assertTrue(services[0].logSource is LogSource.Docker)
        assertEquals("auth-backend-1", (services[0].logSource as LogSource.Docker).containerName)
        assertTrue(services[1].logSource is LogSource.LocalFile)
        assertEquals("/var/log/worker.log", (services[1].logSource as LogSource.LocalFile).path)
        assertTrue(services[2].logSource is LogSource.Command)
        val cmdSource = services[2].logSource as LogSource.Command
        assertEquals("/home/user/project", cmdSource.workingDir)
        assertEquals("npm run dev", cmdSource.startCommand)
        assertEquals("npm run stop", cmdSource.stopCommand)
        assertEquals(mapOf("NODE_ENV" to "development"), cmdSource.environment)
        assertTrue(services[3].logSource is LogSource.None)
    }

    @Test
    fun testLoadCustomExistingConfigFile() {
        // Given pre-existing JSON content
        val rawJson = """
            {
              "version": 1,
              "pollingIntervalSeconds": 3,
              "groups": [
                {
                  "id": "custom-grp",
                  "name": "Custom Services",
                  "services": [
                    {
                      "id": "api",
                      "name": "Custom API",
                      "host": "0.0.0.0",
                      "port": 5000,
                      "logSource": {
                        "type": "docker",
                        "containerName": "api_container"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        configFile.writeText(rawJson)

        // When loadConfig() is called
        val loadedConfig = repository.loadConfig()

        // Then it parses correctly into model
        assertEquals(1, loadedConfig.version)
        assertEquals(3, loadedConfig.pollingIntervalSeconds)
        assertEquals(1, loadedConfig.groups.size)
        assertEquals("custom-grp", loadedConfig.groups[0].id)
        assertEquals("Custom API", loadedConfig.groups[0].services[0].name)
        assertEquals(5000, loadedConfig.groups[0].services[0].port)
        assertEquals(
            LogSource.Docker(containerName = "api_container"),
            loadedConfig.groups[0].services[0].logSource
        )
    }

    @Test
    fun testSaveConfigOverwritesExistingContent() {
        // Given initial config on disk
        val initialConfig = repository.loadConfig() // creates default config
        assertTrue(configFile.exists())

        // When updated with a new configuration
        val updatedConfig = DashboardConfig(
            version = 1,
            pollingIntervalSeconds = 15,
            groups = listOf(
                ServiceGroup(
                    id = "updated-grp",
                    name = "Updated Group",
                    services = listOf(
                        ServiceItem(id = "item-1", name = "Standalone Service", port = 7000)
                    )
                )
            )
        )
        repository.saveConfig(updatedConfig)

        // Then loaded config reflects the update
        val reloaded = repository.loadConfig()
        assertEquals(updatedConfig, reloaded)
        assertEquals("Updated Group", reloaded.groups.first().name)
        assertEquals(15, reloaded.pollingIntervalSeconds)
    }

    @Test
    fun testObserveConfigEmitsInitialAndExternalFileModifications() = runTest {
        // Given initial load to create the file
        val initialConfig = repository.loadConfig()

        // When observing config flow
        val flow = repository.observeConfig()
        
        // Initial emission should match current file
        val firstEmission = flow.first()
        assertEquals(initialConfig, firstEmission)

        // Asynchronously update file
        val updatedConfig = initialConfig.copy(
            pollingIntervalSeconds = 42,
            groups = listOf(
                ServiceGroup(id = "observed-grp", name = "Observed Group", services = emptyList())
            )
        )

        val collector = async {
            flow.take(2).toList()
        }

        // Delay slightly and write update to disk
        delay(100)
        repository.saveConfig(updatedConfig)

        val emissions = withTimeout(5000) {
            collector.await()
        }

        assertEquals(2, emissions.size)
        assertEquals(initialConfig, emissions[0])
        assertEquals(updatedConfig, emissions[1])
    }

    @Test
    fun testMalformedJsonThrowsException() {
        // Given malformed JSON in config file
        configFile.writeText("{ \"version\": \"invalid_number\", unclosed json ")

        // When loading, it should throw an Exception (e.g. SerializationException)
        assertFailsWith<Exception> {
            repository.loadConfig()
        }
    }

    @Test
    fun testResolveDefaultConfigFileWithSystemProperty() {
        val customTestPath = File(tempDir, "custom-prop-services.json").absolutePath
        val oldProp = System.getProperty("dashboard.config.path")
        try {
            System.setProperty("dashboard.config.path", customTestPath)
            val resolved = FileConfigRepository.resolveDefaultConfigFile()
            assertEquals(File(customTestPath).absolutePath, resolved.absolutePath)
        } finally {
            if (oldProp != null) {
                System.setProperty("dashboard.config.path", oldProp)
            } else {
                System.clearProperty("dashboard.config.path")
            }
        }
    }

    @Test
    fun testResolveDefaultConfigFileHasValidName() {
        val resolved = FileConfigRepository.resolveDefaultConfigFile()
        assertEquals("services.json", resolved.name)
    }
}
