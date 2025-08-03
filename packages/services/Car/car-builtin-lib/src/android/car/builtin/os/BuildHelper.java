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

package android.car.builtin.os;

import android.annotation.SystemApi;
import android.os.Build;

/**
 * Helper for {@link Build}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class BuildHelper {
    private BuildHelper() {
        throw new UnsupportedOperationException();
    }

    /** Tells if it is {@code user} build. */
    public static boolean isUserBuild() {
        return Build.IS_USER;
    }

    /** Tells if it is {@code eng} build. */
    public static boolean isEngBuild() {
        return Build.IS_ENG;
    }

    /** Tells if it is {@code userdebug} build. */
    public static boolean isUserDebugBuild() {
        return Build.IS_USERDEBUG;
    }

    /** Tells if the build is debuggable ({@code eng} or {@code userdebug}) */
    public static boolean isDebuggableBuild() {
        return Build.IS_DEBUGGABLE;
    }
}
