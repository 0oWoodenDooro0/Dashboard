package website.woodendoor.dashboard.ui.util

import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem

enum class LogSourceType(val displayName: String) {
    NONE("None"),
    DOCKER("Docker Container"),
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
    val logSourceType: LogSourceType = LogSourceType.NONE,
    val dockerContainerName: String = "",
    val localFilePath: String = ""
) {
    companion object {
        fun fromServiceItem(item: ServiceItem, groupName: String = "Default"): ServiceFormState {
            val (sourceType, containerName, filePath) = when (val src = item.logSource) {
                is LogSource.Docker -> Triple(LogSourceType.DOCKER, src.containerName, "")
                is LogSource.LocalFile -> Triple(LogSourceType.LOCAL_FILE, "", src.path)
                LogSource.None -> Triple(LogSourceType.NONE, "", "")
            }

            return ServiceFormState(
                id = item.id,
                name = item.name,
                groupName = groupName,
                host = item.host,
                port = item.port?.toString() ?: "",
                openUrl = item.openUrl ?: "",
                healthUrl = item.healthUrl ?: "",
                description = item.description ?: "",
                tags = item.tags.joinToString(", "),
                logSourceType = sourceType,
                dockerContainerName = containerName,
                localFilePath = filePath
            )
        }
    }
}

sealed interface FormValidationResult {
    data class Success(val serviceItem: ServiceItem, val groupName: String) : FormValidationResult
    data class Error(val errors: Map<String, String>) : FormValidationResult
}

object ServiceFormValidator {

    fun validate(state: ServiceFormState): FormValidationResult {
        val errors = mutableMapOf<String, String>()

        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            errors["name"] = "Service name cannot be empty"
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
            LogSourceType.LOCAL_FILE -> {
                val filePath = state.localFilePath.trim()
                if (filePath.isEmpty()) {
                    errors["localFilePath"] = "File path cannot be empty for Local File log source"
                    LogSource.None
                } else {
                    LogSource.LocalFile(filePath)
                }
            }
            LogSourceType.NONE -> LogSource.None
        }

        if (errors.isNotEmpty()) {
            return FormValidationResult.Error(errors)
        }

        val effectiveId = if (state.id.isNotBlank()) {
            state.id.trim()
        } else {
            generateSlug(trimmedName)
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

    private fun generateSlug(input: String): String {
        val slug = input.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
        return slug.ifEmpty { "service-${System.currentTimeMillis()}" }
    }
}
