package website.woodendoor.dashboard.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.ui.util.FormValidationResult
import website.woodendoor.dashboard.ui.util.LogSourceType
import website.woodendoor.dashboard.ui.util.ServiceFormState
import website.woodendoor.dashboard.ui.util.ServiceFormValidator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceEditDialog(
    initialService: ServiceItem?,
    initialGroupName: String,
    existingServices: List<ServiceItem> = emptyList(),
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) "Edit Service: ${initialService.name}" else "Add New Service",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .width(520.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LogSourceType.entries.forEach { type ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable { formState = formState.copy(logSourceType = type) }
                                    .padding(end = 6.dp)
                            ) {
                                RadioButton(
                                    selected = formState.logSourceType == type,
                                    onClick = { formState = formState.copy(logSourceType = type) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
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
                        LogSourceType.COMMAND -> {
                            FormField(
                                label = "Working Directory *",
                                value = formState.commandWorkingDir,
                                onValueChange = { formState = formState.copy(commandWorkingDir = it) },
                                placeholder = "e.g. /home/user/project or ./frontend",
                                errorMessage = errors["commandWorkingDir"]
                            )

                            FormField(
                                label = "Start Command *",
                                value = formState.commandStartScript,
                                onValueChange = { formState = formState.copy(commandStartScript = it) },
                                placeholder = "e.g. npm run dev, python app.py, ./gradlew bootRun",
                                errorMessage = errors["commandStartScript"]
                            )

                            FormField(
                                label = "Stop Command (Optional)",
                                value = formState.commandStopScript,
                                onValueChange = { formState = formState.copy(commandStopScript = it) },
                                placeholder = "e.g. npm run stop (optional, defaults to process tree kill)"
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
        },
        confirmButton = {
            Button(
                onClick = {
                    when (val result = ServiceFormValidator.validate(formState, existingServices = existingServices, currentServiceId = initialService?.id)) {
                        is FormValidationResult.Success -> {
                            errors = emptyMap()
                            onSave(result.serviceItem, result.groupName)
                        }
                        is FormValidationResult.Error -> {
                            errors = result.errors
                        }
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isEditMode) "Save Changes" else "Create Service",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    )
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = errorMessage != null,
        supportingText = if (errorMessage != null) {
            {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else null,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
