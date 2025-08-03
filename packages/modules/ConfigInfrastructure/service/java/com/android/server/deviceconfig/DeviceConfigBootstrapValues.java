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

package com.android.server.deviceconfig;

import android.annotation.SuppressLint;
import android.provider.DeviceConfig;
import android.util.Slog;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * @hide
 */
public class DeviceConfigBootstrapValues {
    private static final String TAG = "DeviceConfig";
    private static final String SYSTEM_OVERRIDES_PATH = "file:///system/etc/device-config-defaults";
    private static final String META_NAMESPACE = "DeviceConfigBootstrapValues";
    private static final String META_KEY = "processed_values";

    private final String defaultValuesPath;

    public DeviceConfigBootstrapValues() {
        this(SYSTEM_OVERRIDES_PATH);
    }

    public DeviceConfigBootstrapValues(String defaultValuesPath) {
        this.defaultValuesPath = defaultValuesPath;
    }

    /**
     * Performs the logic to apply bootstrap values when needed.
     *
     * If a file with the bootstrap values exists and they haven't been parsed before,
     * it will parse the file and apply the values.
     *
     * @throws IOException if there's a problem reading the bootstrap file
     * @throws RuntimeException if setting the values in DeviceConfig throws an exception
     */
    public void applyValuesIfNeeded() throws IOException {
        if (getPath().toFile().exists()) {
            if (checkIfHasAlreadyParsedBootstrapValues()) {
                Slog.i(TAG, "Bootstrap values already parsed, not processing again");
            } else {
                parseAndApplyBootstrapValues();
                Slog.i(TAG, "Parsed bootstrap values");
            }
        } else {
            Slog.i(TAG, "Bootstrap values not found");
        }
    }

    @SuppressLint("MissingPermission")
    private boolean checkIfHasAlreadyParsedBootstrapValues() {
        DeviceConfig.Properties properties = DeviceConfig.getProperties(META_NAMESPACE);
        return properties.getKeyset().size() > 0;
    }

    @SuppressLint("MissingPermission")
    private void parseAndApplyBootstrapValues() throws IOException {
        Path path = getPath();
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> processLine(line));
        }
        // store a property in DeviceConfig so that we know we have successufully
        // processed this
        writeToDeviceConfig(META_NAMESPACE, META_KEY, "true");
    }

    private void processLine(String line) {
        // contents for each line:
        // <namespace>:<package>.<flag-name>=[enabled|disabled]
        // we actually use <package>.<flag-name> combined in calls into DeviceConfig
        int namespaceDelimiter = line.indexOf(':');
        String namespace = line.substring(0, namespaceDelimiter);
        if (namespaceDelimiter < 1) {
            throw new IllegalArgumentException("Unexpectedly found : at index "
                    + namespaceDelimiter);
        }
        int valueDelimiter = line.indexOf('=');
        if (valueDelimiter < 5) {
            throw new IllegalArgumentException("Unexpectedly found = at index " + valueDelimiter);
        }
        String key = line.substring(namespaceDelimiter + 1, valueDelimiter);
        String value = line.substring(valueDelimiter + 1);
        String val;
        if ("enabled".equals(value)) {
            val = "true";
        } else if ("disabled".equals(value)) {
            val = "false";
        } else {
            throw new IllegalArgumentException("Received unexpected value: " + value);
        }
        writeToDeviceConfig(namespace, key, val);
    }

    @SuppressLint("MissingPermission")
    private void writeToDeviceConfig(String namespace, String key, String value) {
        boolean result = DeviceConfig.setProperty(namespace, key, value, /* makeDefault= */ true);
        if (!result) {
            throw new RuntimeException("Failed to set DeviceConfig property [" + namespace + "] "
                    + key + "=" + value);
        }
    }

    private Path getPath() {
        return Path.of(URI.create(defaultValuesPath));
    }
}
