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

import static com.android.compatibility.common.util.BackupUtils.LOCAL_TRANSPORT_TOKEN;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.util.BackupUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test checking that files created by an app are restored successfully after a backup, but that
 * files put in the folder provided by getNoBackupFilesDir() [files/no_backup] are NOT backed up,
 * and that files are included/excluded according to rules defined in the manifest.
 *
 * Invokes device side tests provided by android.cts.backup.fullbackupapp.FullbackupTest and
 * android.cts.backup.includeexcludeapp.IncludeExcludeTest.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class FullbackupRulesHostSideTest extends BaseBackupHostSideTest {

    private static final String FULLBACKUP_TESTS_APP_NAME = "android.cts.backup.fullbackupapp";
    private static final String FULLBACKUP_DEVICE_TEST_CLASS_NAME =
            FULLBACKUP_TESTS_APP_NAME + ".FullbackupTest";

    private static final String INCLUDE_EXCLUDE_TESTS_APP_NAME =
            "android.cts.backup.includeexcludeapp";
    private static final String INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME =
            INCLUDE_EXCLUDE_TESTS_APP_NAME + ".IncludeExcludeTest";
    private static final String DATA_EXTRACTION_RULES_APPLICABILITY_DEVICE_TEST_CLASS_NAME =
            INCLUDE_EXCLUDE_TESTS_APP_NAME + ".DataExtractionRulesApplicabilityTest";

    private static final String FULL_BACKUP_CONTENT_APP_APK = "CtsFullBackupContentApp.apk";
    private static final String DATA_EXTRACTION_RULES_APP_APK = "CtsDataExtractionRulesApp.apk";
    private static final String DATA_EXTRACTION_RULES_APPLICABILITY_APP_APK
            = "CtsDataExtractionRulesApplicabilityApp.apk";
    private static final String ENCRYPTION_ATTRIBUTE_APP_APK = "CtsEncryptionAttributeApp.apk";

    private static final String BACKUP_ELIGIBILITY_RULES_FEATURE_FLAG
            = "settings_use_new_backup_eligibility_rules";

    private String originalLocalTransportParameters;
    // Behaviour verified by this test is guarded by a feature flag. The test enables the flag at
    // the beginning and restores its original value at the end.
    private String mOriginalFeatureFlagValue;

    @Before
    public void setUp() throws Exception {
        originalLocalTransportParameters = getLocalTransportParameters();
        mOriginalFeatureFlagValue = getDevice().executeShellCommand("settings get global "
                + BACKUP_ELIGIBILITY_RULES_FEATURE_FLAG);
        setFeatureFlagValue("true");
    }

    @After
    public void tearDown() throws Exception {
        setLocalTransportParameters(originalLocalTransportParameters);
        setFeatureFlagValue(mOriginalFeatureFlagValue);
        uninstallPackage(INCLUDE_EXCLUDE_TESTS_APP_NAME);
    }

    private void setFeatureFlagValue(String value) throws Exception {
        getDevice().executeShellCommand("settings put global "
                + BACKUP_ELIGIBILITY_RULES_FEATURE_FLAG + " " + value);
    }

    @Test
    public void testNoBackupFolder() throws Exception {
        // Generate the files that are going to be backed up.
        checkDeviceTest(FULLBACKUP_TESTS_APP_NAME, FULLBACKUP_DEVICE_TEST_CLASS_NAME,
                "createFiles");

        // Do a backup
        getBackupUtils().backupNowAndAssertSuccess(FULLBACKUP_TESTS_APP_NAME);

        // Delete the files
        checkDeviceTest(FULLBACKUP_TESTS_APP_NAME, FULLBACKUP_DEVICE_TEST_CLASS_NAME,
                "deleteFilesAfterBackup");

        // Do a restore
        getBackupUtils().restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, FULLBACKUP_TESTS_APP_NAME);

        // Check that the right files were restored
        checkDeviceTest(FULLBACKUP_TESTS_APP_NAME, FULLBACKUP_DEVICE_TEST_CLASS_NAME,
                "checkRestoredFiles");
    }

    @Test
    public void testFullBackupContentIncludeExcludeRules() throws Exception {
        installPackage(FULL_BACKUP_CONTENT_APP_APK);

        // Generate test data and run a backup and restore pass.
        runBackupAndRestoreOnTestData(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME);

        // Check that the right files were restored.
        checkFullBackupRulesDeviceTest(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "checkRestoredFiles");
    }

    @Test
    public void testDataExtractionRulesIncludeExcludeRules() throws Exception {
        installPackage(DATA_EXTRACTION_RULES_APP_APK);

        // Generate test data and run a backup and restore pass.
        runBackupAndRestoreOnTestData(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME);

        // Check that the right files were restored.
        checkFullBackupRulesDeviceTest(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "checkRestoredFiles");
    }

    /**
     * Run a backup operation on an app that specifies {@code android:dataExtractionRules} and
     * verify that:
     *
     * <ul> Only {@code <cloudBackup/>} section of the config is respected. </ul>
     * <ul> Rules specified via {@code android:fullBackupContent} are ignored. </ul>
     */
    @Test
    public void testBackup_onlyBackupDataExtractionRulesAreApplied() throws Exception {
        installPackage(DATA_EXTRACTION_RULES_APPLICABILITY_APP_APK);

        // Generate test data and run a backup and restore pass.
        runBackupAndRestoreOnTestData(DATA_EXTRACTION_RULES_APPLICABILITY_DEVICE_TEST_CLASS_NAME);

        // Check that the right files were restored
        checkFullBackupRulesDeviceTest(DATA_EXTRACTION_RULES_APPLICABILITY_DEVICE_TEST_CLASS_NAME,
                "testOnlyBackupDataExtractionRulesAreApplied");
    }

    /**
     * Run a device transfer operation on an app that specifies {@code android:dataExtractionRules}
     * and verify that:
     *
     * <ul> Only {@code <deviceTransfer/>} section of the config is respected. </ul>
     * <ul> Rules specified via {@code android:fullBackupContent} are ignored. </ul>
     */
    @Test
    public void testDeviceTransfer_onlyDeviceTransferDataExtractionRulesAreApplied()
            throws Exception {
        setLocalTransportParameters("is_device_transfer=true");
        installPackage(DATA_EXTRACTION_RULES_APPLICABILITY_APP_APK);

        // Generate test data and run a backup and restore pass.
        runBackupAndRestoreOnTestData(DATA_EXTRACTION_RULES_APPLICABILITY_DEVICE_TEST_CLASS_NAME);

        // Check that the right files were restored
        checkFullBackupRulesDeviceTest(DATA_EXTRACTION_RULES_APPLICABILITY_DEVICE_TEST_CLASS_NAME,
                "testOnlyDeviceTransferDataExtractionRulesAreApplied");
    }

    @Test
    public void testDisableIfNoEncryptionCapabilities_encryptedTransport_doesBackUp()
            throws Exception {
        setLocalTransportParameters("is_encrypted=true");
        installPackage(ENCRYPTION_ATTRIBUTE_APP_APK);

        // Generate test data and run a backup and restore pass.
        runBackupAndRestoreOnTestData(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME);

        // Check that the right files were restored.
        checkFullBackupRulesDeviceTest(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "checkRestoredFiles");
    }

    @Test
    public void testDisableIfNoEncryptionCapabilities_unencryptedTransport_doesNotBackUp()
            throws Exception {
        setLocalTransportParameters("is_encrypted=false");
        installPackage(ENCRYPTION_ATTRIBUTE_APP_APK);

        // Generate test data and run a backup and restore pass.
        runBackupAndRestoreOnTestData(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME);

        // Check that no files were restored.
        checkFullBackupRulesDeviceTest(INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "checkNoFilesExist");
    }

    @Test
    public void testRequireFakeEncryptionFlag_includesFileIfFakeEncryptionEnabled()
            throws Exception {
        installPackage(FULL_BACKUP_CONTENT_APP_APK);

        enableFakeEncryptionOnTransport();

        // Generate the files that are going to be backed up.
        checkDeviceTest(INCLUDE_EXCLUDE_TESTS_APP_NAME, INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "createFiles");

        // Do a backup
        getBackupUtils().backupNowAndAssertSuccess(INCLUDE_EXCLUDE_TESTS_APP_NAME);

        // Delete the files
        checkDeviceTest(INCLUDE_EXCLUDE_TESTS_APP_NAME, INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "deleteFilesAfterBackup");

        // Do a restore
        getBackupUtils()
                .restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, INCLUDE_EXCLUDE_TESTS_APP_NAME);

        // Check that the client-side encryption files were restored
        checkDeviceTest(INCLUDE_EXCLUDE_TESTS_APP_NAME, INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "checkRestoredClientSideEncryptionFiles");
    }

    @Test
    public void testRequireFakeEncryptionFlag_excludesFileIfFakeEncryptionDisabled()
            throws Exception {
        installPackage(FULL_BACKUP_CONTENT_APP_APK);

        disableFakeEncryptionOnTransport();

        // Generate the files that are going to be backed up.
        checkDeviceTest(INCLUDE_EXCLUDE_TESTS_APP_NAME, INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "createFiles");

        // Do a backup
        getBackupUtils().backupNowAndAssertSuccess(INCLUDE_EXCLUDE_TESTS_APP_NAME);

        // Delete the files
        checkDeviceTest(INCLUDE_EXCLUDE_TESTS_APP_NAME, INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "deleteFilesAfterBackup");

        // Do a restore
        getBackupUtils()
                .restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, INCLUDE_EXCLUDE_TESTS_APP_NAME);

        // Check that the client-side encryption files were not restored
        checkDeviceTest(INCLUDE_EXCLUDE_TESTS_APP_NAME, INCLUDE_EXCLUDE_DEVICE_TEST_CLASS_NAME,
                "checkDidNotRestoreClientSideEncryptionFiles");
    }

    /**
     * <ol> Generate test files. </ol>
     * <ol> Run a backup. </ol>
     * <ol> Clear app data. </ol>
     * <ol> Run a restore. </ol>
     */
    private void runBackupAndRestoreOnTestData(String deviceSideTestName) throws Exception {
        // Generate the files that are going to be backed up.
        checkFullBackupRulesDeviceTest(deviceSideTestName, "createFiles");

        // Do a backup
        getBackupUtils().backupNowSync(INCLUDE_EXCLUDE_TESTS_APP_NAME);

        // Delete the files
        checkFullBackupRulesDeviceTest(deviceSideTestName,
                "deleteFilesAfterBackup");

        // Do a restore
        getBackupUtils()
                .restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, INCLUDE_EXCLUDE_TESTS_APP_NAME);
    }

    private void checkFullBackupRulesDeviceTest(String className, String testName)
            throws DeviceNotAvailableException {
        checkDeviceTest(INCLUDE_EXCLUDE_TESTS_APP_NAME, className, testName);
    }
}
