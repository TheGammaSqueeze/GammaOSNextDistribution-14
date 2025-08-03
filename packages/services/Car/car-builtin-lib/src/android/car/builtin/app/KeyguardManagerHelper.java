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

package android.car.builtin.app;

import android.annotation.SystemApi;
import android.car.builtin.util.Slogf;
import android.os.RemoteException;
import android.view.WindowManagerGlobal;

/**
 * Wrapper class for {@code android.app.KeyguardManager}. Check the class for API documentation.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class KeyguardManagerHelper {

    private static final String TAG = "CAR.WM";

    /**
     * Return whether the keyguard is currently locked.
     * Note: CarService can't use KeyguardManager because KeyguardManager needs TrustManager and
     * TrustService is disabled for User0.
     *
     * @return {@code true} if keyguard is locked.
     */
    public static boolean isKeyguardLocked() {
        boolean locked = true;
        try {
            locked = WindowManagerGlobal.getWindowManagerService().isKeyguardLocked();
        } catch (RemoteException e) {
            Slogf.w(TAG, "Failed to get Keyguard state", e);
        }
        return locked;
    }

    private KeyguardManagerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
