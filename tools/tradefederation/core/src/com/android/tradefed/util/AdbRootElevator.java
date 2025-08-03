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
package com.android.tradefed.util;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.INativeDevice;

/**
 * An {@link java.lang.AutoCloseable} that enables adb root when constructed if needed and restores
 * root state when complete.
 */
public class AdbRootElevator implements AutoCloseable {
    private final boolean mWasRoot;
    private final INativeDevice mDevice;

    public AdbRootElevator(INativeDevice device) throws DeviceNotAvailableException {
        mDevice = device;
        mWasRoot = mDevice.isAdbRoot();
        if (!mWasRoot) {
            if (!mDevice.enableAdbRoot()) {
                throw new RuntimeException("Failed to enable adb root.");
            }
        }
    }

    @Override
    public void close() {
        if (!mWasRoot) {
            try {
                mDevice.disableAdbRoot();
            } catch (DeviceNotAvailableException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
