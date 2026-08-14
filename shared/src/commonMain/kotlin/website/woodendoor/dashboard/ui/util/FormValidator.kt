package website.woodendoor.dashboard.ui.util

import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem

enum class LogSourceType(val displayName: String) {
    COMMAND("Directory Command"),
    DOCKER("Docker Container"),
    DOCKER_COMPOSE("Docker Compose"),
    LOCAL_FILE("Local File")
}

data class ServiceFormState(
    val id: String = "",
    val name: String = "",
    val groupName: String = "Default",
    val host: String = "127.0.0.1",
    val port: String = "",
    val openUrl: String = "",
    val healthUrl: String = "",
    val description: String = "",
    val tags: String = "",
    val logSourceType: LogSourceType = LogSourceType.COMMAND,
    val dockerContainerName: String = "",
    val localFilePath: String = "",
    val composeProjectDir: String = "",
    val composeServiceName: String = "",
    val composeFileName: String = "",
    val commandWorkingDir: String = "",
    val commandStartScript: String = "",
    val commandStopScript: String = ""
) {
    companion object {
        fun fromServiceItem(item: ServiceItem, groupName: String = "Default"): ServiceFormState {
            return when (val src = item.logSource) {
                is LogSource.Docker -> ServiceFormState(
                    id = item.id,
                    name = item.name,
                    groupName = groupName,
                    host = item.host,
                    port = item.port?.toString() ?: "",
                    openUrl = item.openUrl ?: "",
                    healthUrl = item.healthUrl ?: "",
                    description = item.description ?: "",
                    tags = item.tags.joinToString(", "),
                    logSourceType = LogSourceType.DOCKER,
                    dockerContainerName = src.containerName
                )
                is LogSource.DockerCompose -> ServiceFormState(
                    id = item.id,
                    name = item.name,
                    groupName = groupName,
                    host = item.host,
                    port = item.port?.toString() ?: "",
                    openUrl = item.openUrl ?: "",
                    healthUrl = item.healthUrl ?: "",
                    description = item.description ?: "",
                    tags = item.tags.joinToString(", "),
                    logSourceType = LogSourceType.DOCKER_COMPOSE,
                    composeProjectDir = src.projectDir,
                    composeServiceName = src.serviceName,
                    composeFileName = src.composeFile ?: ""
                )
                is LogSource.Command -> ServiceFormState(
                    id = item.id,
                    name = item.name,
                    groupName = groupName,
                    host = item.host,
                    port = item.port?.toString() ?: "",
                    openUrl = item.openUrl ?: "",
                    healthUrl = item.healthUrl ?: "",
                    description = item.description ?: "",
                    tags = item.tags.joinToString(", "),
                    logSourceType = LogSourceType.COMMAND,
                    commandWorkingDir = src.workingDir,
                    commandStartScript = src.startCommand,
                    commandStopScript = src.stopCommand ?: ""
                )
                is LogSource.LocalFile -> ServiceFormState(
                    id = item.id,
                    name = item.name,
                    groupName = groupName,
                    host = item.host,
                    port = item.port?.toString() ?: "",
                    openUrl = item.openUrl ?: "",
                    healthUrl = item.healthUrl ?: "",
                    description = item.description ?: "",
                    tags = item.tags.joinToString(", "),
                    logSourceType = LogSourceType.LOCAL_FILE,
                    localFilePath = src.path
                )
                LogSource.None -> ServiceFormState(
                    id = item.id,
                    name = item.name,
                    groupName = groupName,
                    host = item.host,
                    port = item.port?.toString() ?: "",
                    openUrl = item.openUrl ?: "",
                    healthUrl = item.healthUrl ?: "",
                    description = item.description ?: "",
                    tags = item.tags.joinToString(", "),
                    logSourceType = LogSourceType.COMMAND
                )
            }
        }
    }
}

sealed interface FormValidationResult {
    data class Success(val serviceItem: ServiceItem, val groupName: String) : FormValidationResult
    data class Error(val errors: Map<String, String>) : FormValidationResult
}

object ServiceFormValidator {

    fun validate(
        state: ServiceFormState,
        existingServices: List<ServiceItem> = emptyList(),
        currentServiceId: String? = null
    ): FormValidationResult {
        val errors = mutableMapOf<String, String>()

        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            errors["name"] = "Service name cannot be empty"
        } else {
            val isDuplicateName = existingServices.any { service ->
                service.id != currentServiceId && service.name.equals(trimmedName, ignoreCase = true)
            }
            if (isDuplicateName) {
                errors["name"] = "A service with this name already exists"
            }
        }

