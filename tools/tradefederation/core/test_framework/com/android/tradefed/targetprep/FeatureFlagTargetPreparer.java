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
package com.android.tradefed.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.util.flag.DeviceFeatureFlag;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.base.Strings;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Updates the DeviceConfig (feature flags tuned by a remote service).
 *
 * <p>This can be used to reproduce the state of a device (by dumping all flag values to a file
 * using `adb shell device_config list`) or to bulk enable/disable flags (all-on/all-off testing).
 *
 * <p>Example usage:
 *
 * <ul>
 *   <li>To use for all-on/all-off testing, specify the necessary flag file:
 *       <pre>--flag-file=flag_file_path</pre>
 *   <li>To override one or more flags, specify their values (can be combined with flag files):
 *       <pre>--flag-file=flag_file_path --flag-value=namespace/name=value</pre>
 *   <li>To use for reversibility testing, specify the all-on file followed by the all-off file, and
 *       enable rebooting between the two files:
 *       <pre>--flag-file=all_on_file_path --flag-file=all_off_file_path --reboot-between-flag-files
 *       </pre>
 * </ul>
 *
 * <p>Should be used in combination with {@link DeviceSetup} to disable DeviceConfig syncing during
 * the test which could overwrite the changes made by this preparer.
 */
@OptionClass(alias = "feature-flags")
public class FeatureFlagTargetPreparer extends BaseTargetPreparer {

    @Option(
            name = "flag-file",
            description = "File containing flag values to apply. Can be repeated.")
    private List<File> mFlagFiles = new ArrayList<>();

    @Option(name = "flag-value", description = "Additional flag values to apply. Can be repeated.")
    private List<String> mFlagValues = new ArrayList<>();

    @Option(
            name = "reboot-between-flag-files",
            description = "Enables reboots after each input file. Used for reversibility testing.")
    private boolean mRebootBetweenFlagFiles = false;

    private final Map<String, Map<String, String>> mFlagsToRestore = new HashMap<>();

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInformation.getDevice();
        if (mFlagFiles.isEmpty() && mFlagValues.isEmpty()) {
            CLog.i("No flag-file or flag-value option provided, skipping");
            return;
        }

        // Parse input flag files and values.
        List<Map<String, Map<String, String>>> flagBundles = new ArrayList<>();
        for (File flagFile : mFlagFiles) {
            if (flagFile == null || !flagFile.isFile()) {
                throw new TargetSetupError(
                        String.format("Flag file '%s' not found", flagFile),
                        device.getDeviceDescriptor(),
                        InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
            }
            flagBundles.add(parseFlags(device, flagFile));
        }
        if (!mFlagValues.isEmpty()) {
            flagBundles.add(parseFlags(device, mFlagValues, /* throwIfInvalid */ true));
        }

        // Keep track of initial flags to be able to restore them during tearDown.
        Map<String, Map<String, String>> initialFlags = null;
        boolean flagsUpdated = false;

        for (Map<String, Map<String, String>> targetFlags : flagBundles) {
            // Determine current flag values to diff against target flag values.
            Map<String, Map<String, String>> currentFlags = listFlags(device);
            if (initialFlags == null) {
                initialFlags = currentFlags;
            }

            for (String namespace : targetFlags.keySet()) {
                // Ignore unchanged flag values.
                Map<String, String> currentValues = currentFlags.getOrDefault(namespace, Map.of());
                Map<String, String> targetValues = targetFlags.get(namespace);
                // target flags - current flags = new flags to apply
                targetValues.entrySet().removeAll(currentValues.entrySet());
                // new flags to apply - initial flags = new flags to restore
                updateFlagsToRestore(
                        namespace, initialFlags.getOrDefault(namespace, Map.of()), targetValues);
            }

            if (targetFlags.values().stream().allMatch(Map::isEmpty)) {
                continue; // No flags to update.
            }
            updateFlags(device, targetFlags);
            flagsUpdated = true;
            if (mRebootBetweenFlagFiles) {
                device.reboot();
            }
        }

        if (!mRebootBetweenFlagFiles && flagsUpdated) {
            device.reboot();
        }
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        if (e instanceof DeviceNotAvailableException
                || mFlagsToRestore.isEmpty()
                || mFlagsToRestore.values().stream().allMatch(Map::isEmpty)) {
            return;
        }
        try {
            ITestDevice device = testInformation.getDevice();
            updateFlags(device, mFlagsToRestore);
            device.reboot();
        } catch (TargetSetupError tse) {
            CLog.e("Failed to restore flags: %s", tse);
        }
    }

