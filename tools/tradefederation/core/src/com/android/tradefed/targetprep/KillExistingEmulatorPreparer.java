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

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;

import com.google.common.base.Preconditions;

/**
 * A {@link com.android.tradefed.targetprep.ITargetPreparer} that shuts down a running emulator.
 *
 * <p>Typically paired with LocalEmulatorSnapshot to shut down a cold boot emulator, so subsequent
 * preparers/tests can launch from a snapshot.
 */
public class KillExistingEmulatorPreparer extends BaseTargetPreparer {
    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        Preconditions.checkArgument(
                testInformation.getDevice().getDeviceState() == TestDeviceState.ONLINE);
        LogUtil.CLog.i(
                "Killing emulator %s", testInformation.getDevice().getIDevice().getSerialNumber());

        GlobalConfiguration.getDeviceManagerInstance().killEmulator(testInformation.getDevice());
    }
}
