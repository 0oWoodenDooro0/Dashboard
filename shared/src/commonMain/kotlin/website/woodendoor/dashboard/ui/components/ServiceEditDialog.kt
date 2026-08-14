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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.ui.theme.DarkBorder
import website.woodendoor.dashboard.ui.theme.DarkSurface
import website.woodendoor.dashboard.ui.theme.DarkSurfaceElevated
import website.woodendoor.dashboard.ui.theme.DarkSurfaceHighlight
import website.woodendoor.dashboard.ui.theme.PrimaryBlue
import website.woodendoor.dashboard.ui.theme.StatusUnreachable
import website.woodendoor.dashboard.ui.theme.TextMuted
import website.woodendoor.dashboard.ui.theme.TextPrimary
import website.woodendoor.dashboard.ui.theme.TextSecondary
import website.woodendoor.dashboard.ui.util.FormValidationResult
import website.woodendoor.dashboard.ui.util.LogSourceType
import website.woodendoor.dashboard.ui.util.ServiceFormState
import website.woodendoor.dashboard.ui.util.ServiceFormValidator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceEditDialog(
    initialService: ServiceItem?,
    initialGroupName: String,
    onDismiss: () -> Unit,
    onSave: (ServiceItem, String) -> Unit
) {
    var formState by remember {
        mutableStateOf(
            if (initialService != null) {
                ServiceFormState.fromServiceItem(initialService, initialGroupName)
            } else {
                ServiceFormState(groupName = initialGroupName)
            }
        )
    }

    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val isEditMode = initialService != null
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
            color = DarkSurface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (initialService != null) "Edit Service: ${initialService.name}" else "Add New Service",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Text(
                        text = "✕",
                        fontSize = 16.sp,
                        color = TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Service Name
                    FormField(
                        label = "Service Name *",
                        value = formState.name,
                        onValueChange = { formState = formState.copy(name = it) },
                        placeholder = "e.g. Backend API",
                        errorMessage = errors["name"]
                    )

                    // Group Name
                    FormField(
                        label = "Group Name",
                        value = formState.groupName,
                        onValueChange = { formState = formState.copy(groupName = it) },
                        placeholder = "e.g. Web Applications, Databases"
                    )

                    // Host & Port Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FormField(
                            label = "Host",
                            value = formState.host,
                            onValueChange = { formState = formState.copy(host = it) },
                            placeholder = "127.0.0.1",
                            modifier = Modifier.weight(1.5f)
                        )

                        FormField(
                            label = "Port (Optional)",
                            value = formState.port,
                            onValueChange = { formState = formState.copy(port = it) },
                            placeholder = "8000",
                            errorMessage = errors["port"],
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Open URL & Health URL
                    FormField(
                        label = "Open URL (Browser Shortcut)",
                        value = formState.openUrl,
                        onValueChange = { formState = formState.copy(openUrl = it) },
                        placeholder = "http://localhost:8000"
                    )

                    FormField(
                        label = "Health Check URL (Optional)",
                        value = formState.healthUrl,
                        onValueChange = { formState = formState.copy(healthUrl = it) },
                        placeholder = "http://localhost:8000/health"
                    )

                    // Description
                    FormField(
                        label = "Description",
                        value = formState.description,
                        onValueChange = { formState = formState.copy(description = it) },
                        placeholder = "Brief service description"
                    )

                    // Tags
                    FormField(
                        label = "Tags (comma-separated)",
                        value = formState.tags,
                        onValueChange = { formState = formState.copy(tags = it) },
                        placeholder = "backend, api, python"
                    )

                    // Log Source Selector
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Log Stream Source",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LogSourceType.entries.forEach { type ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { formState = formState.copy(logSourceType = type) }
                                        .padding(end = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = formState.logSourceType == type,
                                        onClick = { formState = formState.copy(logSourceType = type) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = PrimaryBlue,
                                            unselectedColor = TextMuted
                                        )
                                    )
                                    Text(
                                        text = type.displayName,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        when (formState.logSourceType) {
                            LogSourceType.DOCKER -> {
                                FormField(
                                    label = "Docker Container Name *",
                                    value = formState.dockerContainerName,
                                    onValueChange = { formState = formState.copy(dockerContainerName = it) },
                                    placeholder = "e.g. backend-api",
                                    errorMessage = errors["dockerContainerName"]
                                )
                            }
                            LogSourceType.DOCKER_COMPOSE -> {
                                FormField(
                                    label = "Project Folder / Directory *",
                                    value = formState.composeProjectDir,
                                    onValueChange = { formState = formState.copy(composeProjectDir = it) },
                                    placeholder = "e.g. /home/user/my-project or ./backend",
                                    errorMessage = errors["composeProjectDir"]
                                )

                                FormField(
                                    label = "Compose Service Name *",
                                    value = formState.composeServiceName,
                                    onValueChange = { formState = formState.copy(composeServiceName = it) },
                                    placeholder = "e.g. backend, web, api",
                                    errorMessage = errors["composeServiceName"]
                                )

                                FormField(
                                    label = "Custom Compose File (Optional)",
                                    value = formState.composeFileName,
                                    onValueChange = { formState = formState.copy(composeFileName = it) },
                                    placeholder = "e.g. docker-compose.prod.yml (optional)"
                                )
                            }
                            LogSourceType.LOCAL_FILE -> {
                                FormField(
                                    label = "Local Log File Path *",
                                    value = formState.localFilePath,
                                    onValueChange = { formState = formState.copy(localFilePath = it) },
                                    placeholder = "e.g. /var/log/app.log",
                                    errorMessage = errors["localFilePath"]
                                )
                            }
                            LogSourceType.NONE -> {}
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            when (val result = ServiceFormValidator.validate(formState)) {
                                is FormValidationResult.Success -> {
                                    errors = emptyMap()
                                    onSave(result.serviceItem, result.groupName)
                                }
                                is FormValidationResult.Error -> {
                                    errors = result.errors
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (isEditMode) "Save Changes" else "Create Service",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (errorMessage != null) StatusUnreachable else TextSecondary
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.sp, color = TextMuted) },
            singleLine = true,
            isError = errorMessage != null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = TextPrimary),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = DarkBorder,
                errorBorderColor = StatusUnreachable,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                fontSize = 10.sp,
                color = StatusUnreachable
            )
        }
    }
}
