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

package android.platform.test.flag.junit.host;

import android.aconfig.Aconfig.flag_permission;
import android.aconfig.Aconfig.flag_state;
import android.aconfig.Aconfig.parsed_flags;
import android.platform.test.flag.util.Flag;
import android.platform.test.flag.util.FlagReadException;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Dumps flags from a give device.
 *
 * <p>There are two sources to dump the flag values: device_config and aconfig dumped output. The
 * device_config contains the ground truth for readwrite flags (including legacy flags), while the
 * aconfig dumped output contains the ground truth for all readonly flags.
 *
 * <p>The {packageName}.{flagName} (we call it full flag name) is the unique identity of each
 * aconfig flag, which is also the constant value for each flag name in the aconfig auto generated
 * library. However, each aconfig flag definitions also contains namespace which is in aconfig
 * dumped output. The {@code DeviceFlags} saves all flag values into a map with the key always
 * starts with "{namespace}/" no matter it is a legacy flag or aconfig flag, and gets flag values
 * from this map.
 */
public class DeviceFlags {
    private static final String DUMP_DEVICE_CONFIG_CMD = "device_config list";

    /**
     * Partitions that contain the aconfig_flags.pb files. Should be consistent with the partitions
     * defined in core/packaging/flags.mk.
     */
    private static final List<String> FLAG_PARTITIONS =
            List.of("product", "system", "system_ext", "vendor");

    /**
     * The key is the flag name with namespace ({namespace}/{flagName} for legacy flags,
     * {namespace}/{packageName}.{flagName} for aconfig flags.
     */
    private final Map<String, String> mAllFlagValues = new HashMap<>();

    /**
     * The key is the aconfig full flag name ({packageName}.{flagName}), the value is the flag name
     * with namespace ({namespace}/{packageName}.{flagName}).
     */
    private final Map<String, String> mFlagNameWithNamespaces = new HashMap<>();

    private DeviceFlags() {}

    public static DeviceFlags createDeviceFlags(ITestDevice testDevice) throws FlagReadException {
        DeviceFlags deviceFlags = new DeviceFlags();
        deviceFlags.init(testDevice);
        return deviceFlags;
    }

    @Nullable
    public String getFlagValue(String flag) {
        String flagWithNamespace =
                flag.contains(Flag.NAMESPACE_FLAG_SEPARATOR)
                        ? flag
                        : mFlagNameWithNamespaces.get(flag);

        return mAllFlagValues.get(flagWithNamespace);
    }

    public void init(ITestDevice testDevice) throws FlagReadException {
        mAllFlagValues.clear();
        mFlagNameWithNamespaces.clear();
        LogUtil.CLog.i("Dumping all flag values from the device ...");
        getDeviceConfigFlags(testDevice);
        getAconfigFlags(testDevice);
        LogUtil.CLog.i("Dumped all flag values from the device.");
    }

    private void getDeviceConfigFlags(ITestDevice testDevice) throws FlagReadException {
        try {
            String deviceConfigList = testDevice.executeShellCommand(DUMP_DEVICE_CONFIG_CMD);
            for (String deviceConfig : deviceConfigList.split("\n")) {
                if (deviceConfig.contains("=")) {
                    String[] flagNameValue = deviceConfig.split("=", 2);
                    mAllFlagValues.put(flagNameValue[0], flagNameValue[1]);
                } else {
                    LogUtil.CLog.w("Invalid device config %s", deviceConfig);
                }
            }
        } catch (DeviceNotAvailableException e) {
            throw new FlagReadException("ALL_FLAGS", e);
        }
    }

    private void getAconfigFlags(ITestDevice testDevice) throws FlagReadException {
        getAconfigParsedFlags(testDevice)
                .getParsedFlagList()
                .forEach(
                        parsedFlag -> {
                            String namespace = parsedFlag.getNamespace();
                            String fullFlagName =
                                    String.format(
                                            Flag.ACONFIG_FULL_FLAG_FORMAT,
                                            parsedFlag.getPackage(),
                                            parsedFlag.getName());
                            String flagWithNamespace =
                                    String.format(
                                            Flag.FLAG_WITH_NAMESPACE_FORMAT,
                                            namespace,
                                            fullFlagName);
                            mFlagNameWithNamespaces.put(fullFlagName, flagWithNamespace);
                            String defaultValue =
                                    parsedFlag.getState().equals(flag_state.ENABLED)
                                            ? "true"
                                            : "false";
                            if (parsedFlag.getPermission().equals(flag_permission.READ_ONLY)
                                    || !mAllFlagValues.containsKey(flagWithNamespace)) {
                                mAllFlagValues.put(flagWithNamespace, defaultValue);
                            }
                        });
    }

    private parsed_flags getAconfigParsedFlags(ITestDevice testDevice) throws FlagReadException {
        parsed_flags.Builder builder = parsed_flags.newBuilder();

        for (String flagPartition : FLAG_PARTITIONS) {
            try {
                String aconfigFlagsPbFilePath =
                        String.format("/%s/etc/aconfig_flags.pb", flagPartition);
                if (!testDevice.doesFileExist(aconfigFlagsPbFilePath)) {
                    LogUtil.CLog.i("Aconfig flags file %s does not exist", aconfigFlagsPbFilePath);
                    continue;
                }
                File aconfigFlagsPbFile = testDevice.pullFile(aconfigFlagsPbFilePath);
                if (aconfigFlagsPbFile.length() <= 1) {
                    LogUtil.CLog.i("Aconfig flags file %s is empty", aconfigFlagsPbFilePath);
                    continue;
                }
                InputStream aconfigFlagsPbInputStream = new FileInputStream(aconfigFlagsPbFile);
                parsed_flags parsedFlags = parsed_flags.parseFrom(aconfigFlagsPbInputStream);
                builder.addAllParsedFlag(parsedFlags.getParsedFlagList());
            } catch (DeviceNotAvailableException | IOException e) {
                throw new FlagReadException("ALL_FLAGS", e);
            }
        }
        return builder.build();
    }
}
