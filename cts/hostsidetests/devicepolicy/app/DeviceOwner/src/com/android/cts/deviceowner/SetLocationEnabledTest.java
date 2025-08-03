/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link DevicePolicyManager#setLocationEnabled}.
 */
public final class SetLocationEnabledTest extends BaseDeviceOwnerTest {

    private static final String TAG = SetLocationEnabledTest.class.getSimpleName();

    private static final long TIMEOUT_MS = 5000;

    private static final boolean ENABLED = true;
    private static final boolean DISABLED = false;

    private LocationManager mLocationManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mLocationManager = mContext.getSystemService(LocationManager.class);
    }

    public void testSetLocationEnabled() throws Exception {
        boolean enabled = mLocationManager.isLocationEnabled();
        if (enabled) {
            Log.d(TAG, "location is initially enabled for user " + mUserId);
            runDisableLocationFirst();
        } else {
            Log.d(TAG, "location is initially disabled for user " + mUserId);
            runEnableLocationFirst();
        }
    }

    private void runDisableLocationFirst() throws Exception {
        Log.v(TAG, "runDisableLocationFirst()");

        setLocationEnabledAndWaitIfNecessary(DISABLED, /* wait= */ !mIsAutomotive);
        assertWithMessage("isLocationEnabled()").that(mLocationManager.isLocationEnabled())
                .isEqualTo(mIsAutomotive ? ENABLED : DISABLED);

        setLocationEnabledAndWaitIfNecessary(ENABLED, /* wait= */ !mIsAutomotive);
        assertWithMessage("isLocationEnabled()").that(mLocationManager.isLocationEnabled())
                .isTrue();
    }

    private void runEnableLocationFirst() throws Exception {
        Log.v(TAG, "runEnableLocationFirst()");

        setLocationEnabledAndWaitIfNecessary(ENABLED, /* wait= */ true);
        assertWithMessage("isLocationEnabled()").that(mLocationManager.isLocationEnabled())
                .isTrue();

        setLocationEnabledAndWaitIfNecessary(DISABLED, /* wait= */ !mIsAutomotive);
        assertWithMessage("isLocationEnabled()").that(mLocationManager.isLocationEnabled())
                .isEqualTo(mIsAutomotive ? ENABLED : DISABLED);
    }

    private void setLocationEnabledAndWaitIfNecessary(boolean enabled, boolean wait)
            throws Exception {
        if (!wait) {
            Log.d(TAG, "setting location to " + enabled);
            mDevicePolicyManager.setLocationEnabled(getWho(), enabled);
            Log.d(TAG, "not waiting for " + LocationManager.MODE_CHANGED_ACTION + " intent");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean actualEnabled = intent
                        .getBooleanExtra(LocationManager.EXTRA_LOCATION_ENABLED, enabled);
                Log.d(TAG, "received intent " + intent.getAction() + ": enabled=" + actualEnabled);
                if (actualEnabled != enabled) {
                    Log.e(TAG, "Invalid value on extra " + LocationManager.EXTRA_LOCATION_ENABLED
                            + ": " + actualEnabled);
                    return;
                }
                latch.countDown();
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
        try {
            Log.d(TAG, "setting location to " + enabled);
            mDevicePolicyManager.setLocationEnabled(getWho(), enabled);
            Log.d(TAG, "Waiting for " + LocationManager.MODE_CHANGED_ACTION + " intent");
            assertWithMessage("%s intent reveiced in %sms", LocationManager.MODE_CHANGED_ACTION,
                    TIMEOUT_MS).that(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }
}
