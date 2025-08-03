/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.inputmethod.cts.installtests.common;


/**
 * Utility class for preparing "adb shell" command.
 */
public final class ShellCommandUtils {

    // This is utility class, can't instantiate.
    private ShellCommandUtils() {
    }

    /** Command to set current IME to {@code imeId} synchronously for the specified {@code user} */
    public static String setCurrentImeSync(String imeId, int userId) {
        return String.format("ime set --user %d %s", userId, imeId);
    }

    /** Command to enable IME of {@code imeId} for the specified {@code userId}. */
    public static String enableIme(String imeId, int userId) {
        return String.format("ime enable --user %d %s", userId, imeId);
    }

    /** Command to reset currently selected/enabled IMEs to the default ones. */
    public static String resetImes() {
        return "ime reset";
    }

    /** Command to reset currently selected/enabled IMEs to the default ones for all the users. */
    public static String resetImesForAllUsers() {
        return "ime reset --user all";
    }

    /** Command to turn on the display (if it's sleeping). */
    public static String wakeUp() {
        return "input keyevent KEYCODE_WAKEUP";
    }

    /** Command to dismiss Keyguard (if it's shown) */
    public static String dismissKeyguard() {
        return "wm dismiss-keyguard";
    }

    /** Command to close system dialogs (if shown) */
    public static String closeSystemDialog() {
        return "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS";
    }

    /** Command to wait until all broadcast queues have passed barrier. */
    public static String waitForBroadcastBarrier() {
        return "am wait-for-broadcast-barrier";
    }

}
