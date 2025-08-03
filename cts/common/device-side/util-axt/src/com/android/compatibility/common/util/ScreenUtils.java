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

package com.android.compatibility.common.util;

public class ScreenUtils {

    public static void setScreenOn(boolean on) throws Exception {
        BatteryUtils.turnOnScreen(on);

        // there is no way to listen for changes except broadcasts, and no way to guarantee
        // broadcast reception except waiting and crossing fingers. 2s should be enough in the idle
        // case, but may not be enough if the phone isn't idle
        Thread.sleep(2000);
    }

    /**
     * Try-with-resources class that resets the screen state on close to whatever the screen state
     * was on acquire.
     */
    public static class ScreenResetter implements AutoCloseable {

        private final boolean mScreenInteractive;

        public ScreenResetter() {
            mScreenInteractive = BatteryUtils.getPowerManager().isInteractive();
        }

        @Override
        public void close() throws Exception {
            BatteryUtils.turnOnScreen(mScreenInteractive);
        }
    }
}
