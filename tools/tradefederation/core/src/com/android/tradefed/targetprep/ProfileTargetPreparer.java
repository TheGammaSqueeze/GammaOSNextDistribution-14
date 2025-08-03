/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import static com.android.tradefed.result.error.DeviceErrorIdentifier.SHELL_COMMAND_ERROR;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.device.UserInfo.UserType;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.error.ErrorIdentifier;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for setting up target preparer for any profile user {@code
 * android.os.usertype.profile.XXX}.
 */
public abstract class ProfileTargetPreparer extends BaseTargetPreparer {

    @VisibleForTesting static final String RUN_TESTS_AS_USER_KEY = "RUN_TESTS_AS_USER";

    @VisibleForTesting static final String TEST_PACKAGE_NAME_OPTION = "test-package-name";

    @VisibleForTesting static final String SKIP_TESTS_REASON_KEY = "skip-tests-reason";

    private static final int USERTYPE_NOT_SUPPORTED = -2;

    private UserType mTradefedUserType;
    private String mProfileUserType;
    private int profileIdToDelete = -1;

    private DeviceOwner mDeviceOwnerToSet = null;

    private static class DeviceOwner {
        final String componentName;
        final int userId;

        DeviceOwner(String componentName, int userId) {
            this.componentName = componentName;
            this.userId = userId;
        }
    }

    ProfileTargetPreparer(UserType userType, String actualType) {
        mTradefedUserType = userType;
        mProfileUserType = actualType;
    }

    @Option(
            name = TEST_PACKAGE_NAME_OPTION,
            description =
                    "the name of a package to be installed on the profile. "
                            + "This must already be installed on the device.",
            importance = Option.Importance.IF_UNSET)
    private List<String> mTestPackages = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        if (checkIfUserTypeIsNotSupported(testInfo)) {
            return;
        }

        int profileId = getExistingProfileId(testInfo.getDevice());

        if (profileId == -1) {
            if (!checkTrueOrSkipOnDevice(
                    canCreateAdditionalUsers(testInfo.getDevice(), /* numberOfUsers*/ 1),
                    "Device cannot support additional users",
                    testInfo)) {
                return;
            }

            profileId = createProfile(testInfo);
            if (profileId == USERTYPE_NOT_SUPPORTED) {
                return;
            }
            profileIdToDelete = profileId;
        }

        // The wait flag is only supported on Android 29+
        testInfo.getDevice()
                .startUser(profileId, /* waitFlag= */ testInfo.getDevice().getApiLevel() >= 29);

        for (String pkg : mTestPackages) {
            testInfo.getDevice()
                    .executeShellCommand("pm install-existing --user " + profileId + " " + pkg);
        }

