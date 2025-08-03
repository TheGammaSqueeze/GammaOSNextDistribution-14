/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.app.time.cts.shell;

import androidx.annotation.NonNull;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A class for interacting with the {@code device_config} service via the shell "cmd" command-line
 * interface. Some behavior it supports is not available via the Android @SystemApi.
 * See {@link com.android.providers.settings.DeviceConfigService} for the shell command
 * implementation details.
 */
public class DeviceConfigShellHelper {

    /**
     * Value used with {@link #setSyncModeForTest}, {@link #getSyncDisabled()},
     * {@link #setSyncDisabled(String)}.
     */
    public static final String SYNC_DISABLED_MODE_NONE = "none";

    /**
     * Value used with {@link #setSyncModeForTest}, {@link #getSyncDisabled()},
     * {@link #setSyncDisabled(String)}.
     */
    public static final String SYNC_DISABLED_MODE_UNTIL_REBOOT = "until_reboot";

    /**
     * Value used with {@link #setSyncModeForTest}, {@link #getSyncDisabled()},
     * {@link #setSyncDisabled(String)}.
     */
    public static final String SYNC_DISABLED_MODE_PERSISTENT = "persistent";

    private static final String SERVICE_NAME = "device_config";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    @NonNull
    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public DeviceConfigShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /**
     * Executes "get_sync_disabled_for_tests". Returns the output, expected to be one of
     * {@link #SYNC_DISABLED_MODE_PERSISTENT}, {@link #SYNC_DISABLED_MODE_UNTIL_REBOOT} or
     * {@link #SYNC_DISABLED_MODE_NONE}.
     */
    public String getSyncDisabled() throws Exception {
        String cmd = SHELL_CMD_PREFIX + "get_sync_disabled_for_tests";
        return mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /**
     * Executes "set_sync_disabled_for_tests". Accepts one of
     * {@link #SYNC_DISABLED_MODE_PERSISTENT}, {@link #SYNC_DISABLED_MODE_UNTIL_REBOOT} or
     * {@link #SYNC_DISABLED_MODE_NONE}.
     */
    public void setSyncDisabled(String syncDisabledMode) throws Exception {
        String cmd = String.format(
                SHELL_CMD_PREFIX + "set_sync_disabled_for_tests %s", syncDisabledMode);
        mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /**
     * Executes "list" with a namespace.
     */
    public NamespaceEntries list(String namespace) throws Exception {
        Objects.requireNonNull(namespace);

        String cmd = String.format(SHELL_CMD_PREFIX + "list %s", namespace);
        String output = mShellCommandExecutor.executeToTrimmedString(cmd);
        Map<String, String> keyValues = new HashMap();
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separatorPos = line.indexOf('=');
                String key = line.substring(0, separatorPos);
                String value = line.substring(separatorPos + 1);
                keyValues.put(key, value);
            }
        }
        return new NamespaceEntries(namespace, keyValues);
    }

    /** Executes "put" without the trailing "default" argument. */
    public void put(String namespace, String key, String value) throws Exception {
        put(namespace, key, value, /*makeDefault=*/false);
    }

    /** Executes "put". */
    public void put(String namespace, String key, String value, boolean makeDefault)
            throws Exception {
        String cmd = String.format(SHELL_CMD_PREFIX + "put %s %s %s", namespace, key, value);
        if (makeDefault) {
            cmd += " default";
        }
        mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /** Executes "delete". */
    public void delete(String namespace, String key) throws Exception {
        String cmd = String.format(SHELL_CMD_PREFIX + "delete %s %s", namespace, key);
        mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /**
     * A test helper method that captures the current sync mode and set of namespace values and sets
     * the current sync mode. See {@link #restoreDeviceConfigStateForTest(PreTestState)}.
     */
    public PreTestState setSyncModeForTest(String syncMode, String... namespacesToSave)
            throws Exception {
        List<NamespaceEntries> savedValues = new ArrayList<>();
        for (String namespacetoSave : namespacesToSave) {
            NamespaceEntries namespaceValues = list(namespacetoSave);
            savedValues.add(namespaceValues);
        }
        PreTestState preTestState = new PreTestState(getSyncDisabled(), savedValues);
        setSyncDisabled(syncMode);
        return preTestState;
    }

    /**
     * Restores the sync mode after a test. See {@link #setSyncModeForTest}.
     */
    public void restoreDeviceConfigStateForTest(PreTestState restoreState) throws Exception {
        for (NamespaceEntries oldEntries : restoreState.mSavedValues) {
            NamespaceEntries currentEntries = list(oldEntries.namespace);

            MapDifference<String, String> difference =
                    Maps.difference(oldEntries.keyValues, currentEntries.keyValues);
            deleteAll(oldEntries.namespace, difference.entriesOnlyOnRight());
            putAll(oldEntries.namespace, difference.entriesOnlyOnLeft());
            Map<String, String> entriesToUpdate =
                    subMap(oldEntries.keyValues, difference.entriesDiffering().keySet());
            putAll(oldEntries.namespace, entriesToUpdate);
        }
        setSyncDisabled(restoreState.mSyncDisabledMode);
    }

    private static <X, Y> Map<X, Y> subMap(Map<X, Y> keyValues, Set<X> keySet) {
        return Maps.filterKeys(keyValues, keySet::contains);
    }

    private void putAll(String namespace, Map<String, String> entriesToAdd) throws Exception {
        for (Map.Entry<String, String> entryToAdd : entriesToAdd.entrySet()) {
            put(namespace, entryToAdd.getKey(), entryToAdd.getValue());
        }
    }

    private void deleteAll(String namespace, Map<String, String> entriesToDelete) throws Exception {
        for (Map.Entry<String, String> entryToDelete : entriesToDelete.entrySet()) {
            delete(namespace, entryToDelete.getKey());
        }
    }

    /** Opaque saved state information. */
    public static class PreTestState {
        private final String mSyncDisabledMode;
        private final List<NamespaceEntries> mSavedValues = new ArrayList<>();

        private PreTestState(String syncDisabledMode, List<NamespaceEntries> values) {
            mSyncDisabledMode = syncDisabledMode;
            mSavedValues.addAll(values);
        }
    }

    public static class NamespaceEntries {
        public final String namespace;
        public final Map<String, String> keyValues = new HashMap<>();

        public NamespaceEntries(String namespace, Map<String, String> keyValues) {
            this.namespace = Objects.requireNonNull(namespace);
            this.keyValues.putAll(Objects.requireNonNull(keyValues));
        }
    }
}
