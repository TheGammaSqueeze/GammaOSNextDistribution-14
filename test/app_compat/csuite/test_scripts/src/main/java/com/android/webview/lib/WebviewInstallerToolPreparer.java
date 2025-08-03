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

package com.android.webview.tests;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

public class WebviewInstallerToolPreparer implements ITargetPreparer {
    private static final long COMMAND_TIMEOUT_MILLIS = 5 * 60 * 1000;
    private static final String WEBVIEW_INSTALLER_TOOL_PATH = "WEBVIEW_INSTALLER_TOOL_PATH";
    private static final String GCLOUD_CLI_PATH = "GCLOUD_CLI_PATH";

    private File mGcloudCliDir;
    private RunUtilProvider mRunUtilProvider;

    @Option(
            name = "gcloud-cli-zip-archive",
            description = "Path to the google cli zip archive.",
            importance = Importance.ALWAYS)
    private File mGcloudCliZipArchive;

    @Option(
            name = "webview-installer-tool",
            description = "Path to the webview installer executable.",
            importance = Importance.ALWAYS)
    private File mWebviewInstallerTool;

    public WebviewInstallerToolPreparer(RunUtilProvider runUtilProvider) {
        mRunUtilProvider = runUtilProvider;
    }

    public WebviewInstallerToolPreparer() {
        this(() -> new RunUtil());
    }

    public static CommandResult runWebviewInstallerToolCommand(
            TestInformation testInformation,
            @Nullable String webviewVersion,
            @Nullable String releaseChannel,
            List<String> extraArgs) {
        RunUtil runUtil = new RunUtil();
        runUtil.setEnvVariable("HOME", getGcloudCliPath(testInformation));

        List<String> commandLineArgs =
                new ArrayList<>(
                        Arrays.asList(
                                getWebviewInstallerToolPath(testInformation),
                                "--non-next",
                                "--serial",
                                testInformation.getDevice().getSerialNumber(),
                                "-vvv",
                                "--gsutil",
                                Paths.get(
                                                getGcloudCliPath(testInformation),
                                                "google-cloud-sdk",
                                                "bin",
                                                "gsutil")
                                        .toFile()
                                        .getAbsolutePath()));
        commandLineArgs.addAll(extraArgs);

        if (webviewVersion != null) {
            commandLineArgs.addAll(Arrays.asList("--chrome-version", webviewVersion));
        }

        if (releaseChannel != null) {
            commandLineArgs.addAll(Arrays.asList("--channel", releaseChannel));
        }

        return runUtil.runTimedCmd(
                COMMAND_TIMEOUT_MILLIS,
                System.out,
                System.out,
                commandLineArgs.toArray(new String[0]));
    }

    public static void setGcloudCliPath(TestInformation testInformation, File gcloudCliDir) {
        testInformation
                .getBuildInfo()
                .addBuildAttribute(GCLOUD_CLI_PATH, gcloudCliDir.getAbsolutePath());
    }

    public static void setWebviewInstallerToolPath(
            TestInformation testInformation, File webviewInstallerTool) {
        testInformation
                .getBuildInfo()
                .addBuildAttribute(
                        WEBVIEW_INSTALLER_TOOL_PATH, webviewInstallerTool.getAbsolutePath());
    }

    public static String getWebviewInstallerToolPath(TestInformation testInformation) {
        return testInformation.getBuildInfo().getBuildAttributes().get(WEBVIEW_INSTALLER_TOOL_PATH);
    }

    public static String getGcloudCliPath(TestInformation testInformation) {
        return testInformation.getBuildInfo().getBuildAttributes().get(GCLOUD_CLI_PATH);
    }

    @Override
    public void setUp(TestInformation testInfo) throws TargetSetupError {
        Assert.assertNotEquals(
                "Argument --webview-installer-tool must be used.", mWebviewInstallerTool, null);
        Assert.assertNotEquals(
                "Argument --gcloud-cli-zip must be used.", mGcloudCliZipArchive, null);
        try {
            RunUtil runUtil = mRunUtilProvider.get();
            mGcloudCliDir = Files.createTempDirectory(null).toFile();
            CommandResult unzipRes =
                    runUtil.runTimedCmd(
                            COMMAND_TIMEOUT_MILLIS,
                            "unzip",
                            mGcloudCliZipArchive.getAbsolutePath(),
                            "-d",
                            mGcloudCliDir.getAbsolutePath());

            Assert.assertEquals(
                    "Unable to unzip the gcloud cli zip archive",
                    unzipRes.getStatus(),
                    CommandStatus.SUCCESS);

            // The 'gcloud init' command creates configuration files for gsutil and other
            // applications that use the gcloud sdk in the home directory. We can isolate
            // the effects of these configuration files to the processes that run the
            // gcloud and gsutil executables tracked by this class by setting the home
            // directory for processes that run those executables to a temporary directory
            // also tracked by this class.
            runUtil.setEnvVariable("HOME", mGcloudCliDir.getAbsolutePath());
            File gcloudBin =
                    mGcloudCliDir
                            .toPath()
                            .resolve(Paths.get("google-cloud-sdk", "bin", "gcloud"))
                            .toFile();
            String gcloudInitScript =
                    String.format(
                            "printf \"1\\n1\" | %s init --console-only",
                            gcloudBin.getAbsolutePath());
            CommandResult gcloudInitRes =
                    runUtil.runTimedCmd(
                            COMMAND_TIMEOUT_MILLIS,
                            System.out,
                            System.out,
                            "sh",
                            "-c",
                            gcloudInitScript);
            Assert.assertEquals(
                    "gcloud cli initialization failed",
                    gcloudInitRes.getStatus(),
                    CommandStatus.SUCCESS);

            CommandResult chmodRes =
                    runUtil.runTimedCmd(
                            COMMAND_TIMEOUT_MILLIS,
                            System.out,
                            System.out,
                            "chmod",
                            "755",
                            "-v",
                            mWebviewInstallerTool.getAbsolutePath());

            Assert.assertEquals(
                    "The 'chmod 755 -v <WebView installer tool>' command failed",
                    chmodRes.getStatus(),
                    CommandStatus.SUCCESS);

        } catch (Exception ex) {
            throw new TargetSetupError("Caught an exception during setup:\n" + ex);
        }
        setGcloudCliPath(testInfo, mGcloudCliDir);
        setWebviewInstallerToolPath(testInfo, mWebviewInstallerTool);
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) {
        // Clean up some files.
        mRunUtilProvider
                .get()
                .runTimedCmd(COMMAND_TIMEOUT_MILLIS, "rm", "-rf", mGcloudCliDir.getAbsolutePath());
    }

    interface RunUtilProvider {
        RunUtil get();
    }
}
