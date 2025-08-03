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

package com.android.server.sdksandbox.helpers;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PackageManagerHelperUnitTest {
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final ArrayList<String> SDK_NAMES =
            new ArrayList<>(
                    Arrays.asList(
                            "com.android.codeprovider",
                            "com.android.codeproviderresources",
                            "com.android.property_sdkprovider_classname_not_present"));
    private static final ArrayList<String> SDK_PACKAGE_NAMES =
            new ArrayList<>(
                    Arrays.asList(
                            "com.android.codeprovider_1",
                            "com.android.codeproviderresources_1",
                            "com.android.property_sdkprovider_classname_not_present_1"));
    private PackageManagerHelper mPackageManagerHelper;
    private int mClientAppUid;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mClientAppUid = Process.myUid();
        mPackageManagerHelper = new PackageManagerHelper(context, mClientAppUid);
    }

    @Test
    public void testSdkSharedLibraryInfo() throws Exception {
        List<SharedLibraryInfo> sharedLibraryInfos =
                mPackageManagerHelper.getSdkSharedLibraryInfo(TEST_PACKAGE);
        assertThat(sharedLibraryInfos.size()).isEqualTo(3);

        List<String> sdks =
                sharedLibraryInfos.stream()
                        .map(sharedLibrary -> sharedLibrary.getName())
                        .collect(Collectors.toList());

        assertThat(sdks).containsExactlyElementsIn(SDK_NAMES);
    }

    @Test
    public void testGetSdkSharedLibraryInfoForSdk() throws Exception {
        SharedLibraryInfo sharedLibraryInfo =
                mPackageManagerHelper.getSdkSharedLibraryInfoForSdk(TEST_PACKAGE, SDK_NAMES.get(0));
        assertThat(sharedLibraryInfo.getDeclaringPackage().getPackageName())
                .isEqualTo(SDK_PACKAGE_NAMES.get(0));
    }

    @Test
    public void testGetProperty() throws Exception {
        String propertyName = "android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME";
        assertThat(
                        mPackageManagerHelper
                                .getProperty(
                                        propertyName, /* packageName= */ SDK_PACKAGE_NAMES.get(0))
                                .getString())
                .isEqualTo("test.class.name");
    }

    @Test
    public void testGetApplicationInfoForSharedLibrary() throws Exception {

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

            SharedLibraryInfo sharedLibraryInfo =
                    mPackageManagerHelper.getSdkSharedLibraryInfoForSdk(
                            TEST_PACKAGE, SDK_NAMES.get(0));
            ApplicationInfo applicationInfo =
                    mPackageManagerHelper.getApplicationInfoForSharedLibrary(
                            sharedLibraryInfo,
                            /* flags= */ PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
                                    | PackageManager.MATCH_ANY_USER);
            assertThat(applicationInfo.packageName).isEqualTo(SDK_PACKAGE_NAMES.get(0));
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testGetPackageNamesForUid() throws Exception {
        List<String> packageNames = mPackageManagerHelper.getPackageNamesForUid(mClientAppUid);
        assertThat(packageNames.size()).isEqualTo(1);
        assertThat(packageNames.get(0)).isEqualTo(TEST_PACKAGE);
    }

    @Test
    public void testGetPackageNamesForUid_invalidUid() throws Exception {
        PackageManager.NameNotFoundException thrown =
                assertThrows(
                        PackageManager.NameNotFoundException.class,
                        () -> mPackageManagerHelper.getPackageNamesForUid(/* callingUid= */ -1));
        assertThat(thrown).hasMessageThat().contains("Could not find package for -1");
    }
}
