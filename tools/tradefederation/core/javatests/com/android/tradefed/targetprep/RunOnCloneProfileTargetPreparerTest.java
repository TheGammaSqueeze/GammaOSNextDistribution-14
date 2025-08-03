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

import static com.android.tradefed.device.UserInfo.UserType.CLONE_PROFILE;
import static com.android.tradefed.targetprep.RunOnWorkProfileTargetPreparer.RUN_TESTS_AS_USER_KEY;
import static com.android.tradefed.targetprep.RunOnWorkProfileTargetPreparer.SKIP_TESTS_REASON_KEY;
import static com.android.tradefed.targetprep.RunOnWorkProfileTargetPreparer.TEST_PACKAGE_NAME_OPTION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public final class RunOnCloneProfileTargetPreparerTest {

    private static final String CREATED_USER_10_MESSAGE = "Created user id 10";
    public static final String CREATE_CLONE_PROFILE_COMMAND =
            "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE"
                    + " --for-testing user";
    public static final String USERTYPE_PROFILE_CLONE = "android.os.usertype.profile.CLONE";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TestInformation mTestInfo;

    private OptionSetter mOptionSetter;

    private ProfileTargetPreparer mPreparer;

    public static final String CLONE_USERTYPE_DISABLED_MESSAGE =
            "RUNNER ERROR:"
                + " com.android.tradefed.error.HarnessRuntimeException[SHELL_COMMAND_ERROR|520100|DEPENDENCY_ISSUE]:"
                + " Error creating profile. Command was 'pm create-user --profileOf 10 --user-type"
                + " android.os.usertype.profile.CLONE user', output was 'Error:"
                + " android.os.ServiceSpecificException: Cannot add a user of disabled type"
                + " android.os.usertype.profile.CLONE.";

    @Before
    public void setUp() throws Exception {
        mPreparer = new RunOnCloneProfileTargetPreparer();
        mOptionSetter = new OptionSetter(mPreparer);
        mPreparer.setProfileUserType(USERTYPE_PROFILE_CLONE);
        mPreparer.setTradefedUserType(CLONE_PROFILE);

        ArrayList<Integer> userIds = new ArrayList<>();
        userIds.add(0);

        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(2);
        when(mTestInfo.getDevice().listUsers()).thenReturn(userIds);
        when(mTestInfo.getDevice().getApiLevel()).thenReturn(34);
    }

    @Test
    public void setUp_doesNotSupportCloneUser_doesNotChangeTestUser() throws Exception {
        when(mTestInfo.getDevice().getApiLevel()).thenReturn(30);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties(), never()).put(eq(RUN_TESTS_AS_USER_KEY), any());
    }

    @Test
    public void setUp_doesNotSupportCloneUser_setsArgumentToSkipTests() throws Exception {
        when(mTestInfo.getDevice().getApiLevel()).thenReturn(32);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(eq(SKIP_TESTS_REASON_KEY), any());
    }

    @Test
    public void setUp_cloneUserType_createsCloneProfileAndStartsUser() throws Exception {
        String expectedCreateUserCommand = CREATE_CLONE_PROFILE_COMMAND;
        when(mTestInfo.getDevice().executeShellCommand(expectedCreateUserCommand))
                .thenReturn(CREATED_USER_10_MESSAGE);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).executeShellCommand(expectedCreateUserCommand);
        verify(mTestInfo.getDevice()).startUser(10, /* waitFlag= */ true);
    }

    @Test
    public void setUp_profileAlreadyExists_doesNotCreateProfile() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                10,
                new UserInfo(
                        10,
                        "clone",
                        /* flag= */ UserInfo.FLAG_PROFILE,
                        /* isRunning= */ true,
                        "profile.CLONE"));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).executeShellCommand(any());
    }

    @Test
    public void setUp_nonZeroCurrentUser_createsProfileForCorrectUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(1);
        String expectedCreateUserCommand =
                "pm create-user --profileOf 1 "
                        + "--user-type android.os.usertype.profile.CLONE --for-testing user";
        when(mTestInfo.getDevice().executeShellCommand(expectedCreateUserCommand))
                .thenReturn(CREATED_USER_10_MESSAGE);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).executeShellCommand(expectedCreateUserCommand);
    }

    @Test
    public void setUp_profileAlreadyExists_runsTestAsExistingUser() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                11,
                new UserInfo(
                        11,
                        "clone",
                        /* flag= */ UserInfo.FLAG_PROFILE,
                        /* isRunning= */ true,
                        "profile.CLONE"));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_AS_USER_KEY, "11");
    }

    @Test
    public void setUp_setsRunTestsAsUser() throws Exception {
        String expectedCreateUserCommand = CREATE_CLONE_PROFILE_COMMAND;
        when(mTestInfo.getDevice().executeShellCommand(expectedCreateUserCommand))
                .thenReturn(CREATED_USER_10_MESSAGE);
        mOptionSetter.setOptionValue(TEST_PACKAGE_NAME_OPTION, "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_AS_USER_KEY, "10");
    }

    @Test
    public void setUp_installsPackagesInProfileUser() throws Exception {
        String expectedCreateUserCommand = CREATE_CLONE_PROFILE_COMMAND;
        when(mTestInfo.getDevice().executeShellCommand(expectedCreateUserCommand))
                .thenReturn(CREATED_USER_10_MESSAGE);
        mOptionSetter.setOptionValue(TEST_PACKAGE_NAME_OPTION, "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .executeShellCommand("pm install-existing --user 10 com.android.testpackage");
    }

    @Test
    public void tearDown_profileAlreadyExists_doesNotRemoveProfile() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                10,
                new UserInfo(
                        10,
                        "clone",
                        /* flag= */ UserInfo.FLAG_PROFILE,
                        /* isRunning= */ true,
                        "profile.CLONE"));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mPreparer.setUp(mTestInfo);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice(), never()).removeUser(10);
    }

    @Test
    public void setUp_doesNotDisableTearDown() throws Exception {
        String expectedCreateUserCommand = CREATE_CLONE_PROFILE_COMMAND;
        when(mTestInfo.getDevice().executeShellCommand(expectedCreateUserCommand))
                .thenReturn(CREATED_USER_10_MESSAGE);
        mOptionSetter.setOptionValue("disable-tear-down", "false");

        mPreparer.setUp(mTestInfo);

        assertThat(mPreparer.isTearDownDisabled()).isFalse();
    }

    @Test
    public void tearDown_removesProfileUser() throws Exception {
        String expectedCreateUserCommand = CREATE_CLONE_PROFILE_COMMAND;
        when(mTestInfo.getDevice().executeShellCommand(expectedCreateUserCommand))
                .thenReturn(CREATED_USER_10_MESSAGE);
        mPreparer.setUp(mTestInfo);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice()).removeUser(10);
    }

    @Test
    public void tearDown_clearsRunTestsAsUserProperty() throws Exception {
        when(mTestInfo.properties().get(RUN_TESTS_AS_USER_KEY)).thenReturn("10");

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.properties()).remove(RUN_TESTS_AS_USER_KEY);
    }

    @Test
    public void setUp_doesNotSupportAdditionalUsers_doesNotChangeTestUser() throws Exception {
        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(1);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties(), never()).put(eq(RUN_TESTS_AS_USER_KEY), any());
        verify(mTestInfo.properties()).put(eq(SKIP_TESTS_REASON_KEY), any());
    }

    @Test
    public void setUp_doesNotSupportAdditionalUsers_alreadyHasProfile_runsTestAsExistingUser()
            throws Exception {
        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(1);
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                11,
                new UserInfo(
                        11,
                        "clone",
                        /* flag= */ UserInfo.FLAG_PROFILE,
                        /* isRunning= */ true,
                        "profile.CLONE"));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue(TEST_PACKAGE_NAME_OPTION, "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .executeShellCommand("pm install-existing --user 11 com.android.testpackage");
    }

    @Test
    public void setUp_cloneUserTypeNotSupportedOnDevice_setsArgumentToSkipTests()
            throws DeviceNotAvailableException, TargetSetupError {
        String expectedCreateUserCommand = CREATE_CLONE_PROFILE_COMMAND;
        when(mTestInfo.getDevice().executeShellCommand(expectedCreateUserCommand))
                .thenReturn(CLONE_USERTYPE_DISABLED_MESSAGE);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(eq(SKIP_TESTS_REASON_KEY), any());
    }

    @Test
    public void setUp_doesNotSupportAdditionalUsers_alreadyHasCloneProfile_doesNotSkipTests()
            throws Exception {
        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(1);
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                11,
                new UserInfo(
                        11,
                        "clone",
                        /* flag= */ UserInfo.FLAG_PROFILE,
                        /* isRunning= */ true,
                        "profile.CLONE"));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue(TEST_PACKAGE_NAME_OPTION, "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties(), never()).put(eq(SKIP_TESTS_REASON_KEY), any());
    }
}
