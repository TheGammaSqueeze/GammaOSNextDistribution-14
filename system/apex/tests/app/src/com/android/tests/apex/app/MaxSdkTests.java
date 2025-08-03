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

package com.android.tests.apex.app;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MaxSdkTests {
    // these apps are installed via apk-in-apex
    private static final String APP_NO_MAX_SDK = "com.android.apex.maxsdk.app.available.test";
    private static final String APP_MAX_SDK_10K =
            "com.android.apex.maxsdk.app.available.target10k.test";
    private static final String APP_MAX_SDK_31 = "com.android.apex.maxsdk.app.unavailable.test";
    // REGULAR_APP is installed as a normal app
    private static final String REGULAR_APP_WITH_MAX_SDK =
            "com.android.apex.maxsdk.regular.app.test";
    private final Context mContext = InstrumentationRegistry.getContext();
    private final PackageManager mPm = mContext.getPackageManager();

    @Test
    public void testApkInApexIsAvailable() throws Exception {
        PackageInfo pi = mPm.getPackageInfo(APP_NO_MAX_SDK, 0);
        assertThat(pi).isNotNull();
    }

    @Test
    public void testAppWithMaxSdk10KIsAvailable() throws Exception {
        PackageInfo pi = mPm.getPackageInfo(APP_MAX_SDK_10K, 0);
        assertThat(pi).isNotNull();
    }

    @Test
    public void testAppWithMaxSdk31() throws Exception {
        // this app should not be available because it has uses-sdk maxSdkVersion="31"
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPm.getPackageInfo(APP_MAX_SDK_31, 0));
    }

    @Test
    public void testRegularAppIsAvailable() throws Exception {
        // because this is a regular app, max-sdk should not be checked
        PackageInfo pi = mPm.getPackageInfo(REGULAR_APP_WITH_MAX_SDK, 0);
        assertThat(pi).isNotNull();
    }
}
