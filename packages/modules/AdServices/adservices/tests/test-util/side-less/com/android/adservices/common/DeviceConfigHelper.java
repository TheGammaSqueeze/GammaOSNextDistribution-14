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

import static com.android.adservices.common.AndroidSdk.Level.S;

import com.android.adservices.common.AndroidSdk.Level;
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
// TODO(b/294423183): use an existing class like DeviceConfigStateManager or DeviceConfigStateHelper
/**
 * Helper class to set {@link android.provider.DeviceConfig} flags and properly reset then to their
 * original values.
 *
 * <p><b>NOTE:</b>this class should not have any dependency on Android classes as its used both on
 * device and host side tests.
 *
 * <p><b>NOTE: </b>this class is not thread safe.
 */
final class DeviceConfigHelper {

    private static final Pattern FLAG_LINE_PATTERN = Pattern.compile("^(?<name>.*)=(?<value>.*)$");

    private final String mNamespace;
    private final Interface mInterface;
    private final Map<String, String> mFlagsToBeReset = new HashMap<>();
    private final Logger mLog;

    DeviceConfigHelper(InterfaceFactory interfaceFactory, String namespace, RealLogger logger) {
        mNamespace = Objects.requireNonNull(namespace);
        mInterface = Objects.requireNonNull(interfaceFactory).getInterface(mNamespace);
        if (mInterface == null) {
            throw new IllegalArgumentException(
                    "factory " + interfaceFactory + " returned null interface");
        }
        mLog = mInterface.mLog;
        mLog.v("Constructor: interface=%s, logger=%s, namespace=%s", mInterface, logger, namespace);
    }

    public void set(String name, String value) {
        savePreviousValue(name);
        setOnly(name, value);
    }

    public void setWithSeparator(String name, String value, String separator) {
        String oldValue = savePreviousValue(name);
        String newValue = oldValue == null ? value : oldValue + separator + value;
        setOnly(name, newValue);
    }

    public void reset() {
        int size = mFlagsToBeReset.size();
        if (size == 0) {
            mLog.d("reset(): not needed");
            return;
        }
        mLog.v("reset(): restoring %d flags", size);
        try {
            for (Entry<String, String> flag : mFlagsToBeReset.entrySet()) {
                String name = flag.getKey();
                String value = flag.getValue();
                if (value == null) {
                    delete(name);
                } else {
                    setOnly(name, value);
                }
            }
        } finally {
            mFlagsToBeReset.clear();
        }
    }

    public void setSyncDisabledMode(SyncDisabledModeForTest mode) {
        mInterface.setSyncDisabledModeForTest(mode);
    }

    public void clearFlags() {
        mInterface.clear();
    }

    @Override
    public String toString() {
        return "DeviceConfigHelper[mNamespace="
                + mNamespace
                + ", mInterface="
                + mInterface
                + ", mFlagsToBeReset="
                + mFlagsToBeReset
                + ", mLog="
                + mLog
                + "]";
    }
    // TODO(b/294423183): temporarily exposed as it's used by legacy helper methods on
    // AdServicesFlagsSetterRule
    String get(String name) {
        return mInterface.get(name, /* defaultValue= */ null);
    }

    public List<NameValuePair> getAll() {
        return mInterface.getAll();
    }

    private String savePreviousValue(String name) {
        String oldValue = get(name);
        if (mFlagsToBeReset.containsKey(name)) {
            mLog.v("Value of %s (%s) already saved for reset()", name, mFlagsToBeReset.get(name));
            return oldValue;
        }
        mLog.v("Saving %s=%s for reset", name, oldValue);
        mFlagsToBeReset.put(name, oldValue);
        return oldValue;
    }

    private void setOnly(String name, String value) {
        mInterface.syncSet(name, value);
    }

    private void delete(String name) {
        mInterface.syncDelete(name);
    }

    enum SyncDisabledModeForTest {
        NONE,
        PERSISTENT,
        UNTIL_REBOOT
    }

    // TODO(b/294423183); move to a separate file (and rename it?)?
    /**
     * Low-level interface for {@link android.provider.DeviceConfig}.
     *
     * <p>By default it uses {@code cmd device_config} to implement all methods, but subclasses
     * could override them (for example, device-side implementation could use {@code DeviceConfig}
     * instead.
     */
    protected abstract static class Interface extends AbstractDeviceGateway {

        private static final int CHANGE_CHECK_TIMEOUT_MS = 5_000;
        private static final int CHANGE_CHECK_SLEEP_TIME_MS = 500;

        protected final Logger mLog;
        protected final String mNamespace;

        protected Interface(String namespace, RealLogger logger) {
            mNamespace = Objects.requireNonNull(namespace);
            mLog = new Logger(Objects.requireNonNull(logger), DeviceConfigHelper.class);
        }

        public void setSyncDisabledModeForTest(SyncDisabledModeForTest mode) {
            String value = mode.name().toLowerCase();
            mLog.v("SyncDisabledModeForTest(%s)", value);

            // TODO(b/294423183): figure out a solution for R when needed
            if (getDeviceApiLevel().isAtLeast(S)) {
                // Command supported only on S+.
                runShellCommand("device_config set_sync_disabled_for_tests %s", value);
                return;
            }
        }

