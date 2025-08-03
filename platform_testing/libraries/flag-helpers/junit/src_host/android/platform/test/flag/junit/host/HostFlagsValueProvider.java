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

import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.IFlagsValueProvider;
import android.platform.test.flag.util.FlagReadException;

import com.android.tradefed.device.ITestDevice;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/** A {@code IFlagsValueProvider} which provides flag values from host side. */
public class HostFlagsValueProvider implements IFlagsValueProvider {
    /** The key is the device serial number. */
    private static final LoadingCache<String, DeviceFlags> CACHED_DEVICE_FLAGS;

    /** The key is the device serial number. */
    private static final Map<String, ITestDevice> TEST_DEVICES = new HashMap<>();

    static {
        CacheLoader<String, DeviceFlags> cacheLoader =
                new CacheLoader<String, DeviceFlags>() {
                    @Override
                    public DeviceFlags load(String deviceSerial) throws FlagReadException {
                        if (!TEST_DEVICES.containsKey(deviceSerial)) {
                            throw new IllegalStateException(
                                    String.format(
                                            "No ITestDevice found for serial %s.", deviceSerial));
                        }
                        return DeviceFlags.createDeviceFlags(TEST_DEVICES.get(deviceSerial));
                    }
                };

        CACHED_DEVICE_FLAGS = CacheBuilder.newBuilder().build(cacheLoader);
    }

    private final Supplier<ITestDevice> mTestDeviceSupplier;
    private DeviceFlags mDeviceFlags;

    HostFlagsValueProvider(Supplier<ITestDevice> testDeviceSupplier) {
        mTestDeviceSupplier = testDeviceSupplier;
    }

    public static CheckFlagsRule createCheckFlagsRule(Supplier<ITestDevice> testDeviceSupplier) {
        return new CheckFlagsRule(new HostFlagsValueProvider(testDeviceSupplier));
    }

    /**
     * Refreshes all flag values of a given device. Must be called when the device flags have been
     * changed.
     */
    public static void refreshFlagsCache(String serial) throws FlagReadException {
        Map<String, DeviceFlags> cachedDeviceFlagsMap = CACHED_DEVICE_FLAGS.asMap();
        if (cachedDeviceFlagsMap.containsKey(serial)) {
            cachedDeviceFlagsMap.get(serial).init(TEST_DEVICES.get(serial));
        }
    }

    @Override
    public void setUp() throws FlagReadException {
        ITestDevice testDevice = mTestDeviceSupplier.get();
        TEST_DEVICES.put(testDevice.getSerialNumber(), testDevice);

        try {
            mDeviceFlags = CACHED_DEVICE_FLAGS.get(testDevice.getSerialNumber());
        } catch (ExecutionException e) {
            throw new FlagReadException("ALL_FLAGS", e);
        }
    }

    @Override
    public boolean getBoolean(String flag) throws FlagReadException {
        String value = mDeviceFlags.getFlagValue(flag);

        if (value == null) {
            throw new FlagReadException(flag, "Flag does not exist.");
        }

        if (!IFlagsValueProvider.isBooleanValue(value)) {
            throw new FlagReadException(
                    flag, String.format("Flag value %s is not a boolean", value));
        }
        return Boolean.valueOf(value);
    }
}
