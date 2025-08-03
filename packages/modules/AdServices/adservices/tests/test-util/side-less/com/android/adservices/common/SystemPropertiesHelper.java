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
package com.android.adservices.common;

import com.android.adservices.common.Logger.RealLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO(b/294423183): add unit tests
/**
 * Helper class to set {@link android.os.SystemProperties} and properly reset then to their original
 * values.
 *
 * <p><b>NOTE:</b>this class should not have any dependency on Android classes as its used both on
 * device and host side tests.
 *
 * <p><b>NOTE: </b>this class is not thread safe.
 */
public final class SystemPropertiesHelper {

    private final Map<String, String> mPropsToBeReset = new HashMap<>();

    private final Logger mLog;
    private final Interface mInterface;

    public SystemPropertiesHelper(Interface helperInterface, RealLogger logger) {
        mInterface = Objects.requireNonNull(helperInterface);
        mLog = new Logger(Objects.requireNonNull(logger), SystemPropertiesHelper.class);
        mLog.v("Constructor: interface=%s, logger=%s", helperInterface, logger);
    }

    public void set(String name, String value) {
        savePreviousValue(name);
        setOnly(name, value);
    }

    public void reset() {
        int size = mPropsToBeReset.size();
        if (size == 0) {
            mLog.d("reset(): not needed");
            return;
        }
        mLog.v("reset(): restoring %d system properties", size);
        try {
            for (Entry<String, String> flag : mPropsToBeReset.entrySet()) {
                setOnly(flag.getKey(), flag.getValue());
            }
        } finally {
            mPropsToBeReset.clear();
        }
    }

    /** Gets all properties that matches the given {@code matcher}. */
    public List<NameValuePair> getAll(NameValuePair.Matcher matcher) {
        return mInterface.getAll(matcher);
    }

    @Override
    public String toString() {
        return "SystemPropertiesHelper [mInterface="
                + mInterface
                + ",mPropsToBeReset="
                + mPropsToBeReset
                + ", mLog="
                + mLog
                + ", regex="
                + Interface.PROP_LINE_REGEX
                + "]";
    }

    private String get(String name) {
        return mInterface.get(name);
    }

    private void savePreviousValue(String name) {
        if (mPropsToBeReset.containsKey(name)) {
            mLog.v("Value of %s (%s) already saved for reset()", name, mPropsToBeReset.get(name));
            return;
        }
        String oldValue = get(name);
        mLog.v("Saving %s=%s for reset", name, oldValue);
        mPropsToBeReset.put(name, oldValue);
    }

    private void setOnly(String name, String value) {
        mInterface.set(name, value);
    }

    /** Low-level interface for {@link android.os.SystemProperties}. */
    protected abstract static class Interface extends AbstractDeviceGateway {

        private static final String PROP_LINE_REGEX = "^\\[(?<name>.*)\\].*\\[(?<value>.*)\\]$";
        private static final Pattern PROP_LINE_PATTERN = Pattern.compile(PROP_LINE_REGEX);

        protected final Logger mLog;

        Interface(RealLogger logger) {
            mLog = new Logger(Objects.requireNonNull(logger), DeviceConfigHelper.class);
        }

        /** Gets the value of a property. */
        abstract String get(String name);

        /**
         * Gets the name (including the prefix) and value of all properties that have the given
         * {@code prefixes}.
         */
        List<NameValuePair> getAll(NameValuePair.Matcher propertyMatcher) {
            List<NameValuePair> allProperties = new ArrayList<>();
            String properties = runShellCommand("getprop");
            // NOTE: prefix most likely have . (like debug.adservices), but that's fine
            String[] lines = properties.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher lineMatcher = PROP_LINE_PATTERN.matcher(line);
                if (lineMatcher.matches()) {
                    String name = lineMatcher.group("name");
                    String value = lineMatcher.group("value");
                    NameValuePair property = new NameValuePair(name, value);
                    if (propertyMatcher.matches(property)) {
                        allProperties.add(property);
                    }
                }
            }
            return allProperties;
        }

        /** Sets the value of a property. */
        void set(String name, String value) {
            mLog.v("set(%s, %s)", name, value);

            if (value != null && !value.isEmpty()) {
                runShellCommand("setprop %s %s", name, value);
                return;
            }
            // TODO(b/293132368): UIAutomation doesn't support passing a "" or '' - it will quote
            // them, which would cause the property value to be "" or '', not the empty String.
            // Another approach would be calling SystemProperties.set(), but that method is hidden
            // (b/294414609)
            mLog.w(
                    "NOT resetting property %s to empty String as it's not supported by"
                            + " runShellCommand(), but setting it as null",
                    name);
            runShellCommand("setprop %s null", name);
        }
    }
}
