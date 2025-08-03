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

package com.android.catbox.targetpreparer;

import com.android.ddmlib.Log.LogLevel;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.android.tradefed.log.LogUtil.CLog;

import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * SkipTestPreparer is an {@link ITargetPreparer} that skips tests based on values of
 * certain ADB properties on the device
 */
@OptionClass(alias = "skip-test-preparer")
public class SkipTestPreparer extends BaseTargetPreparer {

    @Option(name = "comp-property", description = "ADB property of device to check against")
    private String mCompProp;

    @Option(
            name = "comp-property-int-value",
            description = "Integer value of ADB property to check against")
    private int mPropVal;

    @Option(
            name = "int-comparison-operator",
            description = "Operator to compare expected and actual int values")
    private String mCompOperator;

    private static final Set<String> supportedOperators = ImmutableSet.of("lt", "gt", "eq", "neq");

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
           BuildError, DeviceNotAvailableException {

        // Skip this preparer if @Option values are not provided
        if (mCompProp == null || mCompOperator == null) {
            CLog.logAndDisplay(LogLevel.INFO,
                "Missing value for comp-property or comp-property-int-value. Skipping preparer.");
            return;
        }

        boolean skipTestFlag = false;

        if (!supportedOperators.contains(mCompOperator)) {
            CLog.logAndDisplay(LogLevel.WARN,
                String.format("Incompatible operator %s. Skipping preparer", mCompOperator));
            CLog.logAndDisplay(LogLevel.INFO,
                String.format("Supported operators are %s", String.join(",", supportedOperators)));
            return;
        }

        int devicePropertyValue =
            Integer.parseInt(
                device.executeShellCommand(String.format("getprop %s", mCompProp)).trim());
        CLog.logAndDisplay(LogLevel.INFO,
            String.format("%s returned %d", mCompProp, devicePropertyValue)
        );

        skipTestFlag = getSkipTestFlag(devicePropertyValue);

        if (skipTestFlag) {
            CLog.logAndDisplay(LogLevel.INFO, "Skip condition satisfied. Skipping test module.");
            throw new TargetSetupError(
                String.format("Test incompatible with %s = %d", mCompProp, devicePropertyValue),
                device.getDeviceDescriptor());
        } else {
            CLog.logAndDisplay(LogLevel.INFO,
                "Skip condition not satisfied. Proceeding with test module.");
        }
    }

    private boolean getSkipTestFlag(int devicePropertyValue) {
        switch (mCompOperator) {
            case "lt":
                CLog.logAndDisplay(LogLevel.INFO,
                    "Checking skip condition %d < %d", devicePropertyValue, mPropVal);
                return devicePropertyValue < mPropVal;

            case "gt":
                CLog.logAndDisplay(LogLevel.INFO,
                    "Checking skip condition %d > %d", devicePropertyValue, mPropVal);
                return devicePropertyValue > mPropVal;

            case "eq":
                CLog.logAndDisplay(LogLevel.INFO,
                    "Checking skip condition %d == %d", devicePropertyValue, mPropVal);
                return devicePropertyValue == mPropVal;

            case "neq":
                CLog.logAndDisplay(LogLevel.INFO,
                    "Checking condition %d != %d", devicePropertyValue, mPropVal);
                return devicePropertyValue != mPropVal;

            default:
                return false;
        }
    }
}
