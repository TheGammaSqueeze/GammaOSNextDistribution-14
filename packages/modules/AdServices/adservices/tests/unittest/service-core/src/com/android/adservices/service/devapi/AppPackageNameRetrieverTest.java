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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AppPackageNameRetrieverTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock private PackageManager mPackageManager;

    private AppPackageNameRetriever mAppPackageNameRetriever;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mAppPackageNameRetriever = new AppPackageNameRetriever(mPackageManager);
    }

    @Test
    public void testReturnsThePackageName() {
        int appUid = 100;
        String appPackage = "com.test.myapp";
        when(mPackageManager.getPackagesForUid(appUid)).thenReturn(new String[] {appPackage});

        assertThat(mAppPackageNameRetriever.getAppPackageNameForUid(appUid)).isEqualTo(appPackage);
    }

    @Test
    public void testReturnsFirstPackageNameIfMoreThanOneAvailable() {
        int appUid = 100;
        String expectedAppPackage = "com.test.myapp";
        when(mPackageManager.getPackagesForUid(appUid))
                .thenReturn(new String[] {expectedAppPackage, "com.test.otherapp"});

        assertThat(mAppPackageNameRetriever.getAppPackageNameForUid(appUid))
                .isEqualTo(expectedAppPackage);
    }

    @Test
    public void testThrowsExceptionIfNoPackageNameIsAvailable() {
        int appUid = 100;
        when(mPackageManager.getPackagesForUid(appUid)).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> mAppPackageNameRetriever.getAppPackageNameForUid(appUid));
    }

    @Test
    public void testGetCurrentAppName() {
        Context applicationContext = ApplicationProvider.getApplicationContext();
        AppPackageNameRetriever nonMockedInstance =
                AppPackageNameRetriever.create(applicationContext);

        assertThat(nonMockedInstance.getAppPackageNameForUid(Process.myUid()))
                .isEqualTo(applicationContext.getPackageName());
    }
}