    private Map<String, Map<String, String>> listFlags(ITestDevice device)
            throws DeviceNotAvailableException, TargetSetupError {
        String values = runCommand(device, "device_config list");
        try (ByteArrayInputStream stream = new ByteArrayInputStream(values.getBytes());
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return parseFlags(device, reader.lines().collect(Collectors.toList()), false);
        } catch (IOException ioe) {
            throw new TargetSetupError(
                    "Failed to parse device flags",
                    ioe,
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    private Map<String, Map<String, String>> parseFlags(ITestDevice device, File flagFile)
            throws TargetSetupError {
        try (FileInputStream stream = new FileInputStream(flagFile);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return parseFlags(device, reader.lines().collect(Collectors.toList()), false);
        } catch (IOException ioe) {
            throw new TargetSetupError(
                    "Failed to parse flag file",
                    ioe,
                    device.getDeviceDescriptor(),
                    InfraErrorIdentifier.LAB_HOST_FILESYSTEM_ERROR);
        }
    }

    private Map<String, Map<String, String>> parseFlags(
            ITestDevice device, List<String> lines, boolean throwIfInvalid)
            throws TargetSetupError {
        Map<String, Map<String, String>> flags = new HashMap<>();
        for (String line : lines) {
            try {
                DeviceFeatureFlag deviceFeatureFlag = new DeviceFeatureFlag(line);
                flags.computeIfAbsent(deviceFeatureFlag.getNamespace(), ns -> new HashMap<>())
                        .put(deviceFeatureFlag.getFlagName(), deviceFeatureFlag.getFlagValue());
            } catch (IllegalArgumentException e) {
                if (throwIfInvalid) {
                    throw new TargetSetupError(
                            String.format("Failed to parse flag data: %s", line),
                            e,
                            device.getDeviceDescriptor(),
                            InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
                }
                CLog.w("Skipping invalid flag data: %s", line);
                continue;
            }
        }
        return flags;
    }

    private void updateFlagsToRestore(
            String namespace,
            Map<String, String> initialValues,
            Map<String, String> updatedValues) {
        // Keep track of flag values to restore.
        for (String name : updatedValues.keySet()) {
            String initialValue = initialValues.get(name);
            String updatedValue = updatedValues.get(name);
            if (Objects.equals(updatedValue, initialValue)) {
                // When the first file updates a default flag value and the second file sets the
                // flag back to its default, there is no need to restore this flag.
                mFlagsToRestore.getOrDefault(namespace, Map.of()).remove(name);
            } else {
                mFlagsToRestore
                        .computeIfAbsent(namespace, ns -> new HashMap<>())
                        .put(name, initialValue);
            }
        }
    }

    private void updateFlags(ITestDevice device, Map<String, Map<String, String>> flags)
            throws DeviceNotAvailableException, TargetSetupError {
        for (String namespace : flags.keySet()) {
            for (Map.Entry<String, String> entry : flags.get(namespace).entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                updateFlag(device, namespace, name, value);
            }
        }
    }

    private void updateFlag(ITestDevice device, String namespace, String name, String value)
            throws DeviceNotAvailableException, TargetSetupError {
        if (Strings.isNullOrEmpty(value)) { // `device_config put` does not support empty values.
            runCommand(device, String.format("device_config delete '%s' '%s'", namespace, name));
        } else {
            runCommand(
                    device,
                    String.format("device_config put '%s' '%s' '%s'", namespace, name, value));
        }
    }

    private String runCommand(ITestDevice device, String command)
            throws DeviceNotAvailableException, TargetSetupError {
        CommandResult result = device.executeShellV2Command(command);
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new TargetSetupError(
                    String.format(
                            "Command %s failed, stdout = [%s], stderr = [%s]",
                            command, result.getStdout(), result.getStderr()),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        return result.getStdout();
    }
}
