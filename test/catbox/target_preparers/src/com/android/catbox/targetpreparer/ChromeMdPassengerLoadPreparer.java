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

package com.android.catbox.targetpreparer;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OptionClass(alias = "chrome-md-passenger-load")
public class ChromeMdPassengerLoadPreparer extends BaseTargetPreparer {
    private static final String INSTR_SUCCESS = "OK (1 test)";

    @Option(name = "skip-display-id", description = "Display id to skip passenger load for")
    private List<Integer> mSkipDisplayIds = new ArrayList<>();

    @Option(name = "url", description = "Url to open in Chrome browser", mandatory = true)
    private String mUrl;

    @Option(name = "package", description = "Chrome package")
    private String mPackage = "com.android.chrome";

    @Option(name = "activity", description = "Chrome activity")
    private String mActivity = "com.google.android.apps.chrome.Main";

    @Option(
            name = "test-app-file-name",
            description =
                    "the name of an apk file to be installed in the user profiles.")
    private List<String> mTestFiles = new ArrayList<>();

    Map<Integer, Integer> mDisplayToCreatedUsers = new HashMap<>();
    private final ArrayList<TestAppInstallSetup> mInstallPreparers =
            new ArrayList<TestAppInstallSetup>();

    @Override
    public void setUp(TestInformation testInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        Set<Integer> displayIds = device.listDisplayIdsForStartingVisibleBackgroundUsers();
        for (Integer displayId : displayIds) {
            if (mSkipDisplayIds.contains(displayId)) {
                LogUtil.CLog.d("Skipping load on display %d", displayId);
                continue;
            }
            int userId = createUser(device, displayId);
            mDisplayToCreatedUsers.put(displayId, userId);
        }

        if (mDisplayToCreatedUsers.size() == 0) {
            LogUtil.CLog.w("Won't create any passenger load. No display ids matched.");
            throw new TargetSetupError(
                    String.format("Available displays on the device %s. Skipped displays %s",
                            displayIds, mSkipDisplayIds),
                    device.getDeviceDescriptor());
        }

        installApks(testInfo);

        for (Integer displayId : mDisplayToCreatedUsers.keySet()) {
            int userId = mDisplayToCreatedUsers.get(displayId);
            dismissInitialDialog(device, userId);
            simulatePassengerLoad(device, userId);
        }
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e)
            throws DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        for (TestAppInstallSetup installPreparer : mInstallPreparers) {
            installPreparer.tearDown(testInfo, e);
        }
        for (int userId : mDisplayToCreatedUsers.values()) {
            device.removeUser(userId);
        }
    }

    private int createUser(ITestDevice device, int displayId)
            throws TargetSetupError,
            DeviceNotAvailableException {
        int userId = device.createUser(String.format("user-display-%d", displayId));
        LogUtil.CLog.d(
                String.format("Created user with id %d for display %d", userId, displayId));
        if (!device.startVisibleBackgroundUser(userId, displayId, true)) {
            throw new TargetSetupError(
                    String.format("Device failed to switch to user %d", userId),
                    device.getDeviceDescriptor());
        }
        LogUtil.CLog.d(
                String.format("Started background user %d for display %d", userId, displayId));
        return userId;
    }

    private void installApks(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        for (int userId : mDisplayToCreatedUsers.values()) {
            TestAppInstallSetup installPreparer = new TestAppInstallSetup();
            LogUtil.CLog.d(
                    String.format("Installing the following test APKs in user %d: \n%s", userId,
                            mTestFiles));
            installPreparer.setUserId(userId);
            installPreparer.setShouldGrantPermission(true);
            for (String file : mTestFiles) {
                installPreparer.addTestFileName(file);
            }
            installPreparer.addInstallArg("-r");
            installPreparer.addInstallArg("-d");
            installPreparer.setUp(testInfo);
            mInstallPreparers.add(installPreparer);
        }
    }

    private void simulatePassengerLoad(ITestDevice device, int userId)
            throws TargetSetupError, DeviceNotAvailableException {
        LogUtil.CLog.d(String.format("Launching Chrome for User %d with url %s", userId, mUrl));
        String launchChromeActivityWithUrlCommand = String.format(
                "am start -n %s/%s --user %d -a android.intent.action.VIEW -d %s", mPackage,
                mActivity, userId, mUrl);
        CommandResult result = device.executeShellV2Command(launchChromeActivityWithUrlCommand);
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            throw new TargetSetupError(
                    String.format("Chrome activity failed to launch for user %d", userId),
                    device.getDeviceDescriptor());
        }
    }

    private void dismissInitialDialog(ITestDevice device, int userId)
            throws DeviceNotAvailableException, TargetSetupError {
        OutputStream output = new ByteArrayOutputStream();
        String dismissCommand = String.format(
                "am instrument -w --user %d -e class android.platform.tests"
                        + ".ChromeDismissDialogsTest android.platform.tests/androidx.test.runner"
                        + ".AndroidJUnitRunner",
                userId);
        device.executeShellV2Command(dismissCommand, output);
        if (!output.toString().contains(INSTR_SUCCESS)) {
            throw new TargetSetupError(
                    String.format("Failed dismissal.\nCommand output: %s", output),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }
}
