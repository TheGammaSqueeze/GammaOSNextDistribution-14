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

package android.credentials.cts.testcore;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.compatibility.common.util.DeviceConfigStateManager;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This ensures that the current device config state must match some expected value. If it does not,
 * the rule fails.
 */
public class DeviceConfigStateRequiredRule implements TestRule {
    private static final String TAG = "DeviceConfigStateRequiredRule";

    private final String mDeviceConfigState;
    private final String mDeviceConfigNamespace;
    private final String mExpectedValue;
    private final boolean mHasDeviceConfigState;
    private final Context mDeviceConfigContext;

    public DeviceConfigStateRequiredRule(String deviceConfigState, String deviceConfigNamespace,
            Context deviceConfigContext, String expectedValue) {

        mDeviceConfigState = deviceConfigState;
        mDeviceConfigNamespace = deviceConfigNamespace;
        mDeviceConfigContext = deviceConfigContext;
        mExpectedValue = expectedValue;
        mHasDeviceConfigState = hasFeature(mDeviceConfigState, mDeviceConfigNamespace,
                mDeviceConfigContext, mExpectedValue);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                if (!mHasDeviceConfigState) {
                    Log.d(TAG, "skipping "
                            + description.getClassName() + "#" + description.getMethodName()
                            + " because device does not have device config state '"
                            + mDeviceConfigState + "'");
                    assumeTrue("Device does not have config state '" + mDeviceConfigState
                            + "'", mHasDeviceConfigState);
                    return;
                }
                base.evaluate();
            }
        };
    }

    @Override
    public String toString() {
        return "DeviceConfigStateRequiredRule [" + mDeviceConfigState + "]";
    }

    public static boolean hasFeature(String deviceConfigState, String deviceConfigNamespace,
            Context deviceConfigContext, String mExpectedValue) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(deviceConfigContext, deviceConfigNamespace,
                        deviceConfigState);
        final String currentValue = deviceConfigStateManager.get();
        if (currentValue == null || TextUtils.equals(currentValue, mExpectedValue)) {
            Log.v(TAG, "No change in config: " + deviceConfigStateManager);
            return true; // current rule matches what is expected
        }
        return false;
    }
}
