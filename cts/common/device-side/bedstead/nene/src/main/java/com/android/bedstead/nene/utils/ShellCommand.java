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

package com.android.bedstead.nene.utils;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A tool for progressively building and then executing a shell command.
 */
public final class ShellCommand {

    // 10 seconds
    private static final int MAX_WAIT_UNTIL_ATTEMPTS = 100;
    private static final long WAIT_UNTIL_DELAY_MILLIS = 100;

    /**
     * Begin building a new {@link ShellCommand}.
     */
    @CheckResult
    public static Builder builder(String command) {
        if (command == null) {
            throw new NullPointerException();
        }
        return new Builder(command);
    }

    /**
     * Create a builder and if {@code userReference} is not {@code null}, add "--user <userId>".
     */
    @CheckResult
    public static Builder builderForUser(@Nullable UserReference userReference, String command) {
        Builder builder = builder(command);
        if (userReference != null) {
            builder.addOption("--user", userReference.id());
        }

        return builder;
    }

    public static final class Builder {
        private final StringBuilder commandBuilder;
        @Nullable
        private byte[] mStdInBytes = null;
        @Nullable
        private Duration mTimeout = null;
        @Nullable
        private boolean mAllowEmptyOutput = false;
        @Nullable
        private Function<String, Boolean> mOutputSuccessChecker = null;
        private boolean mShouldRunAsRoot = false;

        private Builder(String command) {
            commandBuilder = new StringBuilder(command);
        }

        /**
         * Run command as root by adding {@code su root} as prefix.
         */
        public Builder asRoot(boolean shouldRunAsRoot) {
            mShouldRunAsRoot = shouldRunAsRoot;
            return this;
        }

        /**
         * Add an option to the command.
         *
         * <p>e.g. --user 10
         */
        @CheckResult
        public Builder addOption(String key, Object value) {
            // TODO: Deal with spaces/etc.
            commandBuilder.append(" ").append(key).append(" ").append(value);
            return this;
        }

        /**
         * Add an operand to the command.
         */
        @CheckResult
        public Builder addOperand(Object value) {
            // TODO: Deal with spaces/etc.
            commandBuilder.append(" ").append(value);
            return this;
        }

        /**
         * Add a timeout to the execution of the command.
         */
        public Builder withTimeout(Duration timeout) {
            mTimeout = timeout;
            return this;
        }

        /**
         * If {@code false} an error will be thrown if the command has no output.
         *
         * <p>Defaults to {@code false}
         */
        @CheckResult
        public Builder allowEmptyOutput(boolean allowEmptyOutput) {
            mAllowEmptyOutput = allowEmptyOutput;
            return this;
        }

        /**
         * Write the given {@code stdIn} to standard in.
         */
        @CheckResult
        public Builder writeToStdIn(byte[] stdIn) {
            mStdInBytes = stdIn;
            return this;
        }

        /**
         * Validate the output when executing.
         *
         * <p>{@code outputSuccessChecker} should return {@code true} if the output is valid.
         */
        @CheckResult
        public Builder validate(Function<String, Boolean> outputSuccessChecker) {
            mOutputSuccessChecker = outputSuccessChecker;
            return this;
        }

        /**
         * Build the full command including all options and operands.
         */
        public String build() {
            if (mShouldRunAsRoot) {
                return commandBuilder.insert(0, "su root ").toString();
            }

            return commandBuilder.toString();
        }

        /**
         * See {@link #execute()} except that any {@link AdbException} is wrapped in a
         * {@link NeneException} with the message {@code errorMessage}.
         */
        public String executeOrThrowNeneException(String errorMessage) throws NeneException {
            try {
                return execute();
            } catch (AdbException e) {
                throw new NeneException(errorMessage, e);
            }
        }

