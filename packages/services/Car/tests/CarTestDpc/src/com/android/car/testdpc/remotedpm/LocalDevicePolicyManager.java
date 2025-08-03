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

package com.android.car.testdpc.remotedpm;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

public final class LocalDevicePolicyManager implements DevicePolicyManagerInterface {

    private static final String TAG = LocalDevicePolicyManager.class.getSimpleName();

    private final ComponentName mAdmin;
    private final DevicePolicyManager mDpm;

    /* Constructor for local dpm implementation of DPM Factory */
    public LocalDevicePolicyManager(ComponentName admin, Context context) {
        mAdmin = admin;
        mDpm = context.getSystemService(DevicePolicyManager.class);
    }

    @Override
    public UserHandle getUser() {
        return Process.myUserHandle();
    }

    @Override
    public void startUserInBackground(UserHandle target) {
        int status = mDpm.startUserInBackground(mAdmin, target);
        Log.i(TAG, "User Started status code: " + status);

    }

    @Override
    public void reboot() {
        Log.d(TAG, "Calling local reboot(" + mAdmin + ")");
        mDpm.reboot(mAdmin);
    }

    @Override
    public void addUserRestriction(String key) {
        Log.d(TAG, "Calling local addUserRestriction(" + mAdmin + ", " + key + ")");
        mDpm.addUserRestriction(mAdmin, key);
    }
}
