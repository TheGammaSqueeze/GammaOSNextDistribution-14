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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/** Helper class to get for device logcat. */
public final class LogHelper {

    private static final int WAIT_TIME = 10;

    /**
     * The tag of the SadConfigurationReaderTest launched by the APK.
     */
    private static final String SAD_READER = "SadConfigurationReaderTest";

    private static final String SAD_CONFIGURATION_MARKER = "Supported Audio Formats";

    private static String getLog(ITestDevice device, String tag) throws Exception {
        TimeUnit.SECONDS.sleep(WAIT_TIME);
        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", tag + ":I", "*:S");
        // Search for string.
        StringBuilder testString = new StringBuilder();
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.startsWith("I/" + tag)) {
                testString.append(line.split(":")[1].trim());
            }
        }
        device.executeAdbCommand("logcat", "-c");
        return testString.toString();
    }

    public static void waitForLog(
            ITestDevice device, String tag, int waitSeconds, String expectedOutput)
            throws Exception {
        long timeoutMillis = waitSeconds * 1000;
        long startTime = System.currentTimeMillis();
        long endTime = startTime;

        while ((endTime - startTime <= timeoutMillis)) {
            String testString = getLog(device, tag);
            if (testString.contains(expectedOutput)) {
                return;
            }
            endTime = System.currentTimeMillis();
        }

        throw new Exception("Timed out, could not find the log message.");
    }

    public static void assertLog(ITestDevice device, String tag, String ...expectedOutput)
            throws Exception {
        String testString = getLog(device, tag);
        List<String> expectedOutputs = new ArrayList<>(Arrays.asList(expectedOutput));
        assertThat(testString).isIn(expectedOutputs);
    }

    /** Skip the test if the expectedOutput was not found in the device logs. */
    public static void assumeLog(ITestDevice device, String tag, String expectedOutput)
            throws Exception {
        String testString = getLog(device, tag);
        assumeTrue(
                "Skip the test since "
                        + expectedOutput
                        + " message is not found in the device logs.",
                testString.contains(expectedOutput));
    }

    /** This method will return the DUT volume. */
    public static int parseDutVolume(ITestDevice device, String tag) throws Exception {
        String testString = getLog(device, tag);
        assertWithMessage("No log from Audio Manager which reports the DUT volume percentage")
                .that(testString)
                .isNotEqualTo("");
        try {
            String volume = testString.split("at")[1].trim().replaceAll("%", "");
            return Integer.parseInt(volume);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                    "Volume obtained from Audio Manager can" + "not be parsed");
        }
    }

    public static void assertLogDoesNotContain(ITestDevice device, String tag,
                                               String expectedOutput) throws Exception {
        String testString = getLog(device, tag);
        assertThat(testString).doesNotContain(expectedOutput);
    }

    public static List<Integer> getSupportedAudioFormats(ITestDevice device) throws Exception {
        TimeUnit.SECONDS.sleep(WAIT_TIME);
        String logs =
                device.executeAdbCommand("logcat", "-v", "brief", "-d", SAD_READER + ":I", "*:S");
        // Search for string.
        String testString = "";
        Scanner in = new Scanner(logs);
        List<Integer> mSupportedAudioFormats = null;
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.startsWith("I/" + SAD_READER)) {
                testString = line.split(":")[1].trim();
                if (testString.equals(SAD_CONFIGURATION_MARKER)) {
                    List<String> mFormatsLine =
                            Arrays.asList(in.nextLine().split(":")[1].trim().split(", "));
                    List<String> mCodecSADsLine =
                            Arrays.asList(in.nextLine().split(":")[1].trim().split(", "));
                    mSupportedAudioFormats =
                            Lists.transform(mFormatsLine, fl -> Integer.parseInt(fl));
                }
            }
        }
        device.executeAdbCommand("logcat", "-c");
        assumeTrue(testString.equals(SAD_CONFIGURATION_MARKER));
        return mSupportedAudioFormats;
    }
}
