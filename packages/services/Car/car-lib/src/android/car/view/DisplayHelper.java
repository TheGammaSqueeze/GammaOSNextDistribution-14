/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.view;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.view.Display;

/**
 * Provide access to {@code android.view.Display} calls.
 * @hide
 */
@SystemApi
public final class DisplayHelper {

    /** The same value as {@code android.car.builtin.view.DisplayHelper.INVALID_PORT} */
    public static final int INVALID_PORT = -1;

    private DisplayHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the uniqueId of the given display.
     */
    @Nullable
    public static String getUniqueId(@NonNull Display display) {
        return android.car.builtin.view.DisplayHelper.getUniqueId(display);
    }

    /**
     * @return the physical port of the given display.
     */
    public static int getPhysicalPort(@NonNull Display display) {
        return android.car.builtin.view.DisplayHelper.getPhysicalPort(display);
    }
}
