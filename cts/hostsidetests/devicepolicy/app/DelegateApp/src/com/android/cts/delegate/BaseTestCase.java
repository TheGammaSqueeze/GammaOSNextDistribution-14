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
package com.android.cts.delegate;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.dpmwrapper.IpcBroadcastReceiver;
import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;

import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
abstract class BaseTestCase {

    private static final String TAG = BaseTestCase.class.getSimpleName();

    protected UiDevice mDevice;
    protected Context mContext;
    protected DevicePolicyManager mDpm;

    @Before
    public final void setBaseFixtures() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = InstrumentationRegistry.getContext();
        mDpm = getDpm(mContext);
        Log.d(TAG, getClass().getName() + ".setup(): dpm=" + mDpm);
    }

    static DevicePolicyManager getDpm(Context context) {
        boolean isDeviceOwnerTest = "DeviceOwner"
                .equals(InstrumentationRegistry.getArguments().getString("admin_type"));
        DevicePolicyManager dpm = TestAppSystemServiceFactory.getDevicePolicyManager(context,
                        IpcBroadcastReceiver.class, isDeviceOwnerTest);
        Log.v(TAG, "getDpm(): isDeviceOwnerTest=" + isDeviceOwnerTest + ", returning " + dpm);
        return dpm;
    }
}
