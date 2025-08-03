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
import android.util.Log;

public final class RemoteDevicePolicyManagerServiceImpl extends
        IRemoteDevicePolicyManager.Stub {

    private static final String TAG = RemoteDevicePolicyManagerServiceImpl.class.getSimpleName();

    private final Context mContext;
    private final DevicePolicyManager mDpm;

    public RemoteDevicePolicyManagerServiceImpl(Context context) {
        mContext = context;
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
    }

    @Override
    public void reboot(ComponentName admin) {
        Log.i(TAG, "Cross User: reboot(ComponentName: " + admin + ")");
        try {
            mDpm.reboot(admin);
        } catch (Exception e) {
            Log.e(TAG, "error rebooting", e);
        }
    }

    @Override
    public void addUserRestriction(ComponentName admin, String key) {
        Log.d(TAG, "Cross User: addUserRestriction(admin, key)");
        try {
            mDpm.addUserRestriction(admin, key);
        } catch (Exception e) {
            Log.e(TAG, "error adding user restriction", e);
        }
    }
}
