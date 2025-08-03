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

package android.backup.cts;

import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.Manifest;
import android.app.LocaleManager;
import android.app.time.ExternalTimeSuggestion;
import android.app.time.TimeManager;
import android.app.time.cts.shell.DeviceConfigKeys;
import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.device.InstrumentationShellCommandExecutor;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.LocaleList;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull
public class AppLocalesBackupTest extends BaseBackupCtsTest {
    private static final String APK_PATH = "/data/local/tmp/cts/backup/";
    private static final String TEST_APP_APK_1 = APK_PATH + "CtsAppLocalesBackupApp1.apk";
    private static final String TEST_APP_PACKAGE_1 =
            "android.cts.backup.applocalesbackupapp1";

    private static final String TEST_APP_APK_2 = APK_PATH + "CtsAppLocalesBackupApp2.apk";
    private static final String TEST_APP_PACKAGE_2 =
            "android.cts.backup.applocalesbackupapp2";
    private static final String SYSTEM_PACKAGE = "android";

    private static final LocaleList DEFAULT_LOCALES_1 = LocaleList.forLanguageTags("hi-IN,de-DE");
    private static final LocaleList DEFAULT_LOCALES_2 = LocaleList.forLanguageTags("fr-CA");
    private static final LocaleList EMPTY_LOCALES = LocaleList.getEmptyLocaleList();

    // An identifier for the backup dataset. Since we're using localtransport, it's set to "1".
    private static final String RESTORE_TOKEN = "1";

    private static final Duration RETENTION_PERIOD = Duration.ofDays(3);

    private static final String SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED =
            "cmd time_detector is_auto_detection_enabled";

    private Context mContext;
    private LocaleManager mLocaleManager;
    private boolean mOriginalAutoTime;
    private DeviceShellCommandExecutor mShellCommandExecutor;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mContext = InstrumentationRegistry.getTargetContext();
        mLocaleManager = mContext.getSystemService(LocaleManager.class);
        mShellCommandExecutor = new InstrumentationShellCommandExecutor(
                InstrumentationRegistry.getInstrumentation().getUiAutomation());

        mOriginalAutoTime = isAutoDetectionEnabled(mShellCommandExecutor);
        // Auto time needs to be enabled to be able to suggest external time
        if (!mOriginalAutoTime) {
            assertTrue(setAutoTimeEnabled(/*enabled*/ true, mShellCommandExecutor));
        }

