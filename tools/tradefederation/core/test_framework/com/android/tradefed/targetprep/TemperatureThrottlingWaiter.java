/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tradefed.targetprep;

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An {@link ITargetPreparer} that waits until device's temperature gets down to target */
@OptionClass(alias = "temperature-throttle-waiter")
public class TemperatureThrottlingWaiter extends BaseTargetPreparer {
    // temperature raw format example output : Result:30 Raw:7f6f
    private static final Pattern TEMPERATURE_RAW_FORMAT_REGEX =
        Pattern.compile("Result:(\\d+)\\sRaw:(\\w+)");

    // temperature format example output : 30
    private static final Pattern TEMPERATURE_FORMAT_REGEX = Pattern.compile("\\d+");

    // temperature format example output:
    //    Temperature{mValue=28.787504, mType=3, mName=VIRTUAL-SKIN, mStatus=0}
    private static final Pattern TEMPERATURE_SKIN_FORMAT_PATTERN =
        Pattern.compile("mValue=([\\d]+.[\\d]+).*mType=3");

    @Option(name = "poll-interval",
            description = "Interval in seconds, to poll for device temperature; defaults to 30s")
    private long mPollIntervalSecs = 30;

    @Option(name = "max-wait", description = "Max wait time in seconds, for device cool down "
            + "to target temperature; defaults to 20 minutes")
    private long mMaxWaitSecs = 60 * 20;

    @Option(name = "abort-on-timeout", description = "If test should be aborted if device is still "
        + " above expected temperature; defaults to false")
    private boolean mAbortOnTimeout = false;

    @Option(name = "post-idle-wait", description = "Additional time to wait in seconds, after "
        + "temperature has reached to target; defaults to 120s")
    private long mPostIdleWaitSecs = 120;

    public static final String DEVICE_TEMPERATURE_FILE_PATH_NAME = "device-temperature-file-path";

    @Option(
            name = DEVICE_TEMPERATURE_FILE_PATH_NAME,
            description =
                    "Name of file that contains device"
                            + "temperature. Example: /sys/class/hwmon/hwmon1/device/msm_therm")
    private String mDeviceTemperatureFilePath = null;

    public static final String DEVICE_TEMPERATURE_COMMAND_NAME = "device-temperature-command";

    @Option(
        name = DEVICE_TEMPERATURE_COMMAND_NAME,
        description =
            "Command for getting device temperature. If both device-temperature-file-path"
                + " and device-temperature-command are set,"
                + " only device-temperature-file-path will be actived."
                + " Example: dumpsys thermalservice"
                + " | grep 'mType=3' | grep Temperature | awk 'END{print}'")
    private String mDeviceTemperatureCommand =
        "dumpsys thermalservice | grep 'mType=3' | grep Temperature | awk 'END{print}'";

    @Option(name = "target-temperature", description = "Target Temperature that device should have;"
        + "defaults to 30c")
    private int mTargetTemperature = 30;

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mDeviceTemperatureFilePath == null && mDeviceTemperatureCommand == null) {
            return;
        }
        ITestDevice device = testInfo.getDevice();
        long start = System.currentTimeMillis();
        long maxWaitMs = mMaxWaitSecs * 1000;
        long intervalMs = mPollIntervalSecs * 1000;
        int deviceTemperature = Integer.MAX_VALUE;
        while (true) {
            // get device temperature
            deviceTemperature = getDeviceTemperature(device);
            if (deviceTemperature > mTargetTemperature) {
                CLog.d("Temperature is still high actual %d/expected %d",
                        deviceTemperature, mTargetTemperature);
            } else {
                CLog.i("Total time elapsed to get to %dc : %ds", mTargetTemperature,
                        (System.currentTimeMillis() - start) / 1000);
                break; // while loop
            }
            if ((System.currentTimeMillis() - start) > maxWaitMs) {
                CLog.w("Temperature is still high, actual %d/expected %d; waiting after %ds",
                        deviceTemperature, mTargetTemperature, maxWaitMs);
                if (mAbortOnTimeout) {
                    throw new TargetSetupError(String.format("Temperature is still high after wait "
                            + "timeout; actual %d/expected %d", deviceTemperature,
                            mTargetTemperature), device.getDeviceDescriptor());
                }
                break; // while loop
            }
            getRunUtil().sleep(intervalMs);
        }
        // extra idle time after reaching the targetl to stable the system
        getRunUtil().sleep(mPostIdleWaitSecs * 1000);
        CLog.d("Done waiting, total time elapsed: %ds",
                (System.currentTimeMillis() - start) / 1000);
    }

    /**
     * @param device
     * @throws DeviceNotAvailableException
     */
    protected int getDeviceTemperature (ITestDevice device)
            throws DeviceNotAvailableException, TargetSetupError {
        String result = executeDeviceTemperatureCommand(device);
        CLog.i(String.format("Temperature command output : %s", result));

        if (result == null || result.contains("No such file or directory")) {
            throw new TargetSetupError(
                    String.format("File %s doesn't exist", mDeviceTemperatureFilePath),
                    device.getDeviceDescriptor(),
                    InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        }
        return parseResult(result, device);
    }

    private String executeDeviceTemperatureCommand(ITestDevice device)
            throws DeviceNotAvailableException {
        if (mDeviceTemperatureFilePath != null) {
            return device.executeShellCommand(
                String.format("cat %s", mDeviceTemperatureFilePath)).trim();
        }
        if (mDeviceTemperatureCommand != null) {
            return device.executeShellCommand(mDeviceTemperatureCommand).trim();
        }
        return "UNKNOWN";
    }

    private int parseResult(String result, ITestDevice device) throws TargetSetupError {
        Matcher tempRawFormatMatcher = TEMPERATURE_RAW_FORMAT_REGEX.matcher(result);
        if (tempRawFormatMatcher.find()) {
            return Integer.parseInt(tempRawFormatMatcher.group(1));
        }
        Matcher tempFormatMatcher = TEMPERATURE_FORMAT_REGEX.matcher(result);
        if (tempFormatMatcher.find()) {
            return Integer.parseInt(tempFormatMatcher.group());
        }
        Matcher skinFormatMatcher = TEMPERATURE_SKIN_FORMAT_PATTERN.matcher(result);
        if (skinFormatMatcher.find()) {
            return Integer.parseInt(skinFormatMatcher.group(1));
        }
        throw new TargetSetupError(
            String.format("result content is not as expected. Content : %s", result),
                device.getDeviceDescriptor());
    }

    @VisibleForTesting
    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
