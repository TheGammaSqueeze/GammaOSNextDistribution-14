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

package com.android.adservices.service.common.compat;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

public class PackageManagerCompatUtilsTest {
    private static final String TEST_PACKAGE_NAME = "test";
    private MockitoSession mMockitoSession;

    @Mock private PackageManager mPackageManagerMock;
    @Mock private PackageInfo mPackageInfo;
    @Mock private ApplicationInfo mApplicationInfo;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testPackageManagerCompatUtilsValidatesArguments() {
        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getInstalledApplications(null, 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getInstalledPackages(null, 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getPackageUid(null, "com.example.app", 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, null, 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getApplicationInfo(null, "com.example.app", 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getApplicationInfo(mPackageManagerMock, null, 0));
    }

    @Test
    public void testGetInstalledApplications_SMinus() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(ImmutableList.of(mApplicationInfo))
                .when(mPackageManagerMock)
                .getInstalledApplications(anyInt());

        final int flags = PackageManager.MATCH_APEX;
        assertThat(PackageManagerCompatUtils.getInstalledApplications(mPackageManagerMock, flags))
                .isEqualTo(ImmutableList.of(mApplicationInfo));
        verify(mPackageManagerMock).getInstalledApplications(eq(flags));
    }

    @Test
    public void testGetInstalledApplications_TPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        doReturn(ImmutableList.of(mApplicationInfo))
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        assertThat(PackageManagerCompatUtils.getInstalledApplications(mPackageManagerMock, 0))
                .isEqualTo(ImmutableList.of(mApplicationInfo));
        verify(mPackageManagerMock, never()).getInstalledApplications(anyInt());
        verify(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));
    }

    @Test
    public void testGetInstalledPackages_SMinus() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(ImmutableList.of(mPackageInfo))
                .when(mPackageManagerMock)
                .getInstalledPackages(anyInt());

        final int flags = PackageManager.MATCH_APEX;
        assertThat(PackageManagerCompatUtils.getInstalledPackages(mPackageManagerMock, flags))
                .isEqualTo(ImmutableList.of(mPackageInfo));
        verify(mPackageManagerMock).getInstalledPackages(eq(flags));
    }

    @Test
    public void testGetInstalledPackages_TPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        doReturn(ImmutableList.of(mPackageInfo))
                .when(mPackageManagerMock)
                .getInstalledPackages(any(PackageManager.PackageInfoFlags.class));

        assertThat(PackageManagerCompatUtils.getInstalledPackages(mPackageManagerMock, 0))
                .isEqualTo(ImmutableList.of(mPackageInfo));
        verify(mPackageManagerMock, never()).getInstalledPackages(anyInt());
        verify(mPackageManagerMock)
                .getInstalledPackages(any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    public void testGetUidForPackage_SMinus() throws PackageManager.NameNotFoundException {
        doReturn(false).when(SdkLevel::isAtLeastT);
        final int packageUid = 100;
        doReturn(packageUid).when(mPackageManagerMock).getPackageUid(anyString(), anyInt());

        final int flags = PackageManager.MATCH_APEX;
        final String packageName = "com.example.package";
        assertThat(PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, packageName, flags))
                .isEqualTo(packageUid);
        verify(mPackageManagerMock).getPackageUid(eq(packageName), eq(flags));
    }

    @Test
    public void testGetUidForPackage_TPlus() throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        final int packageUid = 100;
        doReturn(packageUid)
                .when(mPackageManagerMock)
                .getPackageUid(anyString(), any(PackageManager.PackageInfoFlags.class));

        final String packageName = "com.example.package";
        assertThat(PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, packageName, 0))
                .isEqualTo(packageUid);
        verify(mPackageManagerMock, never()).getPackageUid(anyString(), anyInt());
        verify(mPackageManagerMock)
                .getPackageUid(eq(packageName), any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    public void testGetApplicationInfo_SMinus() throws PackageManager.NameNotFoundException {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(mApplicationInfo)
                .when(mPackageManagerMock)
                .getApplicationInfo(anyString(), anyInt());

        final int flags = PackageManager.MATCH_APEX;
        final String packageName = "com.example.package";
        assertThat(
                        PackageManagerCompatUtils.getApplicationInfo(
                                mPackageManagerMock, packageName, flags))
                .isEqualTo(mApplicationInfo);
        verify(mPackageManagerMock).getApplicationInfo(eq(packageName), eq(flags));
    }

    @Test
    public void testGetApplicationInfo_TPlus() throws PackageManager.NameNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastT());

        doReturn(mApplicationInfo)
                .when(mPackageManagerMock)
                .getApplicationInfo(anyString(), any(PackageManager.ApplicationInfoFlags.class));

        final String packageName = "com.example.package";
        ApplicationInfo info =
                PackageManagerCompatUtils.getApplicationInfo(mPackageManagerMock, packageName, 0);
        assertThat(info).isEqualTo(mApplicationInfo);
        verify(mPackageManagerMock, never()).getApplicationInfo(anyString(), anyInt());
        verify(mPackageManagerMock)
                .getApplicationInfo(
                        eq(packageName), any(PackageManager.ApplicationInfoFlags.class));
    }

    @Test
    public void testIsAdServicesActivityEnabled_enabled()
            throws PackageManager.NameNotFoundException {
        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mockContext.getPackageName()).thenReturn(TEST_PACKAGE_NAME);

        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = TEST_PACKAGE_NAME;
        when(mPackageManagerMock.getPackageInfo(eq(packageInfo.packageName), eq(0)))
                .thenReturn(packageInfo);
        when(mPackageManagerMock.getComponentEnabledSetting(any(ComponentName.class)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        boolean isActivityEnabled =
                PackageManagerCompatUtils.isAdServicesActivityEnabled(mockContext);

        assertThat(isActivityEnabled).isTrue();
        verify(mPackageManagerMock, times(7)).getComponentEnabledSetting(any(ComponentName.class));
    }

    @Test
    public void testIsAdServicesActivityEnabled_disabled()
            throws PackageManager.NameNotFoundException {
        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mockContext.getPackageName()).thenReturn(TEST_PACKAGE_NAME);

        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = TEST_PACKAGE_NAME;
        when(mPackageManagerMock.getPackageInfo(eq(packageInfo.packageName), eq(0)))
                .thenReturn(packageInfo);
        when(mPackageManagerMock.getComponentEnabledSetting(any(ComponentName.class)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        boolean isActivityEnabled =
                PackageManagerCompatUtils.isAdServicesActivityEnabled(mockContext);

        assertThat(isActivityEnabled).isFalse();
        verify(mPackageManagerMock, times(1)).getComponentEnabledSetting(any(ComponentName.class));
    }
}
