package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import website.woodendoor.dashboard.ui.theme.DarkBorder
import website.woodendoor.dashboard.ui.theme.DarkSurface
import website.woodendoor.dashboard.ui.theme.DarkSurfaceElevated
import website.woodendoor.dashboard.ui.theme.PrimaryBlue
import website.woodendoor.dashboard.ui.theme.StatusHealthy
import website.woodendoor.dashboard.ui.theme.StatusHealthyBg
import website.woodendoor.dashboard.ui.theme.StatusNeutral
import website.woodendoor.dashboard.ui.theme.StatusNeutralBg
import website.woodendoor.dashboard.ui.theme.StatusUnreachable
import website.woodendoor.dashboard.ui.theme.StatusUnreachableBg
import website.woodendoor.dashboard.ui.theme.TextMuted
import website.woodendoor.dashboard.ui.theme.TextPrimary
import website.woodendoor.dashboard.ui.theme.TextSecondary
import website.woodendoor.dashboard.ui.util.SummaryMetrics

@Composable
fun DashboardTopBar(
    metrics: SummaryMetrics,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAddService: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = DarkBorder),
        color = DarkSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Title & Logo
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryBlue.copy(alpha = 0.2f))
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 18.sp
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
                        color = TextPrimary
                    )
                    Text(
                        text = "Port & Container Observability",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            // Middle: Metric Summary Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Services
                StatusPill(
                    label = "${metrics.totalCount} Services",
                    indicatorColor = TextSecondary,
                    bgColor = DarkSurfaceElevated,
                    textColor = TextPrimary
                )

                // Healthy / Online
                StatusPill(
                    label = "${metrics.healthyCount} Online",
                    indicatorColor = StatusHealthy,
                    bgColor = StatusHealthyBg.copy(alpha = 0.5f),
                    textColor = StatusHealthy
                )

                // Offline / Degraded
                if (metrics.offlineCount > 0) {
                    StatusPill(
                        label = "${metrics.offlineCount} Offline",
                        indicatorColor = StatusUnreachable,
                        bgColor = StatusUnreachableBg.copy(alpha = 0.5f),
                        textColor = StatusUnreachable
                    )
                }

                // Docker Status
                val dockerLabel = if (metrics.isDockerAvailable) "Docker Active" else "Docker Offline"
                val dockerColor = if (metrics.isDockerAvailable) StatusHealthy else StatusNeutral
                val dockerBg = if (metrics.isDockerAvailable) StatusHealthyBg.copy(alpha = 0.4f) else StatusNeutralBg
                StatusPill(
                    label = dockerLabel,
                    indicatorColor = dockerColor,
                    bgColor = dockerBg,
                    textColor = dockerColor,
                    prefix = "🐳 "
                )
            }

            // Right: Global Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Refresh Button
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refreshing...", fontSize = 12.sp)
                    } else {
                        Text("🔄 Refresh", fontSize = 12.sp)
                    }
                }

                // Add Service Button
                Button(
                    onClick = onAddService,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("+ Add Service", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    indicatorColor: Color,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    prefix: String? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, indicatorColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (prefix != null) {
            Text(text = prefix, fontSize = 11.sp)
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
