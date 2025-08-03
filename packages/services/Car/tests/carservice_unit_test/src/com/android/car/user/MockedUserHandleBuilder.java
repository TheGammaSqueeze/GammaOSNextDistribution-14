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
package com.android.car.user;

import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.UserHandle;

public final class MockedUserHandleBuilder {
    private final UserHandle mUser;
    private UserHandleHelper mUserHandleHelper;

    private MockedUserHandleBuilder(UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        mUserHandleHelper = userHandleHelper;
        mUser = UserHandle.of(userId);
        // enabled by default
        when(mUserHandleHelper.isEnabledUser(mUser)).thenReturn(true);
        when(mUserHandleHelper.getExistingUserHandle(userId)).thenReturn(mUser);
    }

    private MockedUserHandleBuilder setDisabled() {
        when(mUserHandleHelper.isEnabledUser(mUser)).thenReturn(false);
        return this;
    }

    private MockedUserHandleBuilder setAdmin() {
        when(mUserHandleHelper.isAdminUser(mUser)).thenReturn(true);
        return this;
    }

    private MockedUserHandleBuilder setGuest() {
        when(mUserHandleHelper.isGuestUser(mUser)).thenReturn(true);
        return this;
    }

    private MockedUserHandleBuilder setEphemeral() {
        when(mUserHandleHelper.isEphemeralUser(mUser)).thenReturn(true);
        return this;
    }

    private MockedUserHandleBuilder setManagedProfile() {
        when(mUserHandleHelper.isManagedProfile(mUser)).thenReturn(true);
        return this;
    }

    private MockedUserHandleBuilder setInitialized() {
        when(mUserHandleHelper.isInitializedUser(mUser)).thenReturn(true);
        return this;
    }

    private MockedUserHandleBuilder expectGettersFail() {
        RuntimeException exception = new RuntimeException("D'OH!");
        when(mUserHandleHelper.isAdminUser(mUser)).thenThrow(exception);
        when(mUserHandleHelper.isEnabledUser(mUser)).thenThrow(exception);
        when(mUserHandleHelper.isProfileUser(mUser)).thenThrow(exception);
        when(mUserHandleHelper.isInitializedUser(mUser)).thenThrow(exception);
        return this;
    }

    private UserHandle build() {
        return mUser;
    }

    public static UserHandle expectRegularUserExists(@NonNull UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        return new MockedUserHandleBuilder(userHandleHelper, userId).build();
    }

    public static UserHandle expectUserExistsButGettersFail(
            @NonNull UserHandleHelper userHandleHelper, @UserIdInt int userId) {
        return new MockedUserHandleBuilder(userHandleHelper, userId).expectGettersFail().build();
    }

    public static UserHandle expectSystemUserExists(@NonNull UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        return new MockedUserHandleBuilder(userHandleHelper, userId).build();
    }

    public static UserHandle expectManagedProfileExists(@NonNull UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        return new MockedUserHandleBuilder(userHandleHelper, userId).setManagedProfile()
                .build();
    }

    public static UserHandle expectAdminUserExists(@NonNull UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        return new MockedUserHandleBuilder(userHandleHelper, userId).setAdmin().build();
    }

    public static UserHandle expectEphemeralUserExists(@NonNull UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        return new MockedUserHandleBuilder(userHandleHelper, userId).setEphemeral().build();
    }

    public static UserHandle expectDisabledUserExists(@NonNull UserHandleHelper userHandleHelper,
            @UserIdInt int userId) {
        return new MockedUserHandleBuilder(userHandleHelper, userId).setDisabled().build();
    }

    public static UserHandle expectGuestUserExists(@NonNull UserHandleHelper userHandleHelper,
            @UserIdInt int userId, boolean isEphemeral) {
        if (isEphemeral) {
            return new MockedUserHandleBuilder(userHandleHelper, userId).setGuest()
                    .setEphemeral().build();
        }
        return new MockedUserHandleBuilder(userHandleHelper, userId).setGuest().build();
    }
}
