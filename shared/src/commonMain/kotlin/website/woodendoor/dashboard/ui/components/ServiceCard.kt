package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import website.woodendoor.dashboard.ui.theme.DarkBorder
import website.woodendoor.dashboard.ui.theme.DarkBorderHover
import website.woodendoor.dashboard.ui.theme.DarkSurface
import website.woodendoor.dashboard.ui.theme.DarkSurfaceElevated
import website.woodendoor.dashboard.ui.theme.DarkSurfaceHighlight
import website.woodendoor.dashboard.ui.theme.MonospaceFontFamily
import website.woodendoor.dashboard.ui.theme.PrimaryBlue
import website.woodendoor.dashboard.ui.theme.PrimaryBlueBg
import website.woodendoor.dashboard.ui.theme.StatusClosed
import website.woodendoor.dashboard.ui.theme.StatusClosedBg
import website.woodendoor.dashboard.ui.theme.StatusHealthy
import website.woodendoor.dashboard.ui.theme.StatusHealthyBg
import website.woodendoor.dashboard.ui.theme.StatusNeutral
import website.woodendoor.dashboard.ui.theme.StatusNeutralBg
import website.woodendoor.dashboard.ui.theme.StatusUnreachable
import website.woodendoor.dashboard.ui.theme.StatusUnreachableBg
import website.woodendoor.dashboard.ui.theme.TextMuted
import website.woodendoor.dashboard.ui.theme.TextPrimary
import website.woodendoor.dashboard.ui.theme.TextSecondary
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
    modifier: Modifier = Modifier
) {
    val portHealthInfo = UiHelpers.getPortHealthDisplayInfo(status?.portHealth)
    val (statusDotColor, statusBgColor) = when (portHealthInfo.statusType) {
        PortStatusType.HEALTHY -> Pair(StatusHealthy, StatusHealthyBg)
        PortStatusType.CLOSED -> Pair(StatusClosed, StatusClosedBg)
        PortStatusType.UNREACHABLE -> Pair(StatusUnreachable, StatusUnreachableBg)
        PortStatusType.NO_PORT -> Pair(StatusNeutral, StatusNeutralBg)
    }

    val cardBorderColor = if (isSelected) PrimaryBlue else DarkBorder
    val cardBgColor = if (isSelected) DarkSurfaceHighlight else DarkSurfaceElevated

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        shape = RoundedCornerShape(10.dp)
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
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Port & Latency Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (service.port != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = DarkSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text(
                                text = ":${service.port}",
                                fontFamily = MonospaceFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Health / Latency badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusBgColor.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusDotColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = portHealthInfo.label,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusDotColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                    color = TextSecondary,
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
                        ContainerStatusType.RESTARTING -> Pair(ContainerRestarting, DarkSurface)
                        ContainerStatusType.EXITED, ContainerStatusType.DEAD -> Pair(ContainerExited, StatusUnreachableBg)
                        ContainerStatusType.NOT_FOUND, ContainerStatusType.UNKNOWN -> Pair(StatusNeutral, StatusNeutralBg)
                    }
                    SmallChip(
                        text = "🐳 ${containerInfo.label}",
                        color = stateColor,
                        bgColor = stateBg.copy(alpha = 0.5f)
                    )
                }

                // Log Source Chip
                when (val src = service.logSource) {
                    is LogSource.Docker -> {
                        SmallChip(
                            text = "src: ${src.containerName}",
                            color = TextSecondary,
                            bgColor = DarkSurface
                        )
                    }
                    is LogSource.DockerCompose -> {
                        SmallChip(
                            text = "compose: ${src.serviceName}",
                            color = TextSecondary,
                            bgColor = DarkSurface
                        )
                    }
                    is LogSource.LocalFile -> {
                        val fileName = src.path.substringAfterLast("/")
                        SmallChip(
                            text = "📄 $fileName",
                            color = TextSecondary,
                            bgColor = DarkSurface
                        )
                    }
                    LogSource.None -> {}
                }

                // Tags
                for (tag in service.tags) {
                    SmallChip(
                        text = "#$tag",
                        color = TextMuted,
                        bgColor = DarkSurface.copy(alpha = 0.7f)
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
                // Open in Browser
                val targetUrl = service.openUrl ?: service.healthUrl
                if (!targetUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onOpenUrl(targetUrl) },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🌐 Open",
                            fontSize = 11.sp,
                            color = PrimaryBlue
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Edit & Delete Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onEdit() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = DarkSurface
                    ) {
                        Text(
                            text = "✏️ Edit",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    // Delete
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = DarkSurface
                    ) {
                        Text(
                            text = "🗑️",
                            fontSize = 11.sp,
                            color = StatusUnreachable
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
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = MonospaceFontFamily,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
