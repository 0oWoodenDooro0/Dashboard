package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import website.woodendoor.dashboard.ui.theme.StatusHealthy
import website.woodendoor.dashboard.ui.theme.StatusHealthyBg
import website.woodendoor.dashboard.ui.theme.StatusNeutral
import website.woodendoor.dashboard.ui.theme.StatusNeutralBg
import website.woodendoor.dashboard.ui.theme.StatusUnreachable
import website.woodendoor.dashboard.ui.util.SummaryMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    metrics: SummaryMetrics,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAddService: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "D",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "DevDashboard",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Port & Container Observability",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                // Total Services Indicator
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "${metrics.totalCount} Services",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = true)
                )

                // Healthy / Online Indicator
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "${metrics.healthyCount} Online",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusHealthy)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = StatusHealthyBg.copy(alpha = 0.4f),
                        labelColor = StatusHealthy
                    ),
                    border = null
                )

                // Offline Indicator (if any)
                if (metrics.offlineCount > 0) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "${metrics.offlineCount} Offline",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusUnreachable)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.error
                        ),
                        border = null
                    )
                }

                // Docker Status Indicator
                val dockerLabel = if (metrics.isDockerAvailable) "Docker Active" else "Docker Offline"
                val dockerColor = if (metrics.isDockerAvailable) StatusHealthy else StatusNeutral
                val dockerBg = if (metrics.isDockerAvailable) StatusHealthyBg.copy(alpha = 0.4f) else StatusNeutralBg
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = dockerLabel,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dockerColor)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = dockerBg,
                        labelColor = dockerColor
                    ),
                    border = null
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Refresh Button
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refreshing...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("Refresh", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Add Service Button
                Button(
                    onClick = onAddService,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Add Service",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
    )
}
