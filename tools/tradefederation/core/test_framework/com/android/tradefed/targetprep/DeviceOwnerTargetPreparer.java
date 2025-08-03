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

package com.android.tradefed.targetprep;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.annotations.VisibleForTesting;

/**
 * An {@link ITargetPreparer} that sets up a device owner component.
 *
 * <p>Existing device and profile owners and secondary users are removed before the new device owner
 * is set.
 *
 * <p>In teardown, the device owner is removed.
 */
@OptionClass(alias = "device-owner")
public class DeviceOwnerTargetPreparer extends BaseTargetPreparer
        implements IConfigurationReceiver {

    @VisibleForTesting
    static final String DEVICE_OWNER_COMPONENT_NAME_OPTION = "device-owner-component-name";

    @VisibleForTesting
    static final String HEADLESS_SYSTEM_USER_PROPERTY = "ro.fw.mu.headless_system_user";

    private static final int USER_SYSTEM = 0;

    private IConfiguration mConfiguration;
    private ITestDevice mDevice;
    private int mDeviceOwnerUserId;

    @Option(
            name = DEVICE_OWNER_COMPONENT_NAME_OPTION,
            description =
                    "the component name that should be set as active admin, and its package as "
                            + "device owner. This must already be installed on the device.",
            importance = Option.Importance.IF_UNSET)
    private String mDeviceOwnerComponentName;

    @Override
    public void setConfiguration(IConfiguration configuration) {
        if (configuration == null) {
            throw new NullPointerException("configuration must not be null");
        }
        mConfiguration = configuration;
    }

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        mDevice = testInfo.getDevice();
        mDeviceOwnerUserId = getDeviceOwnerUserId();

        mDevice.removeOwners();
        switchToDeviceOwnerUser();
        removeSecondaryUsers();
        setDeviceOwner();
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        removeDeviceOwner();
    }

    private void switchToDeviceOwnerUser() throws DeviceNotAvailableException {
        mDevice.switchUser(mDeviceOwnerUserId);
    }

    private boolean isHeadlessSystemUserMode() throws DeviceNotAvailableException {
        return mDevice.getBooleanProperty(HEADLESS_SYSTEM_USER_PROPERTY, false);
    }

    private int getDeviceOwnerUserId() throws DeviceNotAvailableException {
        if (isHeadlessSystemUserMode()) {
            return mDevice.getPrimaryUserId();
        } else {
            return USER_SYSTEM;
        }
    }

    private void removeSecondaryUsers() throws DeviceNotAvailableException {
        for (Integer id : mDevice.listUsers()) {
            if (id != USER_SYSTEM && id != mDeviceOwnerUserId && !mDevice.removeUser(id)) {
                CLog.w("Failed to remove user %d", id);
            }
        }
    }

    private void setDeviceOwner() throws DeviceNotAvailableException {
        mDevice.setDeviceOwner(mDeviceOwnerComponentName, mDeviceOwnerUserId);
    }

    private void removeDeviceOwner() throws DeviceNotAvailableException {
        mDevice.removeAdmin(mDeviceOwnerComponentName, mDeviceOwnerUserId);
    }
}
