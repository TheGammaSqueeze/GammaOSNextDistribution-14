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

package android.car.builtin.util;

import android.annotation.AppIdInt;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.os.UserHandle;

/**
 * Helper to validate various data from android.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ValidationHelper {
    private ValidationHelper()   {
        throw new UnsupportedOperationException();
    }

    /** Returns true if passed userId is valid */
    public static boolean isUserIdValid(@UserIdInt int userId) {
        return !((userId != UserHandle.USER_NULL && userId < UserHandle.USER_CURRENT_OR_SELF)
                || userId > Integer.MAX_VALUE / UserHandle.PER_USER_RANGE);
    }

    /** Returns true if passed appId is valid */
    public static boolean isAppIdValid(@AppIdInt int appId) {
        return !(appId / UserHandle.PER_USER_RANGE != 0 || appId < 0);
    }
}
