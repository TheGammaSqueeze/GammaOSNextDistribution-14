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

package com.android.csuite.core;

import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.MoreFiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/** A Tradefed preparer that preparers an app crawler on the host before testing. */
public final class AppCrawlTesterHostPreparer implements ITargetPreparer {
    private static final long COMMAND_TIMEOUT_MILLIS = 4 * 60 * 1000;
    private static final String SDK_PATH_KEY = "SDK_PATH_KEY";
    private static final String CRAWLER_BIN_PATH_KEY = "CSUITE_INTERNAL_CRAWLER_BIN_PATH";
    private static final String CREDENTIAL_PATH_KEY = "CSUITE_INTERNAL_CREDENTIAL_PATH";
    private static final String IS_READY_KEY = "CSUITE_INTERNAL_IS_READY";
    @VisibleForTesting static final String SDK_TAR_OPTION = "sdk-tar";
    @VisibleForTesting static final String CRAWLER_BIN_OPTION = "crawler-bin";
    @VisibleForTesting static final String CREDENTIAL_JSON_OPTION = "credential-json";
    private final FileSystem mFileSystem;

    @Option(
            name = SDK_TAR_OPTION,
            mandatory = true,
            description = "The path to a tar file that contains the Android SDK.")
    private File mSdkTar;

    @Option(
            name = CRAWLER_BIN_OPTION,
            mandatory = true,
            description = "Path to the directory containing the required crawler binary files.")
    private File mCrawlerBin;

    @Option(
            name = CREDENTIAL_JSON_OPTION,
            mandatory = true,
            description = "The credential json file to access the crawler server.")
    private File mCredential;

    private RunUtilProvider mRunUtilProvider;

    public AppCrawlTesterHostPreparer() {
        this(() -> new RunUtil(), FileSystems.getDefault());
    }

    @VisibleForTesting
    AppCrawlTesterHostPreparer(RunUtilProvider runUtilProvider, FileSystem fileSystem) {
        mRunUtilProvider = runUtilProvider;
        mFileSystem = fileSystem;
    }

    /**
     * Returns a path that contains Android SDK.
     *
     * @param testInfo The test info where the path is stored in.
     * @return The path to Android SDK; Null if not set.
     */
    public static String getSdkPath(TestInformation testInfo) {
        return getPathFromBuildInfo(testInfo, SDK_PATH_KEY);
    }

    /**
     * Returns a path that contains the crawler binaries.
     *
     * @param testInfo The test info where the path is stored in.
     * @return The path to the crawler binaries folder; Null if not set.
     */
    public static String getCrawlerBinPath(TestInformation testInfo) {
        return getPathFromBuildInfo(testInfo, CRAWLER_BIN_PATH_KEY);
    }

    /**
     * Returns a path to the credential json file for accessing the Robo crawler server.
     *
     * @param testInfo The test info where the path is stored in.
     * @return The path to the crawler credential json file.
     */
    public static String getCredentialPath(TestInformation testInfo) {
        return getPathFromBuildInfo(testInfo, CREDENTIAL_PATH_KEY);
    }

    /**
     * Checks whether the preparer has successfully executed.
     *
     * @param testInfo The test info .
     * @return True if the preparer was executed successfully; False otherwise.
     */
    public static boolean isReady(TestInformation testInfo) {
        return testInfo.getBuildInfo().getBuildAttributes().get(IS_READY_KEY) != null;
    }

    private static String getPathFromBuildInfo(TestInformation testInfo, String key) {
        return testInfo.getBuildInfo().getBuildAttributes().get(key);
    }

    @VisibleForTesting
    static void setSdkPath(TestInformation testInfo, Path path) {
        testInfo.getBuildInfo().addBuildAttribute(SDK_PATH_KEY, path.toString());
    }

    @VisibleForTesting
    static void setCrawlerBinPath(TestInformation testInfo, Path path) {
        testInfo.getBuildInfo().addBuildAttribute(CRAWLER_BIN_PATH_KEY, path.toString());
    }

    @VisibleForTesting
    static void setCredentialPath(TestInformation testInfo, Path path) {
        testInfo.getBuildInfo().addBuildAttribute(CREDENTIAL_PATH_KEY, path.toString());
    }

    @Override
    public void setUp(TestInformation testInfo) throws TargetSetupError {
        IRunUtil runUtil = mRunUtilProvider.get();

        Path sdkPath;
        try {
            sdkPath = Files.createTempDirectory("android-sdk");
        } catch (IOException e) {
            throw new TargetSetupError("Failed to create the output path for android sdk.", e);
        }

        String cmd = "tar -xvzf " + mSdkTar.getPath() + " -C " + sdkPath.toString();
        CLog.i("Decompressing Android SDK to " + sdkPath.toString());
        CommandResult res = runUtil.runTimedCmd(COMMAND_TIMEOUT_MILLIS, cmd.split(" "));
        if (!res.getStatus().equals(CommandStatus.SUCCESS)) {
            throw new TargetSetupError(String.format("Failed to untar android sdk: %s", res));
        }

        setSdkPath(testInfo, sdkPath);

        Path jar = mCrawlerBin.toPath().resolve("crawl_launcher_deploy.jar");
        if (!Files.exists(jar)) {
            jar = mCrawlerBin.toPath().resolve("utp-cli-android_deploy.jar");
        }

        // Make the crawler binary executable.
        String chmodCmd = "chmod 555 " + jar.toString();
        CommandResult chmodRes = runUtil.runTimedCmd(COMMAND_TIMEOUT_MILLIS, chmodCmd.split(" "));
        if (!chmodRes.getStatus().equals(CommandStatus.SUCCESS)) {
            throw new TargetSetupError(
                    String.format("Failed to make crawler binary executable: %s", chmodRes));
        }

        setCrawlerBinPath(testInfo, mCrawlerBin.toPath());

        setCredentialPath(testInfo, mCredential.toPath());

        testInfo.getBuildInfo().addBuildAttribute(IS_READY_KEY, "true");
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) {
        try {
            cleanUp(mFileSystem.getPath(getSdkPath(testInfo)));
        } catch (IOException ioException) {
            CLog.e(ioException);
        }
    }

    private static void cleanUp(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        MoreFiles.deleteRecursively(path);
    }

    @VisibleForTesting
    interface RunUtilProvider {
        IRunUtil get();
    }
}
