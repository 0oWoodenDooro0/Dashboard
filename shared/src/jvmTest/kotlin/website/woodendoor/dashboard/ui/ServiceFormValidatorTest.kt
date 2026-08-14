package website.woodendoor.dashboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.ui.util.FormValidationResult
import website.woodendoor.dashboard.ui.util.LogSourceType
import website.woodendoor.dashboard.ui.util.ServiceFormState
import website.woodendoor.dashboard.ui.util.ServiceFormValidator

class ServiceFormValidatorTest {

    @Test
    fun `validate with valid inputs creates valid ServiceItem`() {
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
            dockerContainerName = "test-container",
            localFilePath = ""
        )

        val result = ServiceFormValidator.validate(formState)
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
    fun `validate generates id from name if id is blank`() {
        val formState = ServiceFormState(
            id = "",
            name = "My Amazing Service 2026",
            groupName = "Default",
            port = "3000"
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Success>(result)
        assertEquals("my-amazing-service-2026", result.serviceItem.id)
    }

    @Test
    fun `validate fails when name is blank`() {
        val formState = ServiceFormState(
            name = "   ",
            host = "127.0.0.1"
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("name"))
        assertEquals("Service name cannot be empty", result.errors["name"])
    }

    @Test
    fun `validate defaults host to 127_0_0_1 when host is blank`() {
        val formState = ServiceFormState(
            name = "Service Without Host",
            host = ""
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Success>(result)
        assertEquals("127.0.0.1", result.serviceItem.host)
    }

    @Test
    fun `validate allows optional empty port`() {
        val formState = ServiceFormState(
            name = "No Port Service",
            port = ""
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Success>(result)
        assertNull(result.serviceItem.port)
    }

    @Test
    fun `validate fails when port is non-numeric`() {
        val formState = ServiceFormState(
            name = "Invalid Port Service",
            port = "eight-zero"
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("port"))
        assertEquals("Port must be a valid number (1-65535)", result.errors["port"])
    }

    @Test
    fun `validate fails when port is out of range`() {
        val negativePortState = ServiceFormState(name = "Negative Port", port = "-1")
        val zeroPortState = ServiceFormState(name = "Zero Port", port = "0")
        val excessivePortState = ServiceFormState(name = "Too Large Port", port = "70000")

        val result1 = ServiceFormValidator.validate(negativePortState)
        val result2 = ServiceFormValidator.validate(zeroPortState)
        val result3 = ServiceFormValidator.validate(excessivePortState)

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

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("dockerContainerName"))
        assertEquals("Container name cannot be empty for Docker log source", result.errors["dockerContainerName"])
    }

    @Test
    fun `validate fails when local file source is selected but file path is blank`() {
        val formState = ServiceFormState(
            name = "File Service",
            logSourceType = LogSourceType.LOCAL_FILE,
            localFilePath = "   "
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Error>(result)
        assertTrue(result.errors.containsKey("localFilePath"))
        assertEquals("File path cannot be empty for Local File log source", result.errors["localFilePath"])
    }

    @Test
    fun `validate creates LocalFile log source with valid path`() {
        val formState = ServiceFormState(
            name = "File Service",
            logSourceType = LogSourceType.LOCAL_FILE,
            localFilePath = "/var/log/app.log"
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Success>(result)
        assertEquals(LogSource.LocalFile("/var/log/app.log"), result.serviceItem.logSource)
    }

    @Test
    fun `validate creates None log source when type is NONE`() {
        val formState = ServiceFormState(
            name = "None Service",
            logSourceType = LogSourceType.NONE
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Success>(result)
        assertEquals(LogSource.None, result.serviceItem.logSource)
    }

    @Test
    fun `validate cleans and trims tags`() {
        val formState = ServiceFormState(
            name = "Tagged Service",
            tags = "  redis , cache,, in-memory  , "
        )

        val result = ServiceFormValidator.validate(formState)
        assertIs<FormValidationResult.Success>(result)
        assertEquals(listOf("redis", "cache", "in-memory"), result.serviceItem.tags)
    }
}
