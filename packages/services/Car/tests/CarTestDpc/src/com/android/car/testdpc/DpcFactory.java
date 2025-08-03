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

package com.android.car.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.testdpc.remotedpm.DevicePolicyManagerInterface;
import com.android.car.testdpc.remotedpm.LocalDevicePolicyManager;
import com.android.car.testdpc.remotedpm.RemoteDevicePolicyManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DpcFactory {

    private static final String TAG = DpcFactory.class.getSimpleName();

    private final Map<UserHandle, DevicePolicyManagerInterface> mDevicePolicyManagers;

    private final ComponentName mAdmin;
    private final Context mContext;
    private final DevicePolicyManager mDpm;

    public DpcFactory(Context context) {
        mAdmin = DpcReceiver.getComponentName(context);
        mContext = context;
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
        mDevicePolicyManagers = new HashMap<>();

        if (isPO(mContext, mDpm) || isDO(mContext, mDpm)) {
            assignDevicePolicyManagers();
        }
    }

    private void assignDevicePolicyManagers() {
        List<UserHandle> targetUsers = mDpm.getBindDeviceAdminTargetUsers(mAdmin);
        Log.d(TAG, "targetUsers: " + targetUsers);

        mDevicePolicyManagers.putAll(targetUsers.stream()
                .collect(Collectors.toMap(
                        u -> u, u -> new RemoteDevicePolicyManager(mAdmin, mContext, u))
                ));

        mDevicePolicyManagers.put(Process.myUserHandle(),
                new LocalDevicePolicyManager(mAdmin, mContext));
    }

    public void addProfileOwnerDpm(UserHandle target) {
        mDevicePolicyManagers.put(target,
                new RemoteDevicePolicyManager(mAdmin, mContext, target));
    }

    public void removeProfileOwnerDpm(UserHandle target) {
        mDevicePolicyManagers.remove(target);
    }

    private static boolean isPO(Context context, DevicePolicyManager dpm) {
        return dpm.isProfileOwnerApp(context.getPackageName());
    }

    private static boolean isDO(Context context, DevicePolicyManager dpm) {
        return dpm.isDeviceOwnerApp(context.getPackageName());
    }

    public DevicePolicyManagerInterface getDevicePolicyManager(UserHandle target) {
        if (!mDevicePolicyManagers.containsKey(target)) {
            Log.i(TAG, "Creating new remotedpm");

            mDevicePolicyManagers.get(UserHandle.SYSTEM)
                    .startUserInBackground(target);
            mDevicePolicyManagers.put(target,
                    new RemoteDevicePolicyManager(mAdmin, mContext, target));
        }
        Log.i(TAG, "Returning dpm");
        return mDevicePolicyManagers.get(target);
    }

    public List<UserHandle> getAllBoundUsers() {
        return new ArrayList<>(mDevicePolicyManagers.keySet());
    }
}
