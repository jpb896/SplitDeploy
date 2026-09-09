package com.jpb.splitdeploy.utils

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

sealed class BundleType {
    object Apks : BundleType()
    object Xapk : BundleType()
    object Apkm : BundleType()
    object Unknown : BundleType()
}

data class ParsedBundleResult(
    val bundleType: BundleType,
    val extractedApkFiles: List<File>,
    val obbFiles: List<File> = emptyList(),
    val packageName: String? = null
)

class BundleParser(private val context: Context) {

    /**
     * Extracts an app archive file (.apks, .xapk, .apkm) to a temporary cache directory
     * and returns the list of extracted split APK files ready for PackageInstaller.
     */
    fun parseAndExtractBundle(bundleFile: File): ParsedBundleResult {
        val bundleType = detectBundleType(bundleFile.name)
        val outputDir = File(context.cacheDir, "extracted_bundle_${System.currentTimeMillis()}")
        if (!outputDir.exists()) outputDir.mkdirs()

        val extractedApks = mutableListOf<File>()
        val obbFiles = mutableListOf<File>()
        var packageName: String? = null

        ZipInputStream(bundleFile.inputStream().buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val fileName = File(entryName).name

                if (!entry.isDirectory) {
                    when {
                        // Filter specifically for .apk files while ignoring hidden/system files
                        fileName.endsWith(".apk", ignoreCase = true) && !fileName.startsWith(".") -> {
                            val apkFile = File(outputDir, fileName)
                            saveStreamToFile(zip, apkFile)

                            // Ensure extracted file is a valid APK binary before adding
                            if (isValidApkFile(apkFile)) {
                                extractedApks.add(apkFile)
                            } else {
                                apkFile.delete() // Clean up invalid or corrupted extract
                            }
                        }

                        fileName.endsWith(".obb", ignoreCase = true) -> {
                            val obbFile = File(outputDir, fileName)
                            saveStreamToFile(zip, obbFile)
                            obbFiles.add(obbFile)
                        }

                        fileName.equals("manifest.json", ignoreCase = true) ||
                                fileName.equals("info.json", ignoreCase = true) -> {
                            val jsonString = zip.bufferedReader().readText()
                            packageName = extractPackageNameFromJson(jsonString)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (extractedApks.isEmpty()) {
            outputDir.deleteRecursively()
            throw IllegalArgumentException("No valid APK files found inside the bundle.")
        }

        return ParsedBundleResult(
            bundleType = bundleType,
            extractedApkFiles = extractedApks,
            obbFiles = obbFiles,
            packageName = packageName
        )
    }

    /**
     * Validates that the extracted file has a valid Zip/APK magic header (PK\x03\x04).
     */
    private fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(4)
                val read = input.read(buffer, 0, 4)
                read == 4 && buffer[0] == 'P'.toByte() && buffer[1] == 'K'.toByte() &&
                        buffer[2] == 0x03.toByte() && buffer[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun detectBundleType(fileName: String): BundleType {
        return when {
            fileName.endsWith(".apks", ignoreCase = true) -> BundleType.Apks
            fileName.endsWith(".xapk", ignoreCase = true) -> BundleType.Xapk
            fileName.endsWith(".apkm", ignoreCase = true) -> BundleType.Apkm
            else -> BundleType.Unknown
        }
    }

    private fun saveStreamToFile(inputStream: InputStream, targetFile: File) {
        FileOutputStream(targetFile).use { output ->
            inputStream.copyTo(output)
        }
    }

    private fun extractPackageNameFromJson(jsonContent: String): String? {
        return try {
            val json = JSONObject(jsonContent)
            when {
                json.has("package_name") -> json.getString("package_name") // Common in XAPK
                json.has("pname") -> json.getString("pname")              // Alternative key
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cleans up temporary extracted APKs after the installation session commits.
     */
    fun clearCache(result: ParsedBundleResult) {
        result.extractedApkFiles.firstOrNull()?.parentFile?.deleteRecursively()
    }

    fun copyObbFiles(context: Context, packageName: String, obbFiles: List<File>) {
        if (obbFiles.isEmpty()) return

        // Path: /sdcard/Android/obb/<package_name>/
        val obbDir = File(context.getExternalFilesDir(null)?.parentFile?.parentFile, "obb/$packageName")
        if (!obbDir.exists()) obbDir.mkdirs()

        obbFiles.forEach { obb ->
            val destination = File(obbDir, obb.name)
            obb.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}