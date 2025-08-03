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
package com.android.tradefed.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Helper class for mocking {@link ITestDevice} methods. */
public final class ITestDeviceMockHelper {

    private final ITestDevice mMockDevice;

    public ITestDeviceMockHelper(ITestDevice mockDevice) {
        mMockDevice = Objects.requireNonNull(mockDevice);
    }

    public void mockGetCurrentUser(int userId) throws Exception {
        when(mMockDevice.getCurrentUser()).thenReturn(userId);
    }

    public void mockGetUserInfos(Map<Integer, UserInfo> existingUsers) throws Exception {
        when(mMockDevice.getUserInfos()).thenReturn(existingUsers);
    }

    public void mockGetMaxNumberOfUsersSupported(int max) throws Exception {
        when(mMockDevice.getMaxNumberOfUsersSupported()).thenReturn(max);
    }

    public void mockSwitchUser(int userId) throws Exception {
        when(mMockDevice.switchUser(userId)).thenReturn(true);
    }

    public void mockStartUser(int userId) throws Exception {
        when(mMockDevice.startUser(userId, /* waitFlag= */ true)).thenReturn(true);
    }

    public void mockStartUserVisibleOnBackground(int userId, int displayId) throws Exception {
        mockStartUserVisibleOnBackground(userId, displayId, /* result= */ true);
    }

    public void mockStartUserVisibleOnBackground(int userId, int displayId, boolean result)
            throws Exception {
        when(mMockDevice.startVisibleBackgroundUser(userId, displayId, /* waitFlag= */ true))
                .thenReturn(result);
    }

    public void mockCreateUser(int userId) throws Exception {
        when(mMockDevice.createUser(any())).thenReturn(userId);
    }

    public IllegalStateException mockCreateUserFailure(String message) throws Exception {
        IllegalStateException e = new IllegalStateException(message);
        when(mMockDevice.createUser(any())).thenThrow(e);
        return e;
    }

    public void mockIsVisibleBackgroundUsersSupported(boolean supported) throws Exception {
        when(mMockDevice.isVisibleBackgroundUsersSupported()).thenReturn(supported);
    }

    public void mockIsVisibleBackgroundUsersOnDefaultDisplaySupported(boolean supported)
            throws Exception {
        when(mMockDevice.isVisibleBackgroundUsersOnDefaultDisplaySupported()).thenReturn(supported);
    }

    public void mockIsUserVisibleOnDisplay(int userId, int displayId) throws Exception {
        when(mMockDevice.isUserVisibleOnDisplay(userId, displayId)).thenReturn(true);
    }

    public void mockListDisplayIdsForStartingVisibleBackgroundUsers(Set<Integer> displays)
            throws Exception {
        when(mMockDevice.listDisplayIdsForStartingVisibleBackgroundUsers()).thenReturn(displays);
    }

    public void verifyNoUserCreated() throws Exception {
        verify(mMockDevice, never()).createUser(any());
    }

    public void verifyUserCreated() throws Exception {
        verify(mMockDevice).createUser(any());
    }

    public void verifyNoUserSwitched() throws Exception {
        verify(mMockDevice, never()).switchUser(anyInt());
    }

    public void verifyUserSwitched(int userId) throws Exception {
        verify(mMockDevice).switchUser(userId);
    }

    public void verifyNoUserStarted() throws Exception {
        verify(mMockDevice, never()).startUser(anyInt());
        verify(mMockDevice, never()).startUser(anyInt(), anyBoolean());
    }

    public void verifyNoUserStartedVisibleOnBackground() throws Exception {
        verify(mMockDevice, never()).startVisibleBackgroundUser(anyInt(), anyInt(), anyBoolean());
    }

    public void verifyUserStartedVisibleOnBackground(int userId, int displayId) throws Exception {
        verify(mMockDevice).startVisibleBackgroundUser(userId, displayId, /* waitFlag= */ true);
    }

    public void verifyUserStopped(int userId) throws Exception {
        verify(mMockDevice).stopUser(userId, /* waitFlag= */ true, /* forceFlag= */ true);
    }

    public void verifyNoUserStopped() throws Exception {
        verify(mMockDevice, never()).stopUser(anyInt(), anyBoolean(), anyBoolean());
    }

    public void verifyNoUserRemoved() throws Exception {
        verify(mMockDevice, never()).removeUser(anyInt());
    }

    public void verifyUserRemoved(int userId) throws Exception {
        verify(mMockDevice).removeUser(userId);
    }

    public void verifyUserNotRemoved(int userId) throws Exception {
        verify(mMockDevice, never()).removeUser(userId);
    }

    public void verifyListDisplayIdsForStartingVisibleBackgroundUsersNeverCalled()
            throws Exception {
        verify(mMockDevice, never()).listDisplayIdsForStartingVisibleBackgroundUsers();
    }

    public void verifyUserSettings(int userId, String namespace, String key, String value)
            throws Exception {
        verify(mMockDevice).setSetting(userId, namespace, key, value);
    }
}
