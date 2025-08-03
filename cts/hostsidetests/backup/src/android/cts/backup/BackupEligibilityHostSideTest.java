/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.cts.backup;

import static org.junit.Assert.assertNull;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Test verifying backup eligibility rules are respected.
 *
 * <p>Tests the following scenarios:
 * <ol>
 *   <li>App that has {@code allowBackup=false} in the manifest shouldn't be backed up by {@code
 *       adb shell bmgr}.
 *   <li>App that doesn't have {@code allowBackup} in the manifest (default is true) should be
 *       backed up by {@code adb shell bmgr}.
 *   <li>App that has {@code debuggable=true} in the manifest should be backed up by {@code adb
 *       backup}.
 *   <li>App that has {@code debuggable=false} in the manifest shouldn't be backed up by in
 *       {@code adb backup}.
 * </ol>
 *
 * <p>Invokes device side tests provided by
 * {@link android.cts.backup.backupnotallowedapp.BackupEligibilityTest} and
 * {@link android.cts.backup.adbbackupapp.AdbBackupApp}.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class BackupEligibilityHostSideTest extends BaseBackupHostSideTest {
    private static final String BACKUP_ELIGIBILITY_APP_NAME
            = "android.cts.backup.backupeligibilityapp";
    private static final String ADB_BACKUP_APP_NAME = "android.cts.backup.adbbackupapp";
    private static final String BACKUP_ELIGIBILITY_DEVICE_TEST_CLASS_NAME =
            BACKUP_ELIGIBILITY_APP_NAME + ".BackupEligibilityTest";
    private static final String ADB_BACKUP_DEVICE_SIDE_CLASS_NAME =
            ADB_BACKUP_APP_NAME + ".AdbBackupApp";
    private static final String BACKUP_RESTORE_CONFIRMATION_PACKAGE = "com.android.backupconfirm";
    private static final String ADB_BACKUP_FILE = "adb_backup_file.ab";
    private static final String TEMP_DIR = "backup_eligibility_test_tmp";

    /** The name of the APK of the app that has allowBackup=false in the manifest */
    private static final String ALLOWBACKUP_FALSE_APP_APK = "BackupNotAllowedApp.apk";

    /** The name of the APK of the app that doesn't have allowBackup in the manifest
     * (same as allowBackup=true by default) */
    private static final String ALLOWBACKUP_APP_APK = "BackupAllowedApp.apk";

    /** The name of the APK of the app that has {@code debuggable=false} in the manifest. */
    private static final String DEBUGGABLE_FALSE_APP_APK = "NonDebuggableApp.apk";
    /** The name of the APK of the app that has {@code debuggable=true} in the manifest. */
    private static final String DEBUGGABLE_TRUE_APP_APK = "DebuggableApp.apk";
    private static final String ADB_BACKUP_APP_APK = "AdbBackupApp.apk";

    @After
    public void tearDown() throws Exception {
        // Clear backup data and uninstall the package (in that order!)
        clearBackupDataInLocalTransport(BACKUP_ELIGIBILITY_APP_NAME);
        assertNull(uninstallPackage(BACKUP_ELIGIBILITY_APP_NAME));
    }

    /**
     * <ol>
     *   <li>Install the app
     *   <li>Generate files inside the app's data folder.
     *   <li>Run {@code bmgr backupnow} and assert 'Backup is not allowed' is printed.
     *   <li>Uninstall / reinstall the app
     *   <li>Assert no files have been restored.
     * </ol>
     */
    @Test
    public void testAllowBackup_False() throws Exception {
        installPackage(ALLOWBACKUP_FALSE_APP_APK, "-d", "-r");

        // Generate the files that are going to be backed up.
        checkBackupEligibilityDeviceTest("createFiles");

        getBackupUtils().backupNowAndAssertBackupNotAllowed(BACKUP_ELIGIBILITY_APP_NAME);

        assertNull(uninstallPackage(BACKUP_ELIGIBILITY_APP_NAME));

        installPackage(ALLOWBACKUP_FALSE_APP_APK, "-d", "-r");

        checkBackupEligibilityDeviceTest("checkNoFilesExist");
    }

    /**
     * <ol>
     *   <li>Install the app.
     *   <li>Generate files inside the app's data folder.
     *   <li>Run {@code bmgr backupnow} and assert 'Success' is printed.
     *   <li>Uninstall / reinstall the app.
     *   <li>Assert the files have been restored.
     * </ol>
     */
    @Test
    public void testAllowBackup_True() throws Exception {
        installPackage(ALLOWBACKUP_APP_APK, "-d", "-r");

        // Generate the files that are going to be backed up.
        checkBackupEligibilityDeviceTest("createFiles");

        // Do a backup
        getBackupUtils().backupNowAndAssertSuccess(BACKUP_ELIGIBILITY_APP_NAME);

        assertNull(uninstallPackage(BACKUP_ELIGIBILITY_APP_NAME));

        installPackage(ALLOWBACKUP_APP_APK, "-d", "-r");

        checkBackupEligibilityDeviceTest("checkAllFilesExist");
    }

    /**
     * <ol>
     *   <li>Install the app.
     *   <li>Generate files inside the app's data folder.
     *   <li>Run {@code adb backup}.
     *   <li>Uninstall / reinstall the app.
     *   <li>Run {@code adb restore}.
     *   <li>Assert no files have been restored.
     * </ol>
     */
    @Test
    public void testAdbBackup_offForNonDebuggableApp() throws Exception {
        installPackage(DEBUGGABLE_FALSE_APP_APK, "-d", "-r");

        runAdbBackupAndRestore();

        checkBackupEligibilityDeviceTest("checkNoFilesExist");
    }

    /**
     * <ol>
     *   <li>Install the app.
     *   <li>Generate files inside the app's data folder.
     *   <li>Run {@code adb backup}.
     *   <li>Clear data for the app.
     *   <li>Run {@code adb restore}.
     *   <li>Assert the files have been restored.
     * </ol>
     */
    @Test
    public void testAdbBackup_onForDebuggableApp() throws Exception {
        installPackage(DEBUGGABLE_TRUE_APP_APK, "-d", "-r");

        runAdbBackupAndRestore();

        checkBackupEligibilityDeviceTest("checkAllFilesExist");
    }

    private void runAdbBackupAndRestore() throws Exception {
        installPackage(ADB_BACKUP_APP_APK, "-d", "-r");

        File tempDir = null;
        try {
            // Generate the files that are going to be backed up.
            checkBackupEligibilityDeviceTest("createFiles");

            // Create a temp dir on the host for adb backup output.
            tempDir = FileUtil.createTempDir(TEMP_DIR);
            String backupFileName = new File(tempDir, ADB_BACKUP_FILE).getAbsolutePath();
            runAdbCommand("backup", "-f", backupFileName, BACKUP_ELIGIBILITY_APP_NAME);

            checkBackupEligibilityDeviceTest("deleteFilesAndAssertNoneExist");
            runAdbCommand("restore", backupFileName);
        } finally {
            if (tempDir != null) {
                FileUtil.recursiveDelete(tempDir);
            }
            assertNull(uninstallPackage(ADB_BACKUP_APP_NAME));
        }
    }

    private void checkBackupEligibilityDeviceTest(String methodName)
            throws DeviceNotAvailableException {
        checkDeviceTest(BACKUP_ELIGIBILITY_APP_NAME, BACKUP_ELIGIBILITY_DEVICE_TEST_CLASS_NAME,
                methodName);
    }

    private void runAdbCommand(String... arguments) throws Exception {
        ITestDevice device = getDevice();

        try {
            // Close the backup confirmation window in case there's already one floating around for
            // any reason.
            device.executeShellCommand("am force-stop " + BACKUP_RESTORE_CONFIRMATION_PACKAGE);
        } catch (Exception e) {
            CLog.w("Error while trying to force-stop backup confirmation: " + e.getMessage());
            // Keep going
        }

        Thread restore = new Thread(() -> {
            try {
                device.executeAdbCommand(arguments);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        restore.start();
        runDeviceSideProcedure(ADB_BACKUP_APP_NAME, ADB_BACKUP_DEVICE_SIDE_CLASS_NAME,
                /* procedureName */ "clickAdbBackupConfirmButton");
        restore.join();
    }

    private void runDeviceSideProcedure(String packageName, String className,
            String procedureName) throws Exception {
        checkDeviceTest(packageName, className, procedureName);
    }
}
