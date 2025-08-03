/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.cts.credentials.backuprestore;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.util.BackupHostSideUtils;
import com.android.compatibility.common.util.BackupUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verifies that Credential Manager settings are restored correctly. */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class CredentialManagerRestoreSettingsHostSideTest extends BaseHostJUnit4Test {
    /** Value of PackageManager.FEATURE_BACKUP */
    private static final String FEATURE_BACKUP = "android.software.backup";

    /** Value of PackageManager.FEATURE_CREDENTIALS */
    private static final String FEATURE_CREDENTIALS = "android.software.credentials";

    protected static final String LOCAL_TRANSPORT = "com.android.localtransport/.LocalTransport";

    @Rule
    public final RequiredFeatureRule mBackupRequiredRule =
            new RequiredFeatureRule(this, FEATURE_BACKUP);

    @Rule
    public final RequiredFeatureRule mCredManRequiredRule =
            new RequiredFeatureRule(this, FEATURE_CREDENTIALS);

    private static final String SETTINGS_PACKAGE = "com.android.providers.settings";
    private static final String TEST_APP_PACKAGE = "android.cts.credentials.backuprestoreapp";
    private static final String TEST_APP_APK = "CtsCredentialManagerBackupRestoreApp.apk";

    private static final String AUTOFILL_SETTING_NAME = "autofill_service";
    private static final String CREDMAN_SETTING_NAME = "credential_service";
    private static final String CREDMAN_PRIMARY_SETTING_NAME = "credential_service_primary";
    private static final String SETTINGS_DO_NOT_RESTORE_PRESERVED_SETTING_NAME =
            "settings_do_not_restore_preserved";

    private static final String AUTOFILL_TEST_SERVICE = "com.example.test/.AutofillService";
    private static final String CREDMAN_TEST_SERVICE =
            "com.example.test/.ServiceA:com.example.test/.ServiceB";
    private static final String CREDMAN_TEST_PRIMARY_SERVICE = "com.example.test/.ServiceA";
    private static final String NEW_SETTINGS_VALUE = "com.example.test2/.Service";

    private static final String SECURE_NAMESPACE = "secure";
    private static final String GLOBAL_NAMESPACE = "global";

    private String mOriginalFeatureFlagValue = "";

    private BackupUtils mBackupUtils =
            new BackupUtils() {
                @Override
                protected InputStream executeShellCommand(String command) throws IOException {
                    return executeDeviceShellCommand(getDevice(), command);
                }
            };

    @Before
    public void setUp() throws Exception {
        mOriginalFeatureFlagValue =
                getSettingValue(GLOBAL_NAMESPACE, SETTINGS_DO_NOT_RESTORE_PRESERVED_SETTING_NAME);
        setSettingValue(
                GLOBAL_NAMESPACE,
                SETTINGS_DO_NOT_RESTORE_PRESERVED_SETTING_NAME,
                Boolean.TRUE.toString());

        BackupHostSideUtils.checkSetupComplete(getDevice());

        mBackupUtils.enableBackup(true);
        mBackupUtils.activateBackupForUser(true, 0);
        mBackupUtils.setBackupTransportForUser(mBackupUtils.getLocalTransportName(), 0);

        // Check that the backup wasn't disabled and the transport wasn't switched unexpectedly.
        assertTrue(
                "Backup was unexpectedly disabled during the module test run",
                mBackupUtils.isBackupEnabled());
        assertEquals(
                "LocalTransport should be selected at this point",
                LOCAL_TRANSPORT,
                getCurrentTransport());
        mBackupUtils.wakeAndUnlockDevice();
    }

    @After
    public void tearDown() throws Exception {
        setSettingValue(
                GLOBAL_NAMESPACE,
                SETTINGS_DO_NOT_RESTORE_PRESERVED_SETTING_NAME,
                mOriginalFeatureFlagValue);
    }

    @Test
    public void testSettingsAreRestoredCorrectly() throws Exception {
        // 1. Set the CredMan settings before backup.
        setSecureSettingValue(AUTOFILL_SETTING_NAME, AUTOFILL_TEST_SERVICE);
        setSecureSettingValue(CREDMAN_SETTING_NAME, CREDMAN_TEST_SERVICE);
        setSecureSettingValue(CREDMAN_PRIMARY_SETTING_NAME, CREDMAN_TEST_PRIMARY_SERVICE);

        // 2. Run the backup.
        mBackupUtils.backupNowAndAssertSuccess(SETTINGS_PACKAGE);

        // 3. Clear the credman settings.
        getDevice().executeShellCommand("settings delete secure autofill_service");
        getDevice().executeShellCommand("settings delete secure credential_service");
        getDevice().executeShellCommand("settings delete secure credential_service_primary");

        // 4. Install & remove a test app. This will trigger some logic in Credential Manager
        // that will update the setting values.
        installPackage(TEST_APP_APK);
        assertThat(isPackageInstalled(TEST_APP_PACKAGE)).isTrue();
        uninstallPackage(TEST_APP_PACKAGE);

        // 5. Restore the backup.
        mBackupUtils.restoreAndAssertSuccess("1", SETTINGS_PACKAGE);

        // 6. Make sure the settings were not overridden.
        assertThat(getSecureSettingValue(AUTOFILL_SETTING_NAME)).isEqualTo(AUTOFILL_TEST_SERVICE);
        assertThat(getSecureSettingValue(CREDMAN_SETTING_NAME)).isEqualTo(CREDMAN_TEST_SERVICE);
        assertThat(getSecureSettingValue(CREDMAN_PRIMARY_SETTING_NAME))
                .isEqualTo(CREDMAN_TEST_PRIMARY_SERVICE);
    }

    @Test
    public void testSettingsAreNotRestoredIfUserHasChangedThem() throws Exception {
        // 1. Set the CredMan settings before backup.
        setSecureSettingValue(AUTOFILL_SETTING_NAME, AUTOFILL_TEST_SERVICE);
        setSecureSettingValue(CREDMAN_SETTING_NAME, CREDMAN_TEST_SERVICE);
        setSecureSettingValue(CREDMAN_PRIMARY_SETTING_NAME, CREDMAN_TEST_PRIMARY_SERVICE);

        // 2. Run the backup.
        mBackupUtils.backupNowAndAssertSuccess(SETTINGS_PACKAGE);

        // 3. Simulate the user changing the settings.
        setSecureSettingValue(AUTOFILL_SETTING_NAME, NEW_SETTINGS_VALUE);
        setSecureSettingValue(CREDMAN_SETTING_NAME, NEW_SETTINGS_VALUE);
        setSecureSettingValue(CREDMAN_PRIMARY_SETTING_NAME, NEW_SETTINGS_VALUE);

        // 4. Restore the backup.
        mBackupUtils.restoreAndAssertSuccess("1", SETTINGS_PACKAGE);

        // 5. Make sure the settings were not overridden.
        assertThat(getSecureSettingValue(AUTOFILL_SETTING_NAME)).isEqualTo(NEW_SETTINGS_VALUE);
        assertThat(getSecureSettingValue(CREDMAN_SETTING_NAME)).isEqualTo(NEW_SETTINGS_VALUE);
        assertThat(getSecureSettingValue(CREDMAN_PRIMARY_SETTING_NAME))
                .isEqualTo(NEW_SETTINGS_VALUE);
    }

    private String getSecureSettingValue(String name) throws Exception {
        return getSettingValue(SECURE_NAMESPACE, name);
    }

    private String getSettingValue(String namespace, String name) throws Exception {
        return getDevice()
                .executeShellCommand("settings get " + namespace + " " + name)
                .replace("\n", "");
    }

    private void setSecureSettingValue(String name, String value) throws Exception {
        setSettingValue(SECURE_NAMESPACE, name, value);
    }

    private void setSettingValue(String namespace, String name, String value) throws Exception {
        getDevice().executeShellCommand("settings put " + namespace + " " + name + " " + value);
    }

    protected String getCurrentTransport() throws DeviceNotAvailableException {
        String output = getDevice().executeShellCommand("bmgr list transports");
        Pattern pattern = Pattern.compile("\\* (.*)");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("non-parsable output setting bmgr transport: " + output);
        }
    }

    static InputStream executeDeviceShellCommand(ITestDevice device, String command)
            throws IOException {
        try {
            String result = device.executeShellCommand(command);
            return new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8));
        } catch (DeviceNotAvailableException e) {
            throw new IOException(e);
        }
    }

    private static final class RequiredFeatureRule implements TestRule {

        private final ITestInformationReceiver mReceiver;
        private final String mFeature;

        RequiredFeatureRule(ITestInformationReceiver receiver, String feature) {
            mReceiver = receiver;
            mFeature = feature;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    boolean hasFeature = false;
                    try {
                        hasFeature =
                                mReceiver.getTestInformation().getDevice().hasFeature(mFeature);
                    } catch (DeviceNotAvailableException e) {
                        CLog.e("Could not check if device has feature %s: %e", mFeature, e);
                        return;
                    }

                    if (!hasFeature) {
                        CLog.d(
                                "skipping %s#%s" + " because device does not have feature '%s'",
                                description.getClassName(), description.getMethodName(), mFeature);
                        throw new AssumptionViolatedException(
                                "Device does not have feature '" + mFeature + "'");
                    }
                    base.evaluate();
                }
            };
        }

        @Override
        public String toString() {
            return "RequiredFeatureRule[" + mFeature + "]";
        }
    }
}
