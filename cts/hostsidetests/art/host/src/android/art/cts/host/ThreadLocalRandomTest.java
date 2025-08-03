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

package android.art.cts.host;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class ThreadLocalRandomTest extends BaseHostJUnit4Test {

    private static final String THREAD_LOCAL_APP_APK = "CtsThreadLocalRandomApp.apk";
    private static final Duration MAX_TIMEOUT_FOR_COMMAND = Duration.ofMinutes(2);

    private static final String NEXT_RANDOM_COMMAND =
            String.format("content call --uri content://%s --method %s", "tlrapp", "next_random");

    private static final String KILL_APP_COMMAND = "am force-stop com.android.art.cts.tlr_app";

    private ITestDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        installPackage(THREAD_LOCAL_APP_APK);
    }

    @Test
    public void nextLongAfterRestartShouldReturnDifferentValue() throws Exception {
        long firstCallResult = getNextLong();

        killApp();

        long secondCallResult = getNextLong();

        assertNotEquals("Both calls returned the same value", firstCallResult, secondCallResult);
    }

    private static final Pattern PATTERN =
            Pattern.compile(".*random=(-?\\d+).*", Pattern.DOTALL);

    private long getNextLong() throws Exception {
        return getLongFromResultBundle(execute(NEXT_RANDOM_COMMAND));
    }

    private void killApp() throws Exception {
        execute(KILL_APP_COMMAND);
    }

    private long getLongFromResultBundle(byte[] response) {
        String bundleString = parseBytesAsString(response);
        Matcher matcher = PATTERN.matcher(bundleString);

        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        } else {
            throw new IllegalArgumentException("Bundle '" + bundleString
                    + "' does not have random field");
        }
    }
    private byte[] execute(String command) throws Exception {
        ByteArrayOutputStream stdOutBytesReceiver = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErrBytesReceiver = new ByteArrayOutputStream();
        CommandResult result = mDevice.executeShellV2Command(
                command, /*pipeAsInput=*/null, stdOutBytesReceiver, stdErrBytesReceiver,
                MAX_TIMEOUT_FOR_COMMAND.toMillis(), TimeUnit.MILLISECONDS, 1 /* retries */);
        if (result.getExitCode() != 0 || stdErrBytesReceiver.size() > 0) {
            fail("Command \'" + command + "\' produced exitCode=" + result.getExitCode()
                    + " and stderr="
                    + parseBytesAsString(stdErrBytesReceiver.toByteArray()).trim());
        }
        return stdOutBytesReceiver.toByteArray();
    }

    private static String parseBytesAsString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
