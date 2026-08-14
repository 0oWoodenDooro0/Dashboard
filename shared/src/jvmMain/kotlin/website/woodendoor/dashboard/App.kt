package website.woodendoor.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.service.CliDockerClient
import website.woodendoor.dashboard.service.DefaultLogStreamService
import website.woodendoor.dashboard.service.FileConfigRepository
import website.woodendoor.dashboard.service.SocketPortHealthChecker
import website.woodendoor.dashboard.ui.components.DashboardTopBar
import website.woodendoor.dashboard.ui.components.DeleteConfirmDialog
import website.woodendoor.dashboard.ui.components.LogConsolePane
import website.woodendoor.dashboard.ui.components.ServiceEditDialog
import website.woodendoor.dashboard.ui.components.ServiceListPane
import website.woodendoor.dashboard.ui.theme.DarkBackground
import website.woodendoor.dashboard.ui.theme.DashboardTheme
import website.woodendoor.dashboard.ui.theme.StatusUnreachable
import website.woodendoor.dashboard.ui.theme.StatusUnreachableBg
import website.woodendoor.dashboard.ui.theme.TextPrimary
import website.woodendoor.dashboard.ui.util.PlatformUtils
import website.woodendoor.dashboard.ui.util.UiHelpers
import website.woodendoor.dashboard.viewmodel.DashboardViewModel

@Composable
fun App(
    viewModel: DashboardViewModel = remember {
        val dockerClient = CliDockerClient()
        DashboardViewModel(
            configRepository = FileConfigRepository(),
            healthChecker = SocketPortHealthChecker(),
            logStreamService = DefaultLogStreamService(dockerClient = dockerClient),
            dockerClient = dockerClient
        )
    }
) {
    val state by viewModel.state.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<ServiceItem?>(null) }
    var editingGroupName by remember { mutableStateOf("Default") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingService by remember { mutableStateOf<ServiceItem?>(null) }

    val summaryMetrics = remember(state.allServices, state.serviceStatuses, state.isDockerAvailable) {
        UiHelpers.calculateSummaryMetrics(
            services = state.allServices,
            statuses = state.serviceStatuses,
            isDockerAvailable = state.isDockerAvailable
        )
    }

    DashboardTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Application Bar
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

                // Error Message Banner (if any)
                if (state.errorMessage != null) {
                    ErrorBanner(
                        message = state.errorMessage ?: "Unknown error",
                        onDismiss = { viewModel.clearError() }
                    )
                }

                // Two-Column Split Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Left Column: Service List Pane (Fixed width: 380dp)
                    ServiceListPane(
                        groups = state.config.groups,
                        serviceStatuses = state.serviceStatuses,
                        containerStates = state.containerStates,
                        selectedServiceId = state.selectedServiceId,
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
                        modifier = Modifier.width(380.dp)
                    )

                    // Right Column: Log Console Pane (Remaining space)
                    LogConsolePane(
                        selectedService = state.selectedService,
                        logs = state.logs,
                        filteredLogs = state.filteredLogs,
                        searchQuery = state.logSearchQuery,
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
            }

            // Edit / Add Service Dialog
            if (showEditDialog) {
                ServiceEditDialog(
                    initialService = editingService,
                    initialGroupName = editingGroupName,
                    onDismiss = { showEditDialog = false },
                    onSave = { serviceItem, groupName ->
                        if (editingService != null) {
                            viewModel.updateService(serviceItem)
                        } else {
                            val matchingGroup = state.config.groups.find { it.name.equals(groupName, ignoreCase = true) }
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
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(StatusUnreachableBg)
            .border(1.dp, StatusUnreachable.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "⚠️", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = TextPrimary
            )
        }

        Text(
            text = "✕ Dismiss",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = StatusUnreachable,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}