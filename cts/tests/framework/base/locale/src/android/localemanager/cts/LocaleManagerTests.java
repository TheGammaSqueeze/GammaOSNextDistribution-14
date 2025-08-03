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

package android.localemanager.cts;

import static android.localemanager.cts.util.LocaleConstants.APP_RECEIVER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.CALLING_PACKAGE;
import static android.localemanager.cts.util.LocaleConstants.DEFAULT_APP_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.DEFAULT_SYSTEM_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.EXTRA_QUERY_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.EXTRA_SET_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.IME_APP_CREATION_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.IME_APP_MAIN_ACTIVITY;
import static android.localemanager.cts.util.LocaleConstants.IME_APP_PACKAGE;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_APP_BROADCAST_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_APP_BROADCAST_RECEIVER;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_APP_CREATION_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_APP_MAIN_ACTIVITY;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_PACKAGE;
import static android.localemanager.cts.util.LocaleConstants.NON_EXISTENT_PACKAGE;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_BROADCAST_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_BROADCAST_RECEIVER;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_CONFIG_CHANGED_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_CREATION_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_MAIN_ACTIVITY;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_PACKAGE;
import static android.server.wm.CliIntentExtra.extraString;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Activity;
import android.app.LocaleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.LocaleList;
import android.server.wm.ActivityManagerTestBase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link android.app.LocaleManager} API(s).
 *
 * Build/Install/Run: atest CtsLocaleManagerTestCases
 */
