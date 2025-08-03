/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement.util;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@SmallTest
public final class ApplicationsTest {
    private static final String INSTALLED_PACKAGE_NAME = "test";
    private static final String NOT_INSTALLED_PACKAGE_NAME = "notinstalled";

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        ApplicationInfo installedApplicationInfo = new ApplicationInfo();
        installedApplicationInfo.packageName = INSTALLED_PACKAGE_NAME;
        when(mPackageManager.getApplicationInfo(INSTALLED_PACKAGE_NAME, 0))
                .thenReturn(installedApplicationInfo);
        when(mPackageManager.getApplicationInfo(NOT_INSTALLED_PACKAGE_NAME, 0))
                .thenThrow(new PackageManager.NameNotFoundException());
        if (SdkLevel.isAtLeastT()) {
            when(mPackageManager.getInstalledApplications(
                            any(PackageManager.ApplicationInfoFlags.class)))
                    .thenReturn(ImmutableList.of(installedApplicationInfo));
        } else {
            when(mPackageManager.getInstalledApplications(anyInt()))
                    .thenReturn(ImmutableList.of(installedApplicationInfo));
        }
    }

    @Test
    public void getCurrentInstalledApplicationsList() {
        assertThat(Applications.getCurrentInstalledApplicationsList(mContext))
                .containsExactly(Uri.parse("android-app://test"));
    }

    @Test
    public void anyAppsInstalled_returnsTrue() {
        assertThat(
                        Applications.anyAppsInstalled(
                                mContext,
                                List.of(
                                        Uri.parse("android-app://test"),
                                        Uri.parse("android-app://notinstalled"))))
                .isTrue();
    }

    @Test
    public void anyAppsInstalled_returnsFalse() {
        assertThat(
                        Applications.anyAppsInstalled(
                                mContext, List.of(Uri.parse("android-app://notinstalled"))))
                .isFalse();
    }
}
