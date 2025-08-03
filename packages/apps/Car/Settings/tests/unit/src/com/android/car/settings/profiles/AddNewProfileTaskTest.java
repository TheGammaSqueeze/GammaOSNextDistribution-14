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

package com.android.car.settings.profiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.ResultCallback;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserStopResponse;
import android.car.user.UserStopResult;
import android.car.util.concurrent.AndroidAsyncFuture;
import android.car.util.concurrent.AndroidFuture;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.testutils.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class AddNewProfileTaskTest {

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private AddNewProfileTask mTask;
    private final int mNewUserId = 15;

    @Mock
    private UserManager mUserManager;
    @Mock
    private CarUserManager mCarUserManager;
    @Mock
    private AddNewProfileTask.AddNewProfileListener mAddNewProfileListener;

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        int foregroundUserId = ActivityManager.getCurrentUser();
        when(mContext.getUser()).thenReturn(UserHandle.of(foregroundUserId));
        mTask = new AddNewProfileTask(mContext, mCarUserManager, mAddNewProfileListener);
    }

    @Test
    public void createNewUser_success() {
        String newUserName = "Test name";
        UserInfo newUser = new UserInfo(mNewUserId, newUserName, /* flags= */ 0);

        mockCreateUser(newUser, UserCreationResult.STATUS_SUCCESSFUL);

        mTask.execute(newUserName);
        // wait for async task
        PollingCheck.waitFor(() -> mTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mCarUserManager).createUser(newUserName, /* flags= */ 0);
    }

    @Test
    public void userCreated_switchToNewUser() {
        String newUserName = "Test name";
        UserInfo newUser = new UserInfo(mNewUserId, newUserName, /* flags= */ 0);

        mockCreateUser(newUser, UserCreationResult.STATUS_SUCCESSFUL);

        mTask.execute(newUserName);
        // wait for async task
        PollingCheck.waitFor(() -> mTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mCarUserManager).switchUser(newUser.id);
    }

    @Test
    public void userCreated_successCalled() {
        String newUserName = "Test name";
        UserInfo newUser = new UserInfo(mNewUserId, newUserName, /* flags= */ 0);

        mockCreateUser(newUser, UserCreationResult.STATUS_SUCCESSFUL);

        mTask.execute(newUserName);
        // wait for async task
        PollingCheck.waitFor(() -> mTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mAddNewProfileListener).onProfileAddedSuccess();
        verify(mCarUserManager).switchUser(mNewUserId);
    }

    @Test
    public void userNotCreated_failureCalled() {
        String newUserName = "Test name";

        mockCreateUser(/* user= */ null, UserCreationResult.STATUS_ANDROID_FAILURE);

        mTask.execute(newUserName);
        // wait for async task
        PollingCheck.waitFor(() -> mTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mAddNewProfileListener).onProfileAddedFailure();
    }

    @Test
    public void userCreatedOnPassenger_success_stopsAndStartsUser() {
        String newUserName = "Test name";
        UserInfo newUser = new UserInfo(mNewUserId, newUserName, /* flags= */ 0);

        mockCreateUser(newUser, UserCreationResult.STATUS_SUCCESSFUL);

        // Pretend to execute off of passenger user
        int foregroundUserId = ActivityManager.getCurrentUser();
        when(mContext.getUser()).thenReturn(UserHandle.of(foregroundUserId + 1));

        mTask.execute(newUserName);
        // wait for async task
        PollingCheck.waitFor(() -> mTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mAddNewProfileListener).onProfileAddedSuccess();

        ArgumentCaptor<ResultCallback<UserStopResponse>> captor =
                ArgumentCaptor.forClass(ResultCallback.class);
        verify(mCarUserManager).stopUser(any(), any(), captor.capture());

        ResultCallback<UserStopResponse> callback = captor.getValue();
        UserStopResponse result = new UserStopResponse(UserStopResult.STATUS_SUCCESSFUL);
        callback.onResult(result);

        verify(mCarUserManager).startUser(any(), any(), any());
    }

    @Test
    public void userCreatedOnPassenger_failure_doesNotStartUser() {
        String newUserName = "Test name";
        UserInfo newUser = new UserInfo(mNewUserId, newUserName, /* flags= */ 0);

        mockCreateUser(newUser, UserCreationResult.STATUS_SUCCESSFUL);

        // Pretend to execute off of passenger user
        int foregroundUserId = ActivityManager.getCurrentUser();
        when(mContext.getUser()).thenReturn(UserHandle.of(foregroundUserId + 1));

        mTask.execute(newUserName);
        // wait for async task
        PollingCheck.waitFor(() -> mTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mAddNewProfileListener).onProfileAddedSuccess();

        ArgumentCaptor<ResultCallback<UserStopResponse>> captor =
                ArgumentCaptor.forClass(ResultCallback.class);
        verify(mCarUserManager).stopUser(any(), any(), captor.capture());

        ResultCallback<UserStopResponse> callback = captor.getValue();
        UserStopResponse result = new UserStopResponse(UserStopResult.STATUS_ANDROID_FAILURE);
        callback.onResult(result);

        verify(mCarUserManager, never()).startUser(any(), any(), any());
    }

    private void mockCreateUser(@Nullable UserInfo user, int status) {
        AndroidFuture<UserCreationResult> future = new AndroidFuture<>();
        future.complete(new UserCreationResult(status, user == null ? null : user.getUserHandle()));
        when(mCarUserManager.createUser(anyString(), anyInt()))
                .thenReturn(new AndroidAsyncFuture<>(future));
        if (user != null) {
            when(mUserManager.getUserInfo(user.id)).thenReturn(user);
        }
    }
}
