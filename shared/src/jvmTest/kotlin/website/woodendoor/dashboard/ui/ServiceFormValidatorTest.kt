package website.woodendoor.dashboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import website.woodendoor.dashboard.model.DashboardConfig
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.ui.util.FormValidationResult
import website.woodendoor.dashboard.ui.util.LogSourceType
import website.woodendoor.dashboard.ui.util.ServiceFormState

class ServiceFormValidatorTest {

    @Test
    fun `default ServiceFormState uses COMMAND log source type`() {
        val state = ServiceFormState()
        assertEquals(LogSourceType.COMMAND, state.logSourceType)
    }

    @Test
    fun `fromServiceItem with LogSource None maps to COMMAND log source type`() {
        val item = ServiceItem(
            id = "none-srv",
            name = "None Service",
            logSource = LogSource.None
        )
        val state = ServiceFormState.fromServiceItem(item, "Default Group")
        assertEquals(LogSourceType.COMMAND, state.logSourceType)
        assertEquals("none-srv", state.id)
        assertEquals("None Service", state.name)
        assertEquals("Default Group", state.groupName)
    }

    @Test
    fun `validate with valid Docker inputs creates Docker LogSource`() {
        val formState = ServiceFormState(
            id = "test-service",
            name = "Test Service",
            groupName = "Web Apps",
            host = "127.0.0.1",
            port = "8080",
            openUrl = "http://localhost:8080",
            healthUrl = "http://localhost:8080/health",
            description = "A sample service for testing",
            tags = "web, frontend, auth",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "test-container"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)

        val service = result.serviceItem
        assertEquals("test-service", service.id)
        assertEquals("Test Service", service.name)
        assertEquals("Web Apps", result.groupName)
        assertEquals("127.0.0.1", service.host)
        assertEquals(8080, service.port)
        assertEquals("http://localhost:8080", service.openUrl)
        assertEquals("http://localhost:8080/health", service.healthUrl)
        assertEquals("A sample service for testing", service.description)
        assertEquals(listOf("web", "frontend", "auth"), service.tags)
        assertEquals(LogSource.Docker("test-container"), service.logSource)
    }

