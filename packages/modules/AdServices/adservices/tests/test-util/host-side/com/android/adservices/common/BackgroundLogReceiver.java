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

import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.device.BackgroundDeviceAction;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Enables capturing device logs and exposing them to the host test. */
public final class BackgroundLogReceiver extends MultiLineReceiver {
    private volatile boolean mCancelled;
    private final List<String> mLines = new ArrayList<>();
    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
    private final String mName;
    private final String mLogcatCmd;
    private final ITestDevice mTestDevice;
    private final Predicate<String[]> mEarlyStopCondition;
    private BackgroundDeviceAction mBackgroundDeviceAction;

    private BackgroundLogReceiver(
            String name,
            String logcatCmd,
            ITestDevice device,
            Predicate<String[]> earlyStopCondition) {
        mName = name;
        mLogcatCmd = logcatCmd;
        mEarlyStopCondition = earlyStopCondition;
        mTestDevice = device;
    }

    @Override
    public void processNewLines(String[] lines) {
        if (lines.length == 0) {
            return;
        }

        Arrays.stream(lines).filter(s -> !s.trim().isEmpty()).forEach(mLines::add);

        if (mEarlyStopCondition != null && mEarlyStopCondition.test(lines)) {
            stopBackgroundCollection();
            mCountDownLatch.countDown();
        }
    }

    @Override
    public boolean isCancelled() {
        return mCancelled;
    }

    /**
     * Collects logs until timeout or stop condition is reached. This method needs to be only used
     * once per instance.
     *
     * @param timeoutMilliseconds the maximum time after which log collection should stop, if the
     *     early stop condition was not encountered previously.
     * @return true if log collection stopped because the early stop condition was encountered,
     *     false if log collection stopped due to timeout
     * @throws InterruptedException if the current thread is interrupted
     */
    public boolean collectLogs(long timeoutMilliseconds) throws InterruptedException {
        startBackgroundCollection();
        return waitForLogs(timeoutMilliseconds);
    }

    /** Begins log collection. This method needs to be only used once per instance. */
    public void startBackgroundCollection() {
        if (mBackgroundDeviceAction != null) {
            throw new IllegalStateException("This method should only be called once per instance");
        }

        mBackgroundDeviceAction =
                new BackgroundDeviceAction(mLogcatCmd, mName, mTestDevice, this, 0);
        mBackgroundDeviceAction.start();
    }

    /**
     * Wait until timeout or stop condition is reached. This method can only be used once per
     * instance.
     *
     * @param timeoutMilliseconds the maximum time after which log collection should stop, if the
     *     early stop condition was not encountered previously.
     * @return true if log collection stopped because the early stop condition was encountered,
     *     false if log collection stopped due to timeout
     * @throws InterruptedException if the current thread is interrupted
     */
    public boolean waitForLogs(long timeoutMilliseconds) throws InterruptedException {
        if (mBackgroundDeviceAction == null) {
            throw new IllegalStateException(
                    "Log collection not started. Call startBackgroundCollection first");
        }

        boolean earlyStop = mCountDownLatch.await(timeoutMilliseconds, TimeUnit.MILLISECONDS);
        stopBackgroundCollection();
        return earlyStop;
    }

    /** Stops log collection and adds all logs to input logger. */
    public void stopAndAddTestLog(DeviceJUnit4ClassRunner.TestLogData logger) {
        if (mBackgroundDeviceAction != null) mBackgroundDeviceAction.cancel();
        if (isCancelled()) return;
        mCancelled = true;

        String joined = String.join("\n", mLines);
        try (InputStreamSource data = new ByteArrayInputStreamSource(joined.getBytes())) {
            logger.addTestLog(mName, LogDataType.TEXT, data);
        }
    }

    /** Ends log collection. This method needs to be only used once per instance. */
    private void stopBackgroundCollection() {
        if (mBackgroundDeviceAction != null) {
            mBackgroundDeviceAction.cancel();
        }
        if (isCancelled()) {
            return;
        }
        mCancelled = true;
    }

    /**
     * Checks if the collected logs match the specified regex pattern
     *
     * @param pattern the regex pattern to look for
     * @return {@code true} if the logs are non-empty and match the pattern, {@code false} otherwise
     */
    public boolean patternMatches(Pattern pattern) {
        String joined = String.join("\n", mLines);
        return joined.length() > 0 && pattern.matcher(joined).find();
    }

    /**
     * Gets all the collected log lines.
     *
     * @return the log lines that have been collected.
     */
    public List<String> getCollectedLogs() {
        return mLines;
    }

    /** Builder class for the BackgroundLogReceiver. */
    public static final class Builder {
        private String mName = "background-logcat-receiver";
        private ITestDevice mDevice;
        private String mLogCatCommand;
        private Predicate<String[]> mEarlyStopCondition;

        public Builder setName(String name) {
            mName = Objects.requireNonNull(name);
            return this;
        }

        public Builder setDevice(ITestDevice device) {
            mDevice = Objects.requireNonNull(device);
            return this;
        }

        public Builder setLogCatCommand(String command) {
            mLogCatCommand = Objects.requireNonNull(command);
            return this;
        }

        /**
         * Sets the condition that indicates whether to stop collecting logs before timeout happens
         *
         * @param earlyStopCondition the predicate to invoke with each batch of logs. If the
         *     predicate returns {@code true}, it will cause log collection to stop right away.
         * @return the {@link Builder} instance
         */
        public Builder setEarlyStopCondition(Predicate<String[]> earlyStopCondition) {
            mEarlyStopCondition = earlyStopCondition;
            return this;
        }

        public BackgroundLogReceiver build() {
            Objects.requireNonNull(mDevice);
            Objects.requireNonNull(mLogCatCommand);

            return new BackgroundLogReceiver(mName, mLogCatCommand, mDevice, mEarlyStopCondition);
        }
    }
}
