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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WebviewUtils {
    private TestInformation mTestInformation;

    public WebviewUtils(TestInformation testInformation) {
        mTestInformation = testInformation;
    }

    public WebviewPackage installWebview(String webviewVersion, String releaseChannel)
            throws IOException, InterruptedException, DeviceNotAvailableException {
        List<String> extraArgs = new ArrayList<>();
        if (webviewVersion == null
                && Arrays.asList("beta", "stable").contains(releaseChannel.toLowerCase())) {
            // Get current version of WebView in the stable or beta release channels.
            CLog.i(
                    "Getting the latest nightly official release version of the %s branch",
                    releaseChannel);
            String releaseChannelVersion = getNightlyBranchBuildVersion(releaseChannel);
            Assert.assertNotNull(
                    String.format(
                            "Could not retrieve the latest "
                                    + "nightly release version of the %s channel",
                            releaseChannel),
                    releaseChannelVersion);
            // Install the latest official build compiled for the beta or stable branches.
            extraArgs.addAll(
                    Arrays.asList("--milestone", releaseChannelVersion.split("\\.", 2)[0]));
        }
        CommandResult commandResult =
                WebviewInstallerToolPreparer.runWebviewInstallerToolCommand(
                        mTestInformation, webviewVersion, releaseChannel, extraArgs);

        Assert.assertEquals(
                "The WebView installer tool failed to install WebView:\n"
                        + commandResult.toString(),
                commandResult.getStatus(),
                CommandStatus.SUCCESS);

        printWebviewVersion();
        return getCurrentWebviewPackage();
    }

    private static String getNightlyBranchBuildVersion(String releaseChannel)
            throws IOException, MalformedURLException {
        final URL omahaProxyUrl = new URL("https://omahaproxy.appspot.com/all?os=webview");
        try (BufferedReader bufferedReader =
                new BufferedReader(
                        new InputStreamReader(omahaProxyUrl.openConnection().getInputStream()))) {
            String csvLine = null;
            while ((csvLine = bufferedReader.readLine()) != null) {
                String[] csvLineValues = csvLine.split(",");
                if (csvLineValues[1].toLowerCase().equals(releaseChannel.toLowerCase())) {
                    return csvLineValues[2];
                }
            }
        }
        return null;
    }

    public void uninstallWebview(
            WebviewPackage webviewPackage, WebviewPackage preInstalledWebviewPackage)
            throws DeviceNotAvailableException {
        Assert.assertNotEquals(
                "Test is attempting to uninstall the preinstalled WebView provider",
                webviewPackage,
                preInstalledWebviewPackage);
        updateWebviewImplementation(preInstalledWebviewPackage.getPackageName());
        mTestInformation
                .getDevice()
                .executeAdbCommand("uninstall", webviewPackage.getPackageName());
        printWebviewVersion();
    }

    private void updateWebviewImplementation(String webviewPackageName)
            throws DeviceNotAvailableException {
        CommandResult res =
                mTestInformation
                        .getDevice()
                        .executeShellV2Command(
                                String.format(
                                        "cmd webviewupdate set-webview-implementation %s",
                                        webviewPackageName));
        Assert.assertEquals(
                "Failed to set webview update: " + res, res.getStatus(), CommandStatus.SUCCESS);
    }

    public WebviewPackage getCurrentWebviewPackage() throws DeviceNotAvailableException {
        String dumpsys = mTestInformation.getDevice().executeShellCommand("dumpsys webviewupdate");
        return WebviewPackage.buildFromDumpsys(dumpsys);
    }

    public void printWebviewVersion() throws DeviceNotAvailableException {
        WebviewPackage currentWebview = getCurrentWebviewPackage();
        printWebviewVersion(currentWebview);
    }

    public void printWebviewVersion(WebviewPackage currentWebview)
            throws DeviceNotAvailableException {
        CLog.i("Current webview implementation: %s", currentWebview.getPackageName());
        CLog.i("Current webview version: %s", currentWebview.getVersion());
    }
}
