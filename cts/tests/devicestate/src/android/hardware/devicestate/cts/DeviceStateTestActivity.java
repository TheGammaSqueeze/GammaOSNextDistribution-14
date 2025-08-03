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

package android.hardware.devicestate.cts;

import android.app.Activity;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;
import android.os.Bundle;

/**
 * This is an activity that can request device state changes via
 * {@link DeviceStateManager#requestState} as well as cancel the active
 * override request with {@link DeviceStateManager#cancelStateRequest}.
 *
 * @see {@link DeviceStateManagerTests#testRequestStateFailsAsBackgroundApp}
 * @see {@link DeviceStateManagerTests#testRequestStateSucceedsAsTopApp}
 * @see {@link DeviceStateManagerTests#testCancelOverrideRequestFromNewActivity}
 */
public class DeviceStateTestActivity extends Activity {

    public boolean requestStateFailed = false;
    public boolean cancelStateRequestFailed = false;
    public boolean mResumed = false;
    private DeviceStateManager mDeviceStateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDeviceStateManager = getSystemService(DeviceStateManager.class);
    }

    public void requestDeviceStateChange(int state) {
        DeviceStateRequest request = DeviceStateRequest.newBuilder(state).build();
        try {
            requestStateFailed = false;
            mDeviceStateManager.requestState(request, null, null);
        } catch (SecurityException e) {
            requestStateFailed = true;
        }
    }

    public void cancelOverriddenState() {
        try {
            cancelStateRequestFailed = false;
            mDeviceStateManager.cancelStateRequest();
        } catch (SecurityException e) {
            cancelStateRequestFailed = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
    }
}
