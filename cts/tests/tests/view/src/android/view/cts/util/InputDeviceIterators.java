/*
 * Copyright 2023 The Android Open Source Project
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

package android.view.cts.util;

import android.view.InputDevice;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Static iterators over {@link InputDevice}s. */
public class InputDeviceIterators {
    /** Allows running a logic on some invalid InputDevice IDs. */
    public static void iteratorOverInvalidDeviceIds(Consumer<Integer> invalidDeviceIdConsumer) {
        // "50" randomly chosen to cover some array of integers.
        for (int deviceId = -50; deviceId < 50; deviceId++) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null) {
                // No InputDevice found, so the ID is invalid.
                invalidDeviceIdConsumer.accept(deviceId);
            }
        }
    }

    /**
     * Allows running a logic on every motion range across every InputDevice.
     * The motion range is provided to the consumer along with the InputDevice ID corresponding to
     * the motion range.
     */
    public static void iteratorOverEveryInputDeviceMotionRange(
            BiConsumer<Integer, InputDevice.MotionRange> motionRangeConsumer) {
        iteratorOverEveryValidDeviceId((deviceId) -> {
            InputDevice device = InputDevice.getDevice(deviceId);
            for (InputDevice.MotionRange motionRange : device.getMotionRanges()) {
                motionRangeConsumer.accept(deviceId, motionRange);
            }
        });
    }

    /** Allows running a logic on every valid input device ID. */
    public static void iteratorOverEveryValidDeviceId(Consumer<Integer> deviceIdConsumer) {
        for (int deviceId : InputDevice.getDeviceIds()) {
            deviceIdConsumer.accept(deviceId);
        }
    }
}
