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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceRuntimeStatus
import website.woodendoor.dashboard.model.badgeLabel
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceCard(
    service: ServiceItem,
    status: ServiceRuntimeStatus?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    isOperating: Boolean = false,
    onStart: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onRestart: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerState = status?.containerState
    val portHealth = status?.portHealth ?: PortHealth.None
    val (statusDotColor, statusBgColor) = when (portHealth) {
        is PortHealth.Open -> Pair(StatusHealthy, StatusHealthyBg)
        is PortHealth.Closed -> Pair(StatusClosed, StatusClosedBg)
        is PortHealth.Unreachable -> Pair(StatusUnreachable, StatusUnreachableBg)
        PortHealth.None -> Pair(StatusNeutral, StatusNeutralBg)
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
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = ":${service.port}",
                                fontFamily = MonospaceFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Health / Latency badge
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = statusBgColor.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, statusDotColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = portHealth.badgeLabel,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusDotColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
                    val (stateColor, stateBg) = when (containerState) {
                        is ContainerState.Running -> Pair(ContainerRunning, StatusHealthyBg)
                        is ContainerState.Paused -> Pair(ContainerPaused, StatusClosedBg)
                        is ContainerState.Restarting -> Pair(ContainerRestarting, MaterialTheme.colorScheme.surfaceContainerHigh)
                        is ContainerState.Exited, is ContainerState.Dead -> Pair(ContainerExited, StatusUnreachableBg)
                        is ContainerState.NotFound, is ContainerState.Unknown -> Pair(StatusNeutral, StatusNeutralBg)
                    }
                    SmallChip(
                        text = containerState.badgeLabel,
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val targetUrl = service.openUrl
                    if (!targetUrl.isNullOrBlank()) {
                        IconButton(
                            onClick = { onOpenUrl(targetUrl) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in browser",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    val isControlSupported = service.logSource is LogSource.Command ||
                        service.logSource is LogSource.Docker ||
                        service.logSource is LogSource.DockerCompose

                    if (isControlSupported) {
                        if (isOperating) {
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            val isRunning = containerState is ContainerState.Running
                            if (isRunning) {
                                if (onRestart != null) {
                                    IconButton(
                                        onClick = onRestart,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Restart service",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                if (onStop != null) {
                                    IconButton(
                                        onClick = onStop,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop service",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            } else {
                                if (onStart != null) {
                                    IconButton(
                                        onClick = onStart,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Start service",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
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
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit service",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete service",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
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
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bgColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontFamily = MonospaceFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
