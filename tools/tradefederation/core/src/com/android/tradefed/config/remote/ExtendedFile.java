/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.config.remote;

import com.android.tradefed.build.BuildRetrievalError;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** A extension of standard file to carry a build related metadata. */
public class ExtendedFile extends File {

    private String mBuildId;
    private String mBuildTarget;
    private String mBranch;
    private String mRemoteFilePath;

    private Future<BuildRetrievalError> mParallelDownload;

    ExtendedFile(String path) {
        super(path);
    }

    public ExtendedFile(File file, String buildId, String buildTarget) {
        super(file.getAbsolutePath());
        mBuildId = buildId;
        mBuildTarget = buildTarget;
    }

    public ExtendedFile(File file, String buildId, String buildTarget, String branch) {
        this(file, buildId, buildTarget);
        mBranch = branch;
    }

    public ExtendedFile(
            File file, String buildId, String buildTarget, String branch, String remoteFilePath) {
        this(file, buildId, buildTarget, branch);
        mRemoteFilePath = remoteFilePath;
    }

    /** Returns the buildid metadata. */
    public String getBuildId() {
        return mBuildId;
    }

    /** Returns the target metadata. */
    public String getBuildTarget() {
        return mBuildTarget;
    }

    /** Returns the branch metadata. */
    public String getBranch() {
        return mBranch;
    }

    /** Returns the remote file path metadata. */
    public String getRemoteFilePath() {
        return mRemoteFilePath;
    }

    public void setDownloadFuture(Future<BuildRetrievalError> download) {
        mParallelDownload = download;
    }

    public void cancelDownload() {
        if (!isDownloadingInParallel()) {
            return;
        }
        try {
            mParallelDownload.cancel(true);
        } catch (RuntimeException ignored) {
            // Ignore
        }
    }

    public void waitForDownload() throws BuildRetrievalError {
        if (!isDownloadingInParallel()) {
            return;
        }
        try {
            BuildRetrievalError error = mParallelDownload.get();
            if (error == null) {
                return;
            }
            throw error;
        } catch (ExecutionException | InterruptedException e) {
            throw new BuildRetrievalError(
                    String.format("Error during parallel download: %s", e.getMessage()), e);
        }
    }

    public boolean isDownloadingInParallel() {
        return mParallelDownload != null;
    }

    public boolean isDoneDownloadingInParallel() {
        if (mParallelDownload == null) {
            return true;
        }
        return mParallelDownload.isDone();
    }
}