@RunWith(AndroidJUnit4.class)
public class LocaleManagerTests extends ActivityManagerTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static Context sContext;
    private static LocaleManager sLocaleManager;

    /* System locales that were set on the device prior to running tests */
    private static LocaleList sPreviousSystemLocales;

    /* Receiver to listen to the broadcast in the calling (instrumentation) app. */
    private BlockingBroadcastReceiver mCallingAppBroadcastReceiver;

    /* Receiver to listen to the response from the test app's broadcast receiver. */
    private BlockingBroadcastReceiver mTestAppBroadcastInfoProvider;

    /* Receiver to listen to the response from the test app's activity. */
    private BlockingBroadcastReceiver mTestAppCreationInfoProvider;

    /* Receiver to listen to the response from the test app's onConfigChanged method. */
    private BlockingBroadcastReceiver mTestAppConfigChangedInfoProvider;

    /* Receiver to listen to the response from the installer app. */
    private BlockingBroadcastReceiver mInstallerBroadcastInfoProvider;

    /* Receiver to listen to the response from the installer app's activity. */
    private BlockingBroadcastReceiver mInstallerAppCreationInfoProvider;

    /* Receiver to listen to the response from the ime app's activity. */
    private BlockingBroadcastReceiver mImeAppCreationInfoProvider;

    /* Receiver to listen to the response of the active IME's change. */
    private BlockingBroadcastReceiver mImeChangedBroadcastReceiver;

    private boolean mNeedsImeReset = false;
    private String mTestIme = new ComponentName(IME_APP_PACKAGE,
            IME_APP_PACKAGE + ".TestIme").flattenToShortString();

    @BeforeClass
    public static void setUpClass() {
        sContext = InstrumentationRegistry.getTargetContext();
        sLocaleManager = sContext.getSystemService(LocaleManager.class);

        sPreviousSystemLocales = sLocaleManager.getSystemLocales();
        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setSystemLocales(DEFAULT_SYSTEM_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION, Manifest.permission.WRITE_SETTINGS);
    }

    @AfterClass
    public static void tearDownClass() {
        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setSystemLocales(sPreviousSystemLocales),
                Manifest.permission.CHANGE_CONFIGURATION, Manifest.permission.WRITE_SETTINGS);
    }

    @Before
    public void setUp() throws Exception {
        // Unlocks the device if locked, since we have tests where the app/activity needs
        // to be in the foreground/background.
        super.setUp();

        resetAppLocalesAsEmpty();
        AmUtils.waitForBroadcastIdle();

        mCallingAppBroadcastReceiver = new BlockingBroadcastReceiver();
        mTestAppBroadcastInfoProvider = new BlockingBroadcastReceiver();
        mInstallerBroadcastInfoProvider = new BlockingBroadcastReceiver();
        mTestAppCreationInfoProvider = new BlockingBroadcastReceiver();
        mTestAppConfigChangedInfoProvider = new BlockingBroadcastReceiver();
        mInstallerAppCreationInfoProvider = new BlockingBroadcastReceiver();
        mImeAppCreationInfoProvider = new BlockingBroadcastReceiver();
        mImeChangedBroadcastReceiver = new BlockingBroadcastReceiver();


        sContext.registerReceiver(mCallingAppBroadcastReceiver,
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        sContext.registerReceiver(mTestAppBroadcastInfoProvider,
                new IntentFilter(TEST_APP_BROADCAST_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        sContext.registerReceiver(mInstallerBroadcastInfoProvider,
                new IntentFilter(INSTALLER_APP_BROADCAST_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        sContext.registerReceiver(mTestAppCreationInfoProvider,
                new IntentFilter(TEST_APP_CREATION_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        sContext.registerReceiver(mTestAppConfigChangedInfoProvider,
                new IntentFilter(TEST_APP_CONFIG_CHANGED_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        sContext.registerReceiver(mInstallerAppCreationInfoProvider,
                new IntentFilter(INSTALLER_APP_CREATION_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        sContext.registerReceiver(mImeAppCreationInfoProvider,
                new IntentFilter(IME_APP_CREATION_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        sContext.registerReceiver(mImeChangedBroadcastReceiver,
                new IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED));

        setInstallerForPackage(CALLING_PACKAGE);
        setInstallerForPackage(TEST_APP_PACKAGE);

        bindToBroadcastReceiverOfApp(TEST_APP_PACKAGE, TEST_APP_BROADCAST_RECEIVER);
        bindToBroadcastReceiverOfApp(INSTALLER_PACKAGE, INSTALLER_APP_BROADCAST_RECEIVER);

        resetReceivers();
    }

    @After
    public void tearDown() throws Exception {
        unRegisterReceiver(mCallingAppBroadcastReceiver);
        unRegisterReceiver(mTestAppBroadcastInfoProvider);
        unRegisterReceiver(mInstallerBroadcastInfoProvider);
        unRegisterReceiver(mTestAppCreationInfoProvider);
        unRegisterReceiver(mInstallerAppCreationInfoProvider);
        unRegisterReceiver(mImeAppCreationInfoProvider);
        unRegisterReceiver(mImeChangedBroadcastReceiver);
        resetImes();
    }

    private void unRegisterReceiver(BlockingBroadcastReceiver receiver) {
        if (receiver != null) {
            sContext.unregisterReceiver(receiver);
            receiver = null;
        }
    }

    /**
     * Send broadcast to given app's receiver with flag {@link Intent#FLAG_INCLUDE_STOPPED_PACKAGES}
     * and wait for it to be received.
     *
     * <p/> This is required since app is in stopped state after installation by tradefed,
     * and the receivers in another apps don't receive the broadcast without
     * FLAG_INCLUDE_STOPPED_PACKAGES because they have never been opened even once
     * after installation.
     * By doing this we make sure that app is un-stopped when the system sends the broadcasts
     * in the tests.
     */
    private void bindToBroadcastReceiverOfApp(String packageName, String broadcastReceiver) {
        final Intent intent = new Intent(APP_RECEIVER_ACTION)
                .setComponent(new ComponentName(packageName, broadcastReceiver))
                .setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        CountDownLatch latch = new CountDownLatch(1);
        sContext.sendOrderedBroadcast(
                intent,
                null /* receiverPermission */,
                new BroadcastReceiver() { /* resultReceiver */
                    @Override public void onReceive(Context context, Intent intent) {
                        latch.countDown();
                    }
                },
                null /* scheduler */,
                Activity.RESULT_OK, /* initialCode */
                null /* initialData */,
                null /* initialExtras */);
        try {
            assertTrue("Timed out waiting for test broadcast to be received",
                    latch.await(5_000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
    }

    /**
     * Sets the installer as {@link #INSTALLER_PACKAGE} for the target package.
     */
    private void setInstallerForPackage(String targetPackageName) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sContext.getPackageManager(),
                (pm) -> pm.setInstallerPackageName(targetPackageName, INSTALLER_PACKAGE));
    }

    /**
     * Resets the countdown latch in all the receivers.
     */
    private void resetReceivers() {
        mCallingAppBroadcastReceiver.reset();
        mInstallerBroadcastInfoProvider.reset();
        mTestAppBroadcastInfoProvider.reset();
        mTestAppCreationInfoProvider.reset();
        mTestAppConfigChangedInfoProvider.reset();
        mInstallerAppCreationInfoProvider.reset();
        mImeAppCreationInfoProvider.reset();
        mImeChangedBroadcastReceiver.reset();
    }

    @Test
    public void testSetApplicationLocales_persistsAndSendsBroadcast() throws Exception {
        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);

        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);
        assertTrue(mCallingAppBroadcastReceiver.await());
        assertReceivedBroadcastContains(mCallingAppBroadcastReceiver,
                CALLING_PACKAGE, DEFAULT_APP_LOCALES);

        assertTrue(mInstallerBroadcastInfoProvider.await());
        assertReceivedBroadcastContains(mInstallerBroadcastInfoProvider,
                CALLING_PACKAGE, DEFAULT_APP_LOCALES);
    }

    @Test
    public void testSetApplicationLocales_resetToEmptyLocales_persistsAndSendsBroadcast()
            throws Exception {
        // First set the locales to non-empty
        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);
        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);
        assertTrue(mCallingAppBroadcastReceiver.await());
        assertReceivedBroadcastContains(mCallingAppBroadcastReceiver,
                CALLING_PACKAGE, DEFAULT_APP_LOCALES);
        mCallingAppBroadcastReceiver.reset();

        sLocaleManager.setApplicationLocales(LocaleList.getEmptyLocaleList());

        assertLocalesCorrectlySetForCallingApp(LocaleList.getEmptyLocaleList());
        mCallingAppBroadcastReceiver.await();
        assertReceivedBroadcastContains(mCallingAppBroadcastReceiver,
                CALLING_PACKAGE, LocaleList.getEmptyLocaleList());
    }

    @Test
    public void testSetApplicationLocales_sameLocalesEmpty_noBroadcastSent() throws Exception {
        // At the start of the test, no app-specific locales are set, so just try to set it to
        //   empty again
        sLocaleManager.setApplicationLocales(LocaleList.getEmptyLocaleList());

        assertLocalesCorrectlySetForCallingApp(LocaleList.getEmptyLocaleList());
        mCallingAppBroadcastReceiver.assertNoBroadcastReceived();
    }

    @Test
    public void testSetApplicationLocales_sameLocalesNonEmpty_noBroadcastSent() throws Exception {
        // First set the locales to non-empty
        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);
        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);
        assertTrue(mCallingAppBroadcastReceiver.await());
        assertReceivedBroadcastContains(mCallingAppBroadcastReceiver,
                CALLING_PACKAGE, DEFAULT_APP_LOCALES);
        mCallingAppBroadcastReceiver.reset();

        // Then reset to the same app-specific locales, and verify no broadcasts are sent
        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);

        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);
        mCallingAppBroadcastReceiver.assertNoBroadcastReceived();
    }

    @Test
    public void testSetApplicationLocales_getDefaultLocaleList_returnsCorrectList()
            throws Exception {
        // Fetch the current system locales when there are no app-locales set.
        LocaleList systemLocales = LocaleList.getDefault();
        assertEquals(DEFAULT_SYSTEM_LOCALES, systemLocales);

        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);

        // Wait for a while since LocaleList::getDefault could take a while to
        // reflect the new app locales.
        assertTrue(mCallingAppBroadcastReceiver.await());
        assertEquals(combineLocales(DEFAULT_APP_LOCALES, systemLocales), LocaleList.getDefault());
    }

    @Test
    public void testSetApplicationLocales_forAnotherAppInForeground_persistsAndSendsBroadcast()
            throws Exception {
        // Bring the TestApp to the foreground by invoking an activity and verify its visibility.
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        mWmState.assertVisibility(TEST_APP_MAIN_ACTIVITY, /* visible*/ true);

        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);
        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

        assertTrue(mTestAppBroadcastInfoProvider.await());
        assertReceivedBroadcastContains(mTestAppBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);

        mInstallerBroadcastInfoProvider.await();
        assertReceivedBroadcastContains(mInstallerBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);
    }

    @Test
    public void testSetApplicationLocales_forAnotherAppInBackground_persistsAndSendsBroadcast()
            throws Exception {
        // Invoke the app by launching an activity.
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        // Send the TestApp to the background.
        launchHomeActivity();
        mWmState.waitAndAssertVisibilityGone(TEST_APP_MAIN_ACTIVITY);

        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);

        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

        assertTrue(mTestAppBroadcastInfoProvider.await());
        assertReceivedBroadcastContains(mTestAppBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);

        assertTrue(mInstallerBroadcastInfoProvider.await());
        assertReceivedBroadcastContains(mInstallerBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);
    }

    @Test
    public void testSetApplicationLocales_forAnotherApp_persistsOnAppRestart() throws Exception {
        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);

        // Re-start the app by starting an activity and check if locales correctly
        // received by the app and listen to the broadcast for result from the app.
        launchActivity(TEST_APP_MAIN_ACTIVITY, extraString(EXTRA_QUERY_LOCALES, "true"));

        assertTrue(mTestAppCreationInfoProvider.await());
        assertReceivedBroadcastContains(mTestAppCreationInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);
    }

    @Test
    public void
            testSetApplicationLocales_wthoutPermissionforAnotherApp_throwsExceptionAndNoBroadcast()
            throws Exception {
        try {
            sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);
        } catch (SecurityException e) {
            // expected as not having appropriate permission to change locales for another app.
        }

        // Since the locales weren't allowed to persist, no broadcasts should be sent by the system.
        mTestAppBroadcastInfoProvider.assertNoBroadcastReceived();
        mInstallerBroadcastInfoProvider.assertNoBroadcastReceived();
    }

    @Test
    public void testSetApplicationLocales_forAnotherAppInForeground_callsOnConfigChanged()
            throws Exception {
        // Bring the TestApp to the foreground by invoking an activity and verify its visibility.
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        mWmState.assertVisibility(TEST_APP_MAIN_ACTIVITY, /* visible*/ true);

        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);
        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

        assertTrue(mTestAppConfigChangedInfoProvider.await());
        assertReceivedBroadcastContains(mTestAppConfigChangedInfoProvider, TEST_APP_PACKAGE,
                combineLocales(DEFAULT_APP_LOCALES, DEFAULT_SYSTEM_LOCALES));
    }

    @Test
    public void testSetApplicationLocales_withReadAppSpecificLocalesPermission_receivesBroadcast()
            throws Exception {

        BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                new BlockingBroadcastReceiver();
        sContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));

        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        //   so that we receive it
        runWithShellPermissionIdentity(() -> {
            // Tell the test app to change its app-specific locales
            launchActivity(TEST_APP_MAIN_ACTIVITY, extraString(EXTRA_SET_LOCALES,
                    DEFAULT_APP_LOCALES.toLanguageTags()));
            assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

            appSpecificLocaleBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        assertReceivedBroadcastContains(appSpecificLocaleBroadcastReceiver,
                TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);
    }


    @Test
    public void testSetApplicationLocales_appWithoutPermission_noBroadcastsReceived()
            throws Exception {

        BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                new BlockingBroadcastReceiver();
        sContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));

        // Tell the test app to change its app-specific locales
        launchActivity(TEST_APP_MAIN_ACTIVITY, extraString(EXTRA_SET_LOCALES,
                DEFAULT_APP_LOCALES.toLanguageTags()));
        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

        // Ensure that no broadcasts were received since the change was for another app (the Test
        //   App) and we are neither the Test App's installer, nor do we hold
        //   Manifest.permission.READ_APP_SPECIFIC_LOCALES
        appSpecificLocaleBroadcastReceiver.assertNoBroadcastReceived();
        mCallingAppBroadcastReceiver.assertNoBroadcastReceived();
    }

    @Test
    public void testGetApplicationLocales_callerIsInstaller_returnsLocales() throws Exception {
        // Set locales for calling app
        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);

        // Make sure that locales were set for the app.
        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);
        // Tell the installer app to fetch locales for calling package.
        launchActivity(INSTALLER_APP_MAIN_ACTIVITY,
                extraString(EXTRA_QUERY_LOCALES, CALLING_PACKAGE));

        assertTrue(mInstallerAppCreationInfoProvider.await());
        assertReceivedBroadcastContains(mInstallerAppCreationInfoProvider,
                CALLING_PACKAGE, DEFAULT_APP_LOCALES);
    }

    @Test
    public void testGetApplicationLocales_nonCurrentIme_noBroadcastReceived()
            throws Exception {
        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);

        // Make sure that locales were set for the app.
        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);

        // Tell the IME app to fetch locales for the test app.
        launchActivity(IME_APP_MAIN_ACTIVITY,
                extraString(EXTRA_QUERY_LOCALES, CALLING_PACKAGE));

        // Since the locales weren't allowed to get by none current IME app, no broadcast was sent
        // by test IME
        mImeAppCreationInfoProvider.assertNoBroadcastReceived();
    }

    @Test
    public void testGetApplicationLocales_currentImeGetNonForegroundAppLocales_noBroadcastReceived()
            throws Exception {
        //Set the IME app as the active IME.
        setTestImeAsActive();

        // Invoke the app by launching an activity.
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        // Send the TestApp to the background.
        launchHomeActivity();
        mWmState.waitAndAssertVisibilityGone(TEST_APP_MAIN_ACTIVITY);

        // Set test app's locales
        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);
        // Tell the IME app to fetch locales for the background app.
        launchActivity(IME_APP_MAIN_ACTIVITY,
                extraString(EXTRA_QUERY_LOCALES, TEST_APP_PACKAGE));

        // Since it is not allowed for the current IME app to get locales of non-foreground app, no
        // broadcast was sent by test IME
        mImeAppCreationInfoProvider.assertNoBroadcastReceived();
    }

    @Test
    public void testGetApplicationLocales_currentImeGetForegroundAppLocales_returnsLocales()
            throws Exception {
        //Set the IME app as the active IME.
        setTestImeAsActive();

        //Set app locales
        sLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);
        // Tell the IME app to fetch locales for the foreground app.
        launchActivity(IME_APP_MAIN_ACTIVITY,
                extraString(EXTRA_QUERY_LOCALES, CALLING_PACKAGE));

        assertTrue(mImeAppCreationInfoProvider.await());
        assertReceivedBroadcastContains(mImeAppCreationInfoProvider, CALLING_PACKAGE,
                DEFAULT_APP_LOCALES);
    }
    @Test(expected = SecurityException.class)
    public void testGetApplicationLocales_withoutPermissionforAnotherApp_throwsException()
            throws Exception {
        sLocaleManager.getApplicationLocales(TEST_APP_PACKAGE);
        fail("Expected SecurityException due to not having appropriate permission "
                + "for querying locales of another app.");
    }

    @Test
    public void testGetApplicationLocales_noAppLocalesSet_returnsEmptyList() {
        // When app-specific locales aren't set, we expect get api to return empty list
        // and not throw any error.
        assertEquals(LocaleList.getEmptyLocaleList(), sLocaleManager.getApplicationLocales());
    }

    @Test(expected = NullPointerException.class)
    public void testGetApplicationLocales_nullPackageName_throwsNPE() {
        sLocaleManager.getApplicationLocales(/* appPackageName= */ null);
        fail("Expected NullPointerException due to null package name argument.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetApplicationLocales_invalidPackageName_throwsIllegalArgumentException() {
        sLocaleManager.getApplicationLocales(NON_EXISTENT_PACKAGE);
        fail("Expected IllegalArgumentException due to invalid package.");
    }

    @Test(expected = NullPointerException.class)
    public void testSetApplicationLocales_nullPackageName_throwsNPE() {
        sLocaleManager.setApplicationLocales(/* appPackageName= */ null, DEFAULT_APP_LOCALES);
        fail("Expected NullPointerException due to null package name argument.");
    }

    @Test(expected = NullPointerException.class)
    public void testSetApplicationLocales_nullLocales_throwsNPE() {
        sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, /* locales= */ null);
        fail("Expected NullPointerException due to null locales argument.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetApplicationLocales_invalidPackageName_throwsIllegalArgumentException() {
        sLocaleManager.setApplicationLocales(NON_EXISTENT_PACKAGE, DEFAULT_APP_LOCALES);
        fail("Expected IllegalArgumentException due to invalid package.");
    }

    /**
     * Verifies that the locales are correctly set for calling(instrumentation) app
     * by fetching locales of the app with a binder call.
     */
    private void assertLocalesCorrectlySetForCallingApp(LocaleList expectedLocales) {
        assertEquals(expectedLocales, sLocaleManager.getApplicationLocales());
    }

    /**
     * Verifies that the locales are correctly set for another package
     * by fetching locales of the app with a binder call.
     */
    private void assertLocalesCorrectlySetForAnotherApp(String packageName,
            LocaleList expectedLocales) throws Exception {
        assertEquals(expectedLocales, getApplicationLocales(packageName));
    }

    private LocaleList getApplicationLocales(String packageName) throws Exception {
        return callWithShellPermissionIdentity(() ->
                sLocaleManager.getApplicationLocales(packageName),
                Manifest.permission.READ_APP_SPECIFIC_LOCALES);
    }

    /**
     * Verifies that the broadcast received in the relevant apps have the correct information
     * in the intent extras. It verifies the below extras:
     * <ul>
     * <li> {@link Intent#EXTRA_PACKAGE_NAME}
     * <li> {@link Intent#EXTRA_LOCALE_LIST}
     * </ul>
     */
    private void assertReceivedBroadcastContains(BlockingBroadcastReceiver receiver,
            String expectedPackageName, LocaleList expectedLocales) {
        assertEquals(expectedPackageName, receiver.getPackageName());
        assertEquals(expectedLocales, receiver.getLocales());
    }

    /**
     * Creates a combined {@link LocaleList} by placing app locales before system locales and
     * dropping any duplicates.
     *
     * We need to combine them since {@link LocaleList} does not have any direct way like
     * a normal list to check if locale or subset of locales is present.
     */
    private LocaleList combineLocales(LocaleList appLocales, LocaleList systemLocales) {
        Locale[] combinedLocales = new Locale[appLocales.size() + systemLocales.size()];
        for (int i = 0; i < appLocales.size(); i++) {
            combinedLocales[i] = appLocales.get(i);
        }
        for (int i = 0; i < systemLocales.size(); i++) {
            combinedLocales[i + appLocales.size()] = systemLocales.get(i);
        }
        // Constructor of {@link LocaleList} removes duplicates.
        return new LocaleList(combinedLocales);
    }

    /**
     * Reset the locales for the apps to empty list as they could have been set previously
     * by some other test.
     */
    private void resetAppLocalesAsEmpty() throws Exception {
        // Reset locales for the calling app.
        sLocaleManager.setApplicationLocales(LocaleList.getEmptyLocaleList());

        // Reset locales for the TestApp.
        runWithShellPermissionIdentity(() ->
                        sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE,
                                LocaleList.getEmptyLocaleList()),
                Manifest.permission.CHANGE_CONFIGURATION);
    }

    private void resetImes() throws Exception {
        if (mNeedsImeReset) {
            ShellUtils.runShellCommand("ime reset");
            mNeedsImeReset = false;
        }

        mImeChangedBroadcastReceiver.await();
        assertNotEquals("Should be reset to the default IME", mTestIme,
                mImeChangedBroadcastReceiver.getInputMethodId());
        mImeChangedBroadcastReceiver.reset();
    }

    private void setTestImeAsActive() throws Exception {
        if (sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            ShellUtils.runShellCommand("ime enable " + mTestIme + " --user 10");
            ShellUtils.runShellCommand("ime set " + mTestIme + " --user 10");
        } else {
            ShellUtils.runShellCommand("ime enable " + mTestIme);
            ShellUtils.runShellCommand("ime set " + mTestIme);
        }
        mNeedsImeReset = true;

        assertTrue(mImeChangedBroadcastReceiver.await());
        assertEquals(mTestIme, mImeChangedBroadcastReceiver.getInputMethodId());
        mImeChangedBroadcastReceiver.reset();
    }
}
