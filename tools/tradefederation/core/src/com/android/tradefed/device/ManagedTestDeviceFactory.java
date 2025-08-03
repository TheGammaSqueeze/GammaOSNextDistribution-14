/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.device.IDeviceSelection.BaseDeviceType;
import com.android.tradefed.device.cloud.ManagedRemoteDevice;
import com.android.tradefed.device.cloud.NestedDeviceStateMonitor;
import com.android.tradefed.device.cloud.NestedRemoteDevice;
import com.android.tradefed.device.cloud.RemoteAndroidVirtualDevice;
import com.android.tradefed.device.cloud.VmRemoteDevice;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SystemUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory to create the different kind of devices that can be monitored by Tf
 */
public class ManagedTestDeviceFactory implements IManagedTestDeviceFactory {

    public static final String NOTIFY_AS_NATIVE = "NOTIFY_AS_NATIVE";
    public static final String IPADDRESS_PATTERN =
            "((^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
                    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
                    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
                    + "([01]?\\d\\d?|2[0-4]\\d|25[0-5]))|(localhost)){1}";

    protected boolean mFastbootEnabled;
    protected IDeviceManager mDeviceManager;
    protected IDeviceMonitor mAllocationMonitor;
    protected static final String CHECK_PM_CMD = "ls %s";
    protected static final String EXPECTED_RES = "/system/bin/pm";
    protected static final String EXPECTED_ERROR = "No such file or directory";
    protected static final long FRAMEWORK_CHECK_SLEEP_MS = 500;
    protected static final int FRAMEWORK_CHECK_MAX_RETRY = 3;

    public ManagedTestDeviceFactory(boolean fastbootEnabled, IDeviceManager deviceManager,
            IDeviceMonitor allocationMonitor) {
        mFastbootEnabled = fastbootEnabled;
        mDeviceManager = deviceManager;
        mAllocationMonitor = allocationMonitor;
    }

    @Override
    public IManagedTestDevice createRequestedDevice(IDevice idevice, IDeviceSelection options) {
        IManagedTestDevice testDevice = null;
        if (BaseDeviceType.NATIVE_DEVICE.equals(options.getBaseDeviceTypeRequested())) {
            testDevice =
                    new NativeDevice(
                            idevice,
                            new NativeDeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                            mAllocationMonitor);
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        } else {
            // Default to-go device is Android full stack device.
            testDevice =
                    new TestDevice(
                            idevice,
                            new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                            mAllocationMonitor);
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        }
        return testDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IManagedTestDevice createDevice(IDevice idevice) {
        IManagedTestDevice testDevice = null;
        if (idevice instanceof VmRemoteDevice) {
            testDevice =
                    new ManagedRemoteDevice(
                            idevice,
                            new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                            mAllocationMonitor);
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        } else if (idevice instanceof RemoteAvdIDevice) {
            testDevice =
                    new RemoteAndroidVirtualDevice(
                            idevice,
                            new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                            mAllocationMonitor);
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        } else if (idevice instanceof StubLocalAndroidVirtualDevice) {
            // Virtual device to be launched by TradeFed locally.
            testDevice =
                    new LocalAndroidVirtualDevice(
                            idevice,
                            new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                            mAllocationMonitor);
        } else if (idevice instanceof TcpDevice) {
            // Special device for Tcp device for custom handling.
            testDevice = new RemoteAndroidDevice(idevice,
                    new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                    mAllocationMonitor);
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        } else if (isTcpDeviceSerial(idevice.getSerialNumber())) {
            if (isRemoteEnvironment()) {
                // If we are in a remote environment, treat the device as such
                testDevice =
                        new NestedRemoteDevice(
                                idevice,
                                new NestedDeviceStateMonitor(
                                        mDeviceManager, idevice, mFastbootEnabled),
                                mAllocationMonitor);
            } else {
                Set<String> nativeSerials = new HashSet<>();
                if (System.getenv(NOTIFY_AS_NATIVE) != null) {
                    nativeSerials.addAll(Arrays.asList(System.getenv(NOTIFY_AS_NATIVE).split(",")));
                }
                if (nativeSerials.contains(idevice.getSerialNumber())) {
                    testDevice =
                            new NativeDevice(
                                    idevice,
                                    new NativeDeviceStateMonitor(
                                            mDeviceManager, idevice, mFastbootEnabled),
                                    mAllocationMonitor);
                } else {
                    // Handle device connected via 'adb connect'
                    testDevice =
                            new RemoteAndroidDevice(
                                    idevice,
                                    new DeviceStateMonitor(
                                            mDeviceManager, idevice, mFastbootEnabled),
                                    mAllocationMonitor);
                    testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
                }
            }
        } else {
            // Default to-go device is Android full stack device.
            testDevice = new TestDevice(idevice,
                    new DeviceStateMonitor(mDeviceManager, idevice, mFastbootEnabled),
                    mAllocationMonitor);
        }

        if (idevice instanceof FastbootDevice) {
            if (((FastbootDevice) idevice).isFastbootD()) {
                testDevice.setDeviceState(TestDeviceState.FASTBOOTD);
            } else {
                testDevice.setDeviceState(TestDeviceState.FASTBOOT);
            }
        } else if (idevice instanceof StubDevice) {
            testDevice.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        }
        testDevice.setFastbootEnabled(mFastbootEnabled);
        testDevice.setFastbootPath(mDeviceManager.getFastbootPath());
        return testDevice;
    }

    /** Return the default {@link IRunUtil} instance. */
    @VisibleForTesting
    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Return true if we are currently running in a remote environment. This will alter the device
     * behavior.
     */
    @VisibleForTesting
    protected boolean isRemoteEnvironment() {
        return SystemUtil.isRemoteEnvironment();
    }

    /** Create a {@link CollectingOutputReceiver}. */
    @VisibleForTesting
    protected CollectingOutputReceiver createOutputReceiver() {
        return new CollectingOutputReceiver();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootEnabled(boolean enable) {
        mFastbootEnabled = enable;
    }

    /**
     * Helper to device if it's a serial from a remotely connected device. serial format of tcp
     * device is <ip or locahost>:<port>
     */
    public static boolean isTcpDeviceSerial(String serial) {
        final String remotePattern = IPADDRESS_PATTERN + "(:)([0-9]{2,5})(\\b)";
        Pattern pattern = Pattern.compile(remotePattern);
        Matcher match = pattern.matcher(serial.trim());
        if (match.find()) {
            return true;
        }
        return false;
    }
}
