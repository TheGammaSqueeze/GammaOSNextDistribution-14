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

package com.android.car.internal.common;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.IntDef;
import android.annotation.UserIdInt;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Provides common constants for Car library, Car Service, and System Server.
 *
 * @hide
 */
public final class CommonConstants {

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private CommonConstants() {
        throw new UnsupportedOperationException("contains only static constants");
    }

    // CarUserManagerConstants

    public static final int USER_LIFECYCLE_EVENT_TYPE_STARTING = 1;
    public static final int USER_LIFECYCLE_EVENT_TYPE_SWITCHING = 2;
    public static final int USER_LIFECYCLE_EVENT_TYPE_UNLOCKING = 3;
    public static final int USER_LIFECYCLE_EVENT_TYPE_UNLOCKED = 4;
    public static final int USER_LIFECYCLE_EVENT_TYPE_STOPPING = 5;
    public static final int USER_LIFECYCLE_EVENT_TYPE_STOPPED = 6;
    public static final int USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED = 7;
    public static final int USER_LIFECYCLE_EVENT_TYPE_CREATED = 8;
    public static final int USER_LIFECYCLE_EVENT_TYPE_REMOVED = 9;
    public static final int USER_LIFECYCLE_EVENT_TYPE_VISIBLE = 10;
    public static final int USER_LIFECYCLE_EVENT_TYPE_INVISIBLE = 11;

    // CarService Constants
    public static final String CAR_SERVICE_INTERFACE = "android.car.ICar";

    public static final int INVALID_GID = -1;
    public static final int INVALID_PID = -1;

    // Represents an invalid user id. This must have the same value as UserHandle#USER_NULL.
    public static final @UserIdInt int INVALID_USER_ID = -10000;

    @IntDef(prefix = { "USER_LIFECYCLE_EVENT_TYPE_" }, value = {
            USER_LIFECYCLE_EVENT_TYPE_STARTING,
            USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
            USER_LIFECYCLE_EVENT_TYPE_UNLOCKING,
            USER_LIFECYCLE_EVENT_TYPE_UNLOCKED,
            USER_LIFECYCLE_EVENT_TYPE_STOPPING,
            USER_LIFECYCLE_EVENT_TYPE_STOPPED,
            USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED,
            USER_LIFECYCLE_EVENT_TYPE_CREATED,
            USER_LIFECYCLE_EVENT_TYPE_REMOVED,
            USER_LIFECYCLE_EVENT_TYPE_VISIBLE,
            USER_LIFECYCLE_EVENT_TYPE_INVISIBLE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserLifecycleEventType{}

    /** Empty arrays */
    public static final int[] EMPTY_INT_ARRAY = new int[0];
    public static final long[] EMPTY_LONG_ARRAY = new long[0];
    public static final float[] EMPTY_FLOAT_ARRAY = new float[0];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
}
