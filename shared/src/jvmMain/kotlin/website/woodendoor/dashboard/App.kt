package website.woodendoor.dashboard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import website.woodendoor.dashboard.model.ServiceGroup
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.service.DefaultServiceRuntimeManager
import website.woodendoor.dashboard.service.FileConfigRepository
import website.woodendoor.dashboard.ui.components.DashboardTopBar
import website.woodendoor.dashboard.ui.components.DeleteCategoryConfirmDialog
import website.woodendoor.dashboard.ui.components.DeleteConfirmDialog
import website.woodendoor.dashboard.ui.components.LogConsolePane
import website.woodendoor.dashboard.ui.components.ServiceEditDialog
import website.woodendoor.dashboard.ui.components.ServiceListPane
import website.woodendoor.dashboard.ui.theme.DashboardTheme
import website.woodendoor.dashboard.ui.util.PlatformUtils
import website.woodendoor.dashboard.ui.util.UiHelpers
import website.woodendoor.dashboard.viewmodel.DashboardViewModel

@Composable
fun App(
    viewModel: DashboardViewModel = remember {
        DashboardViewModel(
            configRepository = FileConfigRepository(),
            serviceRuntimeManager = DefaultServiceRuntimeManager()
        )
    }
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<ServiceItem?>(null) }
    var editingGroupName by remember { mutableStateOf("Default") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingService by remember { mutableStateOf<ServiceItem?>(null) }

    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf<ServiceGroup?>(null) }

    // Display reactive error snackbar notifications
    val errorMessage = state.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = "Dismiss"
            )
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                viewModel.clearError()
            }
        }
    }

    val summaryMetrics = remember(state.allServices, state.serviceStatuses, state.isDockerAvailable) {
        UiHelpers.calculateSummaryMetrics(
            services = state.allServices,
            statuses = state.serviceStatuses,
            isDockerAvailable = state.isDockerAvailable
        )
    }

    DashboardTheme {
        Scaffold(
            topBar = {
                DashboardTopBar(
                    metrics = summaryMetrics,
                    isLoading = state.isLoading,
                    onRefresh = { viewModel.triggerRefresh() },
                    onAddService = {
                        editingService = null
                        editingGroupName = state.config.groups.firstOrNull()?.name ?: "Web Applications"
                        showEditDialog = true
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            // Two-Column Split Layout with VerticalDivider
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Left Column: Service List Pane (Fixed width: 380dp)
                ServiceListPane(
                    groups = state.config.groups,
                    serviceStatuses = state.serviceStatuses,
                    selectedServiceId = state.selectedServiceId,
                    operatingServiceIds = state.operatingServiceIds,
                    onSelectService = { serviceId ->
                        viewModel.selectService(serviceId)
                    },
                    onAddServiceToGroup = { groupName ->
                        editingService = null
                        editingGroupName = groupName ?: state.config.groups.firstOrNull()?.name ?: "Web Applications"
                        showEditDialog = true
                    },
                    onEditService = { service, groupName ->
                        editingService = service
                        editingGroupName = groupName
                        showEditDialog = true
                    },
                    onDeleteService = { service ->
                        deletingService = service
                        showDeleteDialog = true
                    },
                    onOpenUrl = { url ->
                        PlatformUtils.openUrlInBrowser(url)
                    },
                    onStartService = { service ->
                        viewModel.startService(service)
                    },
                    onStopService = { service ->
                        viewModel.stopService(service)
                    },
                    onRestartService = { service ->
                        viewModel.restartService(service)
                    },
                    onDeleteGroup = { group ->
                        deletingGroup = group
                        showDeleteCategoryDialog = true
                    },
                    modifier = Modifier.width(380.dp)
                )

                // Vertical Divider between sidebar and console
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Right Column: Log Console Pane (Remaining space)
                LogConsolePane(
                    selectedService = state.selectedService,
                    logSession = state.logSession,
                    onSearchQueryChange = { query ->
                        viewModel.setLogSearchQuery(query)
                    },
                    isAutoScrollEnabled = state.isAutoScrollEnabled,
                    onToggleAutoScroll = { enabled ->
                        viewModel.toggleAutoScroll(enabled)
                    },
                    onClearLogs = {
                        viewModel.clearLogs()
                    },
                    onCopyLogs = {
                        val textToCopy = state.filteredLogs.joinToString("\n")
                        PlatformUtils.copyToClipboard(textToCopy)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Edit / Add Service Dialog
            if (showEditDialog) {
                ServiceEditDialog(
                    initialService = editingService,
                    initialGroupName = editingGroupName,
                    existingServices = state.allServices,
                    onDismiss = { showEditDialog = false },
                    onSave = { serviceItem, groupName ->
                        val matchingGroup = state.config.groups.find { it.name.equals(groupName, ignoreCase = true) }
                        if (editingService != null) {
                            viewModel.updateService(serviceItem, groupId = matchingGroup?.id ?: groupName)
                        } else {
                            viewModel.addService(serviceItem, groupId = matchingGroup?.id ?: groupName)
                        }
                        showEditDialog = false
                    }
                )
            }

            // Delete Confirmation Dialog
            if (showDeleteDialog && deletingService != null) {
                DeleteConfirmDialog(
                    service = deletingService!!,
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        viewModel.deleteService(deletingService!!.id)
                        showDeleteDialog = false
                    }
                )
            }

            // Delete Category Confirmation Dialog
            if (showDeleteCategoryDialog && deletingGroup != null) {
                DeleteCategoryConfirmDialog(
                    group = deletingGroup!!,
                    onDismiss = { showDeleteCategoryDialog = false },
                    onConfirm = {
                        viewModel.deleteGroup(deletingGroup!!.id)
                        showDeleteCategoryDialog = false
                    }
                )
            }
        }
    }
}