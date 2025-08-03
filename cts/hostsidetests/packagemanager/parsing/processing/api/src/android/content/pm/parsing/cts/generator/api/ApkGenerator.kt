/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.pm.parsing.cts.generator.api

import com.android.tradefed.device.ITestDevice
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlin.math.absoluteValue

/**
 * Handles installing APKs previously generated via [AndroidManifestXml] declarations. Supports
 * installing APKs from the annotated strings and cleaning up all packages afterwards.
 */
object ApkGenerator {

    private const val PACKAGE_NAME_PREFIX = "android.content.pm.parsing.cts.generated"
    private const val GENERATED_APKS_FILE = "GeneratedApks.zip"
    private const val GENERATED_PACKAGE_NAMES_FILE = "GeneratedApkPackageNames.txt"

    private lateinit var hostZipFile: File

    /**
     * Call in a @[BeforeClass] method to preload the zip file to the host. Note that this will
     * require that the [TemporaryFolder] be initialized as a @[ClassRule].
     */
    fun initialize(tempFolder: TemporaryFolder) {
        copyHostZipFileIfNeeded(tempFolder)
    }

    fun uninstallAll(device: ITestDevice, tempFolder: TemporaryFolder) {
        getFileFromZip(tempFolder, GENERATED_PACKAGE_NAMES_FILE).readLines().forEach {
            try {
                device.uninstallPackage(it)
            } catch (ignored: Exception) {
            }
        }
    }

    fun install(
        device: ITestDevice,
        @AndroidManifestXml
        @org.intellij.lang.annotations.Language("XML")
        androidManifestXml: String,
        tempFolder: TemporaryFolder
    ): InstallResult {
        val hashCode = androidManifestXml.hashCode().absoluteValue
        val apkFile = getFileFromZip(tempFolder, "$hashCode.apk")
        val error = device.installPackage(apkFile, false, "--bypass-low-target-sdk-block")
        return InstallResult(
            packageName = "$PACKAGE_NAME_PREFIX$hashCode",
            error = error.orEmpty(),
        )
    }

    private fun getFileFromZip(tempFolder: TemporaryFolder, fileName: String): File {
        val zipFile = ZipFile(copyHostZipFileIfNeeded(tempFolder))
        val entry = zipFile.getEntry(fileName)
        return tempFolder.newFile().apply {
            zipFile.getInputStream(entry).use { input ->
                FileOutputStream(this).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun copyHostZipFileIfNeeded(tempFolder: TemporaryFolder): File {
        if (this::hostZipFile.isInitialized && hostZipFile.exists()) {
            return hostZipFile
        }

        val outputFile = tempFolder.newFile()
        ApkGenerator::class.java.classLoader!!.getResource(GENERATED_APKS_FILE)!!.openStream()
            .use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

        hostZipFile = outputFile
        return hostZipFile
    }

    data class InstallResult(
        val packageName: String,
        val error: String,
    )
}
