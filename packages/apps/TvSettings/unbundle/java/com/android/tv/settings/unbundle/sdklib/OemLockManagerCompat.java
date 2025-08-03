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

import android.content.Context;
import android.service.oemlock.OemLockManager;

public class OemLockManagerCompat {
    private final OemLockManager mOemLockManager;

    public OemLockManagerCompat(Context context) {
        mOemLockManager = context.getSystemService(OemLockManager.class);
    }

    public void setOemUnlockAllowedByUser(boolean allowed) {
        mOemLockManager.setOemUnlockAllowedByUser(allowed);
    }

    public boolean isOemUnlockAllowedByCarrier() {
        return mOemLockManager.isOemUnlockAllowedByCarrier();
    }

    public boolean isDeviceOemUnlocked() {
        return mOemLockManager.isDeviceOemUnlocked();
    }

    public boolean isOemUnlockAllowed() {
        return mOemLockManager.isOemUnlockAllowed();
    }
}