        /** Gets the value of a property. */
        public String get(String name, String defaultValue) {
            mLog.d("get(%s, %s): using runShellCommand", name, defaultValue);
            String value = runShellCommand("device_config get %s %s", mNamespace, name).trim();
            mLog.v(
                    "get(%s, %s): raw value is '%s' (is null: %b)",
                    name, defaultValue, value, value == null);
            if (!value.equals("null")) {
                return value;
            }
            // "null" could mean the value doesn't exist, or it's the string "null", so we need to
            // check them
            String allFlags = runShellCommand("device_config list %s", mNamespace);
            for (String line : allFlags.split("\n")) {
                if (line.equals(name + "=null")) {
                    mLog.v("Value of flag %s is indeed \"%s\"", name, value);
                    return value;
                }
            }
            return defaultValue;
        }

        /**
         * Sets the value of a property and blocks until the value is changed.
         *
         * @throws IllegalStateException if the value could not be updated.
         */
        public void syncSet(String name, @Nullable String value) {
            if (value == null) {
                syncDelete(name);
                return;
            }
            // TODO(b/300136201): check current value first and return right away if it matches

            // TODO(b/294423183): optimize code below (once it's unit tested), there's too much
            // duplication.
            String currentValue = get(name, /* defaultValue= */ null);
            boolean changed = !value.equals(currentValue);
            if (!changed) {
                mLog.v("syncSet(%s, %s): already %s, ignoring", name, value, value);
                return;
                // TODO(b/294423183): change it to return a boolean instead so the value doesn't
                // need to be restored. But there would be many corner cases (for example, what if
                // asyncSet() fails? What if the value is the same because it was set by the rule
                // before), so it's better to wait until we have unit tests for it.
            }
            long deadline = System.currentTimeMillis() + CHANGE_CHECK_TIMEOUT_MS;
            do {
                if (!asyncSet(name, value)) {
                    mLog.w("syncSet(%s, %s): call to asyncSet() returned false", name, value);
                    throw new IllegalStateException(
                            "Low-level call to set " + name + "=" + value + " returned false");
                }
                currentValue = get(name, /* defaultValue= */ null);
                changed = value.equals(currentValue);
                if (changed) {
                    mLog.v("change propagated, returning");
                    return;
                }
                if (System.currentTimeMillis() > deadline) {
                    mLog.e(
                            "syncSet(%s, %s): value didn't change after %d ms",
                            name, value, CHANGE_CHECK_TIMEOUT_MS);
                    throw new IllegalStateException(
                            "Low-level call to set "
                                    + name
                                    + "="
                                    + value
                                    + " succeeded, but value change was not propagated after "
                                    + CHANGE_CHECK_TIMEOUT_MS
                                    + "ms");
                }
                mLog.d(
                        "syncSet(%s, %s): current value is still %s, sleeping %d ms",
                        name, value, currentValue, CHANGE_CHECK_SLEEP_TIME_MS);
                sleepBeforeCheckingAgain(name);
            } while (true);
        }

        private void sleepBeforeCheckingAgain(String name) {
            mLog.v(
                    "Sleeping for %dms before checking value of %s again",
                    CHANGE_CHECK_SLEEP_TIME_MS, name);
            try {
                Thread.sleep(CHANGE_CHECK_SLEEP_TIME_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Sets the value of a property, without checking if it changed.
         *
         * @return whether the low-level {@code DeviceConfig} call succeeded.
         */
        public boolean asyncSet(String name, @Nullable String value) {
            mLog.d("asyncSet(%s, %s): using runShellCommand", name, value);
            runShellCommand("device_config put %s %s %s", mNamespace, name, value);
            // TODO(b/294423183): parse result
            return true;
        }

        /**
         * Deletes a property and blocks until the value is changed.
         *
         * @throws IllegalStateException if the value could not be updated.
         */
        public void syncDelete(String name) {
            // TODO(b/294423183): add wait logic here too
            asyncDelete(name);
        }

        /**
         * Deletes a property, without checking if it changed.
         *
         * @return whether the low-level {@code DeviceConfig} call succeeded.
         */
        public boolean asyncDelete(String name) {
            mLog.d("asyncDelete(%s): using runShellCommand", name);
            runShellCommand("device_config delete %s %s", mNamespace, name);
            // TODO(b/294423183): parse result
            return true;
        }

        /** Clears all flags. */
        public void clear() {
            runShellCommand("device_config reset untrusted_clear %s", mNamespace);

            // TODO(b/305877958): command above will "delete all settings set by untrusted packages,
            // which is packages that aren't a part of the system", so it might not delete them
            // all. In fact, after this method was first called, it cause test breakages because
            // disable_sdk_sandbox was still set. So, we should also explicitly delete all flags
            // that remain, but for now clearing those from untrusted packages is enough
            List<NameValuePair> currentFlags = getAll();
            if (!currentFlags.isEmpty()) {
                mLog.w(
                        "clear(): not all flags were deleted, which is a known limitation."
                                + " Following flags remain:\n\n"
                                + "%s",
                        currentFlags);
            }

            // TODO(b/300136201): should wait until they're all cleared
        }

        public List<NameValuePair> getAll() {
            String dump = runShellCommand("device_config list %s", mNamespace).trim();
            String[] lines = dump.split("\n");
            List<NameValuePair> allFlags = new ArrayList<>(lines.length);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher matcher = FLAG_LINE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String name = matcher.group("name");
                    String value = matcher.group("value");
                    allFlags.add(new NameValuePair(name, value));
                }
            }
            return allFlags;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }

        /** Gets the device API level. */
        public abstract Level getDeviceApiLevel();
    }

    /** Factory for {@link Interface} objects. */
    interface InterfaceFactory {

        /**
         * Gets an {@link Interface} for the given {@link android.provider.DeviceConfig} namespace.
         */
        Interface getInterface(String namespace);
    }
}
