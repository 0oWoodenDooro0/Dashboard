package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus
import website.woodendoor.dashboard.ui.theme.ContainerExited
import website.woodendoor.dashboard.ui.theme.ContainerPaused
import website.woodendoor.dashboard.ui.theme.ContainerRestarting
import website.woodendoor.dashboard.ui.theme.ContainerRunning
import website.woodendoor.dashboard.ui.theme.MonospaceFontFamily
import website.woodendoor.dashboard.ui.theme.StatusClosed
import website.woodendoor.dashboard.ui.theme.StatusClosedBg
import website.woodendoor.dashboard.ui.theme.StatusHealthy
import website.woodendoor.dashboard.ui.theme.StatusHealthyBg
import website.woodendoor.dashboard.ui.theme.StatusNeutral
import website.woodendoor.dashboard.ui.theme.StatusNeutralBg
import website.woodendoor.dashboard.ui.theme.StatusUnreachable
import website.woodendoor.dashboard.ui.theme.StatusUnreachableBg
import website.woodendoor.dashboard.ui.util.ContainerStatusType
import website.woodendoor.dashboard.ui.util.PortStatusType
import website.woodendoor.dashboard.ui.util.UiHelpers

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceCard(
    service: ServiceItem,
    status: ServiceStatus?,
    containerState: ContainerState?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onStart: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onRestart: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val portHealthInfo = UiHelpers.getPortHealthDisplayInfo(status?.portHealth)
    val (statusDotColor, statusBgColor) = when (portHealthInfo.statusType) {
        PortStatusType.HEALTHY -> Pair(StatusHealthy, StatusHealthyBg)
        PortStatusType.CLOSED -> Pair(StatusClosed, StatusClosedBg)
        PortStatusType.UNREACHABLE -> Pair(StatusUnreachable, StatusUnreachableBg)
        PortStatusType.NO_PORT -> Pair(StatusNeutral, StatusNeutralBg)
    }

    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    val cardBgColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.medium,
        border = cardBorder,
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardBgColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Status Indicator, Name, Port Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Status Light Indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Port & Latency Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (service.port != null) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = ":${service.port}",
                                    fontFamily = MonospaceFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = MaterialTheme.shapes.extraSmall
                        )
                    }

                    // Health / Latency badge
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = portHealthInfo.label,
                                fontFamily = MonospaceFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = statusBgColor.copy(alpha = 0.5f),
                            labelColor = statusDotColor
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = statusDotColor.copy(alpha = 0.4f)
                        ),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                }
            }

            // Description (if present)
            if (!service.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = service.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Chips (Docker state, tags, log source)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Docker container state chip
                if (containerState != null) {
                    val containerInfo = UiHelpers.getContainerStateDisplayInfo(containerState)
                    val (stateColor, stateBg) = when (containerInfo.statusType) {
                        ContainerStatusType.RUNNING -> Pair(ContainerRunning, StatusHealthyBg)
                        ContainerStatusType.PAUSED -> Pair(ContainerPaused, StatusClosedBg)
                        ContainerStatusType.RESTARTING -> Pair(ContainerRestarting, MaterialTheme.colorScheme.surfaceContainerHigh)
                        ContainerStatusType.EXITED, ContainerStatusType.DEAD -> Pair(ContainerExited, StatusUnreachableBg)
                        ContainerStatusType.NOT_FOUND, ContainerStatusType.UNKNOWN -> Pair(StatusNeutral, StatusNeutralBg)
                    }
                    SmallChip(
                        text = containerInfo.label,
                        color = stateColor,
                        bgColor = stateBg.copy(alpha = 0.5f)
                    )
                }

                // Log Source Chip
                when (val src = service.logSource) {
                    is LogSource.Docker -> {
                        SmallChip(
                            text = "docker: ${src.containerName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                    is LogSource.DockerCompose -> {
                        SmallChip(
                            text = "compose: ${src.serviceName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                    is LogSource.Command -> {
                        SmallChip(
                            text = "cmd: ${src.startCommand}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                    is LogSource.LocalFile -> {
                        val fileName = src.path.substringAfterLast("/")
                        SmallChip(
                            text = "file: $fileName",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                    LogSource.None -> {}
                }

                // Tags
                for (tag in service.tags) {
                    SmallChip(
                        text = tag,
                        color = MaterialTheme.colorScheme.outline,
                        bgColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Open in Browser & Command Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val targetUrl = service.openUrl ?: service.healthUrl
                    if (!targetUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onOpenUrl(targetUrl) },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    val isControlSupported = service.logSource is LogSource.Command ||
                        service.logSource is LogSource.Docker ||
                        service.logSource is LogSource.DockerCompose

                    if (isControlSupported) {
                        val isRunning = containerState is ContainerState.Running
                        if (isRunning) {
                            if (onRestart != null) {
                                OutlinedButton(
                                    onClick = onRestart,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Restart",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }
                            if (onStop != null) {
                                OutlinedButton(
                                    onClick = onStop,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Stop",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    )
                                }
                            }
                        } else {
                            if (onStart != null) {
                                OutlinedButton(
                                    onClick = onStart,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Start",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Edit & Delete Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit Button
                    TextButton(
                        onClick = onEdit,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    // Delete Button
                    TextButton(
                        onClick = onDelete,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmallChip(
    text: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = text,
                fontFamily = MonospaceFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = bgColor,
            labelColor = color
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled = true,
            borderColor = color.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    )
}
