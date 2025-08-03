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

package android.server.wm;

import static android.server.wm.StateLogger.log;
import static android.server.wm.StateLogger.logAlways;

import com.android.compatibility.common.util.SystemUtil;

import java.util.Locale;

public class ShellCommandHelper {
    public static void executeShellCommand(String command) {
        log("Shell command: " + command);
        try {
            SystemUtil.runShellCommandOrThrow(command);
        } catch (AssertionError e) {
            String message = e.getMessage();
            if (message != null
                    && message.contains("Warning: Activity not started")
                    && !message.toLowerCase(Locale.ROOT).contains("error")) {
                logAlways(message);
            } else {
                throw e;
            }
        }
    }

    public static String executeShellCommandAndGetStdout(String command) {
        log("Shell command: " + command);
        return SystemUtil.runShellCommandOrThrow(command);
    }
}
