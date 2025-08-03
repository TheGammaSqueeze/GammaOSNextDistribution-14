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

package com.android.interactive;

import android.os.Environment;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.File;

/**
 * Utility class for taking screenshots during xTS-Interactive tests.
 *
 * <p>It saves screenshots as files to the /Documents/xts/screenshots/ directory on the external
 * storage.
 */
public final class ScreenshotUtil {

    /**
     * Captures a screenshot and saves it as a file.
     *
     * <p>The screenshot file is named using the provided screenshot name, appended with the current
     * system time.
     *
     * @param screenshotName the screenshot name
     * @throws IOException if fails to create and save the screenshot file
     */
    public static void captureScreenshot(String screenshotName) {
        String screenshotDir =
                Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/Documents/xts/screenshots/";
        File file = new File(screenshotDir);
        if (!file.exists()) {
            file.mkdirs();
        }

        File screenshotFile =
                new File(screenshotDir, screenshotName + "_" + System.currentTimeMillis() + ".png");
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(screenshotFile);
    }
}
