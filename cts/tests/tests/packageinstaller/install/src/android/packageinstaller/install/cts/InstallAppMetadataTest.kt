/*
 * Copyright 2022 The Android Open Source Project
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

import android.app.UiAutomation
import android.content.pm.Flags
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageManager.APP_METADATA_SOURCE_APK
import android.content.pm.PackageManager.APP_METADATA_SOURCE_INSTALLER
import android.content.pm.PackageManager.NameNotFoundException
import android.os.PersistableBundle
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
class InstallAppMetadataTest : PackageInstallerTestBase() {

    private val TEST_FIELD = "testField"
    private val TEST_APK2_NAME = "CtsEmptyTestApp_AppMetadataInApk.apk"

    private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation()

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(
        Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE,
        Flags.FLAG_READ_INSTALL_INFO,
        Flags.FLAG_GET_RESOLVED_APK_PATH
    )
    @Test(expected = SecurityException::class)
    fun getAppMetadataSourceWithNoPermission() {
        installTestApp(createAppMetadata())

        pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test(expected = NameNotFoundException::class)
    fun getAppMetadataSourceApNotInstall() {
        uiAutomation.adoptShellPermissionIdentity()
        try {
            pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME)
        } catch (e: Exception) {
            throw e
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun getAppMetadataSourceUnknown() {
        installTestApp(null)

        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.GET_APP_METADATA)
        try {
            val source = pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME)
            assertThat(source).isEqualTo(PackageManager.APP_METADATA_SOURCE_UNKNOWN)
        } catch (e: Exception) {
            throw e
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE,
        Flags.FLAG_READ_INSTALL_INFO,
        Flags.FLAG_GET_RESOLVED_APK_PATH
    )
    @Test
    fun getAppMetadataSourceViaSessionWithAppMetadata() {
        val data = createAppMetadata()
        installTestApp(data)

        uiAutomation.adoptShellPermissionIdentity()
        try {
            val source = pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME)
            assertThat(source).isEqualTo(PackageManager.APP_METADATA_SOURCE_INSTALLER)
        } catch (e: Exception) {
            throw e
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun getAppMetadataInApk() {
        installPackage(TEST_APK2_NAME)

        uiAutomation.adoptShellPermissionIdentity()
        try {
            val data = pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
            assertThat(data.size()).isEqualTo(2)
            assertThat(data.getString("source")).isEqualTo("apk")
            assertThat(data.getLong("version")).isEqualTo(2)
            assertThat(pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME))
                .isEqualTo(APP_METADATA_SOURCE_APK)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun installViaSessionWithAppMetadataInApk() {
        File(TEST_APK_LOCATION, TEST_APK2_NAME)
            .copyTo(target = File(context.filesDir, TEST_APK2_NAME), overwrite = true)
        val data = createAppMetadata()
        installTestApp(data, TEST_APK2_NAME)

        uiAutomation.adoptShellPermissionIdentity()
        try {
            assertAppMetadata(data.getString(TEST_FIELD), pm.getAppMetadata(TEST_APK_PACKAGE_NAME))
            assertThat(pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME))
                .isEqualTo(APP_METADATA_SOURCE_INSTALLER)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun installViaSession() {
        installTestApp(null)

        uiAutomation.adoptShellPermissionIdentity()
        val data = pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
        uiAutomation.dropShellPermissionIdentity()
        assertThat(data).isNotNull()
        assertThat(data.isEmpty()).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun installViaSessionWithAppMetadata() {
        val data = createAppMetadata()
        installTestApp(data)

        uiAutomation.adoptShellPermissionIdentity()
        try {
            assertAppMetadata(data.getString(TEST_FIELD), pm.getAppMetadata(TEST_APK_PACKAGE_NAME))
            assertThat(pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME))
                .isEqualTo(APP_METADATA_SOURCE_INSTALLER)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test(expected = SecurityException::class)
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun getAppMetadataWithNoPermission() {
        installTestApp(createAppMetadata())

        pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
    }

    @Test(expected = IllegalArgumentException::class)
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun installViaSessionWithBadAppMetadata() {
        installTestApp(createAppMetadataExceedSizeLimit())
    }

    @Test(expected = NameNotFoundException::class)
    fun noInstallGetAppMetadata() {
        uiAutomation.adoptShellPermissionIdentity()
        try {
            pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun installViaSessionWithOnlyAppMetadata() {
        val data = createAppMetadata()
        val (sessionId, session) = createSession(0, false, null)
        setAppMetadata(session, data)
        assertThat(session.getNames()).isEmpty()
        commitSession(session, false)
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_FAILURE_INVALID)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun resetAppMetadataInSession() {
        val data = createAppMetadata()
        val (sessionId, session) = createSession(0, false, null)
        writeSession(session, TEST_APK_NAME)
        setAppMetadata(session, data)
        assertAppMetadata(data.getString(TEST_FIELD), session.getAppMetadata())
        setAppMetadata(session, null)
        assertThat(session.getAppMetadata().isEmpty()).isTrue()
        commitSession(session)
        clickInstallerUIButton(INSTALL_BUTTON_ID)
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)

        uiAutomation.adoptShellPermissionIdentity()
        try {
            assertThat(pm.getAppMetadata(TEST_APK_PACKAGE_NAME).isEmpty()).isTrue()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun installWithNoAppMetadataDropExisting() {
        val data = createAppMetadata()
        installTestApp(data)

        uiAutomation.adoptShellPermissionIdentity()
        assertAppMetadata(data.getString(TEST_FIELD), pm.getAppMetadata(TEST_APK_PACKAGE_NAME))
        uiAutomation.dropShellPermissionIdentity()

        installTestApp(null)

        uiAutomation.adoptShellPermissionIdentity()
        assertThat(pm.getAppMetadata(TEST_APK_PACKAGE_NAME).isEmpty()).isTrue()
        uiAutomation.dropShellPermissionIdentity()
    }

    @Test(expected = FileNotFoundException::class)
    @RequiresFlagsEnabled(Flags.FLAG_READ_INSTALL_INFO, Flags.FLAG_GET_RESOLVED_APK_PATH)
    fun readAppMetadataFileShouldFail() {
        val data = createAppMetadata()
        installTestApp(data)

        val appInfo = pm.getApplicationInfo(
            TEST_APK_PACKAGE_NAME,
            PackageManager.ApplicationInfoFlags.of(0)
        )
        val file = File(File(appInfo.publicSourceDir).getParentFile(), "app.metadata")
        PersistableBundle.readFromStream(file.inputStream())
    }

    private fun installTestApp(data: PersistableBundle?, apkName: String = TEST_APK_NAME) {
        val (sessionId, session) = createSession(0, false, null)
        writeSession(session, apkName)
        if (data != null) {
            setAppMetadata(session, data)
            assertAppMetadata(data.getString(TEST_FIELD), session.getAppMetadata())
            assertThat(session.getNames()).hasLength(1)
        }
        commitSession(session)

        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
    }

    private fun setAppMetadata(session: PackageInstaller.Session, data: PersistableBundle?) {
        try {
            session.setAppMetadata(data)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    private fun assertAppMetadata(testVal: String?, data: PersistableBundle) {
        assertThat(data).isNotNull()
        assertEquals(data.size(), 1)
        assertThat(data.containsKey(TEST_FIELD)).isTrue()
        assertEquals(data.getString(TEST_FIELD), testVal)
    }

    private fun createAppMetadata(): PersistableBundle {
        val bundle = PersistableBundle()
        bundle.putString(TEST_FIELD, "testValue")
        return bundle
    }

    private fun createAppMetadataExceedSizeLimit(): PersistableBundle {
        val bundle = PersistableBundle()
        // create a bundle that is greater than default size limit of 32KB.
        bundle.putString(TEST_FIELD, "a".repeat(32000))
        return bundle
    }
}
