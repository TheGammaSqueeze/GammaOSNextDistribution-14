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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;

import java.util.List;
import java.util.Map;

/** A forwarder for {@link IScheduledInvocationListener}. */
class ScheduledInvocationForwarder extends ResultForwarder implements IScheduledInvocationListener {

    ScheduledInvocationForwarder(ITestInvocationListener... listeners) {
        super(listeners);
    }

    @Override
    public void invocationInitiated(IInvocationContext context) {
        for (ITestInvocationListener listener : getListeners()) {
            if (listener instanceof IScheduledInvocationListener) {
                try {
                    ((IScheduledInvocationListener) listener).invocationInitiated(context);
                } catch (RuntimeException e) {
                    CLog.e(
                            "Exception while invoking %s#invocationInitiated",
                            listener.getClass().getName());
                    CLog.e(e);
                }
            }
        }
    }

    @Override
    public void invocationComplete(
            IInvocationContext context, Map<ITestDevice, FreeDeviceState> devicesStates) {
        for (ITestInvocationListener listener : getListeners()) {
            if (listener instanceof IScheduledInvocationListener) {
                try {
                    ((IScheduledInvocationListener) listener)
                            .invocationComplete(context, devicesStates);
                } catch (RuntimeException e) {
                    CLog.e(
                            "Exception while invoking %s#invocationComplete",
                            listener.getClass().getName());
                    CLog.e(e);
                }
            }
        }
    }

    @Override
    public List<ITestInvocationListener> getListeners() {
        return super.getListeners();
    }
}
