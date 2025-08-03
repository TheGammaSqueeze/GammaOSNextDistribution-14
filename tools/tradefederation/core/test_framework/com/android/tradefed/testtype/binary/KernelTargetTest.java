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
package com.android.tradefed.testtype.binary;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test runner for executable running on the target and parsing tesult of kernel test. */
@OptionClass(alias = "kernel-target-test")
public class KernelTargetTest extends ExecutableTargetTest {
    private Integer mCurrKver = null;
    private Pattern mKverPattern = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /**
     * @deprecated use skip-binary-check instead. Left for backwards compatibility.
     */
    @Deprecated
    @Option(
            name = "ignore-binary-check",
            description = "Deprecated - use skip-binary-check instead.")
    private boolean mIgnoreBinaryCheck = false;

    @Option(name = "exit-code-skip", description = "Exit code for skipped tests.")
    private Integer mExitCodeSkip = null;

    @Option(
            name = "min-kernel-version",
            description =
                    "The minimum kernel version needed to run a test.  The test name should be the"
                        + " key and the minimum kernel version should be the value.  Should contain"
                        + " at least the kernel version and major revision, and optionally the"
                        + " minor revision, separated by periods, e.g. 5.4 or 4.19.1")
    private Map<String, String> mTestMinKernelVersion = new LinkedHashMap<>();

    /**
     * Skips the binary check in findBinary. Redundant with mSkipBinaryCheck but needed for
     * backwards compatibility.
     */
    @Override
    public String findBinary(String binary) throws DeviceNotAvailableException {
        if (mIgnoreBinaryCheck) return binary;
        return super.findBinary(binary);
    }

    /**
     * Parse the kernel version, major revision, and, optionally, the minimum revision from a
     * version string into a single integer that can used for numerical comparison.
     *
     * @param version linux version string.
     */
    public Integer parseKernelVersion(String version) {
        Matcher m = mKverPattern.matcher(version);
        if (m.find()) {
            Integer v1 = Integer.valueOf(m.group(1));
            Integer v2 = Integer.valueOf(m.group(2));
            Integer v3 = m.group(3) == null ? 0 : Integer.valueOf(m.group(3));
            return (v1 << 20) + (v2 << 10) + v3;
        }
        throw new IllegalArgumentException("Invalid kernel version string: " + version);
    }

    /**
     * Check if the kernel version meets or exceeds the minimum kernel version for this test.
     *
     * @param minKernelVersion the min version string from the config.
     */
    public boolean compareKernelVersion(String minKernelVersion) {
        Integer minKver = parseKernelVersion(minKernelVersion);
        return mCurrKver >= minKver;
    }

    /** Get the device kernel version with uname -r. */
    public Integer getDeviceKernelVersion() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();
        if (device == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        CommandResult result = device.executeShellV2Command("uname -r");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new DeviceRuntimeException(
                    "Failed to get the kernel version with uname",
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        return parseKernelVersion(result.getStdout());
    }

    @Override
    public void runBinary(
            String binaryPath, ITestInvocationListener listener, TestDescription description)
            throws DeviceNotAvailableException, IOException {
        String testName = description.getTestName();
        if (mTestMinKernelVersion.containsKey(testName)
                && !compareKernelVersion(mTestMinKernelVersion.get(testName))) {
            listener.testIgnored(description);
        } else {
            super.runBinary(binaryPath, listener, description);
        }
    }

    @Override
    public void run(TestInformation testInfo, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        if (!mTestMinKernelVersion.isEmpty()) {
            mCurrKver = getDeviceKernelVersion();
        }
        super.run(testInfo, listener);
    }

    /**
     * Check the result of the test command.
     *
     * @param result test result of the command {@link CommandResult}
     * @param listener the {@link ITestInvocationListener}
     * @param description The test in progress.
     */
    @Override
    protected void checkCommandResult(
            CommandResult result, ITestInvocationListener listener, TestDescription description) {
        if (mExitCodeSkip != null && result.getExitCode().equals(mExitCodeSkip)) {
            listener.testIgnored(description);
        } else {
            super.checkCommandResult(result, listener, description);
        }
    }
}
