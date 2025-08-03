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

package android.cts.host.utils;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.ITestInformationReceiver;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@link TestRule} class that prevents Phenotype overriding device_config changes
 */
public class DisableDeviceConfigSyncRule implements TestRule {

    private final ITestInformationReceiver mTestInformationReceiver;

    public DisableDeviceConfigSyncRule(ITestInformationReceiver testInformationReceiver) {
        mTestInformationReceiver = testInformationReceiver;
    }

    private ITestDevice getDevice() {
        return mTestInformationReceiver.getTestInformation().getDevice();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                ITestDevice device = getDevice();
                String previousSyncDisabledStatus = DeviceConfigHelper.deviceDisableConfigSync(
                        device);
                try {
                    base.evaluate();
                } finally {
                    DeviceConfigHelper.deviceRestoreConfigSync(device, previousSyncDisabledStatus);
                }
            }
        };
    }
}
