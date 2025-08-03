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

package android.autofillservice.cts.testcore;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.LogcatInspector;

import java.io.IOException;
import java.io.InputStream;

public final class DeviceUtils {

    private DeviceUtils() {
        // static class
    }

    private static final UiAutomation sAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    public static final LogcatInspector sLogcatInspector =
            new LogcatInspector() {
                @Override
                protected InputStream executeShellCommand(String command) throws IOException {
                    return shell(command);
                }
            };

    public static void unlockScreen() {
        shell("wm dismiss-keyguard");
    }

    public static void closeSystemDialogs() {
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
    }

    public static void wakeUp() throws Exception {
        UiDevice sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        sDevice.wakeUp();
    }

    public static InputStream shell(String command) {
        return executeInstrumentationShellCommand(DeviceUtils.sAutomation, command);
    }

    private static InputStream executeInstrumentationShellCommand(
            UiAutomation automation, String command) {
        final ParcelFileDescriptor pfd = automation.executeShellCommand(command);
        return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
    }

    public static class Logcat {
        static final int DEFAULT_TIMEOUT_SEC =
                (int)
                                android.autofillservice.cts.testcore.Timeouts
                                        .WINDOW_CHANGE_NOT_GENERATED_NAPTIME_MS
                        / 1000;

        public static void includes(String filter, String... message) throws IOException {
            DeviceUtils.sLogcatInspector.assertLogcatContainsInOrder(
                    filter, DEFAULT_TIMEOUT_SEC, message);
        }

        public static void includes(String message) throws IOException {
            DeviceUtils.sLogcatInspector.assertLogcatContainsInOrder(
                    null, DEFAULT_TIMEOUT_SEC, message);
        }

        public static void excludes(String... message) throws IOException {
            DeviceUtils.sLogcatInspector.assertLogcatDoesNotContainInOrder(
                    DEFAULT_TIMEOUT_SEC, message);
        }
    }

    public static class SaveDialog {

        public static void assertShows(String title) throws IOException {
            DeviceUtils.Logcat.includes("SaveUI:I", "Showing save dialog: " + title);
        }

        public static void assertShows() throws IOException {
            assertShows("");
        }

        // This only happens when SaveUI#hide() is called
        // Does not work if SaveDialog is not show in the first place
        public static void assertHidden() throws IOException {
            DeviceUtils.Logcat.includes("SaveUI:V", "Hiding save dialog.");
        }
    }

    public static class Dataset {

        public static void assertShowsInline() throws IOException {
            assertShows(2);
        }

        public static void assertShows(int uiType) throws IOException {
            DeviceUtils.Logcat.includes("AutofillSession:D", "onShown(): " + uiType);
        }
    }
}
