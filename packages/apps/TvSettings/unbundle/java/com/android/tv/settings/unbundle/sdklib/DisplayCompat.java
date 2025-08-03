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

import android.view.Display;

public class DisplayCompat {
    private final Display mDisplay;

    public DisplayCompat(Display display) {
        this.mDisplay = display;
    }

    public int[] getReportedHdrTypes() {
        return mDisplay.getReportedHdrTypes();
    }

    public int getColorMode() {
        return mDisplay.getColorMode();
    }

    public void requestColorMode(int colorMode) {
        mDisplay.requestColorMode(colorMode);
    }

    public static class Mode {
        private final Display.Mode mMode;

        public Mode(Display.Mode mode) {
            this.mMode = mode;
        }

        public boolean matches(int width, int height, float refreshRate) {
            return mMode.matches(width, height, refreshRate);
        }
    }
}
