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

package com.android.adservices.service.devapi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.common.compat.BuildCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class DevContextFilterTest {
    private static final int APP_UID = 100;
    private static final String APP_PACKAGE = "com.test.myapp";
    private MockitoSession mStaticMockSession = null;

    @Mock private PackageManager mPackageManager;
    @Mock private AppPackageNameRetriever mAppPackageNameRetriever;
    @Mock private ContentResolver mContentResolver;

    private DevContextFilter mDevContextFilter;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Settings.Global.class)
                        .mockStatic(BuildCompatUtils.class)
                        .startMocking();
        MockitoAnnotations.initMocks(this);

        mDevContextFilter =
                new DevContextFilter(mContentResolver, mPackageManager, mAppPackageNameRetriever);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testCreateDevContextReturnsDevContextWithAppInfo()
            throws PackageManager.NameNotFoundException {
        enableDeveloperOptions();
        when(mAppPackageNameRetriever.getAppPackageNameForUid(APP_UID)).thenReturn(APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        assertThat(mDevContextFilter.createDevContext(APP_UID))
                .isEqualTo(
                        DevContext.builder()
                                .setCallingAppPackageName(APP_PACKAGE)
                                .setDevOptionsEnabled(true)
                                .build());
    }

    @Test
    public void testCreateDevContextReturnsDevDisabledInstanceIfDeviceIsNotInDeveloperMode() {
        disableDeveloperOptions();

        assertThat(mDevContextFilter.createDevContext(APP_UID).getDevOptionsEnabled()).isFalse();
    }

    @Test
    public void testCreateDevContextReturnsDevEnabledInstanceIfNotInDeveloperModeDebuggableBuild()
            throws PackageManager.NameNotFoundException {
        // No need to call disableDeveloperOptions since they wouldn't be checked because we are
        // in a debuggable build and Mockito would complain of the not necesasry mock.
        // Not preparing the mock would anyway cause the check method to return false.
        when(BuildCompatUtils.isDebuggable()).thenReturn(true);

        when(mAppPackageNameRetriever.getAppPackageNameForUid(APP_UID)).thenReturn(APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        assertThat(mDevContextFilter.createDevContext(APP_UID))
                .isEqualTo(
                        DevContext.builder()
                                .setCallingAppPackageName(APP_PACKAGE)
                                .setDevOptionsEnabled(true)
                                .build());
    }

    @Test
    public void testCreateDevContextReturnsDevDisabledInstanceIfAppIsNotDebuggable()
            throws PackageManager.NameNotFoundException {
        enableDeveloperOptions();
        when(mAppPackageNameRetriever.getAppPackageNameForUid(APP_UID)).thenReturn(APP_PACKAGE);
        mockInstalledApplications(aNonDebuggableAppInfo());
        assertThat(mDevContextFilter.createDevContext(APP_UID).getDevOptionsEnabled()).isFalse();
    }

    @Test
    public void testGetSettingsForCurrentApp() {
        Context applicationContext = ApplicationProvider.getApplicationContext();
        // Enable development options for the test app
        when(Settings.Global.getInt(
                        applicationContext.getContentResolver(),
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                        0))
                .thenReturn(1);

        DevContextFilter nonMockedFilter = DevContextFilter.create(applicationContext);
        assertThat(nonMockedFilter.createDevContext(Process.myUid()))
                .isEqualTo(
                        DevContext.builder()
                                .setCallingAppPackageName(applicationContext.getPackageName())
                                .setDevOptionsEnabled(true)
                                .build());
    }

    @Test
    public void testNoArgCallFailsIfCalledFromNonBinderThread() {
        Context applicationContext = ApplicationProvider.getApplicationContext();
        DevContextFilter nonMockedFilter = DevContextFilter.create(applicationContext);

        Assert.assertThrows(IllegalStateException.class, nonMockedFilter::createDevContext);
    }

    private void enableDeveloperOptions() {
        when(Settings.Global.getInt(
                        mContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .thenReturn(1);
    }

    private void disableDeveloperOptions() {
        when(Settings.Global.getInt(
                        mContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .thenReturn(0);
    }

    private ApplicationInfo aNonDebuggableAppInfo() {
        return new ApplicationInfo();
    }

    private ApplicationInfo aDebuggableAppInfo() {
        ApplicationInfo result = new ApplicationInfo();
        // Adding some extra flag to verify the check is done correctly
        result.flags = ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_INSTALLED;
        return result;
    }

    private void mockInstalledApplications(ApplicationInfo applicationInfo)
            throws PackageManager.NameNotFoundException {
        if (SdkLevel.isAtLeastT()) {
            when(mPackageManager.getApplicationInfo(
                            eq(APP_PACKAGE), any(PackageManager.ApplicationInfoFlags.class)))
                    .thenReturn(applicationInfo);
        } else {
            when(mPackageManager.getApplicationInfo(eq(APP_PACKAGE), anyInt()))
                    .thenReturn(applicationInfo);
        }
    }
}
