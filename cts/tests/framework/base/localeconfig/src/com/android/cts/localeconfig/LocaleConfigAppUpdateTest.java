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

package com.android.cts.localeconfig;

import static android.server.wm.CliIntentExtra.extraString;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import android.Manifest;
import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.LocaleList;
import android.server.wm.ActivityManagerTestBase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link com.android.server.locales.LocaleManagerBackupHelper#onPackageUpdateFinished()}.
 *
 * Build/Install/Run: atest LocaleConfigAppUpdateTest
 */
@RunWith(AndroidJUnit4.class)
public class LocaleConfigAppUpdateTest extends ActivityManagerTestBase {
    private static final String APK_PATH = "/data/local/tmp/cts/localeconfig/";
    private static final String APK_WITH_LOCALECONFIG = APK_PATH + "ApkWithLocaleConfig.apk";
    private static final String APK_WITHOUT_LOCALECONFIG = APK_PATH + "ApkWithoutLocaleConfig.apk";
    private static final String APK_REMOVEAPPLOCALES_INLOCALECONFIG =
            APK_PATH + "ApkRemoveAppLocalesInLocaleConfig.apk";
    private static final String TEST_PACKAGE = "com.android.cts.localeconfiginorout";
    private static final LocaleList EMPTY_LOCALES = LocaleList.getEmptyLocaleList();

