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

package com.android.cts.packagemanager.stats.device;

import static android.os.UserManager.USER_TYPE_FULL_DEMO;
import static android.os.UserManager.USER_TYPE_FULL_GUEST;
import static android.os.UserManager.USER_TYPE_FULL_RESTRICTED;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_FULL_SYSTEM;
import static android.os.UserManager.USER_TYPE_PROFILE_CLONE;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;
import static android.os.UserManager.USER_TYPE_PROFILE_PRIVATE;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;

import com.android.internal.util.FrameworkStatsLog;

public class UserInfoUtil {

    /**
     * CTS tests do not have access to com.android.server.pm package where this implementations
     * exists. This is duplication of UserJourneyLogger.getUserTypeForStatsd
     * @param userType string
     * @return the enum defined in the statsd UserLifecycleJourneyReported atom corresponding to
     * the user type.
     */
    public static int getUserTypeForStatsd(String userType) {
        switch (userType) {
            case USER_TYPE_FULL_SYSTEM:
                return FrameworkStatsLog.USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__FULL_SYSTEM;
            case USER_TYPE_FULL_SECONDARY:
                return FrameworkStatsLog.USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__FULL_SECONDARY;
            case USER_TYPE_FULL_GUEST:
                return FrameworkStatsLog.USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__FULL_GUEST;
            case USER_TYPE_FULL_DEMO:
                return FrameworkStatsLog.USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__FULL_DEMO;
            case USER_TYPE_FULL_RESTRICTED:
                return FrameworkStatsLog
                        .USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__FULL_RESTRICTED;
            case USER_TYPE_PROFILE_MANAGED:
                return FrameworkStatsLog
                        .USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__PROFILE_MANAGED;
            case USER_TYPE_SYSTEM_HEADLESS:
                return FrameworkStatsLog
                        .USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__SYSTEM_HEADLESS;
            case USER_TYPE_PROFILE_CLONE:
                return FrameworkStatsLog.USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__PROFILE_CLONE;
            case USER_TYPE_PROFILE_PRIVATE:
                return FrameworkStatsLog
                        .USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__PROFILE_PRIVATE;
            default:
                return FrameworkStatsLog.USER_LIFECYCLE_JOURNEY_REPORTED__USER_TYPE__TYPE_UNKNOWN;
        }
    }

}
