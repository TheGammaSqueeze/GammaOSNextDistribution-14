/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.common.CommonConstants.INVALID_PID;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ICarServiceHelper;

import java.util.Arrays;
import java.util.List;

/**
 * Base implementation of {@link ICarServiceHelper.Stub} providing no-ops methods.
 */
@ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
abstract class AbstractICarServiceHelperStub extends ICarServiceHelper.Stub {

    private static final String TAG = AbstractICarServiceHelperStub.class.getSimpleName();

    AbstractICarServiceHelperStub() {
        Log.d(TAG, "I am " + this);
    }

    @Override
    public void setDisplayAllowlistForUser(@UserIdInt int userId, int[] displayIds) {
        Log.d(TAG, "setDisplayAllowlistForUser(user=" + userId + ", displayIds="
                + Arrays.toString(displayIds) + ")");
    }

    @Override
    public void setPassengerDisplays(int[] displayIdsForPassenger) {
        Log.d(TAG, "setPassengerDisplays(displayIdsForPassenger="
                + Arrays.toString(displayIdsForPassenger) + ")");
    }

    @Override
    public void setSafetyMode(boolean safe) {
        Log.d(TAG, "setSafetyMode(safe=" + safe + ")");
    }

    @Override
    public UserHandle createUserEvenWhenDisallowed(String name, String userType, int flags) {
        Log.d(TAG, "createUserEvenWhenDisallowed(name=" + name + ", userType=" + userType
                + ", flags=" + flags + ")");
        return null;
    }

    @Override
    public int setPersistentActivity(ComponentName activity, int displayId, int featureId) {
        Log.d(TAG, "setPersistentActivity(activity=" + activity.toShortString()
                + ", displayId=" + displayId + ", featureId=" + featureId + ")");
        return CarActivityManager.RESULT_SUCCESS;
    }

    @Override
    public void setPersistentActivitiesOnRootTask(List<ComponentName> activities,
            IBinder rootTaskToken) {
        Log.d(TAG, "setPersistentActivitiesOnRootTask(activities=" + activities.toString()
                + ", rootTaskToken=" + rootTaskToken + ")");
    }

    @Override
    public void sendInitialUser(UserHandle user) {
        Log.d(TAG, "sendInitialUser " + user);
    }

    @Override
    public void setProcessGroup(int pid, int group) {
        Log.d(TAG, "setProcessGroup, pid=" + pid + ", group=" + group);
    }

    @Override
    public int getProcessGroup(int pid) {
        Log.d(TAG, "getProcessGroup, pid=" + pid);

        return 0;
    }

    @Override
    public int getMainDisplayAssignedToUser(int userId) {
        Log.d(TAG, "getMainDisplayAssignedToUser(" + userId + ")");

        return Display.INVALID_DISPLAY;
    }

    @Override
    public int getUserAssignedToDisplay(int displayId) {
        Log.d(TAG, "getUserAssignedToDisplay(" + displayId + ")");

        return UserHandle.USER_NULL;
    }

    @Override
    public boolean startUserInBackgroundVisibleOnDisplay(int userId, int displayId) {
        Log.d(TAG, "startUserInBackgroundVisibleOnDisplay(" + userId + ",displaId" + displayId
                + ")");

        return false;
    }

    @Override
    public void setProcessProfile(int pid, int uid, @NonNull String profile) {
        Log.d(TAG, "setProcessProfile(" + pid + "," + uid + "," + profile + ")");

        return;
    }

    @Override
    public int fetchAidlVhalPid() {
        Log.d(TAG, "fetchAidlVhalPid()");

        return INVALID_PID;
    }

    @Override
    public boolean requiresDisplayCompat(String packageName) {
        return false;
    }
}
