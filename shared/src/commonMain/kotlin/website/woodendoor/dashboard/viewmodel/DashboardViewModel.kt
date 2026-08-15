package website.woodendoor.dashboard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DashboardConfig
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceGroup
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.service.ConfigRepository
import website.woodendoor.dashboard.service.ServiceRuntimeManager

class DashboardViewModel(
    private val configRepository: ConfigRepository,
    private val serviceRuntimeManager: ServiceRuntimeManager,
    coroutineScope: CoroutineScope? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxLogBufferSize: Int = 1000
) : ViewModel() {

    private val vmJob = SupervisorJob()
    private val scope: CoroutineScope = if (coroutineScope != null) {
        val parentJob = coroutineScope.coroutineContext[Job]
        parentJob?.invokeOnCompletion { vmJob.cancel() }
        CoroutineScope(coroutineScope.coroutineContext + vmJob)
    } else {
        viewModelScope
    }

    private val _state = MutableStateFlow(DashboardUiState(isLoading = true))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var logStreamJob: Job? = null
    private var tickerJob: Job? = null
    private var configObserveJob: Job? = null

    init {
        initialize()
    }

    private fun initialize() {
        scope.launch(defaultDispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                val initialConfig = configRepository.loadConfig()
                _state.update { it.copy(config = initialConfig) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Failed to load configuration", isLoading = false) }
            }

            startObservingConfig()
            startPollingTicker()
        }
    }

    private fun startObservingConfig() {
        configObserveJob?.cancel()
        configObserveJob = scope.launch(defaultDispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                configRepository.observeConfig().collect { newConfig ->
                    _state.update { current ->
                        val serviceExists = current.selectedServiceId != null &&
                            newConfig.groups.flatMap { g -> g.services }.any { s -> s.id == current.selectedServiceId }
                        val updatedSelectedId = if (serviceExists) current.selectedServiceId else null

                        current.copy(
                            config = newConfig,
                            selectedServiceId = updatedSelectedId,
                            logs = if (serviceExists) current.logs else emptyList()
                        )
                    }
                    refreshHealthAndDocker()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Error observing config", isLoading = false) }
            }
        }
    }

    private fun startPollingTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(defaultDispatcher) {
            while (isActive) {
                val intervalSeconds = _state.value.config.pollingIntervalSeconds.coerceAtLeast(1L)
                delay(intervalSeconds * 1000L)
                refreshHealthAndDocker()
            }
        }
    }

    fun triggerRefresh() {
        scope.launch(defaultDispatcher) {
            refreshInternal()
        }
    }

    private suspend fun refreshInternal() {
        _state.update { it.copy(isLoading = true) }
        try {
            refreshHealthAndDocker()
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun refreshHealthAndDocker() {
        try {
            val isDocker = try {
                serviceRuntimeManager.isDockerAvailable()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Error checking Docker availability") }
                false
            }

            val currentServices = _state.value.allServices
            val statuses = if (currentServices.isNotEmpty()) {
                try {
                    serviceRuntimeManager.inspectServices(currentServices)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(errorMessage = e.message ?: "Error inspecting services") }
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            _state.update { current ->
                val mergedStatuses = current.serviceStatuses + statuses
                current.copy(
                    isDockerAvailable = isDocker,
                    serviceStatuses = mergedStatuses,
                    isLoading = false
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = e.message ?: "Error during refresh", isLoading = false) }
        }
    }

    fun selectService(serviceId: String?) {
        if (serviceId == _state.value.selectedServiceId) return

        logStreamJob?.cancel()
        logStreamJob = null

        _state.update {
            it.copy(
                selectedServiceId = serviceId,
                logs = emptyList()
            )
        }

        if (serviceId == null) return

        val selected = _state.value.allServices.find { it.id == serviceId } ?: return
        startLogStreamForService(selected)
    }

    private fun startLogStreamForService(service: ServiceItem) {
        if (service.logSource is LogSource.None) return

        logStreamJob?.cancel()
        logStreamJob = scope.launch(defaultDispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                serviceRuntimeManager.streamLogs(service, tail = 100)
                    .catch { e ->
                        _state.update { current ->
                            val updatedLogs = current.logs + "[Error streaming logs: ${e.message}]"
                            current.copy(logs = trimLogs(updatedLogs))
                        }
                    }
                    .collect { logLine ->
                        _state.update { current ->
                            val updatedLogs = current.logs + logLine
                            current.copy(logs = trimLogs(updatedLogs))
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { current ->
                    val updatedLogs = current.logs + "[Error streaming logs: ${e.message}]"
                    current.copy(logs = trimLogs(updatedLogs))
                }
            }
        }
    }

    private fun trimLogs(logs: List<String>): List<String> {
        return if (logs.size > maxLogBufferSize) {
            logs.takeLast(maxLogBufferSize)
        } else {
            logs
        }
    }

    fun addService(service: ServiceItem, groupId: String? = null) {
        val currentConfig = _state.value.config
        val targetGroupId = groupId ?: currentConfig.groups.firstOrNull()?.id ?: "default"

        val existingIds = currentConfig.groups.flatMap { it.services }.map { it.id }.toSet()
        val uniqueId = if (service.id in existingIds) {
            var suffix = 2
            var candidate = "${service.id}-$suffix"
            while (candidate in existingIds) {
                suffix++
                candidate = "${service.id}-$suffix"
            }
            candidate
        } else {
            service.id
        }
        val sanitizedService = if (uniqueId != service.id) service.copy(id = uniqueId) else service

        val updatedGroups = if (currentConfig.groups.any { it.id == targetGroupId }) {
            currentConfig.groups.map { group ->
                if (group.id == targetGroupId) {
                    group.copy(services = group.services + sanitizedService)
                } else {
                    group
                }
            }
        } else {
            currentConfig.groups + ServiceGroup(
                id = targetGroupId,
                name = targetGroupId,
                services = listOf(sanitizedService)
            )
        }

        val updatedConfig = currentConfig.copy(groups = updatedGroups)
        saveConfigInternal(updatedConfig)
    }

    fun updateService(service: ServiceItem) {
        val currentConfig = _state.value.config
        val currentSelected = _state.value.selectedService
        val isSelected = _state.value.selectedServiceId == service.id
        val logSourceChanged = isSelected && currentSelected?.logSource != service.logSource

        val updatedGroups = currentConfig.groups.map { group ->
            group.copy(services = group.services.map { s -> if (s.id == service.id) service else s })
        }
        val updatedConfig = currentConfig.copy(groups = updatedGroups)
        saveConfigInternal(updatedConfig)

        if (logSourceChanged) {
            _state.update { it.copy(logs = emptyList()) }
            startLogStreamForService(service)
        }
    }

    fun deleteService(serviceId: String) {
        val currentConfig = _state.value.config
        val targetService = _state.value.allServices.find { it.id == serviceId }
        val updatedGroups = currentConfig.groups.map { group ->
            group.copy(services = group.services.filterNot { it.id == serviceId })
        }
        val updatedConfig = currentConfig.copy(groups = updatedGroups)

        if (_state.value.selectedServiceId == serviceId) {
            selectService(null)
        }

        if (targetService != null && serviceRuntimeManager.isRunning(targetService)) {
            scope.launch(defaultDispatcher) {
                try {
                    serviceRuntimeManager.stopService(targetService)
                } catch (_: Exception) {}
            }
        }

        saveConfigInternal(updatedConfig)
    }

    fun addGroup(group: ServiceGroup) {
        val currentConfig = _state.value.config
        val updatedConfig = currentConfig.copy(groups = currentConfig.groups + group)
        saveConfigInternal(updatedConfig)
    }

    fun deleteGroup(groupId: String) {
        val currentConfig = _state.value.config
        val targetGroup = currentConfig.groups.find { it.id == groupId }
        val targetServiceIds = targetGroup?.services?.map { it.id }?.toSet() ?: emptySet()

        if (_state.value.selectedServiceId in targetServiceIds) {
            selectService(null)
        }

        if (targetGroup != null) {
            for (service in targetGroup.services) {
                if (serviceRuntimeManager.isRunning(service)) {
                    scope.launch(defaultDispatcher) {
                        try {
                            serviceRuntimeManager.stopService(service)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        val updatedConfig = currentConfig.copy(groups = currentConfig.groups.filterNot { it.id == groupId })
        saveConfigInternal(updatedConfig)
    }

    fun startService(service: ServiceItem) {
        if (_state.value.selectedServiceId != service.id) {
            _state.update { it.copy(selectedServiceId = service.id, logs = emptyList()) }
        }

        scope.launch(defaultDispatcher) {
            _state.update { it.copy(operatingServiceIds = it.operatingServiceIds + service.id) }
            try {
                try {
                    serviceRuntimeManager.startService(service)
                    startLogStreamForService(service)
                    refreshHealthAndDocker()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(errorMessage = "Failed to start service ${service.name}: ${e.message}") }
                }
            } finally {
                _state.update { it.copy(operatingServiceIds = it.operatingServiceIds - service.id) }
            }
        }
    }

    fun stopService(service: ServiceItem) {
        scope.launch(defaultDispatcher) {
            _state.update { it.copy(operatingServiceIds = it.operatingServiceIds + service.id) }
            try {
                try {
                    serviceRuntimeManager.stopService(service)
                    refreshHealthAndDocker()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(errorMessage = "Failed to stop service ${service.name}: ${e.message}") }
                }
            } finally {
                _state.update { it.copy(operatingServiceIds = it.operatingServiceIds - service.id) }
            }
        }
    }

    fun restartService(service: ServiceItem) {
        if (_state.value.selectedServiceId != service.id) {
            _state.update { it.copy(selectedServiceId = service.id, logs = emptyList()) }
        }

        scope.launch(defaultDispatcher) {
            _state.update { it.copy(operatingServiceIds = it.operatingServiceIds + service.id) }
            try {
                try {
                    serviceRuntimeManager.restartService(service)
                    startLogStreamForService(service)
                    refreshHealthAndDocker()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(errorMessage = "Failed to restart service ${service.name}: ${e.message}") }
                }
            } finally {
                _state.update { it.copy(operatingServiceIds = it.operatingServiceIds - service.id) }
            }
        }
    }

    private fun saveConfigInternal(config: DashboardConfig) {
        try {
            configRepository.saveConfig(config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = e.message ?: "Failed to save configuration") }
        }
    }

    fun setLogSearchQuery(query: String) {
        _state.update { it.copy(logSearchQuery = query) }
    }

    fun toggleAutoScroll(enabled: Boolean) {
        _state.update { it.copy(isAutoScrollEnabled = enabled) }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    public override fun onCleared() {
        super.onCleared()
        vmJob.cancel()
        logStreamJob?.cancel()
        tickerJob?.cancel()
        configObserveJob?.cancel()
    }
}
