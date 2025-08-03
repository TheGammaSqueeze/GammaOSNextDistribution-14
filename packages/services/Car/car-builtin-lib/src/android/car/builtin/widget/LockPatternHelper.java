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

package android.car.builtin.widget;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.content.Context;

import com.android.internal.widget.LockPatternUtils;

/**
 * This class provides access to {@link com.android.internal.widget.LockPatternUtils} calls.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class LockPatternHelper {

    // NOTE: this class is called just once at boot, so it doesn't need to cache LockPatternUtils

    /**
     * Checks if the given user has lock credentials.
     */
    public static boolean isSecure(@NonNull Context context, @UserIdInt int userId) {
        return new LockPatternUtils(context).isSecure(userId);
    }

    private LockPatternHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
