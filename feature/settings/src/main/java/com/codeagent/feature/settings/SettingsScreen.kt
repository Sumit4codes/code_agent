package com.codeagent.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeagent.core.model.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("AI Provider", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.providerType == ProviderType.OPENAI_COMPATIBLE,
                    onClick = { viewModel.updateProviderType(ProviderType.OPENAI_COMPATIBLE) },
                    label = { Text("OpenAI") }
                )
                FilterChip(
                    selected = state.providerType == ProviderType.ANTHROPIC,
                    onClick = { viewModel.updateProviderType(ProviderType.ANTHROPIC) },
                    label = { Text("Anthropic") }
                )
            }

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { viewModel.updateBaseUrl(it) },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.model,
                onValueChange = { viewModel.updateModel(it) },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.temperature,
                onValueChange = { viewModel.updateTemperature(it) },
                label = { Text("Temperature") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.maxTokens,
                onValueChange = { viewModel.updateMaxTokens(it) },
                label = { Text("Max Tokens") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = { viewModel.updateSystemPrompt(it) },
                label = { Text("System Prompt (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            HorizontalDivider()

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save Settings")
            }

            if (state.savedSuccessfully == true) {
                Text("Settings saved!", color = MaterialTheme.colorScheme.primary)
            } else if (state.savedSuccessfully == false) {
                Text("Failed to save settings", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
