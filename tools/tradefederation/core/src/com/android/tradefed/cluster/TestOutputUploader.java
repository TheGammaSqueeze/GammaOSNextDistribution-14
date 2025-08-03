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
package com.android.tradefed.cluster;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import com.google.common.net.UrlEscapers;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Uploads test output files to local file system, GCS, or an HTTP(S) endpoint. */
public class TestOutputUploader {

    static final long UPLOAD_TIMEOUT_MS = 30 * 60 * 1000;
    static final long RETRY_INTERVAL_MS = 10 * 1000;
    static final int MAX_RETRY_COUNT = 2;
    static final String FILE_PROTOCOL = "file";
    static final String GCS_PROTOCOL = "gs";
    static final String HTTP_PROTOCOL = "http";
    static final String HTTPS_PROTOCOL = "https";

    private String mUploadUrl = null;
    private String mProtocol = null;
    private IRunUtil mRunUtil = null;

    public void setUploadUrl(final String url) throws MalformedURLException {
        mUploadUrl = url;
        URL urlObj = new URL(url);
        mProtocol = urlObj.getProtocol();
    }

    /**
     * Upload a file to the specified destination path (relative to the upload URL).
     *
     * @param file file to upload
     * @param destPath relative destination path
     * @return uploaded file URL
     */
    public String uploadFile(File file, String destPath) throws IOException {
        if (mUploadUrl == null) {
            throw new IllegalStateException("Upload URL is not set");
        }
        String uploadUrl = joinSegments(mUploadUrl, destPath);
        CLog.i("Uploading %s to %s", file.getAbsolutePath(), uploadUrl);

        switch (mProtocol) {
            case FILE_PROTOCOL:
                File destDir = new File(new URL(uploadUrl).getPath());
                destDir.mkdirs();
                File destFile = new File(destDir, file.getName());
                FileUtil.copyFile(file, destFile);
                return joinSegments(uploadUrl, file.getName());
            case GCS_PROTOCOL:
                executeUploadCommand(file, "gsutil", "cp", file.getAbsolutePath(), uploadUrl);
                return joinSegments(uploadUrl, file.getName());
            case HTTP_PROTOCOL:
            case HTTPS_PROTOCOL:
                // Upload URL for HTTP(S) protocols should include the filename, and special
                // characters in the paths should be escaped.
                String fullPath = joinSegments(destPath, file.getName());
                String encodedPath = UrlEscapers.urlFragmentEscaper().escape(fullPath);
                uploadUrl = joinSegments(mUploadUrl, encodedPath);
                executeUploadCommand(
                        file,
                        "curl",
                        "--request",
                        "POST",
                        "--form",
                        "file=@" + file.getAbsolutePath(),
                        "--fail", // Return non-zero status code on error.
                        "--location", // Handle redirects.
                        uploadUrl);
                return uploadUrl;
            default:
                throw new IllegalArgumentException(
                        String.format("Protocol '%s' is not supported", mProtocol));
        }
    }

    /** Executes an upload command and handle the result. */
    private void executeUploadCommand(File file, String... cmdArgs) {
        CommandResult result =
                getRunUtil()
                        .runTimedCmdRetry(
                                UPLOAD_TIMEOUT_MS, RETRY_INTERVAL_MS, MAX_RETRY_COUNT, cmdArgs);
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            String error =
                    String.format(
                            "Failed to upload %s, status = %s, stdout = [%s], stderr = [%s].",
                            file.getAbsolutePath(),
                            result.getStatus(),
                            result.getStdout(),
                            result.getStderr());
            CLog.e(error);
            throw new RuntimeException(error);
        }
    }

    /** Joins path segments with slashes. */
    private String joinSegments(String... segments) {
        return Stream.of(segments)
                .filter(Objects::nonNull) // Ignore null segments.
                .map(s -> s.replaceAll("^/|/$", "")) // Remove leading/trailing slashes.
                .collect(Collectors.joining("/"));
    }

    @VisibleForTesting
    IRunUtil getRunUtil() {
        if (mRunUtil == null) {
            mRunUtil = RunUtil.getDefault();
        }
        return mRunUtil;
    }
}
