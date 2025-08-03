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

package com.android.compatibility.common.util;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assume.assumeTrue;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This is is a test rule that, when used in a test, will enable location before the test starts.
 * After the test is done, location will be disabled if and only if it was disabled before the test
 * started.
 */
@TargetApi(Build.VERSION_CODES.P)
public class EnableLocationRule extends BeforeAfterRule {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final LocationManager mLocationManager =
            mContext.getSystemService(LocationManager.class);

    private boolean mWasLocationEnabled = true;

    private boolean supportsLocation() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION);
    }

    private boolean isLocationEnabled() {
        return mLocationManager.isLocationEnabled();
    }

    private void enableLocation() {
        runWithShellPermissionIdentity(() -> {
            mLocationManager.setLocationEnabledForUser(true, Process.myUserHandle());
        });
    }

    private void disableLocation() {
        runWithShellPermissionIdentity(() -> {
            mLocationManager.setLocationEnabledForUser(false, Process.myUserHandle());
        });
    }

    @Override
    protected void onBefore(Statement base, Description description) {
        assumeTrue(supportsLocation());
        mWasLocationEnabled = isLocationEnabled();
        if (!mWasLocationEnabled) {
            enableLocation();
        }
    }

    @Override
    protected void onAfter(Statement base, Description description) {
        assumeTrue(supportsLocation());
        if (!mWasLocationEnabled) {
            disableLocation();
        }
    }
}
