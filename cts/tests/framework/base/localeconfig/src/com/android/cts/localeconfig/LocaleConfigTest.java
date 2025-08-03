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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.Context;
import android.os.LocaleList;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link android.app.LocaleConfig} API(s).
 *
 * Build/Install/Run: atest LocaleConfigTest
 */

@RunWith(AndroidJUnit4.class)
public class LocaleConfigTest extends ActivityManagerTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private static final String APK_PATH = "/data/local/tmp/cts/localeconfig/";
    private static final String APK_WITHOUT_LOCALECONFIG = APK_PATH + "ApkWithoutLocaleConfig.apk";
    private static final String TEST_PACKAGE = "com.android.cts.localeconfig";
    private static final String TEST_PACKAGE_WITHOUT_LOCALECONFIG =
            "com.android.cts.localeconfiginorout";
    private static final String EXTRA_SET_LOCALECONFIG = "set_localeconfig";
    private static final List<String> RESOURCE_LOCALES = Arrays.asList(
            new String[]{"en-US", "zh-Hant-TW", "pt-PT", "fr-FR", "zh-Hans-SG"});
    public static final LocaleList OVERRIDE_LOCALES =
            LocaleList.forLanguageTags("en-US,fr-FR,zh-Hant-TW");

    private Context mContext;
    private boolean mInstalled = false;
    private boolean mOverrided = false;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @After
    public void tearDown() throws Exception {
        cleanOverrideLocaleConfig();
        uninstall(TEST_PACKAGE_WITHOUT_LOCALECONFIG);
    }

    /**
     * Tests the scenario where a LocaleConfig of an application can be read successfully.
     */
    @Test
    public void testGetLocaleList() throws Exception {
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);

        assertEquals(RESOURCE_LOCALES.stream().sorted().collect(Collectors.toList()),
                new ArrayList<String>(Arrays.asList(
                        localeConfig.getSupportedLocales().toLanguageTags().split(
                                ","))).stream().sorted().collect(Collectors.toList()));

        assertEquals(LocaleConfig.STATUS_SUCCESS, localeConfig.getStatus());
    }

    /**
     * Tests that when a default locale is specified is gets returned correctly
     * @throws Exception
     */
    @Test
    @RequiresFlagsEnabled(android.content.res.Flags.FLAG_DEFAULT_LOCALE)
    public void testGetDefaultLocale() throws Exception {
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);

        assertEquals("en-US", localeConfig.getDefaultLocale().toLanguageTag());
    }

    /**
     * Tests the scenario where the correct status is returned when there is no LocaleConfig in the
     * application.
     */
    @Test
    public void testNoLocaleConfigTag() throws Exception {
        install(APK_WITHOUT_LOCALECONFIG);
        mInstalled = true;
        Context appContext = mContext.createPackageContext(TEST_PACKAGE_WITHOUT_LOCALECONFIG, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);

        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());
    }

    /**
     * Tests the scenario where the correct LocaleConfig is returned when an overridden
     * LocaleConfig has been set by the application.
     */
    @Test
    public void testGetOverrideLocaleConfig() throws Exception {
        // Override app-specific LocaleConfig
        LocaleManager localeManager = mContext.getSystemService(LocaleManager.class);
        localeManager.setOverrideLocaleConfig(new LocaleConfig(OVERRIDE_LOCALES));
        mOverrided = true;
        // Verify whether an override LocaleConfig can be read successfully
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig override = new LocaleConfig(appContext);

        assertEquals(0, override.describeContents());
        assertEquals(OVERRIDE_LOCALES, override.getSupportedLocales());
    }

    /**
     * Tests the scenario where the LocaleConfig from the application resources is returned when
     * an overridden LocaleConfig has been set by the application.
     */
    @Test
    public void testGetOriginalLocaleConfig() throws Exception {
        // Override app-specific LocaleConfig
        LocaleManager localeManager = mContext.getSystemService(LocaleManager.class);
        localeManager.setOverrideLocaleConfig(new LocaleConfig(OVERRIDE_LOCALES));
        mOverrided = true;
        // Verify whether an original LocaleConfig can be read successfully
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);

        assertEquals(RESOURCE_LOCALES.stream().sorted().collect(Collectors.toList()),
                new ArrayList<String>(Arrays.asList(
                        LocaleConfig.fromContextIgnoringOverride(
                                appContext).getSupportedLocales().toLanguageTags().split(
                                ","))).stream().sorted().collect(Collectors.toList()));
    }

    private void install(String apk) throws InterruptedException {
        String installResult = ShellUtils.runShellCommand("pm install -r " + apk);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    private void uninstall(String packageName) throws InterruptedException {
        if (mInstalled) {
            mInstalled = false;
            String uninstallResult = ShellUtils.runShellCommand("pm uninstall " + packageName);
            assertThat(uninstallResult.trim()).isEqualTo("Success");
        }
    }

    private void cleanOverrideLocaleConfig() {
        if (mOverrided) {
            mOverrided = false;
            LocaleManager localeManager = mContext.getSystemService(LocaleManager.class);
            localeManager.setOverrideLocaleConfig(null);
        }
    }
}

