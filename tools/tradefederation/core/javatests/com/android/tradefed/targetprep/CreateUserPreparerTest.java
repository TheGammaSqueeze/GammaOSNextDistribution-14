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
package com.android.tradefed.targetprep;

import static com.google.common.truth.Truth.assertThat;

import static com.android.tradefed.targetprep.UserHelper.USER_SETUP_COMPLETE;

import static org.junit.Assert.assertThrows;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ITestDeviceMockHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

/** Unit tests for {@link CreateUserPreparer}. */
@RunWith(MockitoJUnitRunner.class)
public final class CreateUserPreparerTest {

    @Mock private ITestDevice mMockDevice;

    private OptionSetter mSetter;

    private ITestDeviceMockHelper mTestDeviceMockHelper;
    private CreateUserPreparer mPreparer;
    private TestInformation mTestInfo;

    @Before
    public void setFixtures() throws Exception {
        mTestDeviceMockHelper = new ITestDeviceMockHelper(mMockDevice);
        mPreparer = new CreateUserPreparer();
        mSetter = new OptionSetter(mPreparer);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @Test
    public void testSetUp_tearDown() throws Exception {
        mockGetCurrentUser(10);
        mockCreateUser(5);
        mockSwitchUser(5);
        mockStartUser(5);
        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserSettings(5, USER_SETUP_COMPLETE, "1");
        verifyUserSwitched(5);

        mockSwitchUser(10);
        mPreparer.tearDown(mTestInfo, /* e= */ null);
        verifyUserRemoved(5);
        verifyUserSwitched(10);
    }

    @Test
    public void testSetUp_tearDown_reuseTestUser() throws Exception {
        setParam("reuse-test-user", "true");

        Map<Integer, UserInfo> existingUsers = Map.of(
                0, new UserInfo(
                    /* id= */ 0,
                    /* userName= */ null,
                    /* flags= */ 0x00000013,
                    /* isRunning= */ true),
                13, new UserInfo(
                    /* id= */ 13,
                    "tf_created_user",
                    /* flags= */ 0,
                    /* isRunning= */ false));

        mockGetUserInfos(existingUsers);
        mockGetCurrentUser(0);
        mockSwitchUser(13);
        mockStartUser(13);
        mockSwitchUser(0);

        mPreparer.setUp(mTestInfo);
        // We should reuse the existing, not create a new user.
        verifyNoUserCreated();
        verifyUserSwitched(13);

        mPreparer.tearDown(mTestInfo, /* e= */ null);
        // We should keep the user for the next module to reuse.
        verifyUserNotRemoved(13);
        verifyUserSwitched(0);
    }

    @Test
    public void testSetUp_tearDown_reuseTestUser_noExistingTestUser() throws Exception {
        setParam("reuse-test-user", "true");

        Map<Integer, UserInfo> existingUsers = Map.of(
                0, new UserInfo(
                    /* id= */ 0,
                    /* userName= */ null,
                    /* flags= */ 0x00000013,
                    /* isRunning= */ true));

        mockGetUserInfos(existingUsers);
        mockGetCurrentUser(0);
        mockCreateUser(12);
        mockSwitchUser(12);
        mockStartUser(12);
        mockSwitchUser(0);

        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserSwitched(12);

        mPreparer.tearDown(mTestInfo, /* e= */ null);
        // Newly created user is kept to reuse it in the next run.
        verifyUserNotRemoved(12);
        verifyUserSwitched(0);
    }

    @Test
    public void testSetUp_tearDown_noCurrent() throws Exception {
        mockGetCurrentUser(NativeDevice.INVALID_USER_ID);

        assertThrows(TargetSetupError.class, () -> mPreparer.setUp(mTestInfo));

        mPreparer.tearDown(mTestInfo, null);
        verifyNoUserRemoved();
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_maxUsersReached() throws Exception {
        Map<Integer, UserInfo> existingUsers = Map.of(
                0, new UserInfo(
                    /* id= */ 0,
                    /* userName= */ null,
                    /* flags= */ 0x00000013,
                    /* isRunning= */ true),
                11, new UserInfo(
                    /* id= */ 11,
                    "tf_created_user",
                    /* flags= */ 0,
                    /* isRunning= */ true),
                13, new UserInfo(
                    /* id= */ 13,
                    "tf_created_user",
                    /* flags= */ 0,
                    /* isRunning= */ false));

        mockGetMaxNumberOfUsersSupported(3);
        mockGetUserInfos(existingUsers);
        Exception cause = mockCreateUserFailure("D'OH!");

        TargetSetupError e = assertThrows(TargetSetupError.class, () -> mPreparer.setUp(mTestInfo));
        assertThat(e).hasCauseThat().isSameInstanceAs(cause);

        // verify that it removed the existing tradefed users.
        verifyUserRemoved(11);
        verifyUserRemoved(13);
    }

    @Test
    public void testSetUp_createUserfailed() throws Exception {
        Exception cause = mockCreateUserFailure("D'OH!");

        TargetSetupError e = assertThrows(TargetSetupError.class, () -> mPreparer.setUp(mTestInfo));

        assertThat(e).hasCauseThat().isSameInstanceAs(cause);
    }

    @Test
    public void testTearDown_only() throws Exception {
        mPreparer.tearDown(mTestInfo, /* e= */ null);

        verifyNoUserRemoved();
    }

    private void setParam(String key, String value) throws ConfigurationException {
        CLog.i("Setting param: '%s'='%s'", key, value);
        mSetter.setOptionValue(key, value);
    }

    private void mockGetCurrentUser(int userId) throws Exception {
        mTestDeviceMockHelper.mockGetCurrentUser(userId);
    }

    private void mockGetUserInfos(Map<Integer, UserInfo> existingUsers) throws Exception {
        mTestDeviceMockHelper.mockGetUserInfos(existingUsers);
    }

    private void mockGetMaxNumberOfUsersSupported(int max) throws Exception {
        mTestDeviceMockHelper.mockGetMaxNumberOfUsersSupported(max);
    }

    private void mockSwitchUser(int userId) throws Exception {
        mTestDeviceMockHelper.mockSwitchUser(userId);
    }

    private void mockStartUser(int userId) throws Exception {
        mTestDeviceMockHelper.mockStartUser(userId);
    }

    private void mockCreateUser(int userId) throws Exception {
        mTestDeviceMockHelper.mockCreateUser(userId);
    }

    private IllegalStateException mockCreateUserFailure(String message) throws Exception {
        return mTestDeviceMockHelper.mockCreateUserFailure(message);
    }

    private void verifyNoUserCreated() throws Exception {
        mTestDeviceMockHelper.verifyNoUserCreated();
    }

    private void verifyUserCreated() throws Exception {
        mTestDeviceMockHelper.verifyUserCreated();
    }

    private void verifyNoUserSwitched() throws Exception {
        mTestDeviceMockHelper.verifyNoUserSwitched();
    }

    private void verifyUserSwitched(int userId) throws Exception {
        mTestDeviceMockHelper.verifyUserSwitched(userId);
    }

    private void verifyNoUserRemoved() throws Exception {
        mTestDeviceMockHelper.verifyNoUserRemoved();
    }

    private void verifyUserRemoved(int userId) throws Exception {
        mTestDeviceMockHelper.verifyUserRemoved(userId);
    }

    private void verifyUserNotRemoved(int userId) throws Exception {
        mTestDeviceMockHelper.verifyUserNotRemoved(userId);
    }

    private void verifyUserSettings(int userId, String key, String value) throws Exception {
        mTestDeviceMockHelper.verifyUserSettings(userId, "secure", key, value);
    }
}
