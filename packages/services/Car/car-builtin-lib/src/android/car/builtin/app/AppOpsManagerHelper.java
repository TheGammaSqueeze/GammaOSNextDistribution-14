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

package android.car.builtin.app;

import android.annotation.SystemApi;
import android.app.AppOpsManager;
import android.content.Context;

/**
 * Helper for AppOpsManagerHelper related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class AppOpsManagerHelper {

    private AppOpsManagerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Sets whether the app associated with the given {@code packageName} is allowed to turn the
     * screen on.
     *
     * @param context Context to use.
     * @param uid The user id of the application whose mode will be changed.
     * @param packageName The name of the application package name whose mode will be changed.
     * @param isAllowed Whether to allow or disallow.
     */
    public static void setTurnScreenOnAllowed(Context context, int uid, String packageName,
            boolean isAllowed) {
        int mode = isAllowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED;
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        appOpsManager.setMode(AppOpsManager.OP_TURN_SCREEN_ON, uid, packageName, mode);
    }
}
