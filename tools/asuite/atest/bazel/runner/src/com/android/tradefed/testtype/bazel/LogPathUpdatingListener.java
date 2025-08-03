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

import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Listener implementation that will find log files that have been saved to a new location using the
 * given root and delimiter.
 *
 * <p>Changes all log calls to testLog() to pass the file contents directly since original files may
 * have been deleted by the time they are read with the other calls.
 */
final class LogPathUpdatingListener extends ForwardingTestListener {

    private final ITestInvocationListener mDelegate;
    private final Path mDelimiter;
    private final Path mNewRoot;

    public LogPathUpdatingListener(ITestInvocationListener delegate, Path delimiter, Path newRoot) {
        mDelegate = delegate;
        mDelimiter = delimiter;
        mNewRoot = newRoot;
    }

    @Override
    protected ITestInvocationListener delegate() {
        return mDelegate;
    }

    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {

        // Call testLog() instead to pass file contents directly instead of a reference to a File
        // which may be deleted before it's read.
        delegate().testLog(dataName, logFile.getType(), dataStream);
    }

    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        // Call testLog() instead to pass file contents directly instead of a reference to a File
        // which may be deleted before it's read.
        delegate()
                .testLog(
                        dataName,
                        logFile.getType(),
                        new FileInputStreamSource(
                                new File(findNewArtifactPath(Paths.get(logFile.getPath())))));
    }

    private String findNewArtifactPath(Path originalPath) {
        // The log files are stored under
        // (newRoot)/(delimiter)/inv_xxx/inv_xxx/artifact so the new path is
        // found by trimming down the original path until it starts with (delimiter) and
        // appending that to our new root.

        Path relativePath = originalPath;
        while (!relativePath.startsWith(mDelimiter)
                && relativePath.getNameCount() > mDelimiter.getNameCount()) {
            relativePath = relativePath.subpath(1, relativePath.getNameCount());
        }

        if (!relativePath.startsWith(mDelimiter)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Artifact path '%s' does not contain delimiter '%s' and therefore"
                                    + " cannot be found",
                            originalPath, mDelimiter));
        }

        return mNewRoot.resolve(relativePath).toAbsolutePath().toString();
    }
}
