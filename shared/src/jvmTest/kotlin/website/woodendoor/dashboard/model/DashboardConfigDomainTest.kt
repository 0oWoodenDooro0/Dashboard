package website.woodendoor.dashboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardConfigDomainTest {

    private fun createSampleConfig(): DashboardConfig {
        return DashboardConfig(
            version = 1,
            pollingIntervalSeconds = 5,
            groups = listOf(
                ServiceGroup(
                    id = "web-apps",
                    name = "Web Applications",
                    services = listOf(
                        ServiceItem(id = "frontend", name = "Frontend App", port = 3000),
                        ServiceItem(id = "backend", name = "Backend API", port = 8000)
                    )
                ),
                ServiceGroup(
                    id = "databases",
                    name = "Databases & Cache",
                    services = listOf(
                        ServiceItem(id = "postgres", name = "PostgreSQL", port = 5432)
                    )
                )
            )
        )
    }

    // --- Query & Discovery Tests ---

    @Test
    fun allServices_returnsFlattenedListOfAllServices() {
        val config = createSampleConfig()
        val all = config.allServices

        assertEquals(3, all.size)
        assertEquals(listOf("frontend", "backend", "postgres"), all.map { it.id })
    }

    @Test
    fun allServices_whenEmptyGroups_returnsEmptyList() {
        val emptyConfig = DashboardConfig(groups = emptyList())
        assertTrue(emptyConfig.allServices.isEmpty())
        assertTrue(emptyConfig.allServiceIds.isEmpty())
    }

    @Test
    fun allServiceIds_returnsSetOfUniqueIds() {
        val config = createSampleConfig()
        assertEquals(setOf("frontend", "backend", "postgres"), config.allServiceIds)
    }

    @Test
    fun findService_whenServiceExists_returnsServiceItem() {
        val config = createSampleConfig()
        val service = config.findService("backend")

        assertNotNull(service)
        assertEquals("backend", service.id)
        assertEquals("Backend API", service.name)
        assertEquals(8000, service.port)
    }

    @Test
    fun findService_whenServiceDoesNotExist_returnsNull() {
        val config = createSampleConfig()
        assertNull(config.findService("non-existent"))
    }

    @Test
    fun findGroup_byExactId_returnsMatchingGroup() {
        val config = createSampleConfig()
        val group = config.findGroup("databases")

        assertNotNull(group)
        assertEquals("databases", group.id)
        assertEquals("Databases & Cache", group.name)
    }

    @Test
    fun findGroup_byCaseInsensitiveName_returnsMatchingGroup() {
        val config = createSampleConfig()
        val group = config.findGroup("web applications")

        assertNotNull(group)
        assertEquals("web-apps", group.id)
    }

    @Test
    fun findGroup_whenNotFound_returnsNull() {
        val config = createSampleConfig()
        assertNull(config.findGroup("unknown-group"))
    }

    @Test
    fun findGroupForService_whenServiceExists_returnsParentGroup() {
        val config = createSampleConfig()
        val group = config.findGroupForService("postgres")

        assertNotNull(group)
        assertEquals("databases", group.id)
    }

    @Test
    fun findGroupForService_whenServiceDoesNotExist_returnsNull() {
        val config = createSampleConfig()
        assertNull(config.findGroupForService("ghost-service"))
    }

    // --- ID Uniqueness & Slug Generation Tests ---

    @Test
    fun ensureUniqueServiceId_whenIdIsUnique_returnsOriginalId() {
        val config = createSampleConfig()
        val uniqueId = config.ensureUniqueServiceId("redis")
        assertEquals("redis", uniqueId)
    }

    @Test
    fun ensureUniqueServiceId_whenIdCollides_appendsSuffix2() {
        val config = createSampleConfig()
        val uniqueId = config.ensureUniqueServiceId("frontend")
        assertEquals("frontend-2", uniqueId)
    }

    @Test
    fun ensureUniqueServiceId_whenIdAndSuffix2Collide_appendsSuffix3() {
        val config = createSampleConfig().copy(
            groups = listOf(
                ServiceGroup(
                    id = "g1",
                    name = "Group 1",
                    services = listOf(
                        ServiceItem(id = "api", name = "API 1"),
                        ServiceItem(id = "api-2", name = "API 2")
                    )
                )
            )
        )
        val uniqueId = config.ensureUniqueServiceId("api")
        assertEquals("api-3", uniqueId)
    }

    @Test
    fun ensureUniqueServiceId_whenMatchesCurrentServiceId_allowsSameId() {
        val config = createSampleConfig()
        val uniqueId = config.ensureUniqueServiceId("frontend", currentServiceId = "frontend")
        assertEquals("frontend", uniqueId)
    }

    @Test
    fun generateSlug_convertsToLowerCaseAndReplacesSpecialChars() {
        val slug = DashboardConfig.generateSlug("My Awesome Service #1!")
        assertEquals("my-awesome-service-1", slug)
    }

    @Test
    fun generateSlug_trimsLeadingAndTrailingDashes() {
        val slug = DashboardConfig.generateSlug("---Test Service---")
        assertEquals("test-service", slug)
    }

    @Test
    fun generateSlug_whenEmptyOrOnlySpecialChars_generatesFallbackSlug() {
        val slug = DashboardConfig.generateSlug("!@#$%^&*")
        assertTrue(slug.startsWith("service-"))
    }

    @Test
    fun generateSlug_whenCollisionExists_appendsNumericSuffix() {
        val existing = setOf("my-app")
        val slug = DashboardConfig.generateSlug("My App", existing)
        assertEquals("my-app-2", slug)
    }

    @Test
    fun generateSlug_whenMultipleCollisionsExist_incrementsSuffix() {
        val existing = setOf("test", "test-2", "test-3")
        val slug = DashboardConfig.generateSlug("Test", existing)
        assertEquals("test-4", slug)
    }

    @Test
    fun generateServiceId_withExistingServices_producesUniqueSlug() {
        val config = createSampleConfig()
        val generatedId = config.generateServiceId("Analytics Service")
        assertEquals("analytics-service", generatedId)
    }

    @Test
    fun generateServiceId_whenCollidesWithExistingService_appendsSuffix() {
        val config = createSampleConfig()
        // "Frontend App" -> base slug is "frontend-app" which is not in ["frontend", "backend", "postgres"]
        assertEquals("frontend-app", config.generateServiceId("Frontend App"))

        // Exact collision with "frontend"
        val collidingId = config.generateServiceId("Frontend")
        assertEquals("frontend-2", collidingId)
    }

    @Test
    fun generateServiceId_whenMatchesCurrentServiceId_allowsSameId() {
        val config = createSampleConfig()
        val id = config.generateServiceId("frontend", currentServiceId = "frontend")
        assertEquals("frontend", id)
    }

    // --- Immutable Mutation Tests ---

    @Test
    fun withServiceAdded_toExistingGroupById_appendsServiceToGroup() {
        val config = createSampleConfig()
        val newService = ServiceItem(id = "redis", name = "Redis Cache", port = 6379)

        val updated = config.withServiceAdded(newService, targetGroupIdOrName = "databases")

        assertEquals(4, updated.allServices.size)
        val dbGroup = updated.findGroup("databases")
        assertNotNull(dbGroup)
        assertEquals(2, dbGroup.services.size)
        assertEquals(listOf("postgres", "redis"), dbGroup.services.map { it.id })

        // Original config remains untouched
        assertEquals(3, config.allServices.size)
    }

    @Test
    fun withServiceAdded_toExistingGroupByCaseInsensitiveName_appendsServiceToGroup() {
        val config = createSampleConfig()
        val newService = ServiceItem(id = "admin-web", name = "Admin Portal", port = 3001)

        val updated = config.withServiceAdded(newService, targetGroupIdOrName = "web applications")

        val webGroup = updated.findGroup("web-apps")
        assertNotNull(webGroup)
        assertEquals(3, webGroup.services.size)
        assertTrue(webGroup.services.any { it.id == "admin-web" })
    }

    @Test
    fun withServiceAdded_whenTargetGroupDoesNotExist_createsNewGroup() {
        val config = createSampleConfig()
        val newService = ServiceItem(id = "worker-1", name = "Background Worker")

        val updated = config.withServiceAdded(newService, targetGroupIdOrName = "Workers")

        assertEquals(3, updated.groups.size)
        val workerGroup = updated.findGroup("Workers")
        assertNotNull(workerGroup)
        assertEquals("Workers", workerGroup.name)
        assertEquals(listOf("worker-1"), workerGroup.services.map { it.id })
    }

    @Test
    fun withServiceAdded_whenNoTargetGroupProvided_addsToFirstGroupOrDefault() {
        val config = createSampleConfig()
        val newService = ServiceItem(id = "landing-page", name = "Landing Page")

        val updated = config.withServiceAdded(newService)

        val firstGroup = updated.groups.first()
        assertEquals("web-apps", firstGroup.id)
        assertTrue(firstGroup.services.any { it.id == "landing-page" })
    }

    @Test
    fun withServiceAdded_whenConfigHasNoGroupsAndNoTargetProvided_createsDefaultGroup() {
        val emptyConfig = DashboardConfig(groups = emptyList())
        val newService = ServiceItem(id = "srv-1", name = "Service One")

        val updated = emptyConfig.withServiceAdded(newService)

        assertEquals(1, updated.groups.size)
        assertEquals("default", updated.groups.first().id)
        assertEquals("default", updated.groups.first().name)
        assertEquals(listOf("srv-1"), updated.groups.first().services.map { it.id })
    }

    @Test
    fun withServiceAdded_whenServiceIdCollides_automaticallySanitizesId() {
        val config = createSampleConfig()
        val collidingService = ServiceItem(id = "frontend", name = "Frontend Duplicate", port = 3001)

        val updated = config.withServiceAdded(collidingService, targetGroupIdOrName = "web-apps")

        val added = updated.findService("frontend-2")
        assertNotNull(added)
        assertEquals("frontend-2", added.id)
        assertEquals("Frontend Duplicate", added.name)
        assertEquals(3001, added.port)
        // Original frontend remains untouched
        assertEquals("frontend", updated.findService("frontend")?.id)
    }

    @Test
    fun withServiceUpdated_replacesMatchingServiceInItsGroup() {
        val config = createSampleConfig()
        val updatedService = ServiceItem(
            id = "backend",
            name = "Backend API v2",
            port = 8080,
            description = "Updated description"
        )

        val updated = config.withServiceUpdated(updatedService)

        val found = updated.findService("backend")
        assertNotNull(found)
        assertEquals("Backend API v2", found.name)
        assertEquals(8080, found.port)
        assertEquals("Updated description", found.description)

        // Ensure service remained in same parent group
        val parentGroup = updated.findGroupForService("backend")
        assertEquals("web-apps", parentGroup?.id)
    }

    @Test
    fun withServiceUpdated_whenTargetGroupIsSameGroupById_updatesServiceInSameGroup() {
        val config = createSampleConfig()
        val updatedService = ServiceItem(
            id = "backend",
            name = "Backend API v2",
            port = 8080
        )

        val updated = config.withServiceUpdated(updatedService, targetGroupIdOrName = "web-apps")

        val parentGroup = updated.findGroupForService("backend")
        assertEquals("web-apps", parentGroup?.id)
        assertEquals("Backend API v2", updated.findService("backend")?.name)
    }

    @Test
    fun withServiceUpdated_whenTargetGroupIsSameGroupByCaseInsensitiveName_updatesServiceInSameGroup() {
        val config = createSampleConfig()
        val updatedService = ServiceItem(
            id = "backend",
            name = "Backend API v2",
            port = 8080
        )

        val updated = config.withServiceUpdated(updatedService, targetGroupIdOrName = "Web Applications")

        val parentGroup = updated.findGroupForService("backend")
        assertEquals("web-apps", parentGroup?.id)
        assertEquals("Backend API v2", updated.findService("backend")?.name)
    }

    @Test
    fun withServiceUpdated_whenMovingToExistingGroupById_movesServiceToTargetGroup() {
        val config = createSampleConfig()
        val updatedService = ServiceItem(
            id = "backend",
            name = "Backend API Moved",
            port = 8000
        )

        val updated = config.withServiceUpdated(updatedService, targetGroupIdOrName = "databases")

        val oldGroup = updated.findGroup("web-apps")
        assertNotNull(oldGroup)
        assertEquals(listOf("frontend"), oldGroup.services.map { it.id })

        val targetGroup = updated.findGroup("databases")
        assertNotNull(targetGroup)
        assertEquals(listOf("postgres", "backend"), targetGroup.services.map { it.id })

        val service = updated.findService("backend")
        assertNotNull(service)
        assertEquals("Backend API Moved", service.name)
        assertEquals("databases", updated.findGroupForService("backend")?.id)
    }

    @Test
    fun withServiceUpdated_whenMovingToExistingGroupByCaseInsensitiveName_movesServiceToTargetGroup() {
        val config = createSampleConfig()
        val updatedService = ServiceItem(
            id = "frontend",
            name = "Frontend App Moved",
            port = 3000
        )

        val updated = config.withServiceUpdated(updatedService, targetGroupIdOrName = "databases & cache")

        val oldGroup = updated.findGroup("web-apps")
        assertNotNull(oldGroup)
        assertEquals(listOf("backend"), oldGroup.services.map { it.id })

        val targetGroup = updated.findGroup("databases")
        assertNotNull(targetGroup)
        assertEquals(listOf("postgres", "frontend"), targetGroup.services.map { it.id })

        assertEquals("databases", updated.findGroupForService("frontend")?.id)
    }

    @Test
    fun withServiceUpdated_whenTargetGroupDoesNotExist_createsNewGroupAndMovesService() {
        val config = createSampleConfig()
        val updatedService = ServiceItem(
            id = "frontend",
            name = "Frontend Worker",
            port = 3000
        )

        val updated = config.withServiceUpdated(updatedService, targetGroupIdOrName = "Background Workers")

        assertEquals(3, updated.groups.size)

        val oldGroup = updated.findGroup("web-apps")
        assertNotNull(oldGroup)
        assertEquals(listOf("backend"), oldGroup.services.map { it.id })

        val newGroup = updated.findGroup("Background Workers")
        assertNotNull(newGroup)
        assertEquals("Background Workers", newGroup.id)
        assertEquals("Background Workers", newGroup.name)
        assertEquals(listOf("frontend"), newGroup.services.map { it.id })

        assertEquals("Background Workers", updated.findGroupForService("frontend")?.id)
    }

    @Test
    fun withServiceUpdated_whenServiceDoesNotExist_returnsIdenticalConfig() {
        val config = createSampleConfig()
        val unknownService = ServiceItem(id = "unknown", name = "Unknown Service")

        val updated = config.withServiceUpdated(unknownService)
        assertEquals(config, updated)

        val updatedWithGroup = config.withServiceUpdated(unknownService, targetGroupIdOrName = "databases")
        assertEquals(config, updatedWithGroup)
    }

    @Test
    fun withServiceDeleted_removesServiceFromParentGroup() {
        val config = createSampleConfig()
        val updated = config.withServiceDeleted("frontend")

        assertEquals(2, updated.allServices.size)
        assertNull(updated.findService("frontend"))

        val webGroup = updated.findGroup("web-apps")
        assertNotNull(webGroup)
        assertEquals(listOf("backend"), webGroup.services.map { it.id })
    }

    @Test
    fun withServiceDeleted_whenServiceDoesNotExist_returnsIdenticalConfig() {
        val config = createSampleConfig()
        val updated = config.withServiceDeleted("non-existent")
        assertEquals(config, updated)
    }

    @Test
    fun withGroupAdded_whenNewGroup_appendsToGroups() {
        val config = createSampleConfig()
        val newGroup = ServiceGroup(id = "analytics", name = "Analytics & Logs")

        val updated = config.withGroupAdded(newGroup)

        assertEquals(3, updated.groups.size)
        assertNotNull(updated.findGroup("analytics"))
    }

    @Test
    fun withGroupAdded_whenGroupIdExists_replacesExistingGroup() {
        val config = createSampleConfig()
        val updatedGroup = ServiceGroup(id = "web-apps", name = "Web Apps Renamed", services = emptyList())

        val updated = config.withGroupAdded(updatedGroup)

        assertEquals(2, updated.groups.size)
        val group = updated.findGroup("web-apps")
        assertNotNull(group)
        assertEquals("Web Apps Renamed", group.name)
        assertTrue(group.services.isEmpty())
    }

    @Test
    fun withGroupDeleted_removesGroupAndAllContainedServices() {
        val config = createSampleConfig()
        val updated = config.withGroupDeleted("web-apps")

        assertEquals(1, updated.groups.size)
        assertNull(updated.findGroup("web-apps"))
        assertEquals(listOf("postgres"), updated.allServices.map { it.id })
    }

    @Test
    fun withGroupDeleted_whenGroupIdNotFound_returnsIdenticalConfig() {
        val config = createSampleConfig()
        val updated = config.withGroupDeleted("non-existent-group")
        assertEquals(config, updated)
    }
}
