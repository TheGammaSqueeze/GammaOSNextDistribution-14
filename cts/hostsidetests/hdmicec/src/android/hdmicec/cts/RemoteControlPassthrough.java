/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts;

import com.android.tradefed.device.ITestDevice;

import java.util.HashMap;

/** Helper class with methods to test the remote control passthrough functionality */
public final class RemoteControlPassthrough {

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";
    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecKeyEventCapture";
    /** The command to launch the main activity. */
    private static final String START_COMMAND =
            String.format(
                    "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
                    PACKAGE, PACKAGE, CLASS);
    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    private static final HashMap<Integer, String> mUserControlPressKeys_20 =
            createUserControlPressKeys_20();

    /**
     * Tests that the device responds correctly to a {@code <USER_CONTROL_PRESSED>} message followed
     * immediately by a {@code <USER_CONTROL_RELEASED>} message.
     */
    public static void checkUserControlPressAndRelease(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_UP, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_DOWN, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_LEFT, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_RIGHT, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_SELECT, false);
        LogHelper.assertLog(
                device, CLASS, "Short press KEYCODE_DPAD_CENTER", "Short press KEYCODE_ENTER");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_BACK, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_BACK");
    }

    /**
     * Tests that the device responds correctly to a {@code <USER_CONTROL_PRESSED>} message for
     * press and hold operations.
     */
    public static void checkUserControlPressAndHold(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_UP, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_DOWN, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_LEFT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_RIGHT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_SELECT, true);
        LogHelper.assertLog(
                device, CLASS, "Long press KEYCODE_DPAD_CENTER", "Long press KEYCODE_ENTER");
        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_BACK, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_BACK");
    }

    /**
     * Tests that the device responds correctly to a {@code <User Control Pressed>} message for
     * press and hold operations when no {@code <User Control Released>} is sent.
     */
    public static void checkUserControlPressAndHoldWithNoRelease(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_UP, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_DOWN, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_LEFT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_RIGHT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_SELECT, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_CENTER");
        hdmiCecClient.sendUserControlPress(
                sourceDevice, dutLogicalAddress, HdmiCecConstants.CEC_KEYCODE_BACK, true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_BACK");
    }

    /**
     * Tests that the device responds correctly to a {@code <User Control Pressed> [firstKeycode]}
     * press and hold operation when interrupted by a {@code <User Control Pressed> [secondKeycode]}
     * before a {@code <User Control Released> [firstKeycode]} is sent.
     */
    public static void checkUserControlInterruptedPressAndHoldWithNoRelease(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_KEYCODE_UP,
                HdmiCecConstants.CEC_KEYCODE_BACK,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_UP");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_KEYCODE_DOWN,
                HdmiCecConstants.CEC_KEYCODE_UP,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_DOWN");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_KEYCODE_LEFT,
                HdmiCecConstants.CEC_KEYCODE_DOWN,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_LEFT");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_KEYCODE_RIGHT,
                HdmiCecConstants.CEC_KEYCODE_LEFT,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_RIGHT");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_KEYCODE_SELECT,
                HdmiCecConstants.CEC_KEYCODE_RIGHT,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_DPAD_CENTER");
        hdmiCecClient.sendUserControlInterruptedPressAndHold(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_KEYCODE_BACK,
                HdmiCecConstants.CEC_KEYCODE_SELECT,
                true);
        LogHelper.assertLog(device, CLASS, "Long press KEYCODE_BACK");
    }

    /**
     * Tests that the device responds correctly to a {@code <User Control Pressed> [keyCode]} press
     * and release operation when it has an additional parameter following the keyCode.
     */
    public static void checkUserControlPressAndReleaseWithAdditionalParams(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        hdmiCecClient.sendUserControlPressAndReleaseWithAdditionalParams(
                sourceDevice,
                dutLogicalAddress,
                HdmiCecConstants.CEC_KEYCODE_UP,
                HdmiCecConstants.CEC_KEYCODE_DOWN);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_DPAD_UP");
    }

    /**
     * Tests that the device that support cec version 2.0 responds correctly to a
     * {@code <USER_CONTROL_PRESSED>} message followed immediately by a
     * {@code <USER_CONTROL_RELEASED>} message.
     */
    public static void checkUserControlPressAndRelease_20(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);

        for (Integer userControlPressKey : mUserControlPressKeys_20.keySet()) {
            hdmiCecClient.sendUserControlPressAndRelease(
                    sourceDevice, dutLogicalAddress, userControlPressKey, false);
            // KEYCODE_SETUP_MENU might trigger the notification panel quitting the activity
            // HdmiCecKeyEventCapture.
            if (userControlPressKey == HdmiCecConstants.CEC_KEYCODE_SETUP_MENU) {
                try {
                    LogHelper.waitForLog(device, "ActivityTaskManager", 5,
                            "TOGGLE_NOTIFICATION_HANDLER_PANEL");
                    // HdmiCecKeyEventCapture activity should be resumed.
                    device.executeShellCommand(START_COMMAND);
                    continue;
                } catch (Exception e) {
                    // We have to send the key again since logcat was cleared.
                    hdmiCecClient.sendUserControlPressAndRelease(
                            sourceDevice, dutLogicalAddress, userControlPressKey, false);
                }
            }
            LogHelper.assertLog(
                    device,
                    CLASS,
                    "Short press KEYCODE_" + mUserControlPressKeys_20.get(userControlPressKey));
            // KEYCODE_HOME pressing will let the activity HdmiCecKeyEventCapture be paused.
            // Resume the activity after testing for KEYCODE_HOME pressing.
            if (userControlPressKey == HdmiCecConstants.CEC_KEYCODE_ROOT_MENU) {
                device.executeShellCommand(START_COMMAND);
            }
        }
    }

    private static HashMap<Integer, String> createUserControlPressKeys_20() {
        HashMap<Integer, String> userControlPressKeys = new HashMap<Integer, String>();
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_UP,"DPAD_UP");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_DOWN,"DPAD_DOWN");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_LEFT,"DPAD_LEFT");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_RIGHT,"DPAD_RIGHT");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_ROOT_MENU,"HOME");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_SETUP_MENU,"SETTINGS");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_CONTENTS_MENU,"TV_CONTENTS_MENU");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_BACK,"BACK");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_MEDIA_TOP_MENU,"MEDIA_TOP_MENU");
        userControlPressKeys.put(
                HdmiCecConstants.CEC_KEYCODE_MEDIA_CONTEXT_SENSITIVE_MENU,"TV_MEDIA_CONTEXT_MENU");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBER_0_OR_NUMBER_10,"0");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_1,"1");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_2,"2");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_3,"3");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_4,"4");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_5,"5");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_6,"6");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_7,"7");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_8,"8");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_NUMBERS_9,"9");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_CHANNEL_UP,"CHANNEL_UP");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_CHANNEL_DOWN,"CHANNEL_DOWN");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_PREVIOUS_CHANNEL,"LAST_CHANNEL");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_DISPLAY_INFORMATION,"INFO");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_PLAY,"MEDIA_PLAY");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_STOP,"MEDIA_STOP");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_PAUSE,"MEDIA_PAUSE");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_RECORD,"MEDIA_RECORD");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_REWIND,"MEDIA_REWIND");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_FAST_FORWARD,"MEDIA_FAST_FORWARD");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_EJECT,"MEDIA_EJECT");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_FORWARD,"MEDIA_NEXT");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_BACKWARD,"MEDIA_PREVIOUS");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_F1_BLUE,"PROG_BLUE");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_F2_RED,"PROG_RED");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_F3_GREEN,"PROG_GREEN");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_F4_YELLOW,"PROG_YELLOW");
        userControlPressKeys.put(HdmiCecConstants.CEC_KEYCODE_DATA,"TV_DATA_SERVICE");
        return userControlPressKeys;
    }

    public static void checkUserControlPressAndRelease(
            HdmiCecClientWrapper hdmiCecClient,
            ITestDevice device,
            LogicalAddress sourceDevice,
            LogicalAddress dutLogicalAddress,
            int cecKeycode,
            String androidKeycode)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);

        hdmiCecClient.sendUserControlPressAndRelease(
                sourceDevice, dutLogicalAddress, cecKeycode, false);
        LogHelper.assertLog(device, CLASS, "Short press KEYCODE_" + androidKeycode);
    }
}
