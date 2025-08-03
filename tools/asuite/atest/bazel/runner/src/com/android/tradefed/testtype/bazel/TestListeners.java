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

import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.retry.ISupportGranularResults;

/** Utility class for ITestInvocationListener related functionality. */
final class TestListeners {

    private TestListeners() {}

    static void testLogSaved(
            ITestInvocationListener listener,
            String dataName,
            LogDataType dataType,
            InputStreamSource dataStream,
            LogFile logFile) {

        if (!(listener instanceof ILogSaverListener)) {
            return;
        }

        ((ILogSaverListener) listener).testLogSaved(dataName, dataType, dataStream, logFile);
    }

    static void logAssociation(ITestInvocationListener listener, String dataName, LogFile logFile) {
        if (!(listener instanceof ILogSaverListener)) {
            return;
        }

        ((ILogSaverListener) listener).logAssociation(dataName, logFile);
    }

    static void setLogSaver(ITestInvocationListener listener, ILogSaver logSaver) {
        if (!(listener instanceof ILogSaverListener)) {
            return;
        }

        ((ILogSaverListener) listener).setLogSaver(logSaver);
    }

    static boolean supportGranularResults(ITestInvocationListener listener) {
        if (!(listener instanceof ISupportGranularResults)) {
            return false;
        }

        return ((ISupportGranularResults) listener).supportGranularResults();
    }
}
