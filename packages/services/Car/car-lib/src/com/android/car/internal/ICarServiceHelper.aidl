/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.internal;

import android.content.ComponentName;
import android.os.UserHandle;

import java.util.List;

/**
 * Helper API for CarService.
 *
 * Only for interaction between system server and car service, so it can be changed (without
 * breaking Car Mainline)
 *
 *
 * @hide
 */
interface ICarServiceHelper {
    /**
    * Check
    * {@link com.android.server.wm.CarLaunchParamsModifier#setDisplayAllowlistForUser(int, int[]).
    */
    void setDisplayAllowlistForUser(int userId, in int[] displayIds) = 0;

    /**
     * Check
     * {@link com.android.server.wm.CarLaunchParamsModifier#setPassengerDisplays(int[])}.
     */
    void setPassengerDisplays(in int[] displayIds) = 1;

    /**
     * Sets whether it's safe to run operations (like DevicePolicyManager.lockNow()).
     */
    void setSafetyMode(boolean safe) = 3;

    /**
     * Creates the given user, even when it's disallowed by DevicePolicyManager.
     */
    UserHandle createUserEvenWhenDisallowed(String name, String userType, int flags) = 4;

    /**
     * Designates the given {@code activity} to be launched in {@code TaskDisplayArea} of
     * {@code featureId} in the display of {@code displayId}.
     */
    int setPersistentActivity(in ComponentName activity, int displayId, int featureId) = 5;

    /**
     * Saves initial user information in System Server. If car service crashes, Car service helper
     * service would send back this information.
     */
    void sendInitialUser(in UserHandle user) = 6;

    /** Check {@link android.os.Process#setProcessGroup(int, int)}. */
    void setProcessGroup(int pid, int group) = 7;

    /** Check {@link android.os.Process#getProcessGroup(int)}. */
    int getProcessGroup(int pid) = 8;

    /** Same as {@code UserManagerInternal#getMainDisplayAssignedToUser()} */
    int getMainDisplayAssignedToUser(int userId) = 9;

    /** Same as {@code UserManagerInternal#getUsersAssignedToDisplay()} */
    int getUserAssignedToDisplay(int displayId) = 10;

    /**
     * Check {@link android.app.AcitivityManager#startUserInBackgroundVisibleOnDisplay(int, int)}
     */
    boolean startUserInBackgroundVisibleOnDisplay(int userId, int displayId) = 11;

    /** Check {@link android.os.Process#setProcessProfile(int, int, String)}. */
    void setProcessProfile(int pid, int uid, in String profile) = 12;

    /**
     * Returns the PID for the AIDL VHAL service.
     *
     * On error, returns {@link com.android.car.internal.common.CommonConstants#INVALID_PID}.
     */
    int fetchAidlVhalPid() = 13;

    /**
     * Designates the given {@code activities} to be launched in the root task associated with
     * {@code rootTaskToken}.
     */
    void setPersistentActivitiesOnRootTask(in List<ComponentName> activity,
        in IBinder rootTaskToken) = 14;

    /**
     * Returns true if the given package requires launching in automotive compatibility mode.
     */
    boolean requiresDisplayCompat(String packageName) = 15;
}
