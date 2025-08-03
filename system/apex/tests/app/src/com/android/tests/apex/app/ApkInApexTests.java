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

package com.android.tests.apex.app;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApkInApexTests {
    private final Context mContext = InstrumentationRegistry.getContext();
    private final PackageManager mPm = mContext.getPackageManager();


    @Before
    public void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.DELETE_PACKAGES,
                        Manifest.permission.TEST_MANAGE_ROLLBACKS);
    }

    @After
    public void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testPrivPermissionIsGranted() throws Exception {
        PackageInfo pi = mPm.getPackageInfo("com.android.apex.product.app.test",
                PackageManager.GET_PERMISSIONS);
        assertThat(pi.requestedPermissions).asList()
                .contains("android.permission.PACKAGE_USAGE_STATS");

        pi = mPm.getPackageInfo("com.android.apex.system.app.test",
                PackageManager.GET_PERMISSIONS);
        assertThat(pi.requestedPermissions).asList()
                .contains("android.permission.PACKAGE_USAGE_STATS");

        pi = mPm.getPackageInfo("com.android.apex.system_ext.app.test",
                PackageManager.GET_PERMISSIONS);
        assertThat(pi.requestedPermissions).asList()
                .contains("android.permission.PACKAGE_USAGE_STATS");

        pi = mPm.getPackageInfo("com.android.apex.vendor.app.test",
                PackageManager.GET_PERMISSIONS);

        assertThat(pi.requestedPermissions).asList()
                .contains("android.permission.START_ACTIVITIES_FROM_BACKGROUND");
    }

    @Test
    public void testJniCalls() throws Exception {
        System.loadLibrary("ApkInApex_jni");
        assertThat(nativeFakeMethod()).isTrue();
    }

    private native boolean nativeFakeMethod();
}
