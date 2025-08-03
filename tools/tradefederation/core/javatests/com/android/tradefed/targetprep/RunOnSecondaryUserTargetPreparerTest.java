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

package com.android.tradefed.targetprep;

import static com.android.tradefed.device.UserInfo.FLAG_FOR_TESTING;
import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;
import static com.android.tradefed.targetprep.RunOnSecondaryUserTargetPreparer.TEST_PACKAGE_NAME_OPTION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class RunOnSecondaryUserTargetPreparerTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TestInformation mTestInfo;

    private RunOnSecondaryUserTargetPreparer mPreparer;
    private OptionSetter mOptionSetter;

    @Before
    public void setUp() throws Exception {
        mPreparer = new RunOnSecondaryUserTargetPreparer();
        mOptionSetter = new OptionSetter(mPreparer);

        ArrayList<Integer> userIds = new ArrayList<>();
        userIds.add(0);

        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(2);
        when(mTestInfo.getDevice().listUsers()).thenReturn(userIds);
        when(mTestInfo.getDevice().getApiLevel()).thenReturn(30);
        when(mTestInfo.getDevice().isHeadlessSystemUserMode()).thenReturn(false);

        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(0, new UserInfo(0, "system", /* flag= */ 0, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
    }

    @Test
    public void setUp_createsStartsAndSwitchesToSecondaryUser() throws Exception {
        when(mTestInfo.getDevice().createUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(2);
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .createUser(
                        "secondary",
                        /* guest= */ false,
                        /* ephemeral= */ false,
                        /* forTesting= */ true);
        verify(mTestInfo.getDevice()).startUser(2, /* waitFlag= */ true);
        verify(mTestInfo.getDevice()).switchUser(2);
    }

    @Test
    public void setUp_oldVersion_createsStartsAndSwitchesToSecondaryUserWithoutWait()
            throws Exception {
        when(mTestInfo.getDevice().createUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(2);
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);
        when(mTestInfo.getDevice().getApiLevel()).thenReturn(28);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .createUser(
                        "secondary",
                        /* guest= */ false,
                        /* ephemeral= */ false,
                        /* forTesting= */ true);
        verify(mTestInfo.getDevice()).startUser(2, /* waitFlag= */ false);
        verify(mTestInfo.getDevice()).switchUser(2);
    }

    @Test
    public void setUp_secondaryUserAlreadyExists_doesNotCreateSecondaryUser() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                2,
                new UserInfo(2, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never())
                .createUser(any(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    public void setUp_secondaryUserAlreadyExists_startsAndSwitchesToSecondaryUser()
            throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                2,
                new UserInfo(2, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).startUser(2, /* waitFlag= */ true);
        verify(mTestInfo.getDevice()).switchUser(2);
    }

    @Test
    public void tearDown_switchesBackToInitialUser() throws Exception {
        when(mTestInfo.getDevice().createUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(2);
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);
        mPreparer.setUp(mTestInfo);
        Mockito.reset(mTestInfo);
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(2);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice()).switchUser(0);
    }

    @Test
    public void tearDown_secondaryUserAlreadyExists_switchesBackToInitialUser() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                2,
                new UserInfo(2, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);
        mPreparer.setUp(mTestInfo);
        Mockito.reset(mTestInfo);
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(2);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice()).switchUser(0);
    }

    @Test
    public void setUp_secondaryUserAlreadyExists_runsTestAsExistingUser() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                3,
                new UserInfo(3, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_AS_USER_KEY, "3");
    }

    @Test
    public void setUp_setsRunTestsAsUser() throws Exception {
        when(mTestInfo.getDevice().createUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(2);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_AS_USER_KEY, "2");
    }

    @Test
    public void setUp_secondaryUserAlreadyExists_installsPackagesInExistingUser() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                3,
                new UserInfo(3, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue(
                RunOnWorkProfileTargetPreparer.TEST_PACKAGE_NAME_OPTION, "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .executeShellCommand("pm install-existing --user 3 com.android.testpackage");
    }

    @Test
    public void setUp_installsPackagesInSecondaryUser() throws Exception {
        when(mTestInfo.getDevice().createUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(2);
        mOptionSetter.setOptionValue(
                RunOnSecondaryUserTargetPreparer.TEST_PACKAGE_NAME_OPTION,
                "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .executeShellCommand("pm install-existing --user 2 com.android.testpackage");
    }

    @Test
    public void setUp_secondaryUserAlreadyExists_doesNotRemoveSecondaryUser() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                3,
                new UserInfo(3, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue("disable-tear-down", "false");
        mPreparer.setUp(mTestInfo);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice(), never()).removeUser(3);
    }

    @Test
    public void setUp_secondaryUserIsNonForTesting_createsNewSecondaryUser() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(3, new UserInfo(3, "secondary", /* flag= */ 0, /* isRunning= */ true));
        Map<Integer, UserInfo> emptyUserInfos = new HashMap<>(); // Used after the user is removed
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos).thenReturn(emptyUserInfos);
        mOptionSetter.setOptionValue("disable-tear-down", "false");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .createUser(
                        "secondary",
                        /* guest= */ false,
                        /* ephemeral= */ false,
                        /* forTesting= */ true);
    }

    @Test
    public void setUp_secondaryUserIsNonForTesting_removedNonForTestingSecondaryUser()
            throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(3, new UserInfo(3, "secondary", /* flag= */ 0, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue("disable-tear-down", "false");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).removeUser(3);
    }

    @Test
    public void setUp_existingUserIsSystemUser_doesNotRemove() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(0, new UserInfo(3, "system", /* flag= */ 0, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue("disable-tear-down", "false");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).removeUser(0);
    }

    @Test
    public void
            setUp_headlessDevice_multipleNonForTestingSecondaryUsers_doesNotRemoveFirstSecondaryUser()
                    throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                3,
                new UserInfo(
                        3, "secondary", /* flag= */ 0, /* isRunning= */ true));
        userInfos.put(4, new UserInfo(4, "secondary", /* flag= */ 0, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        when(mTestInfo.getDevice().isHeadlessSystemUserMode()).thenReturn(true);
        mOptionSetter.setOptionValue("disable-tear-down", "false");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).removeUser(3);
        verify(mTestInfo.getDevice()).removeUser(4);
    }

    @Test
    public void setUp_existingUserIsCommunalProfile_doesNotRemove() throws Exception {
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                13,
                new UserInfo(
                        13,
                        "communal",
                        /* flag= */ 0,
                        /* isRunning= */ false,
                        UserInfo.COMMUNAL_PROFILE_TYPE));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue("disable-tear-down", "false");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).removeUser(13);
    }

    @Test
    public void setUp_doesNotDisableTearDown() throws Exception {
        when(mTestInfo.getDevice().createUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(2);
        mOptionSetter.setOptionValue("disable-tear-down", "false");

        mPreparer.setUp(mTestInfo);

        assertThat(mPreparer.isTearDownDisabled()).isFalse();
    }

    @Test
    public void tearDown_removesSecondaryUser() throws Exception {
        when(mTestInfo.getDevice().createUser(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(2);
        mPreparer.setUp(mTestInfo);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice()).removeUser(2);
    }

    @Test
    public void tearDown_clearsRunTestsAsUserProperty() throws Exception {
        when(mTestInfo.properties().get(RUN_TESTS_AS_USER_KEY)).thenReturn("2");

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.properties()).remove(RUN_TESTS_AS_USER_KEY);
    }

    @Test
    public void setUp_doesNotSupportAdditionalUsers_doesNotChangeTestUser() throws Exception {
        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(1);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties(), never()).put(eq(RUN_TESTS_AS_USER_KEY), any());
    }

    @Test
    public void setUp_doesNotSupportAdditionalUsers_setsArgumentToSkipTests() throws Exception {
        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(1);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties())
                .put(eq(RunOnSecondaryUserTargetPreparer.SKIP_TESTS_REASON_KEY), any());
    }

    @Test
    public void setUp_doesNotSupportAdditionalUsers_alreadyHasSecondaryUser_runsTestAsExistingUser()
            throws Exception {
        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(1);
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                3,
                new UserInfo(3, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue(TEST_PACKAGE_NAME_OPTION, "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice())
                .executeShellCommand("pm install-existing --user 3 com.android.testpackage");
    }

    @Test
    public void setUp_doesNotSupportAdditionalUsers_alreadyHasSecondaryUser_doesNotSkipTests()
            throws Exception {
        when(mTestInfo.getDevice().getMaxNumberOfUsersSupported()).thenReturn(1);
        Map<Integer, UserInfo> userInfos = new HashMap<>();
        userInfos.put(
                3,
                new UserInfo(3, "secondary", /* flag= */ FLAG_FOR_TESTING, /* isRunning= */ true));
        when(mTestInfo.getDevice().getUserInfos()).thenReturn(userInfos);
        mOptionSetter.setOptionValue(TEST_PACKAGE_NAME_OPTION, "com.android.testpackage");

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties(), never())
                .put(eq(RunOnSecondaryUserTargetPreparer.SKIP_TESTS_REASON_KEY), any());
    }
}
