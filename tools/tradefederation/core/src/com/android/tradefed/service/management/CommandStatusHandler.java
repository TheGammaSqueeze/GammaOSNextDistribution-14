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
package com.android.tradefed.service.management;

import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;

import com.proto.tradefed.invocation.InvocationStatus;

import java.util.Map;

/** Handler helping to monitor and update the status of an invocation. */
public class CommandStatusHandler implements IScheduledInvocationListener {

    private InvocationStatus.Status mStatus = InvocationStatus.Status.PENDING;

    @Override
    public void invocationInitiated(IInvocationContext context) {
        mStatus = InvocationStatus.Status.RUNNING;
    }

    @Override
    public void invocationComplete(
            IInvocationContext context, Map<ITestDevice, FreeDeviceState> devicesStates) {
        mStatus = InvocationStatus.Status.DONE;
    }

    /** Returns the currently known status of the invocation. */
    public InvocationStatus.Status getCurrentStatus() {
        return mStatus;
    }
}
