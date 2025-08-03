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

package com.android.tradefed.result;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A custom Tradefed reporter for Bazel test rules.
 *
 * <p>This custom result reporter computes and exports the exit code for Bazel to determine whether
 * a test target passes or fails. The file is written to a file for downstream test rules to read
 * and is required because Tradefed commands terminate with a 0 exit code despite test failures.
 */
@OptionClass(alias = "bazel-exit-code-result-reporter")
public final class BazelExitCodeResultReporter implements ITestInvocationListener {

    private final FileSystem mFileSystem;

    // This is not a File object in order to use an in-memory FileSystem in tests. Using Path would
    // have been more appropriate but Tradefed does not support option fields of that type.
    @Option(name = "file", mandatory = true, description = "Bazel exit code file")
    private String mExitCodeFile;

    private boolean mHasRunFailures;
    private boolean mHasTestFailures;

    @VisibleForTesting
    BazelExitCodeResultReporter(FileSystem fs) {
        this.mFileSystem = fs;
    }

    public BazelExitCodeResultReporter() {
        this(FileSystems.getDefault());
    }

    @Override
    public void testRunFailed(String errorMessage) {
        mHasRunFailures = true;
    }

    @Override
    public void testRunFailed(FailureDescription failure) {
        mHasRunFailures = true;
    }

    @Override
    public void testFailed(TestDescription test, String trace) {
        mHasTestFailures = true;
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        mHasTestFailures = true;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        writeExitCodeFile();
    }

    private void writeExitCodeFile() {
        ExitCode code = computeExitCode();

        CLog.logAndDisplay(
                LogLevel.INFO,
                "Test exit code file generated at %s. Exit Code %s",
                mExitCodeFile,
                code);

        try {
            Path path = mFileSystem.getPath(mExitCodeFile);
            Files.createDirectories(path.getParent());
            Files.write(path, String.valueOf(code.value).getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write exit code file.", e);
        }
    }

    private ExitCode computeExitCode() {
        if (mHasRunFailures) {
            return ExitCode.RUN_FAILURE;
        }

        if (mHasTestFailures) {
            return ExitCode.TESTS_FAILED;
        }

        return ExitCode.SUCCESS;
    }

    private enum ExitCode {
        SUCCESS(0),
        TESTS_FAILED(3),
        RUN_FAILURE(6);

        private final int value;

        ExitCode(int value) {
            this.value = value;
        }
    }
}
