package com.jpb.splitdeploy

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpb.splitdeploy.ui.theme.SplitDeployTheme
import com.jpb.splitdeploy.viewmodels.InstallerViewModel
class MainActivity : ComponentActivity() {

    private val viewModel: InstallerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SplitDeployTheme {
                val uiState by viewModel.uiState.collectAsState()

                // Launcher for selecting .apks, .xapk, or .apkm files
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let { viewModel.onFileSelected(applicationContext, it) }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SplitDeployAppContent(
                        state = uiState,
                        onPickFile = { filePickerLauncher.launch("*/*") },
                        onInstall = { viewModel.startInstallation(applicationContext) },
                        onReset = { viewModel.resetState() }
                    )
                }
            }
        }
    }
}

@Composable
fun SplitDeployAppContent(
    state: UIInstallerState,
    onPickFile: () -> Unit,
    onInstall: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            is UIInstallerState.Idle -> {
                Text(
                    text = "SplitDeploy",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select an .apks, .xapk, or .apkm bundle to install")
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onPickFile) {
                    Text("Select Bundle File")
                }
            }

            is UIInstallerState.Selected -> {
                Text(
                    text = "File Selected",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = state.fileName, fontWeight = FontWeight.Medium)
                Text(text = state.fileSize, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onReset) {
                        Text("Cancel")
                    }
                    Button(onClick = onInstall) {
                        Text("Start Installation")
                    }
                }
            }

            is UIInstallerState.Processing -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = state.phase)
            }

            is UIInstallerState.Success -> {
                Text(
                    text = "Installation Successful!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) {
                    Text("Done")
                }
            }

            is UIInstallerState.Error -> {
                Text(
                    text = "Error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = state.message)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) {
                    Text("Try Again")
                }
            }
        }
    }
}

sealed class UIInstallerState {
    object Idle : UIInstallerState()
    data class Selected(val fileName: String, val fileSize: String) : UIInstallerState()
    data class Processing(val phase: String, val progress: Float) : UIInstallerState()
    data class Success(val packageName: String) : UIInstallerState()
    data class Error(val message: String) : UIInstallerState()
}