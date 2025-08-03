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

package com.android.catbox.targetpreparer;

import com.android.ddmlib.Log.LogLevel;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import com.android.tradefed.log.LogUtil.CLog;

import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * SetPropPreparer is an {@link ITargetPreparer} that sets properties on the device
 */
@OptionClass(alias = "set-device-property")
public class SetDevicePropertyPreparer implements ITargetPreparer {

    /* Allow user to pass in property and value to be set */
    @Option(name = "set-property", description = "Map of properties and values to set and restored")
    private Map<String, String> setPropertyMap = new HashMap<String, String>();

    /* Assign the property value to class variable */
    private static Map<String, String> sharedPropValues = new HashMap<String, String>();
    private final String EMPTY_STRING = "''";

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        CLog.logAndDisplay(
                LogLevel.INFO,
                "Starting set property target preparer"
        );

        /* If no properties or values supplied, return immediately */
        if (setPropertyMap.isEmpty()) {
            CLog.logAndDisplay(
                    LogLevel.INFO,
                    "No properties to set. Skipping target preparer"
            );
            return;
        }

        for (Map.Entry<String, String> entry : setPropertyMap.entrySet()) {

            /* Get the existing values for the properties */
            sharedPropValues.put(entry.getKey(),
                    device.executeShellCommand(String.format("getprop %s", entry.getKey())).trim());

            /* Set the property to the value provided */
            device.executeShellCommand(
                    String.format("setprop %s %s", entry.getKey(), entry.getValue())
            );
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        CLog.logAndDisplay(
                LogLevel.INFO,
                "Starting Device Property teardown - resetting device properties"
        );

        /* Reset the properties back to their original values */
        for (Map.Entry<String, String> entry : sharedPropValues.entrySet()) {
            CLog.logAndDisplay(
                    LogLevel.DEBUG,
                    String.format("Resetting prop %s to %s", entry.getKey(), entry.getValue())
            );

            /* If property did not exist before, set to empty string */
            if (!entry.getValue().isEmpty()) {
                device.executeShellCommand(
                        String.format("setprop %s %s", entry.getKey(), entry.getValue())
                );
            } else {
                device.executeShellCommand(
                        String.format("setprop %s %s", entry.getKey(), EMPTY_STRING)
                );
            }
        }
    }
}