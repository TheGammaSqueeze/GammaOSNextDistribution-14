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

package com.android.compatibility.common.util;

import android.content.Context;
import android.hardware.display.DisplayManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Manages the state of {@link DisplayManager}.
 */
public class DisplayStateManager implements StateManager<DisplayStateManager.DisplayState> {
    private final DisplayManager mDisplayManager;

    public DisplayStateManager(Context context) {
        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    public void set(@Nullable DisplayState value) {
        if (value == null || mDisplayManager == null) {
            return;
        }
        mDisplayManager.setUserDisabledHdrTypes(value.mUserDisabledHdrTypes);
        mDisplayManager.setAreUserDisabledHdrTypesAllowed(value.mAreUserDisabledHdrTypesAllowed);
    }

    @Nullable
    @Override
    public DisplayState get() {
        return mDisplayManager == null ? null : new DisplayState(mDisplayManager);
    }

    /**
     * Container for storing state of {@link DisplayManager}.
     */
    public static final class DisplayState {
        private final boolean mAreUserDisabledHdrTypesAllowed;
        @NonNull
        private final int[] mUserDisabledHdrTypes;

        public DisplayState(@NonNull DisplayManager displayManager) {
            mAreUserDisabledHdrTypesAllowed = displayManager.areUserDisabledHdrTypesAllowed();
            int[] disabledHdrTypes = displayManager.getUserDisabledHdrTypes();
            mUserDisabledHdrTypes = Arrays.copyOf(disabledHdrTypes, disabledHdrTypes.length);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DisplayState)) return false;
            DisplayState that = (DisplayState) o;
            return mAreUserDisabledHdrTypesAllowed == that.mAreUserDisabledHdrTypesAllowed
                    && Arrays.equals(mUserDisabledHdrTypes, that.mUserDisabledHdrTypes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(mAreUserDisabledHdrTypesAllowed);
            result = 31 * result + Arrays.hashCode(mUserDisabledHdrTypes);
            return result;
        }
    }
}