    private Context mContext;
    private LocaleManager mLocaleManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mLocaleManager = mContext.getSystemService(LocaleManager.class);
    }

    @After
    public void tearDown() throws Exception {
        uninstall(TEST_PACKAGE);
    }

    /**
     * Tests the scenario where the per-app locales are kept after the app upgrades.
     *
     * <p>If the per-app locales are set by a delegate selector and the same LocaleConfig is still
     * existed after the app upgrades, the per-app locales should be kept without clearing.
     */
    @Test
    public void testUpgradedApk_KeepLocaleConfig_keepAppLocales() throws Exception {
        install(APK_WITH_LOCALECONFIG);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        runWithShellPermissionIdentity(
                () -> mLocaleManager.setApplicationLocales(TEST_PACKAGE, expectedLocales),
                Manifest.permission.CHANGE_CONFIGURATION);
        Thread.sleep(1000);
        verifyLocalesCorrectlySetForAnotherApp(TEST_PACKAGE, expectedLocales);

        install(APK_WITH_LOCALECONFIG);
        LocaleList getAppLocales_afterUpgraded = callWithShellPermissionIdentity(() ->
                        mLocaleManager.getApplicationLocales(TEST_PACKAGE),
                Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        assertEquals(expectedLocales, getAppLocales_afterUpgraded);
    }

    /**
     * Tests the scenario where the per-app locales set from the app should be kept regardless of
     * whether there is a LocaleConfig before and after the app upgrades.
     *
     * <p>What we want to avoid is when the per-app locales are set from a delegate selector, and
     * the LocaleConfig is removed after the app upgrades, then previously set per-app locales can't
     * be changed. So if the per-app locales are set from the app in the same scenario, they will
     * still be kept without clearing.
     */
    @Test
    public void testUpgradedApk_setFromApp_KeepAppLocales() throws Exception {
        install(APK_WITHOUT_LOCALECONFIG);
        // Tell the app to set its app-specific locales
        launchActivity(new ComponentName(TEST_PACKAGE, TEST_PACKAGE + ".MainActivity"),
                extraString("set_locales", "fr"));
        Thread.sleep(1000);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        verifyLocalesCorrectlySetForAnotherApp(TEST_PACKAGE, expectedLocales);

        install(APK_WITHOUT_LOCALECONFIG);
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);
        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());

        LocaleList upgradedAppLocales = callWithShellPermissionIdentity(() ->
                        mLocaleManager.getApplicationLocales(TEST_PACKAGE),
                Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        assertEquals(expectedLocales, upgradedAppLocales);
    }

    /**
     * Tests the scenario where the per-app locales set from a delegate selector should be cleared
     * when the LocaleConfig is removed after the app upgrades.
     *
     * <p>What we want to avoid is when the per-app locales are set from a delegate selector, and
     * the LocaleConfig is removed after the app upgrades, then previously set per-app locales can't
     * be changed.
     */
    @Test
    public void testUpgradedApk_removeLocaleConfig_ClearAppLocales() throws Exception {
        install(APK_WITH_LOCALECONFIG);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        runWithShellPermissionIdentity(
                () -> mLocaleManager.setApplicationLocales(TEST_PACKAGE, expectedLocales),
                Manifest.permission.CHANGE_CONFIGURATION);
        Thread.sleep(1000);
        verifyLocalesCorrectlySetForAnotherApp(TEST_PACKAGE, expectedLocales);

        BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                new BlockingBroadcastReceiver();
        mContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));
        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        // so that we receive it.
        runWithShellPermissionIdentity(() -> {
            // Installation will clear per-app locale, which internally calls setApplicationLocales
            // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
            install(APK_WITHOUT_LOCALECONFIG);
            appSpecificLocaleBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        appSpecificLocaleBroadcastReceiver.assertReceivedBroadcastContains(TEST_PACKAGE,
                EMPTY_LOCALES);

        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);
        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());
    }

    /**
     * Tests the scenario where the per-app locales set from a delegate selector should be cleared
     * when the locales are removed from the LocaleConfig after the app upgrades.
     *
     * <p>What we want to avoid is when the per-app locales are set from a delegate selector, and
     * the locales are removed from the LocaleConfig after the app upgrades, it would confuse the
     * user since the removed locales are still shown to the user and the they are not present in
     * the delegate selector.
     */
    @Test
    public void testUpgradedApk_removeAppLocalesInLocaleConfig_ClearAppLocales() throws Exception {
        install(APK_WITH_LOCALECONFIG);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        runWithShellPermissionIdentity(
                () -> mLocaleManager.setApplicationLocales(TEST_PACKAGE, expectedLocales),
                Manifest.permission.CHANGE_CONFIGURATION);
        Thread.sleep(1000);
        verifyLocalesCorrectlySetForAnotherApp(TEST_PACKAGE, expectedLocales);

        BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                new BlockingBroadcastReceiver();
        mContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));
        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        // so that we receive it.
        runWithShellPermissionIdentity(() -> {
            // Installation will clear per-app locale, which internally calls setApplicationLocales
            // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
            install(APK_REMOVEAPPLOCALES_INLOCALECONFIG);
            appSpecificLocaleBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        appSpecificLocaleBroadcastReceiver.assertReceivedBroadcastContains(TEST_PACKAGE,
                EMPTY_LOCALES);
    }

    private void install(String apk) throws InterruptedException {
        String installResult = ShellUtils.runShellCommand("pm install -r " + apk);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    private void uninstall(String packageName) throws InterruptedException {
        String uninstallResult = ShellUtils.runShellCommand("pm uninstall " + packageName);
        assertThat(uninstallResult.trim()).isEqualTo("Success");
    }

    private void verifyLocalesCorrectlySetForAnotherApp(String packageName,
            LocaleList expectedLocales) throws Exception {
        LocaleList getAppLocales = callWithShellPermissionIdentity(
                () -> mLocaleManager.getApplicationLocales(packageName),
                Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        assertEquals(expectedLocales, getAppLocales);
    }

    private static final class BlockingBroadcastReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private String mPackageName;
        private LocaleList mLocales;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(Intent.EXTRA_PACKAGE_NAME)) {
                mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            }
            if (intent.hasExtra(Intent.EXTRA_LOCALE_LIST)) {
                mLocales = intent.getParcelableExtra(Intent.EXTRA_LOCALE_LIST);
            }
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await(/* timeout= */ 5, TimeUnit.SECONDS);
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
