/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tests.apex.app;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.rollback.RollbackInfo;

import androidx.test.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.rollback.lib.RollbackUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApexCompressionTests {
    private static final String COMPRESSED_APEX_PACKAGE_NAME = "com.android.apex.compressed";
    private final Context mContext = InstrumentationRegistry.getContext();
    private final PackageManager mPm = mContext.getPackageManager();

    private static final TestApp UNCOMPRESSED_APEX_V1 = new TestApp(
            "TestAppUncompressedApexV1", COMPRESSED_APEX_PACKAGE_NAME, 2, /*isApex*/ true,
            "com.android.apex.compressed.v1_original.apex");
    private static final TestApp UNCOMPRESSED_APEX_V2 = new TestApp(
            "TestAppUncompressedApexV2", COMPRESSED_APEX_PACKAGE_NAME, 2, /*isApex*/ true,
            "com.android.apex.compressed.v2_original.apex");

    @Before
    public void adoptShellPermissions() {
        androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.DELETE_PACKAGES,
                        Manifest.permission.TEST_MANAGE_ROLLBACKS);
    }

    @After
    public void dropShellPermissions() {
        androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Check if the given package is pre-installed and not have been updated.
     */
    private boolean isFactoryPackage(PackageInfo pi) {
        return (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
            && (pi.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
    }

    @Test
    public void testDecompressedApexIsConsideredFactory() throws Exception {
        // Only retrieve active apex package
        PackageInfo pi = mPm.getPackageInfo(
                COMPRESSED_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(pi).isNotNull();
        assertThat(pi.isApex).isTrue();
        assertThat(pi.packageName).isEqualTo(COMPRESSED_APEX_PACKAGE_NAME);
        assertThat(pi.getLongVersionCode()).isEqualTo(1);
        assertThat(isFactoryPackage(pi)).isTrue();
    }

    @Test
    public void testUnusedDecompressedApexIsCleanedUp_HigherVersion() throws Exception {
        Install.single(UNCOMPRESSED_APEX_V2).setStaged().commit();
    }

    @Test
    public void testUnusedDecompressedApexIsCleanedUp_SameVersion() throws Exception {
        Install.single(UNCOMPRESSED_APEX_V1).setStaged().commit();
    }


    @Test
    public void testCapexToApexSwitch() throws Exception {
        // Only retrieve active apex package
        PackageInfo pi = mPm.getPackageInfo(
                COMPRESSED_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(pi).isNotNull();
        assertThat(pi.isApex).isTrue();
        assertThat(pi.packageName).isEqualTo(COMPRESSED_APEX_PACKAGE_NAME);
        assertThat(pi.getLongVersionCode()).isEqualTo(1);
        assertThat(isFactoryPackage(pi)).isTrue();
        assertThat(pi.applicationInfo.sourceDir).startsWith("/system/apex");
    }

    @Test
    public void testDecompressedApexVersionAlwaysHasSameVersionAsCapex() throws Exception {
        // Only retrieve active apex package
        PackageInfo pi = mPm.getPackageInfo(
                COMPRESSED_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(pi).isNotNull();
        assertThat(pi.isApex).isTrue();
        assertThat(pi.packageName).isEqualTo(COMPRESSED_APEX_PACKAGE_NAME);
        assertThat(pi.getLongVersionCode()).isEqualTo(1);
        assertThat(isFactoryPackage(pi)).isTrue();
    }

    @Test
    public void testCompressedApexCanBeRolledBack_Commit() throws Exception {
        // Verify before updating
        PackageInfo pi = mPm.getPackageInfo(
                COMPRESSED_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(pi.getLongVersionCode()).isEqualTo(1);
        assertThat(isFactoryPackage(pi)).isTrue();
        assertThat(pi.applicationInfo.sourceDir).startsWith("/data/apex/decompressed/");

        Install.single(UNCOMPRESSED_APEX_V2).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testCompressedApexCanBeRolledBack_Rollback() throws Exception {
        // Verify before rollback
        PackageInfo pi = mPm.getPackageInfo(
                COMPRESSED_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(pi.getLongVersionCode()).isEqualTo(2);
        assertThat(isFactoryPackage(pi)).isFalse();
        assertThat(pi.applicationInfo.sourceDir).startsWith("/data/apex/active/");

        // Trigger rollback
        RollbackInfo available = RollbackUtils.getAvailableRollback(COMPRESSED_APEX_PACKAGE_NAME);
        RollbackUtils.rollback(available.getRollbackId(), UNCOMPRESSED_APEX_V2);
    }

    @Test
    public void testCompressedApexCanBeRolledBack_Verify() throws Exception {
        // Verify rollback worked
        PackageInfo pi = mPm.getPackageInfo(
                COMPRESSED_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(pi.getLongVersionCode()).isEqualTo(1);
        assertThat(isFactoryPackage(pi)).isFalse();
        assertThat(pi.applicationInfo.sourceDir).startsWith("/data/apex/active/");
    }
}