        testInfo.properties().put(RUN_TESTS_AS_USER_KEY, Integer.toString(profileId));
    }

    private boolean checkIfUserTypeIsNotSupported(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        if (mTradefedUserType.isManagedProfile()
                && !requireFeatures(testInfo, "android.software.managed_users")) {
            return true;
        } else if (mTradefedUserType.isCloneProfile() && !matchesApiLevel(testInfo, 34)) {
            // Clone profile type was introduced in Android S(api 31).
            // However, major functionalities supporting clone got added in 34.
            // Android U = 34
            return true;
        }
        return false;
    }

    private boolean matchesApiLevel(TestInformation testInfo, int apiLevel)
            throws DeviceNotAvailableException, TargetSetupError {
        return checkTrueOrSkipOnDevice(
                (testInfo.getDevice().getApiLevel() >= apiLevel),
                "Device does not support feature as api level "
                        + apiLevel
                        + " requirement does not match",
                testInfo);
    }

    private int getExistingProfileId(ITestDevice device) throws DeviceNotAvailableException {
        for (Map.Entry<Integer, UserInfo> userInfo : device.getUserInfos().entrySet()) {
            if (userInfo.getValue().isUserType(mTradefedUserType, -1)) {
                return userInfo.getKey();
            }
        }
        return -1;
    }

    private int createProfile(TestInformation testInfo)
            throws DeviceNotAvailableException, TargetSetupError {
        final String createUserOutput;
        ITestDevice device = testInfo.getDevice();
        int parentProfile = device.getCurrentUser();
        String command = "";

        if (!mTradefedUserType.isProfile()) {
            return -1;
        }

        command =
                "pm create-user --profileOf " + parentProfile + " --user-type " + mProfileUserType;

        if (device.getApiLevel() >= 34) { // --for-testing was added in U
            command += " --for-testing";
        }

        command += " user";

        if (mTradefedUserType.isCloneProfile() || mTradefedUserType.isManagedProfile()) {
            removeDeviceOwnerIfPresent(device);
        }

        createUserOutput = device.executeShellCommand(command);

        if (!checkTrueOrSkipOnDevice(
                !createUserOutput.contains("Cannot add a user of disabled type"),
                "Device does not support " + mProfileUserType,
                testInfo)) {
            return USERTYPE_NOT_SUPPORTED;
        }

        try {
            return Integer.parseInt(createUserOutput.split(" id ")[1].trim());
        } catch (RuntimeException e) {
            throw commandError(
                    "Error creating profile", command, createUserOutput, e, SHELL_COMMAND_ERROR);
        }
    }

    private void removeDeviceOwnerIfPresent(ITestDevice device) throws DeviceNotAvailableException {
        mDeviceOwnerToSet = getDeviceOwner(device);

        if (mDeviceOwnerToSet != null) {
            LogUtil.CLog.d(
                    mTradefedUserType
                            + " cannot be created after device owner is set. Attempting to"
                            + " remove device owner");
            removeDeviceOwner(device, mDeviceOwnerToSet);
        }
    }

    private DeviceOwner getDeviceOwner(ITestDevice device) throws DeviceNotAvailableException {
        String command = "dumpsys device_policy";
        String dumpsysOutput = device.executeShellCommand(command);

        if (dumpsysOutput == null || !dumpsysOutput.contains("Device Owner:")) {
            return null;
        }

        try {
            String deviceOwnerOnwards = dumpsysOutput.split("Device Owner:", 2)[1];
            String componentName =
                    deviceOwnerOnwards.split("ComponentInfo\\{", 2)[1].split("}", 2)[0];
            int userId =
                    Integer.parseInt(
                            deviceOwnerOnwards.split("User ID: ", 2)[1].split("\n", 2)[0].trim());
            return new DeviceOwner(componentName, userId);
        } catch (RuntimeException e) {
            throw commandError(
                    "Error reading device owner information",
                    command,
                    dumpsysOutput,
                    e,
                    SHELL_COMMAND_ERROR);
        }
    }

    private void removeDeviceOwner(ITestDevice device, DeviceOwner deviceOwner)
            throws DeviceNotAvailableException {
        String command =
                "dpm remove-active-admin --user "
                        + deviceOwner.userId
                        + " "
                        + deviceOwner.componentName;

        String commandOutput = device.executeShellCommand(command);
        if (!commandOutput.startsWith("Success")) {
            throw commandError(
                    "Error removing device owner", command, commandOutput, SHELL_COMMAND_ERROR);
        }
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        String value = testInfo.properties().remove(SKIP_TESTS_REASON_KEY);
        if (value != null) {
            // Skip teardown if a skip test reason was set.
            return;
        }
        testInfo.properties().remove(RUN_TESTS_AS_USER_KEY);
        if (profileIdToDelete != -1) {
            testInfo.getDevice().removeUser(profileIdToDelete);
        }

        if (mDeviceOwnerToSet != null) {
            testInfo.getDevice()
                    .setDeviceOwner(mDeviceOwnerToSet.componentName, mDeviceOwnerToSet.userId);
        }
    }

    /**
     * Disable teardown and set the {@link #SKIP_TESTS_REASON_KEY} if {@code value} isn't true.
     *
     * <p>This will return {@code value} and, if it is not true, setup should be skipped.
     */
    private boolean checkTrueOrSkipOnDevice(boolean value, String reason, TestInformation testInfo)
            throws TargetSetupError {
        if (!value) {
            testInfo.properties().put(SKIP_TESTS_REASON_KEY, reason.replace(" ", "\\ "));
        }
        return value;
    }

    private boolean requireFeatures(TestInformation testInfo, String... features)
            throws TargetSetupError, DeviceNotAvailableException {
        for (String feature : features) {
            if (!checkTrueOrSkipOnDevice(
                    testInfo.getDevice().hasFeature(feature),
                    "Device does not have feature " + feature,
                    testInfo)) {
                return false;
            }
        }

        return true;
    }

    /** Checks whether it is possible to create the desired number of users. */
    protected boolean canCreateAdditionalUsers(ITestDevice device, int numberOfUsers)
            throws DeviceNotAvailableException {
        return device.listUsers().size() + numberOfUsers <= device.getMaxNumberOfUsersSupported();
    }

    private static RuntimeException commandError(String error, String command, String commandOutput, ErrorIdentifier errorIdentifier) {
        return commandError(error, command, commandOutput, /* exception= */ null, errorIdentifier);
    }

    private static RuntimeException commandError(
            String error, String command, String commandOutput, Exception exception, ErrorIdentifier errorIdentifier) {
        return new HarnessRuntimeException(
                error + ". Command was '" + command + "', output was '" + commandOutput + "'",
                exception,
                errorIdentifier);
    }

    @VisibleForTesting
    void setProfileUserType(String userType) {
        mProfileUserType = userType;
    }

    @VisibleForTesting
    void setTradefedUserType(UserType tradefedUserType) {
        mTradefedUserType = tradefedUserType;
    }
}
