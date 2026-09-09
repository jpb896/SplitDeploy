package com.jpb.splitdeploy.utils

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

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
     * Parses and extracts bundle archives (.apks, .xapk, .apkm) safely to a cache directory.
     * Uses ZipFile random access to handle XAPK architectures without stream corruption.
     */
    fun parseAndExtractBundle(bundleFile: File): ParsedBundleResult {
        val bundleType = detectBundleType(bundleFile.name)
        val outputDir = File(context.cacheDir, "extracted_bundle_${System.currentTimeMillis()}")
        if (!outputDir.exists()) outputDir.mkdirs()

        val extractedApks = mutableListOf<File>()
        val obbFiles = mutableListOf<File>()
        var packageName: String? = null

        ZipFile(bundleFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val fileName = File(entry.name).name

                if (!entry.isDirectory) {
                    when {
                        // Match valid APK files and filter out hidden or macOS-specific system entries
                        fileName.endsWith(".apk", ignoreCase = true) && !fileName.startsWith(".") -> {
                            val targetApk = File(outputDir, fileName)

                            zip.getInputStream(entry).use { input ->
                                saveStreamToFile(input, targetApk)
                            }

                            // Validate APK magic bytes before registering
                            if (isValidApkFile(targetApk)) {
                                extractedApks.add(targetApk)
                            } else {
                                targetApk.delete()
                            }
                        }

                        // Match OBB expansion files
                        fileName.endsWith(".obb", ignoreCase = true) -> {
                            val targetObb = File(outputDir, fileName)
                            zip.getInputStream(entry).use { input ->
                                saveStreamToFile(input, targetObb)
                            }
                            obbFiles.add(targetObb)
                        }

                        // Parse package name metadata if manifest exists
                        fileName.equals("manifest.json", ignoreCase = true) ||
                                fileName.equals("info.json", ignoreCase = true) -> {
                            zip.getInputStream(entry).bufferedReader().use { reader ->
                                packageName = extractPackageNameFromJson(reader.readText())
                            }
                        }
                    }
                }
            }
        }

        if (extractedApks.isEmpty()) {
            outputDir.deleteRecursively()
            throw IllegalArgumentException("No valid APK files could be extracted from this file.")
        }

        return ParsedBundleResult(
            bundleType = bundleType,
            extractedApkFiles = extractedApks,
            obbFiles = obbFiles,
            packageName = packageName
        )
    }

    /**
     * Copies extracted OBB files to (/storage/emulated/0/)Android/obb/<package_name>/
     */
    fun copyObbFiles(extractedApks: List<File>, obbFiles: List<File>, parsedPackageName: String?) {
        if (obbFiles.isEmpty()) return

        val packageName = parsedPackageName ?: getPackageNameFromApks(extractedApks) ?: return
        val externalStorage = context.getExternalFilesDir(null)?.parentFile?.parentFile
        val obbDir = File(externalStorage, "obb/$packageName")

        if (!obbDir.exists()) {
            obbDir.mkdirs()
        }

        obbFiles.forEach { obb ->
            val destination = File(obbDir, obb.name)
            obb.inputStream().use { input ->
                saveStreamToFile(input, destination)
            }
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
            output.flush()
            output.fd.sync() // Forces file buffers to sync to physical storage
        }
    }

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

    private fun extractPackageNameFromJson(jsonContent: String): String? {
        return try {
            val json = JSONObject(jsonContent)
            when {
                json.has("package_name") -> json.getString("package_name")
                json.has("pname") -> json.getString("pname")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getPackageNameFromApks(apkFiles: List<File>): String? {
        val baseApk = apkFiles.firstOrNull { it.name.contains("base", ignoreCase = true) } ?: apkFiles.firstOrNull() ?: return null
        val archiveInfo = context.packageManager.getPackageArchiveInfo(
            baseApk.absolutePath,
            PackageManager.GET_META_DATA
        )
        return archiveInfo?.packageName
    }

    fun clearCache(result: ParsedBundleResult) {
        result.extractedApkFiles.firstOrNull()?.parentFile?.deleteRecursively()
    }
}