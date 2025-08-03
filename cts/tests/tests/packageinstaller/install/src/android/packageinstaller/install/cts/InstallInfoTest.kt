/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.packageinstaller.install.cts

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.platform.test.annotations.AppModeFull
import androidx.test.InstrumentationRegistry
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@AppModeFull(reason = "Instant apps cannot access device storage")
class InstallInfoTest : PackageInstallerTestBase() {
    private val NOT_AN_APK = "NotAnApk.txt"
    // corruptedapk.apk in /install/assets dir was copied over from
    // cts/hostsidetests/appsecurity/test-apps/CorruptApkTests/ and renamed here.
    private val CORRUPTED_APK_NAME = "corruptedapk.apk"
    private val mContext: Context = InstrumentationRegistry.getContext()

    @get:Rule
    val tempFolder = TemporaryFolder()
    private val mParams = PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_FULL_INSTALL
    )

    @Test
    fun testInstallInfoOfMonolithicPackage() {
        val apk = File(context.filesDir.canonicalPath + "/$TEST_APK_NAME")
        val installInfo = pi.readInstallInfo(apk, 0)

        assertEquals(PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL, installInfo.installLocation)
        assertEquals(TEST_APK_PACKAGE_NAME, installInfo.packageName)
        assertEquals(apk.length(), installInfo.calculateInstalledSize(mParams))
    }

    @Test
    fun testInstallInfoOfMonolithicPackageViaFileDescriptor() {
        val apk = File(context.filesDir.canonicalPath + "/$TEST_APK_NAME")
        ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use {
            val installInfo = pi.readInstallInfo(it, apk.absolutePath, 0)

            assertEquals(PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL, installInfo.installLocation)
            assertEquals(TEST_APK_PACKAGE_NAME, installInfo.packageName)
            assertEquals(apk.length(), installInfo.calculateInstalledSize(mParams, it))
        }
    }

    @Test
    fun testInstallInfoOfClusterPackage() {
        // Copy the test APK to an unique folder.
        val clusterDir = File(context.filesDir, "testInstallInfoOfClusterPackage")
        File(TEST_APK_LOCATION, TEST_APK_NAME).copyTo(
                target = File(clusterDir, TEST_APK_NAME), overwrite = true)
        val apk = File(clusterDir.canonicalPath)
        val installInfo = pi.readInstallInfo(apk, 0)

        // The test APKs do not include native binaries or dex metadata. Thus, the total size of
        // the cluster package should be equal to sum of size of each APKs in the folder.
        val expectedSize = apk.listFiles()!!
            .filter { file -> file.isFile() && file.getPath().endsWith(".apk") }.sumOf(File::length)

        assertEquals(TEST_APK_PACKAGE_NAME, installInfo.packageName)
        assertEquals(PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL, installInfo.installLocation)
        assertEquals(expectedSize, installInfo.calculateInstalledSize(mParams))
    }

    @Test
    fun testInstallInfoOfNonApk_throwsException() {
        val apk = tempFolder.newFile(NOT_AN_APK).apply {
            this.writeBytes(Random.nextBytes(ByteArray(10)))
        }
        assertThrows(PackageInstaller.PackageParsingException::class.java) {
            pi.readInstallInfo(apk, 0)
        }
    }

    @Test
    fun testInstallInfoOfCorruptedApk_throwsException() {
        val apk = tempFolder.newFile(CORRUPTED_APK_NAME)
        mContext.assets.open(CORRUPTED_APK_NAME).use { input ->
            apk.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        assertThrows(PackageInstaller.PackageParsingException::class.java) {
            pi.readInstallInfo(apk, 0)
        }
    }

    @Test
    fun testExceptionErrorCodeOfNonApk() {
        val apk = tempFolder.newFile(NOT_AN_APK).apply {
            this.writeBytes(Random.nextBytes(ByteArray(10)))
        }
        try {
            pi.readInstallInfo(apk, 0)
        } catch (e: PackageInstaller.PackageParsingException) {
            assertEquals(e.errorCode, PackageManager.INSTALL_PARSE_FAILED_NOT_APK)
        }
    }
}
