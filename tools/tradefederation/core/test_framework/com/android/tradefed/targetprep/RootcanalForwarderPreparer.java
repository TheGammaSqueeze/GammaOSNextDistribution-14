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

package com.android.tradefed.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;

@OptionClass(alias = "rootcanal")
public class RootcanalForwarderPreparer extends BaseTargetPreparer {

    @Option(name = "hci-port", description = "Rootcanal HCI port")
    private String mHciPort = "vsock:2:7300";

    @Option(name = "control-port", description = "Rootcanal control port")
    private String mControlPort = "vsock:2:7500";

    private static final String ROOTCANAL_HCI_ADDRESS = "ROOTCANAL_HCI_ADDRESS";
    private static final String ROOTCANAL_CONTROL_ADDRESS = "ROOTCANAL_CONTROL_ADDRESS";

    // These are the host (i.e. the machine running tests) ports on which we
    // forward Rootcanal HCI and Control ports of the DUT.
    private String mHostHciRootcanalPort = "";
    private String mHostControlRootcanalPort = "";

    @Override
    public void setUp(TestInformation testInformation) throws TargetSetupError {
        // default execution properties to empty strings.
        testInformation.properties().put(ROOTCANAL_HCI_ADDRESS, "");
        testInformation.properties().put(ROOTCANAL_CONTROL_ADDRESS, "");

        if (isDisabled()) {
            CLog.i("Skipping RootcanalForwarderPreparer setup");
            return;
        }

        try {
            ITestDevice testDevice = testInformation.getDevice();
            mHostHciRootcanalPort = adbForward(testDevice, mHciPort);
            mHostControlRootcanalPort = adbForward(testDevice, mControlPort);

            // update execution properties.
            testInformation
                    .properties()
                    .put(ROOTCANAL_HCI_ADDRESS, "localhost:" + mHostHciRootcanalPort);
            testInformation
                    .properties()
                    .put(ROOTCANAL_CONTROL_ADDRESS, "localhost:" + mHostControlRootcanalPort);
        } catch (DeviceNotAvailableException e) {
            CLog.e("Unable to forward Rootcanal ports: %s", e.getMessage());
        }
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable t) {
        // remove execution properties.
        testInformation.properties().remove(ROOTCANAL_HCI_ADDRESS);
        testInformation.properties().remove(ROOTCANAL_CONTROL_ADDRESS);

        if (isTearDownDisabled()) {
            CLog.i("Skipping RootcanalForwarderPreparer teardown");
            return;
        }

        try {
            ITestDevice testDevice = testInformation.getDevice();

            if (!mHostHciRootcanalPort.isEmpty())
                adbForwardRemove(testDevice, mHostHciRootcanalPort);
            if (!mHostControlRootcanalPort.isEmpty())
                adbForwardRemove(testDevice, mHostControlRootcanalPort);
        } catch (DeviceNotAvailableException e) {
            CLog.e("Unable to remove Rootcanal ports forwarding: %s", e.getMessage());
        }
    }

    private String adbForward(ITestDevice testDevice, String port)
            throws DeviceNotAvailableException {
        String hostPort = testDevice.executeAdbCommand("forward", "tcp:0", port).trim();
        CLog.i("Forwarded %s to host tcp:%s", port, hostPort);
        return hostPort;
    }

    private void adbForwardRemove(ITestDevice testDevice, String hostPort)
            throws DeviceNotAvailableException {
        CLog.i("Removing host tcp:%s forwarding", hostPort);
        testDevice.executeAdbCommand("forward", "--remove", String.format("tcp:%s", hostPort));
    }
}
