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

package android.test.featureflag;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

/*
An experimental test class to verify Android Test Notification (ATN) configurations.

Sample test command:
atest FeatureFlagTest -- --template:map \
preparers=template/preparers/feature-flags --flag-value test_namespace/pass_rate=100
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class FeatureFlagTest implements IDeviceTest {
    private static final String NAMESPACE = "test_namespace";
    private static final String FLAG_NAME = "pass_rate";
    private static final int MAX_PASS_RATE = 100;

    private ITestDevice device;

    @Test
    public void testFlagValue () throws DeviceNotAvailableException {
        String flagValue = getFlagValue(NAMESPACE, FLAG_NAME);
        CLog.i("Flag value is:\n" + flagValue);
        int passRate = Integer.parseInt(flagValue);

        Assert.assertTrue(getExpectedTestResult(passRate));
    }

    // Run an ADB shell command and extract flag value from the output.
    private String getFlagValue(String namespace, String flag) throws DeviceNotAvailableException {
        CommandResult result =
                device.executeShellV2Command(
                        String.format("device_config get %s %s", namespace, flag));
        return result.getStdout().trim();
    }

    // Determine expected test result (boolean) based on passRate.
    private boolean getExpectedTestResult(int passRate) {
        Random rand = new Random();
        int randomValue = rand.nextInt(MAX_PASS_RATE);
        return randomValue < passRate;
    }

    @Override
    public void setDevice(ITestDevice device) {
        this.device = device;
    }

    @Override
    public ITestDevice getDevice() {
        return this.device;
    }
}