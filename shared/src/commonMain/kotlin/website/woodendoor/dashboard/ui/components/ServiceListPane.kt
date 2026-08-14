package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceGroup
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus
import website.woodendoor.dashboard.model.stateKey

@Composable
fun ServiceListPane(
    groups: List<ServiceGroup>,
    serviceStatuses: Map<String, ServiceStatus>,
    containerStates: Map<String, ContainerState>,
    selectedServiceId: String?,
    operatingServiceIds: Set<String> = emptySet(),
    onSelectService: (String) -> Unit,
    onAddServiceToGroup: (String?) -> Unit,
    onEditService: (ServiceItem, String) -> Unit,
    onDeleteService: (ServiceItem) -> Unit,
    onOpenUrl: (String) -> Unit,
    onStartService: (ServiceItem) -> Unit = {},
    onStopService: (ServiceItem) -> Unit = {},
    onRestartService: (ServiceItem) -> Unit = {},
    onDeleteGroup: ((ServiceGroup) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchFilter by remember { mutableStateOf("") }

    val filteredGroups = remember(groups, searchFilter) {
        if (searchFilter.isBlank()) {
            groups
        } else {
            groups.mapNotNull { group ->
                val matchingServices = group.services.filter { service ->
                    service.name.contains(searchFilter, ignoreCase = true) ||
                        service.host.contains(searchFilter, ignoreCase = true) ||
                        (service.port?.toString()?.contains(searchFilter) == true) ||
                        service.tags.any { it.contains(searchFilter, ignoreCase = true) } ||
                        (service.description?.contains(searchFilter, ignoreCase = true) == true)
                }
                if (matchingServices.isNotEmpty()) {
                    group.copy(services = matchingServices)
                } else {
                    null
                }
            }
        }
    }

    val totalServices = groups.sumOf { it.services.size }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Sidebar Header & Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SERVICES",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "$totalServices total",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Search Field
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                placeholder = {
                    Text(
                        text = "Filter services...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Service Groups & Cards
            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (searchFilter.isBlank()) "No services configured" else "No matching services",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchFilter.isBlank()) {
                            Button(
                                onClick = { onAddServiceToGroup(null) },
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Add Service",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    filteredGroups.forEach { group ->
                        item(key = "header_${group.id}") {
                            GroupHeader(
                                groupName = group.name,
                                count = group.services.size,
                                onAddClick = { onAddServiceToGroup(group.name) },
                                onDeleteClick = if (onDeleteGroup != null) { { onDeleteGroup(group) } } else null
                            )
                        }

                        items(group.services, key = { it.id }) { service ->
                            val containerState = when (val src = service.logSource) {
                                is LogSource.Docker -> containerStates[src.containerName]
                                is LogSource.DockerCompose -> containerStates[src.stateKey()]
                                is LogSource.Command -> containerStates[src.stateKey(service.id)]
                                else -> null
                            }

                            ServiceCard(
                                service = service,
                                status = serviceStatuses[service.id],
                                containerState = containerState,
                                isSelected = service.id == selectedServiceId,
                                isOperating = service.id in operatingServiceIds,
                                onSelect = { onSelectService(service.id) },
                                onEdit = { onEditService(service, group.name) },
                                onDelete = { onDeleteService(service) },
                                onOpenUrl = onOpenUrl,
                                onStart = { onStartService(service) },
                                onStop = { onStopService(service) },
                                onRestart = { onRestartService(service) },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupHeader(
    groupName: String,
    count: Int,
    onAddClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = groupName.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null,
                    shape = MaterialTheme.shapes.extraSmall
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onAddClick,
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (onDeleteClick != null) {
                    TextButton(
                        onClick = onDeleteClick,
                        shape = MaterialTheme.shapes.extraSmall,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
