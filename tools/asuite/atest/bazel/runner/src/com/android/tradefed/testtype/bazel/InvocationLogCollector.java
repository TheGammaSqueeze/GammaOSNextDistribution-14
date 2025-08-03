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
package com.android.tradefed.testtype.bazel;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Listener which collects all invocation-level test logs. */
final class InvocationLogCollector extends NullTestListener {

    private final List<Consumer<ITestInvocationListener>> mLogCalls;
    private boolean mInModule;

    InvocationLogCollector() {
        mLogCalls = new ArrayList<>();
    }

    public List<Consumer<ITestInvocationListener>> getLogCalls() {
        return mLogCalls;
    }

    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        if (mInModule) {
            return;
        }
        mLogCalls.add(
                (ITestInvocationListener l) -> {
                    l.testLog(dataName, dataType, dataStream);
                });
    }

    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        mInModule = true;
    }

    @Override
    public void testModuleEnded() {
        mInModule = false;
    }

    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {

        if (mInModule) {
            return;
        }
        mLogCalls.add(
                (ITestInvocationListener l) -> {
                    TestListeners.testLogSaved(l, dataName, dataType, dataStream, logFile);
                });
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        if (mInModule) {
            return;
        }
        mLogCalls.add(
                (ITestInvocationListener l) -> {
                    TestListeners.logAssociation(l, dataName, logFile);
                });
    }
}
