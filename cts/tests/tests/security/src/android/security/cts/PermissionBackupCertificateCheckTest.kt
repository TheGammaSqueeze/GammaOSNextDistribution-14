/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */
package android.security.cts

import android.Manifest.permission.*
import android.app.AppOpsManager
import android.content.pm.PackageManager.*
import android.os.ParcelFileDescriptor
import android.permission.cts.PermissionUtils.grantPermission
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.BackupUtils
import com.android.compatibility.common.util.BackupUtils.LOCAL_TRANSPORT_TOKEN
import com.android.compatibility.common.util.BusinessLogicTestCase
import com.android.compatibility.common.util.ShellUtils.runShellCommand
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import java.io.InputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that permissions for backed up apps are restored only after checking that their signing
 * certificates are compared.
 *
 * @see [com.android.permissioncontroller.permission.service.BackupHelper]
 */
@AppModeFull
@RunWith(AndroidJUnit4::class)
class PermissionBackupCertificateCheckTest : StsExtraBusinessLogicTestCase() {
    private val backupUtils: BackupUtils =
        object : BackupUtils() {
            override fun executeShellCommand(command: String): InputStream {
                val pfd =
                    BusinessLogicTestCase.getInstrumentation()
                        .uiAutomation
                        .executeShellCommand(command)
                return ParcelFileDescriptor.AutoCloseInputStream(pfd)
            }
        }

    private var isBackupSupported = false

    private val targetContext = InstrumentationRegistry.getTargetContext()

    @Before
    fun setUp() {
        val packageManager = BusinessLogicTestCase.getInstrumentation().context.packageManager
        isBackupSupported =
            (packageManager != null && packageManager.hasSystemFeature(FEATURE_BACKUP))

        if (isBackupSupported) {
            assertTrue("Backup not enabled", backupUtils.isBackupEnabled)
            assertTrue("LocalTransport not selected", backupUtils.isLocalTransportSelected)
            backupUtils.executeShellCommandSync("setprop log.tag.$APP_LOG_TAG VERBOSE")
        }
    }

