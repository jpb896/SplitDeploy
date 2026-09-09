package com.jpb.splitdeploy.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpb.splitdeploy.utils.BundleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.jpb.splitdeploy.UIInstallerState
import com.jpb.splitdeploy.utils.PackageInstallerHelper
import kotlinx.coroutines.flow.asStateFlow

class InstallerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UIInstallerState>(UIInstallerState.Idle)
    val uiState: StateFlow<UIInstallerState> = _uiState.asStateFlow()

    private var selectedFile: File? = null

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Copy stream from Content URI to temporary file
                val fileName = getFileName(context, uri) ?: "bundle_file"
                val tempFile = File(context.cacheDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                selectedFile = tempFile
                val sizeInMb = String.format("%.2f MB", tempFile.length() / (1024.0 * 1024.0))

                _uiState.value = UIInstallerState.Selected(
                    fileName = fileName,
                    fileSize = sizeInMb
                )
            } catch (e: Exception) {
                _uiState.value = UIInstallerState.Error("Failed to open file: ${e.localizedMessage}")
            }
        }
    }

    fun startInstallation(context: Context) {
        val file = selectedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = UIInstallerState.Processing("Extracting bundle contents...", 0.3f)

                val parser = BundleParser(context)
                val parsedResult = parser.parseAndExtractBundle(file)

                _uiState.value = UIInstallerState.Processing("Creating installation session...", 0.7f)

                // Trigger PackageInstaller session
                val helper = PackageInstallerHelper(context)
                helper.installApks(parsedResult.extractedApkFiles)

                // Clean up temporary extracted files
                parser.clearCache(parsedResult)

            } catch (e: Exception) {
                _uiState.value = UIInstallerState.Error(e.localizedMessage ?: "Installation failed.")
            }
        }
    }

    fun resetState() {
        selectedFile?.delete()
        selectedFile = null
        _uiState.value = UIInstallerState.Idle
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}