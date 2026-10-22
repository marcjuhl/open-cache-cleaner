package com.opencachecleaner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opencachecleaner.app.data.model.CleaningMode
import com.opencachecleaner.app.ui.theme.DangerRed
import com.opencachecleaner.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    isAccessibilityGranted: Boolean,
    isUsageAccessGranted: Boolean,
    isRootAvailable: Boolean,
    isShizukuAvailable: Boolean,
    selectedMode: CleaningMode,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onGrantAccessibility: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onSelectMode: (CleaningMode) -> Unit,
    onProceed: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (canNavigateBack) "Settings & Permissions" else "Permission Setup") },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to apps"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Welcome to Open Cache Cleaner",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "To reclaim storage by purging temporary caches across all your installed applications without root, the following Android system permissions are required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1: Accessibility
            PermissionCard(
                stepNumber = "1",
                icon = Icons.Default.AccessibilityNew,
                title = "Accessibility Service (Core)",
                description = "Enables automated navigation to Settings and simulates clicking 'Clear Cache'. Open Cache Cleaner NEVER clicks 'Clear Data'.",
                isGranted = isAccessibilityGranted,
                actionButtonText = if (isAccessibilityGranted) "Configured" else "Enable in Settings",
                onActionClick = onGrantAccessibility
            )

            // Step 2: Usage Stats
            PermissionCard(
                stepNumber = "2",
                icon = Icons.Default.DataUsage,
                title = "Usage Access (Storage Stats)",
                description = "Allows reading the exact cache footprint (MB/GB) for each package via Android's StorageStatsManager.",
                isGranted = isUsageAccessGranted,
                actionButtonText = if (isUsageAccessGranted) "Configured" else "Grant Access",
                onActionClick = onGrantUsageAccess
            )

            // Step 3: Execution Engine Selector (Root / Shizuku / Non-root)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Engine Strategy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose your preferred clearing mechanism:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    EngineOptionRow(
                        mode = CleaningMode.ACCESSIBILITY,
                        selected = selectedMode == CleaningMode.ACCESSIBILITY,
                        availableText = "Standard Non-Root UI Automation",
                        onSelect = { onSelectMode(CleaningMode.ACCESSIBILITY) }
                    )

                    EngineOptionRow(
                        mode = CleaningMode.ROOT,
                        selected = selectedMode == CleaningMode.ROOT,
                        availableText = if (isRootAvailable) "Root shell available" else "Root not detected",
                        isEnabled = isRootAvailable,
                        onSelect = { onSelectMode(CleaningMode.ROOT) }
                    )

                    EngineOptionRow(
                        mode = CleaningMode.SHIZUKU,
                        selected = selectedMode == CleaningMode.SHIZUKU,
                        availableText = if (isShizukuAvailable) "Shizuku service active" else "Shizuku not detected",
                        isEnabled = isShizukuAvailable,
                        onSelect = { onSelectMode(CleaningMode.SHIZUKU) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = (selectedMode == CleaningMode.ACCESSIBILITY && isAccessibilityGranted) ||
                        (selectedMode == CleaningMode.ROOT && isRootAvailable) ||
                        (selectedMode == CleaningMode.SHIZUKU && isShizukuAvailable),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Launch Cache Cleaner",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionButtonText: String,
    onActionClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = SuccessGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onActionClick,
                enabled = !isGranted,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionButtonText)
            }
        }
    }
}

@Composable
private fun EngineOptionRow(
    mode: CleaningMode,
    selected: Boolean,
    availableText: String,
    isEnabled: Boolean = true,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = isEnabled
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = availableText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
