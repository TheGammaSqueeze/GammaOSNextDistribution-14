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

import android.annotation.NonNull;
import android.hardware.display.DisplayManager;
import android.view.Display;

public class DisplayManagerCompat {
    private final DisplayManager mManager;

    public boolean areUserDisabledHdrTypesAllowed() {
        return this.mManager.areUserDisabledHdrTypesAllowed();
    }

    public DisplayManagerCompat(DisplayManager manager) {
        this.mManager = manager;
    }


    public Display.Mode getGlobalUserPreferredDisplayMode() {
        return mManager.getGlobalUserPreferredDisplayMode();
    }

    public void setGlobalUserPreferredDisplayMode(@NonNull Display.Mode mode) {
        mManager.setGlobalUserPreferredDisplayMode(mode);
    }

    public void clearGlobalUserPreferredDisplayMode() {
        mManager.clearGlobalUserPreferredDisplayMode();
    }

    public void setUserDisabledHdrTypes(
            @NonNull @Display.HdrCapabilities.HdrType int[] userDisabledTypes) {
        mManager.setUserDisabledHdrTypes(userDisabledTypes);
    }

    public @NonNull
    int[] getUserDisabledHdrTypes() {
        return mManager.getUserDisabledHdrTypes();
    }

    public void setAreUserDisabledHdrTypesAllowed(boolean areUserDisabledHdrTypesAllowed) {
        mManager.setAreUserDisabledHdrTypesAllowed(areUserDisabledHdrTypesAllowed);
    }
}
