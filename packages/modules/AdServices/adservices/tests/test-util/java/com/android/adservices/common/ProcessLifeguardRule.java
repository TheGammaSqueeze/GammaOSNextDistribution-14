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
package com.android.adservices.common;

import static com.android.adservices.shared.testing.common.FileHelper.writeFile;

import android.os.Looper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Rule used to protect the test process from crashing if an uncaught exception is thrown in the
 * background.
 *
 * <p><b>NOTE: </b>once this rule is used, it will call {@link
 * Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)} and never reset
 * it.
 */
public final class ProcessLifeguardRule extends AbstractProcessLifeguardRule {

    public ProcessLifeguardRule(Mode mode) {
        super(AndroidLogger.getInstance(), mode);
    }

    @Override
    protected boolean isMainThread() {
        return Looper.getMainLooper().isCurrentThread();
    }

    @Override
    protected void ignoreUncaughtBackgroundException(
            String testName,
            Thread thread,
            List<String> allTests,
            List<String> lastTests,
            Throwable uncaughtThrowable) {
        super.ignoreUncaughtBackgroundException(
                testName, thread, allTests, lastTests, uncaughtThrowable);
        writeToTestStorage(
                testName, thread, allTests, lastTests, /* testFailure= */ null, uncaughtThrowable);
    }

    @Override
    protected UncaughtBackgroundException newUncaughtBackgroundException(
            String testName,
            Thread thread,
            List<String> allTests,
            List<String> lastTests,
            Throwable uncaughtThrowable) {
        writeToTestStorage(
                testName, thread, allTests, lastTests, /* testFailure= */ null, uncaughtThrowable);
        return super.newUncaughtBackgroundException(
                testName, thread, allTests, lastTests, uncaughtThrowable);
    }

    @Override
    protected UncaughtBackgroundException newUncaughtBackgroundException(
            String testName,
            Thread thread,
            List<String> allTests,
            List<String> lastTests,
            Throwable testFailure,
            Throwable uncaughtThrowable) {
        writeToTestStorage(testName, thread, allTests, lastTests, testFailure, uncaughtThrowable);
        return super.newUncaughtBackgroundException(
                testName, thread, allTests, lastTests, testFailure, uncaughtThrowable);
    }

    private void writeToTestStorage(
            String testName,
            Thread thread,
            List<String> allTests,
            List<String> lastTests,
            @Nullable Throwable testFailure,
            Throwable uncaughtThrowable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        sw.append("Mode: ").append(mMode.toString()).append("\n");
        sw.append("Thread: ").append(thread.toString()).append("\n");

        sw.append("Uncaught failure: ");
        uncaughtThrowable.printStackTrace(pw);
        if (testFailure != null) {
            sw.append("Test failure: ");
            testFailure.printStackTrace(pw);
        }

        sw.append("" + allTests.size()).append(" total tests:\n");
        allTests.forEach(t -> sw.append('\t').append(t).append('\n'));
        sw.append("" + lastTests.size()).append(" tests since last failure:\n");
        lastTests.forEach(t -> sw.append('\t').append(t).append('\n'));

        writeFile(
                getClass().getSimpleName()
                        + "-"
                        + testName
                        + "-"
                        + System.currentTimeMillis()
                        + ".txt",
                sw.toString());
    }
}
