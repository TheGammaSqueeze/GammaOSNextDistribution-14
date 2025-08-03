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
package com.android.tradefed.device.connection;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.IConfigurableVirtualDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ManagedTestDeviceFactory;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.device.RemoteAndroidDevice;
import com.android.tradefed.device.TestDeviceOptions.InstanceType;
import com.android.tradefed.device.cloud.GceAvdInfo;
import com.android.tradefed.device.cloud.RemoteAndroidVirtualDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;

/**
 * Default connection representation of a device, assumed to be a standard adb connection of the
 * device.
 */
public class DefaultConnection extends AbstractConnection {

    private final IRunUtil mRunUtil;
    private final ITestDevice mDevice;
    private final IBuildInfo mBuildInfo;
    private final MultiMap<String, String> mAttributes;
    private final String mInitialIpDevice;
    private final String mInitialUser;
    private final Integer mInitialDeviceNumOffset;
    private final String mInitialSerial;
    private final ITestLogger mTestLogger;

    private final boolean mTemporaryHolder;

    public static DefaultConnection createInopConnection(ConnectionBuilder builder) {
        return new DefaultConnection(builder);
    }

    /** Create the requested connection. */
    public static DefaultConnection createConnection(ConnectionBuilder builder) {
        ITestDevice device = builder.device;
        if (device == null) {
            return new DefaultConnection(builder);
        }

        final InstanceType type = device.getOptions().getInstanceType();
        CLog.d("Instance type for connection: %s", type);

        final boolean isCuttlefish = type.equals(InstanceType.CUTTLEFISH)
                || type.equals(InstanceType.REMOTE_NESTED_AVD);

        if (device instanceof RemoteAndroidVirtualDevice) {
            ((NativeDevice) device).setFastbootEnabled(isCuttlefish);
            ((NativeDevice) device).setLogStartDelay(0);
            return new AdbSshConnection(builder);
        }
        if (device instanceof RemoteAndroidDevice) {
            return new AdbTcpConnection(builder);
        }

        CLog.d("Instance type for connection: %s", type);
        if (isCuttlefish) {
            if (ManagedTestDeviceFactory.isTcpDeviceSerial(device.getSerialNumber())) {
                // TODO: Add support for remote environment
                // If the device is already started just go for TcpConnection
                return new AdbTcpConnection(builder);
            } else {
                ((NativeDevice) device).setLogStartDelay(0);
                return new AdbSshConnection(builder);
            }
        }

        return new DefaultConnection(builder);
    }

    /** Builder used to described the connection. */
    public static class ConnectionBuilder {

        ITestDevice device;
        IBuildInfo buildInfo;
        MultiMap<String, String> attributes;
        IRunUtil runUtil;
        ITestLogger logger;
        GceAvdInfo existingAvdInfo;

        public ConnectionBuilder(
                IRunUtil runUtil, ITestDevice device, IBuildInfo buildInfo, ITestLogger logger) {
            this.runUtil = runUtil;
            this.device = device;
            this.buildInfo = buildInfo;
            this.logger = logger;
            attributes = new MultiMap<String, String>();
        }

        public ConnectionBuilder addAttributes(MultiMap<String, String> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public ConnectionBuilder setExistingAvdInfo(GceAvdInfo info) {
            existingAvdInfo = info;
            return this;
        }
    }

    /** Constructor */
    protected DefaultConnection(ConnectionBuilder builder) {
        mRunUtil = builder.runUtil;
        mDevice = builder.device;
        mBuildInfo = builder.buildInfo;
        mAttributes = builder.attributes;
        IDevice idevice = mDevice.getIDevice();
        mInitialSerial = mDevice.getSerialNumber();
        mTestLogger = builder.logger;
        if (idevice instanceof IConfigurableVirtualDevice) {
            mInitialIpDevice = ((IConfigurableVirtualDevice) idevice).getKnownDeviceIp();
            mInitialUser = ((IConfigurableVirtualDevice) idevice).getKnownUser();
            mInitialDeviceNumOffset = ((IConfigurableVirtualDevice) idevice).getDeviceNumOffset();
        } else {
            mInitialIpDevice = null;
            mInitialUser = null;
            mInitialDeviceNumOffset = null;
        }
        if (idevice instanceof NullDevice) {
            mTemporaryHolder = ((NullDevice) idevice).isTemporary();
        } else {
            mTemporaryHolder = false;
        }
    }

    /** Returns {@link IRunUtil} to execute commands. */
    protected IRunUtil getRunUtil() {
        return mRunUtil;
    }

    public final ITestDevice getDevice() {
        return mDevice;
    }

    public final IBuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    public final MultiMap<String, String> getAttributes() {
        return mAttributes;
    }

    /**
     * Returns the initial associated ip to the device if any. Returns null if no known initial ip.
     */
    public String getInitialIp() {
        return mInitialIpDevice;
    }

    /** Returns the initial known user if any. Returns null if no initial known user. */
    public String getInitialUser() {
        return mInitialUser;
    }

    /** Returns the known device num offset if any. Returns null if not available. */
    public Integer getInitialDeviceNumOffset() {
        return mInitialDeviceNumOffset;
    }

    /** Returns the initial serial name of the device. */
    public String getInitialSerial() {
        return mInitialSerial;
    }

    /** Returns the {@link ITestLogger} to log files. */
    public ITestLogger getLogger() {
        return mTestLogger;
    }

    public boolean wasTemporaryHolder() {
        return mTemporaryHolder;
    }
}
