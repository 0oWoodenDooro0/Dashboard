package website.woodendoor.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LogSource {
    @Serializable
    @SerialName("docker")
    data class Docker(val containerName: String) : LogSource

    @Serializable
    @SerialName("docker-compose")
    data class DockerCompose(
        val projectDir: String,
        val serviceName: String,
        val composeFile: String? = null
    ) : LogSource

    @Serializable
    @SerialName("file")
    data class LocalFile(val path: String) : LogSource

    @Serializable
    @SerialName("command")
    data class Command(
        val workingDir: String,
        val startCommand: String,
        val stopCommand: String? = null,
        val environment: Map<String, String> = emptyMap()
    ) : LogSource

    @Serializable
    @SerialName("none")
    data object None : LogSource
}

@Serializable
data class ServiceItem(
    val id: String,
    val name: String,
    val host: String = "127.0.0.1",
    val port: Int? = null,
    val healthUrl: String? = null,
    val openUrl: String? = null,
    val logSource: LogSource = LogSource.None,
    val description: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class ServiceGroup(
    val id: String,
    val name: String,
    val services: List<ServiceItem> = emptyList()
)

@Serializable
data class DashboardConfig(
    val version: Int = 1,
    val pollingIntervalSeconds: Long = 5,
    val groups: List<ServiceGroup> = emptyList()
) {
    /**
     * Flattened list of all configured services across all groups.
     */
    val allServices: List<ServiceItem>
        get() = groups.flatMap { it.services }

    /**
     * Set of all unique service IDs across all groups.
     */
    val allServiceIds: Set<String>
        get() = allServices.map { it.id }.toSet()

    /**
     * Finds a service by its unique ID, or returns null if not found.
     */
    fun findService(id: String): ServiceItem? =
        allServices.find { it.id == id }

    /**
     * Finds a group by exact ID or case-insensitive name match.
     */
    fun findGroup(idOrName: String): ServiceGroup? =
        groups.find { it.id == idOrName || it.name.equals(idOrName, ignoreCase = true) }

    /**
     * Finds the parent group containing the given service ID.
     */
    fun findGroupForService(serviceId: String): ServiceGroup? =
        groups.find { group -> group.services.any { it.id == serviceId } }

    /**
     * Generates a unique service ID slug from the provided service [name],
     * taking into account all existing service IDs in this config (except [currentServiceId] if provided).
     */
    fun generateServiceId(name: String, currentServiceId: String? = null): String {
        val existingIds = if (currentServiceId != null) {
            allServiceIds - currentServiceId
        } else {
            allServiceIds
        }
        return generateSlug(name, existingIds)
    }

    /**
     * Resolves a guaranteed unique service ID using numeric suffix incrementation (-2, -3, ...).
     * If [proposedId] is already unique (or matches [currentServiceId]), returns [proposedId] untouched.
     */
    fun ensureUniqueServiceId(proposedId: String, currentServiceId: String? = null): String {
        val existingIds = if (currentServiceId != null) {
            allServiceIds - currentServiceId
        } else {
            allServiceIds
        }

        if (proposedId !in existingIds) {
            return proposedId
        }

        var suffix = 2
        var candidate = "$proposedId-$suffix"
        while (candidate in existingIds) {
            suffix++
            candidate = "$proposedId-$suffix"
        }
        return candidate
    }

    companion object {
        /**
         * Normalizes a service name or arbitrary string into a URL/ID-safe slug,
         * ensuring uniqueness against [existingIds] with numeric suffix incrementation (-2, -3, ...).
         */
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

    /**
     * Returns a new [DashboardConfig] with the service added to the target group.
     * Automatically ensures ID uniqueness and creates the target group if it doesn't exist.
     */
    fun withServiceAdded(service: ServiceItem, targetGroupIdOrName: String? = null): DashboardConfig {
        val uniqueId = ensureUniqueServiceId(service.id)
        val sanitizedService = if (uniqueId != service.id) service.copy(id = uniqueId) else service

        val targetGroup = targetGroupIdOrName?.let { findGroup(it) }
            ?: (if (targetGroupIdOrName == null) groups.firstOrNull() else null)

        val updatedGroups = if (targetGroup != null) {
            groups.map { group ->
                if (group.id == targetGroup.id) {
                    group.copy(services = group.services + sanitizedService)
                } else {
                    group
                }
            }
        } else {
            val newGroupId = targetGroupIdOrName ?: "default"
            val newGroupName = targetGroupIdOrName ?: "default"
            groups + ServiceGroup(
                id = newGroupId,
                name = newGroupName,
                services = listOf(sanitizedService)
            )
        }

        return copy(groups = updatedGroups)
    }

    /**
     * Returns a new [DashboardConfig] with the existing service updated in-place or moved to the target group.
     */
    fun withServiceUpdated(service: ServiceItem, targetGroupIdOrName: String? = null): DashboardConfig {
        val currentGroup = findGroupForService(service.id) ?: return this
        val targetGroup = targetGroupIdOrName?.let { findGroup(it) }

        val isSameGroup = targetGroupIdOrName == null ||
            (targetGroup != null && targetGroup.id == currentGroup.id) ||
            (targetGroup == null && (currentGroup.id == targetGroupIdOrName || currentGroup.name.equals(targetGroupIdOrName, ignoreCase = true)))

        if (isSameGroup) {
            val updatedGroups = groups.map { group ->
                if (group.id == currentGroup.id) {
                    val updatedServices = group.services.map { existing ->
                        if (existing.id == service.id) service else existing
                    }
                    group.copy(services = updatedServices)
                } else {
                    group
                }
            }
            return copy(groups = updatedGroups)
        }

        if (targetGroup != null) {
            val updatedGroups = groups.map { group ->
                when (group.id) {
                    currentGroup.id -> group.copy(services = group.services.filterNot { it.id == service.id })
                    targetGroup.id -> {
                        val existingIndex = group.services.indexOfFirst { it.id == service.id }
                        val updatedServices = if (existingIndex >= 0) {
                            group.services.toMutableList().apply { set(existingIndex, service) }
                        } else {
                            group.services + service
                        }
                        group.copy(services = updatedServices)
                    }
                    else -> group
                }
            }
            return copy(groups = updatedGroups)
        }

        val newGroupId = targetGroupIdOrName
        val newGroupName = targetGroupIdOrName
        val updatedGroups = groups.map { group ->
            if (group.id == currentGroup.id) {
                group.copy(services = group.services.filterNot { it.id == service.id })
            } else {
                group
            }
        } + ServiceGroup(
            id = newGroupId,
            name = newGroupName,
            services = listOf(service)
        )
        return copy(groups = updatedGroups)
    }

    /**
     * Returns a new [DashboardConfig] with the specified service removed.
     */
    fun withServiceDeleted(serviceId: String): DashboardConfig {
        var found = false
        val updatedGroups = groups.map { group ->
            val filtered = group.services.filterNot {
                if (it.id == serviceId) {
                    found = true
                    true
                } else {
                    false
                }
            }
            group.copy(services = filtered)
        }
        return if (found) copy(groups = updatedGroups) else this
    }

    /**
     * Returns a new [DashboardConfig] with the service group added or updated.
     */
    fun withGroupAdded(group: ServiceGroup): DashboardConfig {
        val existingIndex = groups.indexOfFirst { it.id == group.id }
        val updatedGroups = if (existingIndex >= 0) {
            groups.toMutableList().apply { set(existingIndex, group) }
        } else {
            groups + group
        }
        return copy(groups = updatedGroups)
    }

    /**
     * Returns a new [DashboardConfig] with the specified service group removed.
     */
    fun withGroupDeleted(groupId: String): DashboardConfig {
        val filtered = groups.filterNot { it.id == groupId }
        return if (filtered.size != groups.size) copy(groups = filtered) else this
    }
}

