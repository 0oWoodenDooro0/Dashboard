package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceGroup
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus
import website.woodendoor.dashboard.ui.theme.DarkBackground
import website.woodendoor.dashboard.ui.theme.DarkBorder
import website.woodendoor.dashboard.ui.theme.DarkSurface
import website.woodendoor.dashboard.ui.theme.DarkSurfaceElevated
import website.woodendoor.dashboard.ui.theme.PrimaryBlue
import website.woodendoor.dashboard.ui.theme.TextMuted
import website.woodendoor.dashboard.ui.theme.TextPrimary
import website.woodendoor.dashboard.ui.theme.TextSecondary

@Composable
fun ServiceListPane(
    groups: List<ServiceGroup>,
    serviceStatuses: Map<String, ServiceStatus>,
    containerStates: Map<String, ContainerState>,
    selectedServiceId: String?,
    onSelectService: (String) -> Unit,
    onAddServiceToGroup: (String?) -> Unit,
    onEditService: (ServiceItem, String) -> Unit,
    onDeleteService: (ServiceItem) -> Unit,
    onOpenUrl: (String) -> Unit,
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
        modifier = modifier
            .fillMaxHeight()
            .border(width = 1.dp, color = DarkBorder),
        color = DarkBackground
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
                    color = TextSecondary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Text(
                        text = "$totalServices total",
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Search Field
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                placeholder = { Text("Filter services...", fontSize = 12.sp, color = TextMuted) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
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
                            color = TextMuted
                        )
                        if (searchFilter.isBlank()) {
                            Button(
                                onClick = { onAddServiceToGroup(null) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("+ Add First Service", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    filteredGroups.forEach { group ->
                        item(key = "header_${group.id}") {
                            GroupHeader(
                                groupName = group.name,
                                count = group.services.size,
                                onAddClick = { onAddServiceToGroup(group.name) }
                            )
                        }

                        items(group.services, key = { it.id }) { service ->
                            val containerState = when (val src = service.logSource) {
                                is LogSource.Docker -> containerStates[src.containerName]
                                else -> null
                            }

                            ServiceCard(
                                service = service,
                                status = serviceStatuses[service.id],
                                containerState = containerState,
                                isSelected = service.id == selectedServiceId,
                                onSelect = { onSelectService(service.id) },
                                onEdit = { onEditService(service, group.name) },
                                onDelete = { onDeleteService(service) },
                                onOpenUrl = onOpenUrl,
                                modifier = Modifier.padding(bottom = 6.dp)
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                color = TextSecondary
            )

            Surface(
                shape = CircleShape,
                color = DarkSurfaceElevated
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkBorder),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onAddClick() }
        ) {
            Text(
                text = "+ Add",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryBlue,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