    @After
    fun tearDown() {
        uninstallIfInstalled(APP)
        clearFlag(APP, ACCESS_FINE_LOCATION, FLAG_PERMISSION_USER_SET)
        clearFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET)
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has the
     * same certificate as the backed up app.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_sameCert_restoresRuntimePermissions() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_1_DUP)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has a
     * different certificate as the backed up app.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_diffCert_doesNotGrantRuntimePermissions() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_3)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has the
     * backed up app's certificate in its signing history.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_midHistoryToRotated_restoresRuntimePermissions() {
        install(APP_APK_CERT_2)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has the
     * backed up app's certificate as the original certificate in its signing history.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_origToRotated_restoresRuntimePermissions() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the
     * restored app's certificate in its signing history.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_rotatedToMidHistory_restoresRuntimePermissions() {
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_2)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the
     * restored app's certificate in its signing history as its original certificate.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_rotatedToOrig_restoresRuntimePermissions() {
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_1)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the same
     * certificate as the restored app, but the restored app additionally has signing certificate
     * history.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_sameWithHistory_restoresRuntimePermissions() {
        install(APP_APK_CERT_4)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app has the same
     * certificate as the restored app, but the backed up app additionally has signing certificate
     * history.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_sameWithoutHistory_restoresRuntimePermissions() {
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * signing history, but the backed up app's certificate is not in this signing history.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_notInBackedUpHistory_doesNotRestoreRuntimePerms() {
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_3)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * signing history, but the backed up app's certificate is not in this signing history.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_notInRestoredHistory_doesNotRestoreRuntimePerms() {
        install(APP_APK_CERT_3)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * multiple certificates, and the backed up app also has identical multiple certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_sameMultCerts_restoresRuntimePermissions() {
        install(APP_APK_CERT_1_2)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_1_2_DUP)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * multiple certificates, and the backed up app do not have identical multiple certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_diffMultCerts_doesNotRestoreRuntimePermissions() {
        install(APP_APK_CERT_1_2)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_3_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the app being restored has
     * multiple certificates, and the backed up app's certificate is present in th restored app's
     * certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_singleToMultiCert_restoresRuntimePerms() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_1_2_3)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app and the app
     * being restored have multiple certificates, and the backed up app's certificates are a subset
     * of the restored app's certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_multCertsToSuperset_doesNotRestoreRuntimePerms() {
        install(APP_APK_CERT_1_2)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_1_2_3)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of regular runtime permissions, when the backed up app and the app
     * being restored have multiple certificates, and the backed up app's certificates are a
     * superset of the restored app's certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_multCertsToSubset_doesNotRestoreRuntimePermissions() {
        install(APP_APK_CERT_1_2_3)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_1_2)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, READ_CONTACTS))
        }
    }

    /**
     * Test backup and restore of tri-state permissions, when both foreground and background runtime
     * permissions are not granted and the backed up and restored app have compatible certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_fgBgDenied_matchingCerts_restoresFgBgPermissions() {
        install(APP_APK_CERT_2)
        if (!isBackupSupported) {
            return
        }
        // Make a token change to permission state, to enable to us to determine when restore is
        // complete.
        grantPermission(APP, WRITE_CONTACTS)
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_FINE_LOCATION, FLAG_PERMISSION_USER_SET)
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {

            // Wait until restore is complete.
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, WRITE_CONTACTS))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION))
            assertEquals(AppOpsManager.MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION))
        }
    }

    /**
     * Test backup and restore of tri-state permissions, when both foreground and background runtime
     * permissions are not granted and the backed up and restored app don't have compatible
     * certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_fgBgDenied_notMatchingCerts_doesNotRestorePerms() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        // Make a token change to permission state, to enable to us to determine when restore is
        // complete.
        grantPermission(APP, WRITE_CONTACTS)
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_FINE_LOCATION, FLAG_PERMISSION_USER_SET)
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_2)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {

            // Wait until restore is complete.
            assertEquals(PERMISSION_DENIED, checkPermission(APP, WRITE_CONTACTS))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION))
            assertEquals(AppOpsManager.MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION))
        }
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground runtime permission is
     * granted and the backed up and restored app have compatible certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_fgGranted_matchingCerts_restoresFgBgPermissions() {
        install(APP_APK_CERT_2)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION))
            assertEquals(AppOpsManager.MODE_FOREGROUND, getAppOp(APP, ACCESS_FINE_LOCATION))
        }
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground runtime permission is
     * granted and the backed up and restored app don't have compatible certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_fgGranted_notMatchingCerts_doesNotRestoreFgBgPerms() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)
        // PERMISSION_DENIED is the default state, so we mark the permissions as user set in order
        // to ensure that permissions are backed up.
        setFlag(APP, ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_USER_SET)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_2)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION))
            assertEquals(AppOpsManager.MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION))
        }
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground and background runtime
     * permissions are granted and the backed up and restored app have compatible certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_fgBgGranted_matchingCerts_restoresFgBgPermissions() {
        install(APP_APK_CERT_2)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)
        grantPermission(APP, ACCESS_BACKGROUND_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION))
            assertEquals(AppOpsManager.MODE_ALLOWED, getAppOp(APP, ACCESS_FINE_LOCATION))
        }
    }

    /**
     * Test backup and restore of tri-state permissions, when foreground and background runtime
     * permissions are granted and the backed up and restored app don't have compatible
     * certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_fgBgGranted_notMatchingCerts_restoresFgBgPerms() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)
        grantPermission(APP, ACCESS_BACKGROUND_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_2)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually {
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION))
            assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_BACKGROUND_LOCATION))
            assertEquals(AppOpsManager.MODE_IGNORED, getAppOp(APP, ACCESS_FINE_LOCATION))
        }
    }

    /**
     * Test backup and restore of flags when the backed up app and restored app have compatible
     * certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_matchingCerts_restoresFlags() {
        install(APP_APK_CERT_2)
        if (!isBackupSupported) {
            return
        }
        setFlag(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_4_HISTORY_1_2_4)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually { assertTrue(isFlagSet(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET)) }
    }

    /**
     * Test backup and restore of flags when the backed up app and restored app don't have
     * compatible certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_notMatchingCerts_doesNotRestoreFlag() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        setFlag(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        install(APP_APK_CERT_2)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)

        eventually { assertFalse(isFlagSet(APP, WRITE_CONTACTS, FLAG_PERMISSION_USER_SET)) }
    }

    /**
     * Test backup and delayed restore of regular runtime permission, i.e. when an app is installed
     * after restore has run, and the backed up app and restored app have compatible certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_appInstalledLater_matchingCerts_restoresCorrectly() {
        install(APP_APK_CERT_2)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)
        install(APP_APK_CERT_4_HISTORY_1_2_4)

        eventually { assertEquals(PERMISSION_GRANTED, checkPermission(APP, ACCESS_FINE_LOCATION)) }
    }

    /**
     * Test backup and delayed restore of regular runtime permission, i.e. when an app is installed
     * after restore has run, and the backed up app and restored app don't have compatible
     * certificates.
     */
    @Test
    @AsbSecurityTest(cveBugId = [184847040])
    fun testRestore_appInstalledLater_notMatchingCerts_doesNotRestore() {
        install(APP_APK_CERT_1)
        if (!isBackupSupported) {
            return
        }
        grantPermission(APP, ACCESS_FINE_LOCATION)

        backupUtils.backupNowAndAssertSuccess(ANDROID_PACKAGE)
        uninstallIfInstalled(APP)
        backupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, ANDROID_PACKAGE)
        install(APP_APK_CERT_4_HISTORY_1_2_4)

        eventually { assertEquals(PERMISSION_DENIED, checkPermission(APP, ACCESS_FINE_LOCATION)) }
    }

    private fun install(apk: String) {
        val output = runShellCommand("pm install -r $apk")
        assertEquals("Success", output)
    }

    private fun uninstallIfInstalled(packageName: String) {
        runShellCommand("pm uninstall $packageName")
    }

    private fun setFlag(app: String, permission: String, flag: Int) {
        runWithShellPermissionIdentity {
            targetContext.packageManager.updatePermissionFlags(
                permission, app, flag, flag, targetContext.user)
        }
    }

    private fun clearFlag(app: String, permission: String, flag: Int) {
        runWithShellPermissionIdentity {
            targetContext.packageManager.updatePermissionFlags(
                permission, app, flag, 0, targetContext.user)
        }
    }

    private fun isFlagSet(app: String, permission: String, flag: Int): Boolean {
        return try {
            callWithShellPermissionIdentity<Int> {
                targetContext.packageManager.getPermissionFlags(permission, app, targetContext.user)
            } and flag == flag
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun checkPermission(app: String, permission: String): Int {
        return targetContext.packageManager.checkPermission(permission, app)
    }

    private fun getAppOp(app: String, permission: String): Int {
        return try {
            callWithShellPermissionIdentity {
                targetContext
                    .getSystemService<AppOpsManager>(AppOpsManager::class.java)!!
                    .unsafeCheckOpRaw(
                        AppOpsManager.permissionToOp(permission)!!,
                        targetContext.packageManager.getPackageUid(app, 0),
                        app)
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    companion object {
        /** The name of the package of the apps under test */
        private const val APP = "android.security.permissionbackup"
        /** The apk of the packages */
        private const val APK_PATH = "/data/local/tmp/cts/security/"
        private const val APP_APK_CERT_1 = "${APK_PATH}CtsPermissionBackupAppCert1.apk"
        private const val APP_APK_CERT_1_DUP = "${APK_PATH}CtsPermissionBackupAppCert1Dup.apk"
        private const val APP_APK_CERT_2 = "${APK_PATH}CtsPermissionBackupAppCert2.apk"
        private const val APP_APK_CERT_3 = "${APK_PATH}CtsPermissionBackupAppCert3.apk"
        private const val APP_APK_CERT_4 = "${APK_PATH}CtsPermissionBackupAppCert4.apk"
        private const val APP_APK_CERT_1_2 = "${APK_PATH}CtsPermissionBackupAppCert12.apk"
        private const val APP_APK_CERT_1_2_DUP = "${APK_PATH}CtsPermissionBackupAppCert12Dup.apk"
        private const val APP_APK_CERT_1_2_3 = "${APK_PATH}CtsPermissionBackupAppCert123.apk"
        private const val APP_APK_CERT_3_4 = "${APK_PATH}CtsPermissionBackupAppCert34.apk"
        private const val APP_APK_CERT_4_HISTORY_1_2_4 =
            "${APK_PATH}CtsPermissionBackupAppCert4History124.apk"
        private const val APP_LOG_TAG = "PermissionBackupApp"
        /** The name of the package for backup */
        private const val ANDROID_PACKAGE = "android"
        private const val TIMEOUT_MILLIS: Long = 10000

        /**
         * Make sure that a [Runnable] eventually finishes without throwing an [Exception].
         *
         * @param r The [Runnable] to run.
         */
        fun eventually(r: Runnable) {
            val start = System.currentTimeMillis()
            while (true) {
                try {
                    r.run()
                    return
                } catch (e: Throwable) {
                    if (System.currentTimeMillis() - start < TIMEOUT_MILLIS) {
                        try {
                            Thread.sleep(100)
                        } catch (ignored: InterruptedException) {
                            throw RuntimeException(e)
                        }
                    } else {
                        throw e
                    }
                }
            }
        }
    }
}
