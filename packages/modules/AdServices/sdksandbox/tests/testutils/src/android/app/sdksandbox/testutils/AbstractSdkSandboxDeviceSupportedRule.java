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
package android.app.sdksandbox.testutils;

import com.android.adservices.common.Logger;
import com.android.adservices.common.Logger.RealLogger;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;

// NOTE: this class is used by device and host side, so it cannot have any Android dependency
/**
 * Rule used to properly check a test behavior depending on whether the device supports {@code
 * SdkSandbox}.
 *
 * <p>See {@link com.android.adservices.common.AdServicesDeviceSupportedRule} for usage - this rule
 * is analogous to it.
 */
abstract class AbstractSdkSandboxDeviceSupportedRule implements TestRule {

    protected final Logger mLog;

    /** Default constructor. */
    AbstractSdkSandboxDeviceSupportedRule(RealLogger logger) {
        mLog = new Logger(Objects.requireNonNull(logger), "SdkSandboxDeviceSupportedRule");
        mLog.d("Constructor: logger=%s", logger);
    }

    /** Checks whether {@code SdkSandbox} is supported by the device. */
    public abstract boolean isSdkSandboxSupportedOnDevice();

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String testName = description.getDisplayName();
                boolean isDeviceSupported = isSdkSandboxSupportedOnDevice();
                mLog.d("apply(): testName=%s, isDeviceSupported=%b", testName, isDeviceSupported);
                if (!isDeviceSupported) {
                    throw new AssumptionViolatedException("SdkSandbox not supported on device");
                }
                base.evaluate();
            }
        };
    }
}