    @Test
    fun `validate with valid Command inputs creates Command LogSource`() {
        val formState = ServiceFormState(
            name = "Command Service",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = " /home/user/app ",
            commandStartScript = " npm run dev "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(
            LogSource.Command(
                workingDir = "/home/user/app",
                startCommand = "npm run dev",
                stopCommand = null
            ),
            result.serviceItem.logSource
        )
    }

    @Test
    fun `validate with optional stop command preserves stopCommand`() {
        val formState = ServiceFormState(
            name = "Gradle Service",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "/home/user/project",
            commandStartScript = "./gradlew bootRun",
            commandStopScript = " ./gradlew --stop "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(
            LogSource.Command(
                workingDir = "/home/user/project",
                startCommand = "./gradlew bootRun",
                stopCommand = "./gradlew --stop"
            ),
            result.serviceItem.logSource
        )
    }

    @Test
    fun `validate with valid Docker Compose inputs creates DockerCompose LogSource`() {
        val formState = ServiceFormState(
            name = "Compose Service",
            logSourceType = LogSourceType.DOCKER_COMPOSE,
            composeProjectDir = " /apps/my-project ",
            composeServiceName = " backend "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(
            LogSource.DockerCompose(
                projectDir = "/apps/my-project",
                serviceName = "backend",
                composeFile = null
            ),
            result.serviceItem.logSource
        )
    }

    @Test
    fun `validate with custom compose file preserves composeFile`() {
        val formState = ServiceFormState(
            name = "Custom Compose Service",
            logSourceType = LogSourceType.DOCKER_COMPOSE,
            composeProjectDir = "/apps/my-project",
            composeServiceName = "api",
            composeFileName = " docker-compose.prod.yml "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(
            LogSource.DockerCompose(
                projectDir = "/apps/my-project",
                serviceName = "api",
                composeFile = "docker-compose.prod.yml"
            ),
            result.serviceItem.logSource
        )
    }

    @Test
    fun `validate with valid Local File inputs creates LocalFile LogSource`() {
        val formState = ServiceFormState(
            name = "File Service",
            logSourceType = LogSourceType.LOCAL_FILE,
            localFilePath = " /var/log/app.log "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(LogSource.LocalFile("/var/log/app.log"), result.serviceItem.logSource)
    }

    @Test
    fun `validate generates id from name if id is blank`() {
        val formState = ServiceFormState(
            id = "",
            name = "My Amazing Service 2026",
            groupName = "Default",
            port = "3000",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "my-srv"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals("my-amazing-service-2026", result.serviceItem.id)
    }

    @Test
    fun `validate fails when name is blank`() {
        val formState = ServiceFormState(
            name = "   ",
            host = "127.0.0.1",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "my-srv"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("name"))
        assertEquals("Service name cannot be empty", result.errors["name"])
    }

    @Test
    fun `validate defaults host to 127_0_0_1 when host is blank`() {
        val formState = ServiceFormState(
            name = "Service Without Host",
            host = "",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "srv"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals("127.0.0.1", result.serviceItem.host)
    }

    @Test
    fun `validate allows optional empty port`() {
        val formState = ServiceFormState(
            name = "No Port Service",
            port = "",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "srv"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertNull(result.serviceItem.port)
    }

    @Test
    fun `validate fails when port is non-numeric`() {
        val formState = ServiceFormState(
            name = "Invalid Port Service",
            port = "eight-zero",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "srv"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("port"))
        assertEquals("Port must be a valid number (1-65535)", result.errors["port"])
    }

    @Test
    fun `validate fails when port is out of range`() {
        val negativePortState = ServiceFormState(name = "Negative Port", port = "-1", logSourceType = LogSourceType.DOCKER, dockerContainerName = "s")
        val zeroPortState = ServiceFormState(name = "Zero Port", port = "0", logSourceType = LogSourceType.DOCKER, dockerContainerName = "s")
        val excessivePortState = ServiceFormState(name = "Too Large Port", port = "70000", logSourceType = LogSourceType.DOCKER, dockerContainerName = "s")

        val result1 = negativePortState.validate()
        val result2 = zeroPortState.validate()
        val result3 = excessivePortState.validate()

        assertIs<FormValidationResult.Error>(result1)
        assertIs<FormValidationResult.Error>(result2)
        assertIs<FormValidationResult.Error>(result3)
    }

    @Test
    fun `validate fails when docker source is selected but container name is blank`() {
        val formState = ServiceFormState(
            name = "Docker Service",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "   "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("dockerContainerName"))
        assertEquals("Container name cannot be empty for Docker log source", result.errors["dockerContainerName"])
    }

    @Test
    fun `validate fails when command working directory is blank`() {
        val formState = ServiceFormState(
            name = "Missing Dir Service",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "   ",
            commandStartScript = "python main.py"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("commandWorkingDir"))
        assertEquals("Working directory cannot be empty for Directory Command log source", result.errors["commandWorkingDir"])
    }

    @Test
    fun `validate fails when command start script is blank`() {
        val formState = ServiceFormState(
            name = "Missing Script Service",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "/home/user/app",
            commandStartScript = "   "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("commandStartScript"))
        assertEquals("Start command cannot be empty for Directory Command log source", result.errors["commandStartScript"])
    }

    @Test
    fun `validate fails when docker compose project directory is blank`() {
        val formState = ServiceFormState(
            name = "Compose Missing Dir",
            logSourceType = LogSourceType.DOCKER_COMPOSE,
            composeProjectDir = "   ",
            composeServiceName = "api"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("composeProjectDir"))
        assertEquals("Project folder/directory cannot be empty for Docker Compose log source", result.errors["composeProjectDir"])
    }

    @Test
    fun `validate fails when docker compose service name is blank`() {
        val formState = ServiceFormState(
            name = "Compose Missing Service",
            logSourceType = LogSourceType.DOCKER_COMPOSE,
            composeProjectDir = "/apps/project",
            composeServiceName = "   "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("composeServiceName"))
        assertEquals("Service name cannot be empty for Docker Compose log source", result.errors["composeServiceName"])
    }

    @Test
    fun `validate fails when local file source is selected but file path is blank`() {
        val formState = ServiceFormState(
            name = "File Service",
            logSourceType = LogSourceType.LOCAL_FILE,
            localFilePath = "   "
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("localFilePath"))
        assertEquals("File path cannot be empty for Local File log source", result.errors["localFilePath"])
    }

    @Test
    fun `validate cleans and trims tags`() {
        val formState = ServiceFormState(
            name = "Tagged Service",
            tags = "  redis , cache,, in-memory  , ",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "redis-1"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(listOf("redis", "cache", "in-memory"), result.serviceItem.tags)
    }

    @Test
    fun `validate sets null for blank openUrl and healthUrl`() {
        val formState = ServiceFormState(
            name = "URL Test",
            openUrl = "   ",
            healthUrl = "",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "srv"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertNull(result.serviceItem.openUrl)
        assertNull(result.serviceItem.healthUrl)
    }

    @Test
    fun `fromServiceItem correctly maps Docker LogSource`() {
        val item = ServiceItem(
            id = "docker-srv",
            name = "Docker Backend",
            host = "192.168.1.10",
            port = 8080,
            openUrl = "http://localhost:8080",
            healthUrl = "http://localhost:8080/health",
            description = "Docker Backend Description",
            tags = listOf("docker", "api"),
            logSource = LogSource.Docker(containerName = "api-container")
        )

        val formState = ServiceFormState.fromServiceItem(item, "Microservices")
        assertEquals("docker-srv", formState.id)
        assertEquals("Docker Backend", formState.name)
        assertEquals("Microservices", formState.groupName)
        assertEquals("192.168.1.10", formState.host)
        assertEquals("8080", formState.port)
        assertEquals("http://localhost:8080", formState.openUrl)
        assertEquals("http://localhost:8080/health", formState.healthUrl)
        assertEquals("Docker Backend Description", formState.description)
        assertEquals("docker, api", formState.tags)
        assertEquals(LogSourceType.DOCKER, formState.logSourceType)
        assertEquals("api-container", formState.dockerContainerName)
    }

    @Test
    fun `fromServiceItem correctly maps DockerCompose LogSource`() {
        val item = ServiceItem(
            id = "compose-item",
            name = "Compose Item",
            logSource = LogSource.DockerCompose(
                projectDir = "/home/user/workspace",
                serviceName = "web-worker",
                composeFile = "compose.yaml"
            )
        )

        val formState = ServiceFormState.fromServiceItem(item, "Backend Services")
        assertEquals(LogSourceType.DOCKER_COMPOSE, formState.logSourceType)
        assertEquals("/home/user/workspace", formState.composeProjectDir)
        assertEquals("web-worker", formState.composeServiceName)
        assertEquals("compose.yaml", formState.composeFileName)
        assertEquals("Backend Services", formState.groupName)
    }

    @Test
    fun `fromServiceItem correctly maps Command LogSource`() {
        val item = ServiceItem(
            id = "cmd-item",
            name = "Node Backend",
            logSource = LogSource.Command(
                workingDir = "/apps/node-backend",
                startCommand = "npm start",
                stopCommand = "npm stop"
            )
        )

        val formState = ServiceFormState.fromServiceItem(item, "Node Services")
        assertEquals(LogSourceType.COMMAND, formState.logSourceType)
        assertEquals("/apps/node-backend", formState.commandWorkingDir)
        assertEquals("npm start", formState.commandStartScript)
        assertEquals("npm stop", formState.commandStopScript)
        assertEquals("Node Services", formState.groupName)
    }

    @Test
    fun `fromServiceItem correctly maps LocalFile LogSource`() {
        val item = ServiceItem(
            id = "file-item",
            name = "File Worker",
            logSource = LogSource.LocalFile(path = "/var/log/syslog")
        )

        val formState = ServiceFormState.fromServiceItem(item, "System Logs")
        assertEquals(LogSourceType.LOCAL_FILE, formState.logSourceType)
        assertEquals("/var/log/syslog", formState.localFilePath)
        assertEquals("System Logs", formState.groupName)
    }

    @Test
    fun `validate fails when service name is duplicate across existing services`() {
        val existing = listOf(
            ServiceItem(id = "srv-1", name = "My Service", logSource = LogSource.Docker("c1")),
            ServiceItem(id = "srv-2", name = "Another Service", logSource = LogSource.Docker("c2"))
        )
        val formState = ServiceFormState(
            name = "my service",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "c3"
        )

        val result = formState.validate(existingServices = existing)
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("name"))
        assertEquals("A service with this name already exists", result.errors["name"])
    }

    @Test
    fun `validate allows same name when editing the same service`() {
        val existing = listOf(
            ServiceItem(id = "srv-1", name = "My Service", logSource = LogSource.Docker("c1"))
        )
        val formState = ServiceFormState(
            id = "srv-1",
            name = "My Service",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "c1"
        )

        val result = formState.validate(existingServices = existing, currentServiceId = "srv-1")
        assertIs<FormValidationResult.Success>(result)
        assertEquals("My Service", result.serviceItem.name)
    }

    @Test
    fun `generateSlug produces unique ids when duplicates exist`() {
        val existingIds = setOf("test", "test-2", "test-3")
        val uniqueSlug = DashboardConfig.generateSlug("Test", existingIds)
        assertEquals("test-4", uniqueSlug)
    }

    @Test
    fun `validate auto-generates slug when id is blank`() {
        val formState = ServiceFormState(
            id = "",
            name = "Order Service API",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "order-api"
        )
        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals("order-service-api", result.serviceItem.id)
    }

    @Test
    fun `validate preserves provided id when id is not blank`() {
        val formState = ServiceFormState(
            id = "custom-order-svc",
            name = "Order Service API",
            logSourceType = LogSourceType.DOCKER,
            dockerContainerName = "order-api"
        )
        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals("custom-order-svc", result.serviceItem.id)
    }

    @Test
    fun `validate with valid Command inputs and environment variables parses environment map correctly`() {
        val formState = ServiceFormState(
            name = "Command Service with Env",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "/home/user/app",
            commandStartScript = "npm run start",
            commandEnvironment = "PORT=8080\nNODE_ENV=production\nDEBUG=true"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(
            LogSource.Command(
                workingDir = "/home/user/app",
                startCommand = "npm run start",
                stopCommand = null,
                environment = mapOf(
                    "PORT" to "8080",
                    "NODE_ENV" to "production",
                    "DEBUG" to "true"
                )
            ),
            result.serviceItem.logSource
        )
    }

    @Test
    fun `validate with Command environment handles comments and blank lines gracefully`() {
        val formState = ServiceFormState(
            name = "Command Service with Comments",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "/home/user/app",
            commandStartScript = "python main.py",
            commandEnvironment = """
                # Server configuration
                HOST=0.0.0.0

                # Port configuration
                PORT=3000
                   # Indented comment
            """.trimIndent()
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(
            mapOf("HOST" to "0.0.0.0", "PORT" to "3000"),
            (result.serviceItem.logSource as LogSource.Command).environment
        )
    }

    @Test
    fun `validate with Command environment preserves values containing equals signs`() {
        val formState = ServiceFormState(
            name = "Command Service with Complex Values",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "/home/user/app",
            commandStartScript = "./run.sh",
            commandEnvironment = """
                DATABASE_URL=postgres://user:pass@localhost:5432/mydb?sslmode=disable
                GREETING = Hello = World = !
                EMPTY_VAL =
            """.trimIndent()
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Success>(result)
        assertEquals(
            mapOf(
                "DATABASE_URL" to "postgres://user:pass@localhost:5432/mydb?sslmode=disable",
                "GREETING" to "Hello = World = !",
                "EMPTY_VAL" to ""
            ),
            (result.serviceItem.logSource as LogSource.Command).environment
        )
    }

    @Test
    fun `validate fails when Command environment variable lacks equals sign`() {
        val formState = ServiceFormState(
            name = "Command Service Invalid Env",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "/home/user/app",
            commandStartScript = "npm start",
            commandEnvironment = "PORT=8080\nINVALID_LINE_WITHOUT_EQUALS"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("commandEnvironment"))
    }

    @Test
    fun `validate fails when Command environment variable has empty key`() {
        val formState = ServiceFormState(
            name = "Command Service Empty Key Env",
            logSourceType = LogSourceType.COMMAND,
            commandWorkingDir = "/home/user/app",
            commandStartScript = "npm start",
            commandEnvironment = " =value_without_key"
        )

        val result = formState.validate()
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("commandEnvironment"))
    }

    @Test
    fun `fromServiceItem correctly maps Command LogSource with environment variables to multiline string`() {
        val item = ServiceItem(
            id = "cmd-env-item",
            name = "Env Command Service",
            logSource = LogSource.Command(
                workingDir = "/apps/srv",
                startCommand = "npm start",
                stopCommand = null,
                environment = mapOf("PORT" to "8080", "NODE_ENV" to "production")
            )
        )

        val formState = ServiceFormState.fromServiceItem(item, "Default Group")
        assertEquals(LogSourceType.COMMAND, formState.logSourceType)
        assertEquals("PORT=8080\nNODE_ENV=production", formState.commandEnvironment)
    }

    @Test
    fun `fromServiceItem correctly maps Command LogSource with empty environment to empty string`() {
        val item = ServiceItem(
            id = "cmd-empty-env-item",
            name = "Empty Env Command Service",
            logSource = LogSource.Command(
                workingDir = "/apps/srv",
                startCommand = "npm start",
                stopCommand = null,
                environment = emptyMap()
            )
        )

        val formState = ServiceFormState.fromServiceItem(item, "Default Group")
        assertEquals(LogSourceType.COMMAND, formState.logSourceType)
        assertEquals("", formState.commandEnvironment)
    }
}