        val parsedPort = if (state.port.isNotBlank()) {
            val portNum = state.port.trim().toIntOrNull()
            if (portNum == null || portNum !in 1..65535) {
                errors["port"] = "Port must be a valid number (1-65535)"
                null
            } else {
                portNum
            }
        } else {
            null
        }

        val logSource = when (state.logSourceType) {
            LogSourceType.DOCKER -> {
                val containerName = state.dockerContainerName.trim()
                if (containerName.isEmpty()) {
                    errors["dockerContainerName"] = "Container name cannot be empty for Docker log source"
                    LogSource.None
                } else {
                    LogSource.Docker(containerName)
                }
            }
            LogSourceType.DOCKER_COMPOSE -> {
                val projectDir = state.composeProjectDir.trim()
                val serviceName = state.composeServiceName.trim()
                val composeFile = state.composeFileName.trim().ifEmpty { null }

                if (projectDir.isEmpty()) {
                    errors["composeProjectDir"] = "Project folder/directory cannot be empty for Docker Compose log source"
                }
                if (serviceName.isEmpty()) {
                    errors["composeServiceName"] = "Service name cannot be empty for Docker Compose log source"
                }

                if (projectDir.isNotEmpty() && serviceName.isNotEmpty()) {
                    LogSource.DockerCompose(
                        projectDir = projectDir,
                        serviceName = serviceName,
                        composeFile = composeFile
                    )
                } else {
                    LogSource.None
                }
            }
            LogSourceType.COMMAND -> {
                val workingDir = state.commandWorkingDir.trim()
                val startCommand = state.commandStartScript.trim()
                val stopCommand = state.commandStopScript.trim().ifEmpty { null }

                if (workingDir.isEmpty()) {
                    errors["commandWorkingDir"] = "Working directory cannot be empty for Directory Command log source"
                }
                if (startCommand.isEmpty()) {
                    errors["commandStartScript"] = "Start command cannot be empty for Directory Command log source"
                }

                if (workingDir.isNotEmpty() && startCommand.isNotEmpty()) {
                    LogSource.Command(
                        workingDir = workingDir,
                        startCommand = startCommand,
                        stopCommand = stopCommand
                    )
                } else {
                    LogSource.None
                }
            }
            LogSourceType.LOCAL_FILE -> {
                val filePath = state.localFilePath.trim()
                if (filePath.isEmpty()) {
                    errors["localFilePath"] = "File path cannot be empty for Local File log source"
                    LogSource.None
                } else {
                    LogSource.LocalFile(filePath)
                }
            }
        }

        if (errors.isNotEmpty()) {
            return FormValidationResult.Error(errors)
        }

        val existingIds = existingServices.filter { it.id != currentServiceId }.map { it.id }.toSet()
        val effectiveId = if (state.id.isNotBlank()) {
            state.id.trim()
        } else {
            generateSlug(trimmedName, existingIds)
        }

        val effectiveHost = if (state.host.isNotBlank()) state.host.trim() else "127.0.0.1"
        val effectiveGroup = if (state.groupName.isNotBlank()) state.groupName.trim() else "Default"
        val effectiveOpenUrl = state.openUrl.trim().ifEmpty { null }
        val effectiveHealthUrl = state.healthUrl.trim().ifEmpty { null }
        val effectiveDescription = state.description.trim().ifEmpty { null }

        val tagsList = state.tags.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val serviceItem = ServiceItem(
            id = effectiveId,
            name = trimmedName,
            host = effectiveHost,
            port = parsedPort,
            openUrl = effectiveOpenUrl,
            healthUrl = effectiveHealthUrl,
            logSource = logSource,
            description = effectiveDescription,
            tags = tagsList
        )

        return FormValidationResult.Success(
            serviceItem = serviceItem,
            groupName = effectiveGroup
        )
    }

    fun generateSlug(input: String, existingIds: Set<String> = emptySet()): String {
        val baseSlug = input.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .ifEmpty { "service-${System.currentTimeMillis()}" }

        if (baseSlug !in existingIds) {
            return baseSlug
        }

        var suffix = 2
        var candidate = "$baseSlug-$suffix"
        while (candidate in existingIds) {
            suffix++
            candidate = "$baseSlug-$suffix"
        }
        return candidate
    }
}
