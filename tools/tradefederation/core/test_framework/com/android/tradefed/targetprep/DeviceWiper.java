/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

/** A {@link ITargetPreparer} that wipes userdata */
@OptionClass(alias = "device-wiper")
public class DeviceWiper extends BaseTargetPreparer {
    @Deprecated
    @Option(name = "use-erase",
            description =
                    "Deprecated. Please use mode instead. Instruct wiper to use fastboot erase "
                    + "instead of format.")
    protected boolean mUseErase = false;

    private enum Mode {
        /** Use fastboot format. */
        FORMAT,
        /** Use fastboot erase. */
        ERASE,
        /**
         * Use factory reset. Recommended when the test needs to support Cuttlefish, where fastboot
         * is not fully supported.
         */
        FACTORY_RESET,
    }

    @Option(name = "mode", description = "Determines how to wipes userdata. Default: FORMAT")
    private Mode mMode = Mode.FORMAT;

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        CLog.i("Wiping device");
        if (mMode.equals(Mode.FORMAT) && mUseErase) {
            mMode = Mode.ERASE;
        }
        switch (mMode) {
            case FORMAT:
                doFormat(device);
                break;
            case ERASE:
                doErase(device);
                break;
            case FACTORY_RESET:
                doFactoryReset(device);
                break;
        }
        device.waitForDeviceAvailable();
    }

    private void doFormat(ITestDevice device) throws DeviceNotAvailableException, TargetSetupError {
        try {
            device.rebootIntoBootloader();
            CLog.d("Attempting fastboot wiping");
            CommandResult r = device.executeLongFastbootCommand("-w");
            if (r.getStatus() != CommandStatus.SUCCESS) {
                throw new TargetSetupError(
                        String.format("fastboot wiping failed: %s", r.getStderr()),
                        device.getDeviceDescriptor());
            }
        } finally {
            device.executeFastbootCommand("reboot");
        }
    }

    private void doErase(ITestDevice device) throws DeviceNotAvailableException, TargetSetupError {
        try {
            device.rebootIntoBootloader();
            performFastbootOp(device, "erase", "cache");
            performFastbootOp(device, "erase", "userdata");
        } finally {
            device.executeFastbootCommand("reboot");
        }
    }

    private void performFastbootOp(ITestDevice device, String op, String partition)
            throws DeviceNotAvailableException, TargetSetupError {
        CLog.d("Attempting fastboot %s %s", op, partition);
        CommandResult r = device.executeLongFastbootCommand(op, partition);
        if (r.getStatus() != CommandStatus.SUCCESS) {
            throw new TargetSetupError(String.format("%s %s failed: %s", op, partition,
                    r.getStderr()), device.getDeviceDescriptor());
        }
    }

    private void doFactoryReset(ITestDevice device)
            throws DeviceNotAvailableException, TargetSetupError {
        if (!device.enableAdbRoot()) {
            throw new TargetSetupError("Unable to do factory reset because adb root failed",
                    device.getDeviceDescriptor());
        }
        CommandResult r =
                device.executeShellV2Command("am broadcast -p android --receiver-foreground "
                        + "-a android.intent.action.FACTORY_RESET");
        if (r.getStatus() != CommandStatus.SUCCESS) {
            throw new TargetSetupError(String.format("factory reset failed: %s", r.getStderr()),
                    device.getDeviceDescriptor());
        }
        device.waitForDeviceNotAvailable(30_000);
    }
}
