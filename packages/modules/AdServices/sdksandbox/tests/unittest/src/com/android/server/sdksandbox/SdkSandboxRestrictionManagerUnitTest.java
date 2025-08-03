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

package com.android.server.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
import android.os.Build;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.sdksandbox.helpers.PackageManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link SdkSandboxRestrictionManager}. */
public class SdkSandboxRestrictionManagerUnitTest {
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final int DEFAULT_TARGET_SDK_VERSION = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    private SdkSandboxRestrictionManager mSdkSandboxRestrictionManager;
    private PackageManagerHelper mPackageManagerHelper;
    private SdkSandboxRestrictionManager.Injector mInjector;
    private int mClientAppUid;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        mClientAppUid = Process.myUid();
        PackageManagerHelper packageManagerHelper =
                new PackageManagerHelper(context, mClientAppUid);

        mPackageManagerHelper = Mockito.spy(packageManagerHelper);
        mInjector = Mockito.spy(new FakeInjector(context, mPackageManagerHelper));
        mSdkSandboxRestrictionManager = new SdkSandboxRestrictionManager(mInjector);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void
            testGetEffectiveTargetSdkVersion_default_noAdSdk_currentSdkLevelLessThanDefaultValue()
                    throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(33);
        Mockito.when(mPackageManagerHelper.getSdkSharedLibraryInfo(TEST_PACKAGE))
                .thenReturn(new ArrayList<>());
        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mClientAppUid))
                .isEqualTo(DEFAULT_TARGET_SDK_VERSION);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_noAdSdk_currentSdkLevelSameAsDefaultValue()
            throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(34);
        Mockito.when(mPackageManagerHelper.getSdkSharedLibraryInfo(TEST_PACKAGE))
                .thenReturn(new ArrayList<>());
        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mClientAppUid))
                .isEqualTo(DEFAULT_TARGET_SDK_VERSION);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_noAdSdk_equalToCurrentSdkLevel() throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(35);
        Mockito.when(mPackageManagerHelper.getSdkSharedLibraryInfo(TEST_PACKAGE))
                .thenReturn(new ArrayList<>());
        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mClientAppUid))
                .isEqualTo(35);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_currentSdkVersionUsed() throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(34);
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = 35;

        Mockito.doReturn(applicationInfo, applicationInfo, applicationInfo)
                .when(mPackageManagerHelper)
                .getApplicationInfoForSharedLibrary(
                        Mockito.any(SharedLibraryInfo.class), Mockito.anyInt());

        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mClientAppUid))
                .isEqualTo(DEFAULT_TARGET_SDK_VERSION);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_withDifferentTargetSdkVersionForSdk()
            throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(36);

        ApplicationInfo applicationInfo1 = new ApplicationInfo();
        applicationInfo1.targetSdkVersion = 35;
        ApplicationInfo applicationInfo2 = new ApplicationInfo();
        applicationInfo2.targetSdkVersion = 36;

        Mockito.doReturn(applicationInfo1, applicationInfo2, applicationInfo2)
                .when(mPackageManagerHelper)
                .getApplicationInfoForSharedLibrary(
                        Mockito.any(SharedLibraryInfo.class), Mockito.anyInt());

        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mClientAppUid))
                .isEqualTo(35);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_sharedUid() throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(37);

        String packageName2 = TEST_PACKAGE + "_1";
        List<String> packageNames = List.of(TEST_PACKAGE, packageName2);

        Mockito.when(mPackageManagerHelper.getPackageNamesForUid(mClientAppUid))
                .thenReturn(packageNames);

        SharedLibraryInfo sharedLibraryInfo1 =
                new SharedLibraryInfo(
                        "testpath1",
                        TEST_PACKAGE,
                        new ArrayList<>(),
                        "test1",
                        0L,
                        SharedLibraryInfo.TYPE_SDK_PACKAGE,
                        new VersionedPackage("test1", 0L),
                        /*dependentPackages= */ null,
                        /* dependencies= */ null,
                        /* isNative= */ false);

        SharedLibraryInfo sharedLibraryInfo2 =
                new SharedLibraryInfo(
                        "testpath2",
                        packageName2,
                        new ArrayList<>(),
                        "test2",
                        0L,
                        SharedLibraryInfo.TYPE_SDK_PACKAGE,
                        new VersionedPackage("test2", 0L),
                        /*dependentPackages= */ null,
                        /* dependencies= */ null,
                        /* isNative= */ false);

        Mockito.doReturn(List.of(sharedLibraryInfo1))
                .when(mPackageManagerHelper)
                .getSdkSharedLibraryInfo(TEST_PACKAGE);

        Mockito.doReturn(List.of(sharedLibraryInfo2))
                .when(mPackageManagerHelper)
                .getSdkSharedLibraryInfo(packageName2);

        ApplicationInfo applicationInfo1 = new ApplicationInfo();
        applicationInfo1.targetSdkVersion = 35;
        ApplicationInfo applicationInfo2 = new ApplicationInfo();
        applicationInfo2.targetSdkVersion = 36;

        Mockito.doReturn(applicationInfo1)
                .when(mPackageManagerHelper)
                .getApplicationInfoForSharedLibrary(
                        Mockito.eq(sharedLibraryInfo1), /* flags= */ Mockito.anyInt());

        Mockito.doReturn(applicationInfo2)
                .when(mPackageManagerHelper)
                .getApplicationInfoForSharedLibrary(
                        Mockito.eq(sharedLibraryInfo2), /* flags= */ Mockito.anyInt());

        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mClientAppUid))
                .isEqualTo(35);
    }

    static class FakeInjector extends SdkSandboxRestrictionManager.Injector {
        private PackageManagerHelper mPackageManagerHelper;

        FakeInjector(Context context, PackageManagerHelper packageManagerHelper) {
            super(context);
            mPackageManagerHelper = packageManagerHelper;
        }

        PackageManagerHelper getPackageManagerHelper(int callingUid) {
            return mPackageManagerHelper;
        }
    }
}
