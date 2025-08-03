/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.content.pm.cts;

import static android.content.Context.RECEIVER_EXPORTED;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

@AppModeFull // TODO(Instant) Figure out which APIs should work.
public class FeatureTest extends AndroidTestCase {

    private static final String TAG = "FeatureTest";
    private static final long TWO_GB = 1536000000; // 2 GB

    private PackageManager mPackageManager;
    private ActivityManager mActivityManager;
    private WindowManager mWindowManager;
    private boolean mSupportsDeviceAdmin;
    private boolean mSupportsManagedProfiles;
    private long mTotalMemory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPackageManager = getContext().getPackageManager();
        mActivityManager = (ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
        mWindowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        mSupportsDeviceAdmin =
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        mSupportsManagedProfiles =
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        mActivityManager.getMemoryInfo(memInfo);
        mTotalMemory = memInfo.totalMem;
    }

    /**
     * Test whether device supports managed profiles as required by CDD
     */
    public void testManagedProfileSupported() throws Exception {
        // Managed profiles only required if device admin feature is supported
        if (!mSupportsDeviceAdmin) {
            Log.w(TAG, "Skipping testManagedProfileSupported");
            return;
        }

        if (mSupportsManagedProfiles) {
            // Managed profiles supported nothing to check.
            return;
        }

        // Managed profiles only required for handheld devices
        if (!isHandheldOrTabletDevice()) {
            return;
        }

        // Skip the tests for non-emulated sdcard
        if (!Environment.isExternalStorageEmulated()) {
            return;
        }

        // Skip the tests for devices with less than 2GB of ram available
        if (lessThanTwoGbDevice()) {
            return;
        }

        fail("Device should support managed profiles, but "
                + PackageManager.FEATURE_MANAGED_USERS + " is not enabled");
    }

    /**
     * The CDD defines:
     * - A handheld device as one that has a battery and a screen size between 2.5 and 8 inches.
     * - A tablet device as one that has a battery and a screen size between 7 and 18 inches.
     */
    private boolean isHandheldOrTabletDevice() throws Exception {
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || mPackageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)) {
            return false;
        }

        double screenInches = getScreenSizeInInches();
        return deviceHasBattery() && screenInches >= 2.5 && screenInches <= 18.0;
    }

    private boolean deviceHasBattery() {
        final Intent batteryInfo = getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_EXPORTED);
        return batteryInfo.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
    }

    private double getScreenSizeInInches() {
        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(dm);
        double widthInInchesSquared = Math.pow(dm.widthPixels/dm.xdpi,2);
        double heightInInchesSquared = Math.pow(dm.heightPixels/dm.ydpi,2);
        return Math.sqrt(widthInInchesSquared + heightInInchesSquared);
    }

    // Implementation copied from CoreGmsAppsTest#twoGbDevice()
    private boolean lessThanTwoGbDevice() {
        return mTotalMemory < TWO_GB;
    }
}
