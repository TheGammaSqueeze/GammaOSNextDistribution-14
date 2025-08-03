/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.sample.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.CommandResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Scanner;

/**
 * Test to check the APK logs to Logcat.
 *
 * <p>When this test builds, it also builds {@link android.sample.app.SampleDeviceActivity} into an
 * APK which it then installed at runtime and started. The activity simply prints a message to
 * Logcat and then gets uninstalled.
 *
 * <p>Instead of extending DeviceTestCase, this JUnit4 test extends IDeviceTest and is run with
 * tradefed's DeviceJUnit4ClassRunner.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class SampleHostJUnit4Test implements IDeviceTest {

    /** The package name of the APK. */
    private static final String PACKAGE = "android.sample.app";

    /** The class name of the main activity in the APK. */
    private static final String CLASS = "SampleDeviceActivity";

    /** The command to launch the main activity. */
    private static final String START_COMMAND =
            String.format(
                    "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
                    PACKAGE, PACKAGE, CLASS);

    /** The command to clear the main activity. */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    /** The logcat command to clear the existing log. */
    private static final String LOGCAT_CLEAR_COMMAND = "logcat -c";

    /** The logcat command to capture the desired log. */
    private static final String LOGCAT_FILTER_COMMAND =
            String.format("logcat -v brief -d %s:I *:S", CLASS);

    /** The test string to look for. */
    private static final String TEST_STRING = "SampleTestString";

    /** The number of the retry in case logcat command fails. E.g. logd crashes. */
    private static final int RETRY_NUM = 5;

    /** A rule annotation that allows to log metrics in test cases. */
    @Rule public TestMetrics mMetrics = new TestMetrics();

    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /** Tests the string was successfully logged to Logcat from the activity. */
    @Test
    public void testLogcat() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        var unused = executeLogcatCommandWithRetry(LOGCAT_CLEAR_COMMAND);
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        // Dump logcat.
        String logs = executeLogcatCommandWithRetry(LOGCAT_FILTER_COMMAND).getStdout();
        // Search for string.
        String testString = "";
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.startsWith("I/" + CLASS)) {
                testString = line.split(":")[1].trim();
            }
        }
        in.close();
        // Assert the logged string matches the test string.
        assertEquals("Incorrect test string", TEST_STRING, testString);
    }

    /**
     * Documentation:
     * https://source.android.com/devices/tech/test_infra/tradefed/testing/through-tf/report-metrics
     */
    @Test
    public void testMetrics() {
        mMetrics.addTestMetric("somekey", "some_values");
    }

    private CommandResult executeLogcatCommandWithRetry(String logcatCommand) throws Exception {
        int i = 0;
        while (i < RETRY_NUM) {
            CommandResult result = getDevice().executeShellV2Command(logcatCommand);
            if (result.getStderr() == null || result.getStderr().isEmpty()) {
                return result;
            }
            i++;
        }
        fail(String.format("Failed to execute \"%s\" multiple times!", logcatCommand));
        return null;
    }
}
