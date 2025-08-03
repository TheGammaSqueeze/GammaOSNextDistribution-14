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

package com.android.sts.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;

import java.util.Optional;

/** Various system-related helper functions */
public class SystemUtil {
    private SystemUtil() {}

    /**
     * Set the value of a property and set it back to old value upon closing.
     *
     * @param device the device to use
     * @param name the name of the property to set
     * @param value the value that the property should be set to
     * @return AutoCloseable that resets the property back to old value upon closing
     */
    public static AutoCloseable withProperty(
            final ITestDevice device, final String name, final String value)
            throws DeviceNotAvailableException {
        final String oldValue = device.getProperty(name);
        assumeTrue("Could not set property: " + name, device.setProperty(name, value));
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                assumeTrue(
                        "Could not reset property: " + name,
                        device.setProperty(name, oldValue == null ? "" : oldValue));
            }
        };
    }

    /**
     * Set the value of a device setting and set it back to old value upon closing.
     *
     * @param device the device to use
     * @param namespace "system", "secure", or "global"
     * @param key setting key to set
     * @param value setting value to set to
     * @return AutoCloseable that resets the setting back to existing value upon closing.
     */
    public static AutoCloseable withSetting(
            final ITestDevice device, final String namespace, final String key, String value)
            throws DeviceNotAvailableException {
        String getSettingRes = device.getSetting(namespace, key);
        final Optional<String> oldSetting = Optional.ofNullable(getSettingRes);

        device.setSetting(namespace, key, value);
        assumeThat(
                String.format("Could not set %s:%s to %s", namespace, key, value),
                device.getSetting(namespace, key),
                equalTo(value));

        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                if (!oldSetting.isPresent()) {
                    String cmd = String.format("settings delete %s %s", namespace, key);
                    CommandResult res = CommandUtil.runAndCheck(device, cmd);
                } else {
                    String oldValue = oldSetting.get();
                    device.setSetting(namespace, key, oldValue);
                    String failMsg =
                            String.format("could not reset '%s' back to '%s'", key, oldValue);
                    assumeThat(failMsg, device.getSetting(namespace, key), equalTo(oldValue));
                }
            }
        };
    }
}
