package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.ui.theme.ConsoleHighlightBg
import website.woodendoor.dashboard.ui.theme.ConsoleHighlightText
import website.woodendoor.dashboard.ui.theme.MonospaceFontFamily
import website.woodendoor.dashboard.ui.theme.StatusHealthy
import website.woodendoor.dashboard.viewmodel.LogConsoleSession

@Composable
fun LogConsolePane(
    selectedService: ServiceItem?,
    logSession: LogConsoleSession,
    onSearchQueryChange: (String) -> Unit,
    isAutoScrollEnabled: Boolean,
    onToggleAutoScroll: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    LogConsolePane(
        selectedService = selectedService,
        logs = logSession.logs,
        filteredLogs = logSession.filteredLogs,
        searchQuery = logSession.searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        isAutoScrollEnabled = isAutoScrollEnabled,
        onToggleAutoScroll = onToggleAutoScroll,
        onClearLogs = onClearLogs,
        onCopyLogs = onCopyLogs,
        modifier = modifier
    )
}

@Composable
fun LogConsolePane(
    selectedService: ServiceItem?,
    logs: List<String>,
    filteredLogs: List<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isAutoScrollEnabled: Boolean,
    onToggleAutoScroll: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom whenever new filtered logs arrive
    LaunchedEffect(filteredLogs.size, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Console Control Bar
            ConsoleControlBar(
                selectedService = selectedService,
                totalLines = logs.size,
                filteredLines = filteredLogs.size,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isAutoScrollEnabled = isAutoScrollEnabled,
                onToggleAutoScroll = onToggleAutoScroll,
                onClearLogs = onClearLogs,
                onCopyLogs = onCopyLogs
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Terminal Console Body
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                if (selectedService == null) {
                    EmptyConsolePlaceholder(
                        title = "No Service Selected",
                        subtitle = "Select a service from the left sidebar to stream live logs."
                    )
                } else if (selectedService.logSource is LogSource.None) {
                    EmptyConsolePlaceholder(
                        title = "No Log Source Configured",
                        subtitle = "Edit '${selectedService.name}' and specify a Docker container or local file path to enable live log streaming."
                    )
                } else if (logs.isEmpty()) {
                    EmptyConsolePlaceholder(
                        title = "Waiting for logs...",
                        subtitle = "Connected to ${describeLogSource(selectedService.logSource)}. Output will appear here."
                    )
                } else if (filteredLogs.isEmpty() && searchQuery.isNotBlank()) {
                    EmptyConsolePlaceholder(
                        title = "No Matching Logs",
                        subtitle = "No lines match the search filter '$searchQuery'."
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredLogs) { index, logLine ->
                            LogLineItem(
                                lineNumber = index + 1,
                                lineContent = logLine,
                                searchQuery = searchQuery
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleControlBar(
    selectedService: ServiceItem?,
    totalLines: Int,
    filteredLines: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isAutoScrollEnabled: Boolean,
    onToggleAutoScroll: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Active Service & Source Info + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Selected Service Details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TERMINAL",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selectedService != null) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = selectedService.name,
                                fontFamily = MonospaceFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = describeLogSource(selectedService.logSource),
                            fontFamily = MonospaceFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right: Action buttons (Copy, Clear)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy Button
                    IconButton(
                        onClick = onCopyLogs,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy logs",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Clear Buffer Button
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear logs",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Row 2: Search Box & Auto-Scroll Toggle & Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Filter logs (regex/text)...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Line Counter Badge
                val countText = if (searchQuery.isBlank()) "$totalLines lines" else "$filteredLines / $totalLines lines"
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = countText,
                        fontFamily = MonospaceFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Auto Scroll Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Auto-Scroll",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAutoScrollEnabled) StatusHealthy else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = isAutoScrollEnabled,
                        onCheckedChange = onToggleAutoScroll,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StatusHealthy,
                            checkedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LogLineItem(
    lineNumber: Int,
    lineContent: String,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val horizontalScroll = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line number gutter
        Text(
            text = lineNumber.toString(),
            fontFamily = MonospaceFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(44.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Log content with highlight
        val annotatedText = remember(lineContent, searchQuery) {
            highlightLogLine(lineContent, searchQuery)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScroll)
        ) {
            Text(
                text = annotatedText,
                fontFamily = MonospaceFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun highlightLogLine(line: String, query: String): AnnotatedString {
    val ranges = LogConsoleSession.findHighlightRanges(line, query)
    if (ranges.isEmpty()) {
        return AnnotatedString(line)
    }

    return buildAnnotatedString {
        var lastIndex = 0
        for (range in ranges) {
            if (range.start > lastIndex) {
                append(line.substring(lastIndex, range.start))
            }
            val matchText = line.substring(range.start, range.end)
            val spanStyle = SpanStyle(
                background = ConsoleHighlightBg,
                color = ConsoleHighlightText,
                fontWeight = FontWeight.Bold
            )
            append(AnnotatedString(matchText, spanStyle))
            lastIndex = range.end
        }
        if (lastIndex < line.length) {
            append(line.substring(lastIndex))
        }
    }
}

fun describeLogSource(source: LogSource): String {
    return when (source) {
        is LogSource.Docker -> "docker: ${source.containerName}"
        is LogSource.DockerCompose -> "compose: ${source.serviceName} (${source.projectDir})"
        is LogSource.Command -> "cmd: ${source.startCommand} (${source.workingDir})"
        is LogSource.LocalFile -> "file: ${source.path}"
        LogSource.None -> "No Log Source"
    }
}

@Composable
fun EmptyConsolePlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
