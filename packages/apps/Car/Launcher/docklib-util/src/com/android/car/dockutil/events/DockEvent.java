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

package com.android.car.dockutil.events;

import androidx.annotation.Nullable;

/**
 * Enum for events sent by the system to trigger change in the Dock.
 */
public enum DockEvent {
    LAUNCH("com.android.car.docklib.events.LAUNCH"),
    PIN("com.android.car.docklib.events.PIN"),
    UNPIN("com.android.car.docklib.events.UNPIN");

    private final String mStr;

    DockEvent(String str) {
        mStr = str;
    }

    @Override
    public String toString() {
        return mStr;
    }

    /**
     * Converts the string to {@link DockEvent}. Returns {@code null} if the string is not
     * convertible.
     */
    @Nullable
    public static DockEvent toDockEvent(@Nullable String str) {
        if (LAUNCH.toString().equals(str)) {
            return LAUNCH;
        } else if (PIN.toString().equals(str)) {
            return PIN;
        } else if (UNPIN.toString().equals(str)) {
            return UNPIN;
        } else {
            return null;
        }
    }
}
