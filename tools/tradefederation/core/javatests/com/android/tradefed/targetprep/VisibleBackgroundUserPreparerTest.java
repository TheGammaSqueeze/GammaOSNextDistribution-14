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
package com.android.tradefed.targetprep;

import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;
import static com.android.tradefed.targetprep.UserHelper.USER_SETUP_COMPLETE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link VisibleBackgroundUserPreparer} */
@RunWith(MockitoJUnitRunner.class)
public final class VisibleBackgroundUserPreparerTest {

    @Mock private ITestDevice mMockDevice;

    private OptionSetter mSetter;

    private ITestDeviceMockHelper mTestDeviceMockHelper;
    private VisibleBackgroundUserPreparer mPreparer;
    private TestInformation mTestInfo;

    @Before
    public void setFixtures() throws Exception {
        mTestDeviceMockHelper = new ITestDeviceMockHelper(mMockDevice);
        mPreparer = new VisibleBackgroundUserPreparer();
        mSetter = new OptionSetter(mPreparer);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();

        mockIsVisibleBackgroundUsersSupported(true);
    }

    @Test
    public void testSetUp_featureNotSupported() throws Exception {
        mockIsVisibleBackgroundUsersSupported(false);

        TargetSetupError e = assertThrows(TargetSetupError.class, () -> mPreparer.setUp(mTestInfo));
        assertThat(e).hasMessageThat().contains("not supported");

        verifyNoUserCreated();
        verifyNoUserRemoved();
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_tearDown_noDisplayAvailable() throws Exception {
        mockListDisplayIdsForStartingVisibleBackgroundUsers(Collections.emptySet());
        mockCreateUser(42);

        TargetSetupError e = assertThrows(TargetSetupError.class, () -> mPreparer.setUp(mTestInfo));
        assertThat(e).hasMessageThat().containsMatch("No display.*available.* .*42.*");

        mPreparer.tearDown(mTestInfo, /* e= */ null);
        verifyUserRemoved(42);
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_tearDown() throws Exception {
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(108));
        mockCreateUser(42);
        mockStartUserVisibleOnBackground(42, 108);

        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserStartedVisibleOnBackground(42, 108);
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");
        verifyUserSettings(42, USER_SETUP_COMPLETE, "1");
        verifyNoUserSwitched();

        mPreparer.tearDown(mTestInfo, /* e= */ null);
        verifyUserStopped(42);
        verifyUserRemoved(42);
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_specificDisplayByOption() throws Exception {
        setParam("display-id", "108");
        mockCreateUser(42);
        mockStartUserVisibleOnBackground(42, 108);

        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserStartedVisibleOnBackground(42, 108);
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_specificDisplayBySetter() throws Exception {
        setParam("display-id", "666");
        mPreparer.setDisplayId(108);
        mockCreateUser(42);
        mockStartUserVisibleOnBackground(42, 108);

        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserStartedVisibleOnBackground(42, 108);
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");
        verifyNoUserSwitched();
    }

    @Test
    public void
            testSetUp_useDefaultDisplayWhenVisibleBackgroundUsersOnDefaultDisplayIsNotSupported()
                    throws Exception {
        mockIsVisibleBackgroundUsersOnDefaultDisplaySupported(false);
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(0, 108));
        mockCreateUser(42);
        mockStartUserVisibleOnBackground(42, 0);

        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserStartedVisibleOnBackground(42, 0);
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");
        verifyNoUserSwitched();
    }

    @Test
    public void
            testSetUp_ignoreDefaultDisplayWhenVisibleBackgroundUsersOnDefaultDisplayIsSupported()
                    throws Exception {
        mockIsVisibleBackgroundUsersOnDefaultDisplaySupported(true);
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(0, 108));
        mockCreateUser(42);
        mockStartUserVisibleOnBackground(42, 108);

        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserStartedVisibleOnBackground(42, 108);
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_onlyDefaultDisplayWhenVisibleBackgroundUsersOnDefaultDisplayIsSupported()
            throws Exception {
        mockIsVisibleBackgroundUsersOnDefaultDisplaySupported(true);
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(0));
        mockCreateUser(42);
        mockStartUserVisibleOnBackground(42, 108);

        assertThrows(TargetSetupError.class, () -> mPreparer.setUp(mTestInfo));

        verifyNoUserStartedVisibleOnBackground();
    }

    @Test
    public void testSetDisplay_invalidId() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> mPreparer.setDisplayId(VisibleBackgroundUserPreparer.INVALID_DISPLAY));
    }

    @Test
    public void testSetUp_tearDown_reuseTestUser_invisible() throws Exception {
        setParam("reuse-test-user", "true");
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(108));
        Map<Integer, UserInfo> existingUsers =
                Map.of(
                        0,
                                new UserInfo(
                                        /* id= */ 0,
                                        /* userName= */ null,
                                        /* flags= */ 0x00000013,
                                        /* isRunning= */ true),
                        42,
                                new UserInfo(
                                        /* id= */ 42,
                                        "tf_created_user",
                                        /* flags= */ 0,
                                        /* isRunning= */ false));
        mockGetUserInfos(existingUsers);
        mockStartUserVisibleOnBackground(42, 108);

        mPreparer.setUp(mTestInfo);
        // We should reuse the existing, not create a new user.
        verifyNoUserCreated();
        verifyUserStartedVisibleOnBackground(42, 108);
        verifyNoUserSwitched();
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");

        mPreparer.tearDown(mTestInfo, /* e= */ null);
        // We should keep the user for the next module to reuse.
        verifyUserStopped(42);
        verifyNoUserRemoved();
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_tearDown_reuseTestUser_alreadyVisible() throws Exception {
        setParam("reuse-test-user", "true");
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(108));
        Map<Integer, UserInfo> existingUsers =
                Map.of(
                        0,
                                new UserInfo(
                                        /* id= */ 0,
                                        /* userName= */ null,
                                        /* flags= */ 0x00000013,
                                        /* isRunning= */ true),
                        42,
                                new UserInfo(
                                        /* id= */ 42,
                                        "tf_created_user",
                                        /* flags= */ 0,
                                        /* isRunning= */ false));
        mockGetUserInfos(existingUsers);
        mockIsUserVisibleOnDisplay(42, 108);

        mPreparer.setUp(mTestInfo);
        // We should reuse the existing, not create a new user.
        verifyNoUserCreated();
        verifyNoUserStartedVisibleOnBackground();
        verifyNoUserStarted();
        verifyNoUserSwitched();
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");

        mPreparer.tearDown(mTestInfo, /* e= */ null);
        // We should keep the user for the next module to reuse.
        verifyNoUserRemoved();
        verifyNoUserSwitched();
        verifyNoUserStopped();
    }

    @Test
    public void testSetUp_tearDown_reuseTestUser_noExistingTestUser() throws Exception {
        setParam("reuse-test-user", "true");
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(108));
        Map<Integer, UserInfo> existingUsers =
                Map.of(
                        0,
                        new UserInfo(
                                /* id= */ 0,
                                /* userName= */ null,
                                /* flags= */ 0x00000013,
                                /* isRunning= */ true));
        mockGetUserInfos(existingUsers);
        mockCreateUser(42);
        mockStartUserVisibleOnBackground(42, 108);

        mPreparer.setUp(mTestInfo);
        verifyUserCreated();
        verifyUserStartedVisibleOnBackground(42, 108);
        verifyTestInfoProperty(RUN_TESTS_AS_USER_KEY, "42");
        verifyNoUserStarted();
        verifyNoUserSwitched();

        mPreparer.tearDown(mTestInfo, /* e= */ null);
        // Newly created user is kept to reuse it in the next run.
        verifyUserNotRemoved(42);
        verifyUserStopped(42);
        verifyNoUserSwitched();
    }

    @Test
    public void testSetUp_maxUsersReached() throws Exception {
        Map<Integer, UserInfo> existingUsers =
                Map.of(
                        0,
                                new UserInfo(
                                        /* id= */ 0,
                                        /* userName= */ null,
                                        /* flags= */ 0x00000013,
                                        /* isRunning= */ true),
                        11,
                                new UserInfo(
                                        /* id= */ 11,
                                        "tf_created_user",
                                        /* flags= */ 0,
                                        /* isRunning= */ true),
                        13,
                                new UserInfo(
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
    public void testSetUp_starUserFailed() throws Exception {
        mockListDisplayIdsForStartingVisibleBackgroundUsers(orderedSetOf(108));
        mockCreateUser(12);
        mockStartUserVisibleOnBackground(42, 108, /*result= */ false);

        TargetSetupError e = assertThrows(TargetSetupError.class, () -> mPreparer.setUp(mTestInfo));

        assertThat(e).hasMessageThat().containsMatch(".ailed.*start.*12.*108");
    }

    @Test
    public void testTearDown_only() throws Exception {
        mPreparer.tearDown(mTestInfo, /* e= */ null);

        verifyNoUserRemoved();
        verifyNoUserStopped();
    }

    private <T> Set<T> orderedSetOf(@SuppressWarnings("unchecked") T... elements) {
        return new LinkedHashSet<>(Arrays.asList(elements));
    }

    private void setParam(String key, String value) throws ConfigurationException {
        CLog.i("Setting param: '%s'='%s'", key, value);
        mSetter.setOptionValue(key, value);
    }

    private void mockGetUserInfos(Map<Integer, UserInfo> existingUsers) throws Exception {
        mTestDeviceMockHelper.mockGetUserInfos(existingUsers);
    }

    private void mockGetMaxNumberOfUsersSupported(int max) throws Exception {
        mTestDeviceMockHelper.mockGetMaxNumberOfUsersSupported(max);
    }

    private void mockStartUserVisibleOnBackground(int userId, int displayId) throws Exception {
        mTestDeviceMockHelper.mockStartUserVisibleOnBackground(userId, displayId);
    }

    private void mockStartUserVisibleOnBackground(int userId, int displayId, boolean result)
            throws Exception {
        mTestDeviceMockHelper.mockStartUserVisibleOnBackground(userId, displayId, result);
    }

    private void mockCreateUser(int userId) throws Exception {
        mTestDeviceMockHelper.mockCreateUser(userId);
    }

    private IllegalStateException mockCreateUserFailure(String message) throws Exception {
        return mTestDeviceMockHelper.mockCreateUserFailure(message);
    }

    private void mockIsVisibleBackgroundUsersSupported(boolean supported) throws Exception {
        mTestDeviceMockHelper.mockIsVisibleBackgroundUsersSupported(supported);
    }

    private void mockIsVisibleBackgroundUsersOnDefaultDisplaySupported(boolean supported)
            throws Exception {
        mTestDeviceMockHelper.mockIsVisibleBackgroundUsersOnDefaultDisplaySupported(supported);
    }

    private void mockIsUserVisibleOnDisplay(int userId, int displayId) throws Exception {
        mTestDeviceMockHelper.mockIsUserVisibleOnDisplay(userId, displayId);
    }

    private void mockListDisplayIdsForStartingVisibleBackgroundUsers(Set<Integer> displays)
            throws Exception {
        mTestDeviceMockHelper.mockListDisplayIdsForStartingVisibleBackgroundUsers(displays);
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

    private void verifyNoUserStarted() throws Exception {
        mTestDeviceMockHelper.verifyNoUserStarted();
    }

    private void verifyNoUserStartedVisibleOnBackground() throws Exception {
        mTestDeviceMockHelper.verifyNoUserStartedVisibleOnBackground();
    }

    private void verifyUserStartedVisibleOnBackground(int userId, int displayId) throws Exception {
        mTestDeviceMockHelper.verifyUserStartedVisibleOnBackground(userId, displayId);
    }

    private void verifyUserStopped(int userId) throws Exception {
        mTestDeviceMockHelper.verifyUserStopped(userId);
    }

    private void verifyNoUserStopped() throws Exception {
        mTestDeviceMockHelper.verifyNoUserStopped();
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

    private void verifyTestInfoProperty(String key, String expectedValue) {
        String actualValue = mTestInfo.properties().get(key);
        assertWithMessage("value of property %s (all properties: %s)", key, mTestInfo.properties())
                .that(actualValue)
                .isEqualTo(expectedValue);
    }

    private void verifyUserSettings(int userId, String key, String value) throws Exception {
        mTestDeviceMockHelper.verifyUserSettings(userId, "secure", key, value);
    }
}
