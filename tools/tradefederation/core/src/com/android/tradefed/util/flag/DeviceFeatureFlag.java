/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.util.flag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceFeatureFlag {

    // Expected flag format (same as output by "device_config list"):
    // namespace/name=[value]
    // Note value is optional.
    private static final Pattern FLAG_PATTERN =
            Pattern.compile("^(?<namespace>[^\\s/=]+)/(?<name>[^\\s/=]+)=(?<value>.*)$");

    private final String namespace;
    private final String flagName;
    private final String flagValue;

    /**
     * Constructor to create a new DeviceFeatureFlag object.
     *
     * @param flagString A device config flag string in the format of "namespace/flagName=flagValue"
     * @throws IllegalArgumentException if the flagString parameter cannot be parsed
     */
    public DeviceFeatureFlag(String flagString) {
        Matcher match = FLAG_PATTERN.matcher(flagString);
        if (!match.matches()) {
            throw new IllegalArgumentException(
                    String.format("Failed to parse flag data: %s", flagString));
        }
        namespace = match.group("namespace");
        flagName = match.group("name");
        flagValue = match.group("value");
    }

    /**
     * Get the namespace of the DeviceFeatureFlag. E.g. "namespace" in flag string
     * "namespace/flagName=flagValue".
     *
     * @return namespace string
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get the flag name of the DeviceFeatureFlag. E.g. "flagName" in flag string
     * "namespace/flagName=flagValue".
     *
     * @return flag name string
     */
    public String getFlagName() {
        return flagName;
    }

    /**
     * Get the flag value of the DeviceFeatureFlag. E.g. "flagValue" in flag string
     * "namespace/flagName=flagValue".
     *
     * @return flag value string
     */
    public String getFlagValue() {
        return flagValue;
    }

    /**
     * Convert the DeviceFeatureFlag object to a flag string in the format of
     * "namespace/flagName=flagValue"
     *
     * @return formatted flag string
     */
    @Override
    public String toString() {
        return String.format("%s/%s=%s", namespace, flagName, flagValue);
    }
}