        /** See {@link ShellCommandUtils#executeCommand(java.lang.String)}. */
        public String execute() throws AdbException {
            if (mTimeout == null) {
                return executeSync();
            }

            AtomicReference<AdbException> adbException = new AtomicReference<>(null);
            AtomicReference<String> result = new AtomicReference<>(null);

            CountDownLatch latch = new CountDownLatch(1);

            Thread thread = new Thread(() -> {
                try {
                    result.set(executeSync());
                } catch (AdbException e) {
                    adbException.set(e);
                } finally {
                    latch.countDown();
                }
            });
            thread.start();

            try {
                if (!latch.await(mTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new AdbException("Command could not run in " + mTimeout, build(), "");
                }
            } catch (InterruptedException e) {
                throw new AdbException("Interrupted while executing command", build(), "", e);
            }

            if (adbException.get() != null) {
                throw adbException.get();
            }

            return result.get();
        }

        private String executeSync() throws AdbException {
            if (mOutputSuccessChecker != null) {
                return ShellCommandUtils.executeCommandAndValidateOutput(
                        build(),
                        /* allowEmptyOutput= */ mAllowEmptyOutput,
                        mStdInBytes,
                        mOutputSuccessChecker);
            }

            return ShellCommandUtils.executeCommand(
                    build(),
                    /* allowEmptyOutput= */ mAllowEmptyOutput,
                    mStdInBytes);
        }

        /**
         * See {@link #execute} and then extract information from the output using
         * {@code outputParser}.
         *
         * <p>If any {@link Exception} is thrown by {@code outputParser}, and {@link AdbException}
         * will be thrown.
         */
        public <E> E executeAndParseOutput(Function<String, E> outputParser) throws AdbException {
            String output = execute();

            try {
                return outputParser.apply(output);
            } catch (RuntimeException e) {
                throw new AdbException(
                        "Could not parse output", commandBuilder.toString(), output, e);
            }
        }

        /**
         * Execute the command and check that the output meets a given criteria. Run the
         * command repeatedly until the output meets the criteria.
         *
         * <p>{@code outputSuccessChecker} should return {@code true} if the output indicates the
         * command executed successfully.
         */
        public String executeUntilValid() throws InterruptedException, AdbException {
            int maxWaitUntilAttempts = MAX_WAIT_UNTIL_ATTEMPTS;
            long waitUntilDelayMillis = WAIT_UNTIL_DELAY_MILLIS;
            return executeUntilValid(maxWaitUntilAttempts, waitUntilDelayMillis);
        }

        /**
         * Execute the command and check that the output meets a given criteria. Run the
         * command repeatedly until the output meets the criteria.
         *
         * @param maxWaitUntilAttempts maximum number of attempts
         * @param waitUntilDelayMillis minimum interval between calls in milliseconds
         * <p>{@code outputSuccessChecker} should return {@code true} if the output indicates the
         * command executed successfully.
         */
        public String executeUntilValid(int maxWaitUntilAttempts, long waitUntilDelayMillis) throws
                InterruptedException, AdbException {
            int attempts = 0;
            while (attempts++ < maxWaitUntilAttempts) {
                try {
                    return execute();
                } catch (AdbException e) {
                    // ignore, will retry
                    Thread.sleep(waitUntilDelayMillis);
                }
            }
            return execute();
        }

        public BytesBuilder forBytes() {
            if (mOutputSuccessChecker != null) {
                throw new IllegalStateException("Cannot call .forBytes after .validate");
            }

            return new BytesBuilder(this);
        }

        @Override
        public String toString() {
            return "ShellCommand$Builder{cmd=" + build() + "}";
        }
    }

    public static final class BytesBuilder {

        private final Builder mBuilder;

        private BytesBuilder(Builder builder) {
            mBuilder = builder;
        }

        /** See {@link ShellCommandUtils#executeCommandForBytes(java.lang.String)}. */
        public byte[] execute() throws AdbException {
            return ShellCommandUtils.executeCommandForBytes(
                    mBuilder.build(),
                    mBuilder.mStdInBytes);
        }
    }
}