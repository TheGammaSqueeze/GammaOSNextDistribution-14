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
package com.android.tradefed.targetprep.suite;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceFoldableState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.TestErrorIdentifier;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

/**
 * A target preparer that can switch the foldable state of a device.
 */
public class FoldableModePreparer extends BaseTargetPreparer {

    @Option(name = "foldable-state-identifier",
            description = "The integer state identifier of the foldable mode.")
    private Long mStateIdentifier = null;

    @Option(name = "enforce-state",
            description = "Ensure the state wasn't changed between setup & teardown.")
    private boolean mValidateSameState = true;

    public FoldableModePreparer() {}

    public FoldableModePreparer(long stateIdentifier) {
        mStateIdentifier = stateIdentifier;
    }

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mStateIdentifier == null) {
            return;
        }
        CommandResult result = testInformation.getDevice().executeShellV2Command(
                String.format("cmd device_state state %s", mStateIdentifier));
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            throw new TargetSetupError(
                    String.format("Could not set device_state %s. stderr: %s",
                            mStateIdentifier, result.getStderr()),
                    testInformation.getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        result = testInformation.getDevice().executeShellV2Command("input keyevent KEYCODE_WAKEUP");
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            throw new TargetSetupError(
                    String.format("Could not wake up the screen. stderr: %s", result.getStderr()),
                    testInformation.getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        if (mStateIdentifier == null) {
            return;
        }
        if (mValidateSameState) {
            DeviceFoldableState state = testInformation.getDevice().getCurrentFoldableState();
            if (state == null || state.getIdentifier() != mStateIdentifier) {
                throw new HarnessRuntimeException(
                        String.format("Device foldable state changed to '%s', "
                                + "it should have been '%s'", state, mStateIdentifier),
                            TestErrorIdentifier.MODIFIED_FOLDABLE_STATE);
            }
        }

        CommandResult result = testInformation.getDevice().executeShellV2Command(
                "cmd device_state state reset");
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            throw new HarnessRuntimeException(
                    String.format("Could not reset device_state. stderr: %s", result.getStderr()),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }
}
