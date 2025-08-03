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

package com.android.car.user;

import static android.car.test.mocks.AndroidMockitoHelper.mockContextCreateContextAsUser;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.car.builtin.os.UserManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
public final class UserHandleHelperUnitTest extends AbstractExtendedMockitoTestCase {

    @Mock
    private UserManager mUserManager;
    @Mock
    private Context mMockContext;
    @Mock
    private Context mMockUserContext;

    private UserHandleHelper mUserHandleHelper;

    private final UserHandle mContextUser = UserHandle.of(111);

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(UserManagerHelper.class);
    }

    @Before
    public void setup() {
        mUserHandleHelper = new UserHandleHelper(mMockContext, mUserManager);
        mockContextCreateContextAsUser(mMockContext, mMockUserContext,
                mContextUser.getIdentifier());
        when(mMockUserContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
    }

    @Test
    public void testGetExistingUserHandle() {
        List<UserHandle> users = Arrays.asList(UserHandle.of(100), UserHandle.of(101),
                UserHandle.of(102));
        mockGetUserHandles(false, users);
        UserHandle user100 = UserHandle.of(100);
        UserHandle user101 = UserHandle.of(101);
        UserHandle user102 = UserHandle.of(102);

        expectThat(mUserHandleHelper.getExistingUserHandle(100)).isEqualTo(user100);
        expectThat(mUserHandleHelper.getExistingUserHandle(101)).isEqualTo(user101);
        expectThat(mUserHandleHelper.getExistingUserHandle(102)).isEqualTo(user102);
        expectThat(mUserHandleHelper.getExistingUserHandle(103)).isNull();
    }

    @Test
    public void testGetEnabledProfiles() {
        List<UserHandle> users = Arrays.asList(UserHandle.of(100), UserHandle.of(101),
                UserHandle.of(102));
        when(mUserManager.getEnabledProfiles()).thenReturn(users);

        assertThat(mUserHandleHelper.getEnabledProfiles(
                mContextUser.getIdentifier())).containsExactlyElementsIn(users);
    }

    @Test
    public void testIsGuestUser() {
        when(mUserManager.isGuestUser()).thenReturn(true);

        assertThat(mUserHandleHelper.isGuestUser(mContextUser)).isTrue();
    }

    @Test
    public void testIsAdminUser() {
        when(mUserManager.isAdminUser()).thenReturn(true);

        assertThat(mUserHandleHelper.isAdminUser(mContextUser)).isTrue();
    }

    @Test
    public void testIsEphemeralUser() {
        UserHandle user = UserHandle.of(100);
        doReturn(true).when(() -> UserManagerHelper.isEphemeralUser(mUserManager, user));

        assertThat(mUserHandleHelper.isEphemeralUser(user)).isTrue();
    }

    @Test
    public void testIsEnabledUser() {
        UserHandle user = UserHandle.of(100);
        doReturn(true).when(() -> UserManagerHelper.isEnabledUser(mUserManager, user));

        assertThat(mUserHandleHelper.isEnabledUser(user)).isTrue();
    }

    @Test
    public void testIsManagedProfile() {
        UserHandle user = UserHandle.of(100);
        when(mUserManager.isManagedProfile(anyInt())).thenReturn(true);

        assertThat(mUserHandleHelper.isManagedProfile(user)).isTrue();
    }

    @Test
    public void testIsProfileUser() {
        when(mUserManager.isProfile()).thenReturn(true);

        assertThat(mUserHandleHelper.isProfileUser(mContextUser)).isTrue();
    }

    @Test
    public void testIsInitializedUser() {
        UserHandle user = UserHandle.of(100);
        doReturn(true).when(() -> UserManagerHelper.isInitializedUser(mUserManager, user));

        assertThat(mUserHandleHelper.isInitializedUser(user)).isTrue();
    }

    private void mockGetUserHandles(boolean excludeDying, List<UserHandle> userHandles) {
        doReturn(userHandles).when(
                () -> UserManagerHelper.getUserHandles(mUserManager, excludeDying));
    }
}
