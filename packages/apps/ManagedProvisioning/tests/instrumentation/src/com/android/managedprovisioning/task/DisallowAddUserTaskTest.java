/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.managedprovisioning.task;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit-tests for {@link DisallowAddUserTask}.
 *
 * <p>Run as {@code atest ManagedProvisioningTests:DisallowAddUserTaskTest}.
 */
public class DisallowAddUserTaskTest extends AndroidTestCase {
    @Mock private Context mMockContext;
    @Mock private UserManager mMockUserManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;

    // Normal cases.
    private final UserInfo mSystemUser = new UserInfo(UserHandle.USER_SYSTEM, "System",
            UserInfo.FLAG_PRIMARY | UserInfo.FLAG_ADMIN);

    // Headless-system-user cases.
    private final UserInfo mFullUser = new UserInfo(10, "FullUser", UserInfo.FLAG_ADMIN);

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mMockContext.getSystemService(Context.USER_SERVICE)).thenReturn(mMockUserManager);
        // Setup sensible default responses.
        when(mMockUserManager.hasUserRestriction(anyString(), any(UserHandle.class)))
                .thenReturn(false);
    }

    @SmallTest
    public void testMaybeDisallowAddUsers_normalSystem() {
        // GIVEN that only one user exists on the device and the system doesn't have a headless
        // system user
        when(mMockUserManager.getUsers()).thenReturn(Collections.singletonList(mSystemUser));
        final DisallowAddUserTask task =
                new DisallowAddUserTask(false, mMockContext, null, mCallback,
                        mProvisioningAnalyticsTracker);

        // WHEN running the DisallowAddUserTask on the single user
        task.run(mSystemUser.id);

        // THEN the user restriction should be set
        verify(mMockUserManager).setUserRestriction(UserManager.DISALLOW_ADD_USER, true,
                mSystemUser.getUserHandle());
        verify(mCallback).onSuccess(task);
    }

    @SmallTest
    public void testMaybeDisallowAddUsers_normalSystem_restrictionAlreadySetupForOneUser() {
        // GIVEN that only one user exists on the device and the system doesn't have a headless
        // system user
        when(mMockUserManager.getUsers()).thenReturn(Collections.singletonList(mSystemUser));
        final DisallowAddUserTask task =
                new DisallowAddUserTask(false, mMockContext, null, mCallback,
                        mProvisioningAnalyticsTracker);

        // GIVEN that the user restriction has already been set
        when(mMockUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER,
                mSystemUser.getUserHandle()))
                .thenReturn(true);

        // WHEN running the DisallowAddUserTask on the single user
        task.run(mSystemUser.id);

        // THEN the user restriction should not be set
        verify(mMockUserManager, never()).setUserRestriction(anyString(), anyBoolean(),
                any(UserHandle.class));
        verify(mCallback).onSuccess(task);
    }

    @SmallTest
    public void testNeverDisallowAddUsers_headlessSystemUserMode_currentUserIsSystemUser() {
        testNeverDisallowAddUsers_headlessSystemUserMode(/* currentUser= */ mSystemUser);
    }

    @SmallTest
    public void testNeverDisallowAddUsers_headlessSystemUserMode_currentUserIsFullUser() {
        // This scenario should not happen on real life, but the end result would be the same - the
        // restriction is not set on any user
        testNeverDisallowAddUsers_headlessSystemUserMode(/* currentUser= */ mFullUser);
    }

    // Not a @Test per se, but a helpers that contains the logic of the 2
    // testNeverDisallowAddUsers_headlessSystemUserMode() - the behavior and logic is identical, the
    // only difference is the current user
    private void testNeverDisallowAddUsers_headlessSystemUserMode(UserInfo currentUser) {
        // GIVEN that we have a headless system user, it has a full user as well
        when(mMockUserManager.getUsers()).thenReturn(Arrays.asList(mSystemUser, mFullUser));
        final DisallowAddUserTask task =
                new DisallowAddUserTask(true, mMockContext, null, mCallback,
                        mProvisioningAnalyticsTracker);

        // WHEN running the DisallowAddUserTask as the given user
        task.run(currentUser.id);

        // THEN the user restriction should not be set
        verify(mMockUserManager, never()).setUserRestriction(anyString(), anyBoolean(),
                any(UserHandle.class));
        verify(mCallback).onSuccess(task);
    }
}
