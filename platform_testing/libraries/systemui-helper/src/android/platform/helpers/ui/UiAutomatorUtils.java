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

package android.platform.helpers.ui;

import android.app.Instrumentation;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Most commonly used UiAutomator CommonUtils required for writing UI tests.
 */
public class UiAutomatorUtils {

    private static final String TAG = "UiAutomatorUtils";

    private UiAutomatorUtils() {
    }

    /**
     * To get the instance of UiDevice which provides access to state information about the device.
     *
     * @return an instance of UiDevice.
     */
    public static UiDevice getUiDevice() {
        return UiDevice.getInstance(getInstrumentation());
    }

    /**
     * To get base class for implementing application instrumentation code.
     *
     * @return an instance of Instrumentation
     */
    public static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    /**
     * Dumps the current view hierarchy
     */
    public static void dumpViewHierarchy() {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            UiAutomatorUtils.getUiDevice().dumpWindowHierarchy(stream);
            stream.flush();
            stream.close();
            for (String line : stream.toString().split("\\r?\\n")) {
                Log.i(TAG, line.trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "error dumping XML to logcat", e);
        }
    }
}
