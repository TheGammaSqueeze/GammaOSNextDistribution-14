/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.testtype.junit4;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

/** Wrapper of {@link RunNotifier} so we can carry the {@link DeviceNotAvailableException}. */
public class RunNotifierWrapper extends RunNotifier {

    private DeviceNotAvailableException mDnae;
    private RunNotifier notifier;
    private TestInformation mTestInfo;

    public RunNotifierWrapper(RunNotifier notifier) {
        this.notifier = notifier;
    }

    public void setTestInfo(TestInformation testInfo) {
        mTestInfo = testInfo;
    }

    @Override
    public void addFirstListener(RunListener listener) {
        notifier.addFirstListener(listener);
    }

    @Override
    public void fireTestRunStarted(Description description) {
        notifier.fireTestRunStarted(description);
    }

    @Override
    public void fireTestRunFinished(Result result) {
        notifier.fireTestRunFinished(result);
    }

    @Override
    public void fireTestSuiteStarted(Description description) {
        notifier.fireTestSuiteStarted(description);
    }

    @Override
    public void fireTestSuiteFinished(Description description) {
        notifier.fireTestSuiteFinished(description);
    }

    @Override
    public void pleaseStop() {
        notifier.pleaseStop();
    }

    @Override
    public void addListener(RunListener listener) {
        notifier.addListener(listener);
    }

    @Override
    public void removeListener(RunListener listener) {
        notifier.removeListener(listener);
    }

    @Override
    public void fireTestFailure(Failure failure) {
        notifier.fireTestFailure(failure);
        if (failure.getException() instanceof DeviceNotAvailableException) {
            mDnae = (DeviceNotAvailableException) failure.getException();
        } else if (failure.getException() instanceof InterruptedException) {
            throw new CarryInterruptedException((InterruptedException) failure.getException());
        }
    }

    @Override
    public void fireTestAssumptionFailed(Failure failure) {
        notifier.fireTestAssumptionFailed(failure);
    }

    @Override
    public void fireTestFinished(Description description) {
        notifier.fireTestFinished(description);
    }

    @Override
    public void fireTestStarted(Description description) {
        notifier.fireTestStarted(description);
        if (mTestInfo != null && mTestInfo.isTestTimedOut()) {
            notifier.fireTestIgnored(description);
            throw new CarryInterruptedException(new InterruptedException());
        }
    }

    @Override
    public void fireTestIgnored(Description description) {
        notifier.fireTestIgnored(description);
    }

    /** Returns the {@link DeviceNotAvailableException} if any was thrown. */
    public DeviceNotAvailableException getDeviceNotAvailableException() {
        return mDnae;
    }
}