        install(TEST_APP_APK_1);
        install(TEST_APP_APK_2);
    }

    @After
    public void tearDown() throws Exception {
        // reset auto time to its original value
        if (!mOriginalAutoTime) {
            setAutoTimeEnabled(/*enabled*/ false, mShellCommandExecutor);
        }

        uninstall(TEST_APP_PACKAGE_1);
        uninstall(TEST_APP_PACKAGE_2);
    }

    /**
     * Tests the scenario where all apps are installed on the device when restore is triggered.
     *
     * <p>In this case, all the apps should have their locales restored as soon as the restore
     * operation finishes. The only condition is that the apps should not have the locales set
     * already before restore.
     */
    public void testBackupRestore_allAppsInstalledNoAppLocalesSet_restoresImmediately()
            throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        resetAppLocales();

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        assertLocalesForApp(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);
    }

    /**
     * Tests the scenario where the user sets the app-locales before the restore could be applied.
     *
     * <p>The locales from the backup data should be ignored in this case.
     */
    public void testBackupRestore_localeAlreadySet_doesNotRestore() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        LocaleList newLocales = LocaleList.forLanguageTags("zh,hi");
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, newLocales);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_2, EMPTY_LOCALES);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // Should restore only for app_2.
        assertLocalesForApp(TEST_APP_PACKAGE_1, newLocales);
        assertLocalesForApp(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);
    }

    /**
     * Tests the scenario when some apps are installed after the restore finishes.
     *
     * <p>More specifically, this tests the lazy restore where the locales are fetched and
     * restored from the stage file if the app is installed within a certain amount of time after
     * the initial restore.
     */
    public void testBackupRestore_appInstalledAfterRestore_doesLazyRestore() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        resetAppLocales();

        uninstall(TEST_APP_PACKAGE_2);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // Locales for App1 should be restored immediately since that's present already.
        assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);

        // This is to ensure there are no lingering broadcasts (could be from the setUp method
        // where we are calling setApplicationLocales).
        AmUtils.waitForBroadcastIdle();

        BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                new BlockingBroadcastReceiver();
        mContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));

        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        // so that we receive it.
        runWithShellPermissionIdentity(() -> {
            // Installation will trigger lazy restore, which internally calls setApplicationLocales
            // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
            install(TEST_APP_APK_2);
            appSpecificLocaleBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        appSpecificLocaleBroadcastReceiver.assertOneBroadcastReceived();
        appSpecificLocaleBroadcastReceiver.assertReceivedBroadcastContains(TEST_APP_PACKAGE_2,
                DEFAULT_LOCALES_2);

        // Verify that lazy restore occurred upon package install.
        assertLocalesForApp(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);

        // APP2's entry is removed from the stage file after restore so nothing should be restored
        // when APP2 is installed for the second time.
        uninstall(TEST_APP_PACKAGE_2);
        install(TEST_APP_APK_2);
        assertLocalesForApp(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
    }

    /**
     * Tests the scenario when an application is removed from the device.
     *
     * <p>The data for the uninstalled app should be removed from the next backup pass.
     */
    public void testBackupRestore_uninstallApp_deletesDataFromBackup() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        // Uninstall an app and run the backup pass. The locales for the uninstalled app should
        // be removed from the backup.
        uninstall(TEST_APP_PACKAGE_2);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        getBackupUtils().backupNowAndAssertSuccess(SYSTEM_PACKAGE);

        install(TEST_APP_APK_2);
        // Remove app1's locales so that it can be restored.
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, EMPTY_LOCALES);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // Restores only app1's locales because app2's data is no longer present in the backup.
        assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        assertLocalesForApp(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
    }

    /**
     * Tests the scenario when backup pass is run after retention period has expired.
     *
     * <p>Stage data should be removed since retention period has expired.
     * <p><b>Note:</b>Manipulates device's system clock directly to simulate the passage of time.
     */
    public void testRetentionPeriod_backupPassAfterRetentionPeriod_removesStagedData()
            throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        uninstall(TEST_APP_PACKAGE_2);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // Locales for App1 should be restored immediately since that's present already.
        assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);

        DeviceConfigShellHelper deviceConfigShellHelper = new DeviceConfigShellHelper(
                mShellCommandExecutor);

        // This anticipates a future state where a generally applied target preparer may disable
        // device_config sync for all CTS tests: only suspend syncing if it isn't already suspended,
        // and only resume it if this test suspended it.
        DeviceConfigShellHelper.PreTestState deviceConfigPreTestState =
                deviceConfigShellHelper.setSyncModeForTest(
                        SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        TimeManager timeManager = mContext.getSystemService(TimeManager.class);
        assertNotNull(timeManager);

        // Capture clock values so that the system clock can be set back after the test.
        long startCurrentTimeMillis = System.currentTimeMillis();
        long elapsedRealtimeMillis = SystemClock.elapsedRealtime();

        try {

            // Set the time detector to only use ORIGIN_EXTERNAL.
            deviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                    DeviceConfigKeys.TimeDetector.KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE,
                    DeviceConfigKeys.TimeDetector.ORIGIN_EXTERNAL);
            sleepForAsyncOperation();

            // 1 second elapses after retention period.
            long afterRetentionPeriodMillis = Duration.ofMillis(startCurrentTimeMillis).plusMillis(
                    RETENTION_PERIOD.plusSeconds(1).toMillis()).toMillis();
            ExternalTimeSuggestion futureTimeSuggestion =
                    new ExternalTimeSuggestion(elapsedRealtimeMillis, afterRetentionPeriodMillis);

            runWithShellPermissionIdentity(() -> {
                timeManager.suggestExternalTime(futureTimeSuggestion);
            }, Manifest.permission.SUGGEST_EXTERNAL_TIME);
            sleepForAsyncOperation();

            // The suggestion should have been accepted so the system clock should have advanced.
            assertTrue(System.currentTimeMillis() >= afterRetentionPeriodMillis);

            // Run the backup pass now.
            getBackupUtils().backupNowAndAssertSuccess(SYSTEM_PACKAGE);
        } finally {

            // Now do our best to return the device to its original state.
            ExternalTimeSuggestion originalTimeSuggestion =
                    new ExternalTimeSuggestion(elapsedRealtimeMillis, startCurrentTimeMillis);
            runWithShellPermissionIdentity(() -> {
                timeManager.suggestExternalTime(originalTimeSuggestion);
            }, Manifest.permission.SUGGEST_EXTERNAL_TIME);
            sleepForAsyncOperation();

            deviceConfigShellHelper.restoreDeviceConfigStateForTest(deviceConfigPreTestState);
        }

        // We install the app after restoring the device time so that retention check during lazy
        // restore doesn't try to delete the stage data. Hence, ensuring that stage data is deleted
        // during the backup pass.
        BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                new BlockingBroadcastReceiver();
        mContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));

        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        // so that we receive it.
        runWithShellPermissionIdentity(() -> {
            // Installation will trigger lazy restore, which internally calls setApplicationLocales
            // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
            install(TEST_APP_APK_2);
            appSpecificLocaleBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        appSpecificLocaleBroadcastReceiver.assertNoBroadcastReceived();

        // Does not restore the locales on package install.
        assertLocalesForApp(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
    }

    /**
     * Tests the scenario when lazy restore happens after retention period has expired.
     *
     * <p>Stage data should be removed since retention period has expired.
     * <p><b>Note:</b>Manipulates device's system clock directly to simulate the passage of time.
     */
    public void testRetentionPeriod_lazyRestoreAfterRetentionPeriod_removesStagedData()
            throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        uninstall(TEST_APP_PACKAGE_1);
        uninstall(TEST_APP_PACKAGE_2);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);


        DeviceConfigShellHelper deviceConfigShellHelper = new DeviceConfigShellHelper(
                mShellCommandExecutor);

        // This anticipates a future state where a generally applied target preparer may disable
        // device_config sync for all CTS tests: only suspend syncing if it isn't already suspended,
        // and only resume it if this test suspended it.
        DeviceConfigShellHelper.PreTestState deviceConfigPreTestState =
                deviceConfigShellHelper.setSyncModeForTest(
                        SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        TimeManager timeManager = mContext.getSystemService(TimeManager.class);
        assertNotNull(timeManager);

        // Capture clock values so that the system clock can be set back after the test.
        long startCurrentTimeMillis = System.currentTimeMillis();
        long elapsedRealtimeMillis = SystemClock.elapsedRealtime();

        try {

            BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                    new BlockingBroadcastReceiver();
            mContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                    new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));

            // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
            // so that we receive it.
            runWithShellPermissionIdentity(() -> {
                // Installation will trigger lazy restore, which internally calls
                // setApplicationLocales
                // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
                install(TEST_APP_APK_1);
                appSpecificLocaleBroadcastReceiver.await();
            }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

            appSpecificLocaleBroadcastReceiver.assertOneBroadcastReceived();
            appSpecificLocaleBroadcastReceiver.assertReceivedBroadcastContains(TEST_APP_PACKAGE_1,
                    DEFAULT_LOCALES_1);
            assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
            appSpecificLocaleBroadcastReceiver.reset();

            // Set the time detector to only use ORIGIN_EXTERNAL.
            deviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                    DeviceConfigKeys.TimeDetector.KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE,
                    DeviceConfigKeys.TimeDetector.ORIGIN_EXTERNAL);
            sleepForAsyncOperation();

            // 1 second elapses after retention period.
            long afterRetentionPeriodMillis = Duration.ofMillis(startCurrentTimeMillis).plusMillis(
                    RETENTION_PERIOD.plusSeconds(1).toMillis()).toMillis();
            ExternalTimeSuggestion futureTimeSuggestion =
                    new ExternalTimeSuggestion(elapsedRealtimeMillis, afterRetentionPeriodMillis);

            runWithShellPermissionIdentity(() -> {
                timeManager.suggestExternalTime(futureTimeSuggestion);
            }, Manifest.permission.SUGGEST_EXTERNAL_TIME);
            sleepForAsyncOperation();

            // The suggestion should have been accepted so the system clock should have advanced.
            assertTrue(System.currentTimeMillis() >= afterRetentionPeriodMillis);

            // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
            // so that we receive it.
            runWithShellPermissionIdentity(() -> {
                // Installation will trigger lazy restore, which internally calls
                // setApplicationLocales
                // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
                install(TEST_APP_APK_2);
                appSpecificLocaleBroadcastReceiver.await();
            }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

            appSpecificLocaleBroadcastReceiver.assertNoBroadcastReceived();

            // Does not restore the locales on package install.
            assertLocalesForApp(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
        } finally {

            // Now do our best to return the device to its original state.
            ExternalTimeSuggestion originalTimeSuggestion =
                    new ExternalTimeSuggestion(elapsedRealtimeMillis, startCurrentTimeMillis);
            runWithShellPermissionIdentity(() -> {
                timeManager.suggestExternalTime(originalTimeSuggestion);
            }, Manifest.permission.SUGGEST_EXTERNAL_TIME);
            sleepForAsyncOperation();

            deviceConfigShellHelper.restoreDeviceConfigStateForTest(deviceConfigPreTestState);
        }
    }

    /**
     * Sleeps for a length of time sufficient to allow async operations to complete. Many time
     * manager APIs are or could be asynchronous and deal with time, so there are no practical
     * alternatives.
     */
    private static void sleepForAsyncOperation() throws Exception {
        Thread.sleep(5_000);
    }

    // TODO(b/210593602): Add a test to check staged data removal after the retention period.

    private void setApplicationLocalesAndVerify(String packageName, LocaleList locales)
            throws Exception {
        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setApplicationLocales(packageName, locales),
                Manifest.permission.CHANGE_CONFIGURATION);
        assertLocalesForApp(packageName, locales);
    }

    /**
     * Verifies that the locales are correctly set for another package
     * by fetching locales of the app with a binder call.
     */
    private void assertLocalesForApp(String packageName,
            LocaleList expectedLocales) throws Exception {
        assertEquals(expectedLocales, getApplicationLocales(packageName));
    }

    private LocaleList getApplicationLocales(String packageName) throws Exception {
        return callWithShellPermissionIdentity(() ->
                        mLocaleManager.getApplicationLocales(packageName),
                Manifest.permission.READ_APP_SPECIFIC_LOCALES);
    }

    private void install(String apk) {
        ShellUtils.runShellCommand("pm install -r " + apk);
    }

    private void uninstall(String packageName) {
        ShellUtils.runShellCommand("pm uninstall " + packageName);
    }

    private void setAndBackupDefaultAppLocales() throws Exception {
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);
        // Backup the data for SYSTEM_PACKAGE which includes app-locales.
        getBackupUtils().backupNowAndAssertSuccess(SYSTEM_PACKAGE);
    }

    private void resetAppLocales() throws Exception {
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, EMPTY_LOCALES);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
    }

    private boolean isAutoDetectionEnabled(
            DeviceShellCommandExecutor shellCommandExecutor) throws Exception {
        return shellCommandExecutor.executeToBoolean(SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED);
    }

    private boolean setAutoTimeEnabled(
            boolean enabled, DeviceShellCommandExecutor shellCommandExecutor) throws Exception {
        // Android T does not have a dedicated shell command or API to change time auto detection
        // setting, so direct Settings changes are used.
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME,
                enabled ? 1 : 0);

        sleepForAsyncOperation();

        return isAutoDetectionEnabled(shellCommandExecutor) == enabled;
    }

    private static final class BlockingBroadcastReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private String mPackageName;
        private LocaleList mLocales;
        private int mCalls;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(Intent.EXTRA_PACKAGE_NAME)) {
                mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            }
            if (intent.hasExtra(Intent.EXTRA_LOCALE_LIST)) {
                mLocales = intent.getParcelableExtra(Intent.EXTRA_LOCALE_LIST);
            }
            mCalls += 1;
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await(/* timeout= */ 5, TimeUnit.SECONDS);
        }

        public void reset() {
            mLatch = new CountDownLatch(1);
            mCalls = 0;
            mPackageName = null;
            mLocales = null;
        }

        public void assertOneBroadcastReceived() {
            assertEquals(1, mCalls);
        }

        public void assertNoBroadcastReceived() {
            assertEquals(0, mCalls);
        }

        /**
         * Verifies that the broadcast received in the relevant apps have the correct information
         * in the intent extras. It verifies the below extras:
         * <ul>
         * <li> {@link Intent#EXTRA_PACKAGE_NAME}
         * <li> {@link Intent#EXTRA_LOCALE_LIST}
         * </ul>
         */
        public void assertReceivedBroadcastContains(String expectedPackageName,
                LocaleList expectedLocales) {
            assertEquals(expectedPackageName, mPackageName);
            assertEquals(expectedLocales, mLocales);
        }
    }
}
