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
package com.android.tradefed.testtype.bazel;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.Consumer;

/**
 * Listener implementation that handles BazelTest-specific result manipulation including reporting
 * all invocation logs as module logs and adding in extra log calls.
 */
final class BazelTestListener extends ForwardingTestListener {

    private final ITestInvocationListener mDelegate;
    private final ImmutableList<Consumer<ITestInvocationListener>> mExtraModuleLogCalls;
    private boolean mInModule;
    private boolean mModuleCached;

    public BazelTestListener(
            ITestInvocationListener delegate,
            List<Consumer<ITestInvocationListener>> extraModuleLogCalls,
            boolean moduleCached) {

        mDelegate = delegate;
        mExtraModuleLogCalls = ImmutableList.copyOf(extraModuleLogCalls);
        mModuleCached = moduleCached;
    }

    @Override
    protected ITestInvocationListener delegate() {
        return mDelegate;
    }

    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        if (!mInModule) {
            return;
        }
        delegate().testLog(dataName, dataType, dataStream);
    }

    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {

        if (!mInModule) {
            return;
        }
        TestListeners.testLogSaved(delegate(), dataName, dataType, dataStream, logFile);
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        if (!mInModule) {
            return;
        }
        TestListeners.logAssociation(delegate(), dataName, logFile);
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        mInModule = true;
        if (mModuleCached) {
            moduleContext.addInvocationAttribute("module-cached", "true");
        }
        delegate().testModuleStarted(moduleContext);
    }

    @Override
    public void testModuleEnded() {
        mInModule = false;
        replayExtraModuleLogCalls();
        delegate().testModuleEnded();
    }

    private void replayExtraModuleLogCalls() {
        for (Consumer<ITestInvocationListener> c : mExtraModuleLogCalls) {
            c.accept(delegate());
        }
    }
}
