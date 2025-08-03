/*
 * Copyright 2020 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Helper class to get DUT audio status using Audio manager app */
public final class AudioManagerHelper {

    /** The package name of the APK. */
    private static final String PACKAGE = "android.hdmicec.app";

    /** The class name of the main activity in the APK. */
    private static final String CLASS = "HdmiCecAudioManager";

    /** The command to launch the main activity. */
    private static final String START_COMMAND =
            String.format("am start -n %s/%s.%s -a ", PACKAGE, PACKAGE, CLASS);

    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    public static final int MAX_AUDIO_FORMATS = 4;
    public static final int MAX_VALID_AUDIO_FORMATS = 2;

    public static List<Integer> mSupportedAudioFormats = null;

    public static void muteDevice(ITestDevice device, HdmiCecClientWrapper hdmiCecClient)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.MUTE");
        // The audio device should send <Report Audio Status> message after mute.
        hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.REPORT_AUDIO_STATUS);
    }

    public static void unmuteDevice(ITestDevice device, HdmiCecClientWrapper hdmiCecClient)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.UNMUTE");
        // The audio device should send <Report Audio Status> message after unmute.
        hdmiCecClient.checkExpectedOutput(LogicalAddress.TV, CecOperand.REPORT_AUDIO_STATUS);
    }

    public static void unmuteDevice(ITestDevice device)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.UNMUTE");
    }

    public static boolean isDeviceMuted(ITestDevice device) throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.REPORT_VOLUME");
        return (LogHelper.parseDutVolume(device, CLASS) >= 128);
    }

    public static void lowerVolume(ITestDevice device) throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Start the APK and wait for it to complete
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.LOWER_VOLUME");
    }

    public static void raiseVolume(ITestDevice device) throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Start the APK and wait for it to complete
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.RAISE_VOLUME");
    }

    public static void setDeviceVolume(ITestDevice device, int percentVolume) throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Start the APK and wait for it to complete.
        device.executeShellCommand(
                START_COMMAND
                        + "android.hdmicec.app.SET_VOLUME --ei "
                        + "\"volumePercent\" "
                        + percentVolume);
    }

    public static int getDutAudioVolume(ITestDevice device) throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.REPORT_VOLUME");
        return LogHelper.parseDutVolume(device, CLASS);
    }

    public static String getRequestSadFormatsParams(ITestDevice device, boolean sendValidFormats)
            throws Exception {
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND + "android.hdmicec.app.GET_SUPPORTED_SAD_FORMATS");
        mSupportedAudioFormats = LogHelper.getSupportedAudioFormats(device);

        // Create a list of all the audio format codes according to CEA-861-D. Remove the supported
        // audio format codes from it, to get the unsupported audio format codes.
        List<Integer> mAllCodecFormats =
                IntStream.range(1, 15).boxed().collect(Collectors.toList());
        List<Integer> unsupportedAudioFormats = new ArrayList<>();
        unsupportedAudioFormats.addAll(mAllCodecFormats);
        unsupportedAudioFormats.removeAll(mSupportedAudioFormats);
        // Create params message for REQUEST_SHORT_AUDIO_DESCRIPTOR
        String messageParams = "";
        int i = 0;
        int listIndex = 0;
        if (sendValidFormats) {
            while (i < Math.min(MAX_VALID_AUDIO_FORMATS, mSupportedAudioFormats.size())) {
                messageParams += CecMessage.formatParams(mSupportedAudioFormats.get(listIndex), 2);
                i++;
                listIndex++;
            }
            listIndex = 0;
        }
        while (i < Math.min(MAX_AUDIO_FORMATS, unsupportedAudioFormats.size())) {
            messageParams += CecMessage.formatParams(unsupportedAudioFormats.get(listIndex), 2);
            i++;
            listIndex++;
        }
        return messageParams;
    }
}
