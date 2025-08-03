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

package android.server.wm;

import static android.hardware.devicestate.DeviceStateManager.MAXIMUM_DEVICE_STATE_IDENTIFIER;
import static android.hardware.devicestate.DeviceStateManager.MINIMUM_DEVICE_STATE_IDENTIFIER;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.hardware.devicestate.DeviceState;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;

/** Utility methods for CTS tests requiring the use of {@DeviceStateManager}. */
public final class DeviceStateUtils {
    /**
     * Runs the supplied {@code runnable} with the
     * {@link android.Manifest.permission.CONTROL_DEVICE_STATE} permission held.
     *
     * @throws Throwable if the runnable throws an exception during execution.
     */
    public static void runWithControlDeviceStatePermission(@NonNull ThrowingRunnable runnable)
            throws Throwable {
        try {
            SystemUtil.runWithShellPermissionIdentity(runnable,
                    android.Manifest.permission.CONTROL_DEVICE_STATE);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity() wraps exceptions thrown by the underlying runnable
            // in runtime exceptions.
            Throwable t = e.getCause();
            if (t != null) {
                throw t;
            }
            throw e;
        }
    }

    /**
     * Asserts that the provided {@code state} is in the range
     * [{@link MINIMUM_DEVICE_STATE}, {@link MAXIMUM_DEVICE_STATE}].
     */
    public static void assertValidState(int state) {
        assertTrue("Device state identifier is smaller than the minimum state identifier value",
                state >= MINIMUM_DEVICE_STATE_IDENTIFIER);
        assertTrue("Device state identifier is larger than the maximum state identifier value",
                state <= MAXIMUM_DEVICE_STATE_IDENTIFIER);
    }

    /**
     * asserts that the provided {@code state} has an identifier in the range
     * [{@link MINIMUM_DEVICE_STATE}, {@link MAXIMUM_DEVICE_STATE}] and a non-null {@code name}.
     */
    public static void assertValidDeviceState(DeviceState state) {
        assertTrue("Device state identifier is smaller than the minimum state identifier value",
                state.getIdentifier() >= MINIMUM_DEVICE_STATE_IDENTIFIER);
        assertTrue("Device state identifier is larger than the maximum state identifier value",
                state.getIdentifier() <= MAXIMUM_DEVICE_STATE_IDENTIFIER);
        assertNotNull("Device state name was null", state.getName());
    }

    private DeviceStateUtils() {}
}
