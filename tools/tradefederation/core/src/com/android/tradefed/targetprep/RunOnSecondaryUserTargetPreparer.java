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

package com.android.tradefed.targetprep;

import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.TestInformation;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * An {@link ITargetPreparer} that creates a secondary user in setup, and marks that tests should be
 * run in that user.
 *
 * <p>In teardown, the secondary user is removed.
 *
 * <p>If a secondary user already exists, it will be used rather than creating a new one, and it
 * will not be removed in teardown.
 *
 * <p>If the device does not have capacity to create a new user when one is required, then the
 * instrumentation argument skip-tests-reason will be set, and the user will not be changed. Tests
 * running on the device can read this argument to respond to this state.
 */
@OptionClass(alias = "run-on-secondary-user")
public class RunOnSecondaryUserTargetPreparer extends BaseTargetPreparer {

    @VisibleForTesting static final String TEST_PACKAGE_NAME_OPTION = "test-package-name";

    @VisibleForTesting static final String SKIP_TESTS_REASON_KEY = "skip-tests-reason";

    private int userIdToDelete = -1;
    private int originalUserId;

    @Option(
            name = TEST_PACKAGE_NAME_OPTION,
            description =
                    "the name of a package to be installed on the secondary user. "
                            + "This must already be installed on the device.",
            importance = Option.Importance.IF_UNSET)
    private List<String> mTestPackages = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        removeNonForTestingUsers(testInfo.getDevice());

        // This must be a for-testing user because we removed the not-for-testing ones
        int secondaryUserId = getSecondaryUserId(testInfo.getDevice());

        if (secondaryUserId == -1) {
            if (!assumeTrue(
                    canCreateAdditionalUsers(testInfo.getDevice(), 1),
                    "Device cannot support additional users",
                    testInfo)) {
                return;
            }

            secondaryUserId = createSecondaryUser(testInfo.getDevice());
            userIdToDelete = secondaryUserId;
        }

        // The wait flag is only supported on Android 29+
        testInfo.getDevice()
                .startUser(
                        secondaryUserId, /* waitFlag= */ testInfo.getDevice().getApiLevel() >= 29);

        originalUserId = testInfo.getDevice().getCurrentUser();

        if (originalUserId != secondaryUserId) {
            testInfo.getDevice().switchUser(secondaryUserId);
        }

        for (String pkg : mTestPackages) {
            testInfo.getDevice()
                    .executeShellCommand(
                            "pm install-existing --user " + secondaryUserId + " " + pkg);
        }

        testInfo.properties().put(RUN_TESTS_AS_USER_KEY, Integer.toString(secondaryUserId));
    }

    /**
     * Get the id of a secondary user currently on the device. -1 if there is
     * none.
     */
    private static int getSecondaryUserId(ITestDevice device)
            throws DeviceNotAvailableException {
        for (Map.Entry<Integer, UserInfo> userInfo : device.getUserInfos().entrySet()) {
            if (userInfo.getValue().isSecondary()) {
                return userInfo.getKey();
            }
        }
        return -1;
    }

    /** Creates a secondary user and returns the new user ID. */
    private static int createSecondaryUser(ITestDevice device) throws DeviceNotAvailableException {
        return device.createUser(
                "secondary", /* guest= */ false, /* ephemeral= */ false, /* forTesting= */ true);
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        String value = testInfo.properties().remove(SKIP_TESTS_REASON_KEY);
        if (value != null) {
            // Skip teardown if a skip test reason was set.
            return;
        }

        testInfo.properties().remove(RUN_TESTS_AS_USER_KEY);

        ITestDevice device = testInfo.getDevice();
        int currentUser = device.getCurrentUser();

        if (currentUser != originalUserId) {
            device.switchUser(originalUserId);
        }
        if (userIdToDelete != -1) {
            device.removeUser(userIdToDelete);
        }
    }

    /**
     * Disable teardown and set the {@link #SKIP_TESTS_REASON_KEY} if {@code value} isn't true.
     *
     * <p>This will return {@code value} and, if it is not true, setup should be skipped.
     */
    private boolean assumeTrue(boolean value, String reason, TestInformation testInfo) {
        if (!value) {
            testInfo.properties().put(SKIP_TESTS_REASON_KEY, reason.replace(" ", "\\ "));
        }

        return value;
    }

    /**
     * Remove all non for-testing users.
     *
     * <p>For a headless device, it would remove every non for-testing user except the first
     * secondary user and the system user.
     *
     * <p>For a non-headless device, it would remove every non for-testing user except the system
     * user.
     *
     * <p>A communal profile is never removed.
     */
    private static void removeNonForTestingUsers(ITestDevice device)
            throws DeviceNotAvailableException {
        Map<Integer, UserInfo> userInfoMap = device.getUserInfos();

        List<UserInfo> userInfos = new ArrayList<>(userInfoMap.values());
        Collections.sort(userInfos, Comparator.comparing(UserInfo::userId));

        int maxSkippedUsers = device.isHeadlessSystemUserMode() ? 1 : 0;
        int skippedUsers = 0;

        for (UserInfo userInfo : userInfos) {
            if (isForTesting(userInfo)) {
                continue;
            }

            if (skippedUsers < maxSkippedUsers) {
                skippedUsers++;
                continue;
            }

            device.removeUser(userInfo.userId());
        }
    }

    private static boolean isForTesting(UserInfo userInfo) {
        return userInfo.isSystem()
                || userInfo.isFlagForTesting()
                // Communal profile doesn't align with DPM implementation - it's only acceptable
                // here for now because no test with communal profile also uses enterprise
                || userInfo.isCommunalProfile();
    }

    /** Checks whether it is possible to create the desired number of users. */
    protected boolean canCreateAdditionalUsers(ITestDevice device, int numberOfUsers)
            throws DeviceNotAvailableException {
        return device.listUsers().size() + numberOfUsers <= device.getMaxNumberOfUsersSupported();
    }
}
