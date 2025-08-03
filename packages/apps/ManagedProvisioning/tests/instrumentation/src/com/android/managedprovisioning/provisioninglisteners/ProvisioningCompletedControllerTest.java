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

package com.android.managedprovisioning.provisioninglisteners;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.task.interactacrossprofiles.CrossProfileAppsSnapshot;
import com.android.managedprovisioning.task.nonrequiredapps.SystemAppsSnapshot;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for {@link ProvisioningCompletedController}.
 */
@SmallTest
public class ProvisioningCompletedControllerTest {

    private static final int MANAGED_USER_USER_ID = 12;
    private static final int PARENT_USER_USER_ID = 0;

    @Mock
    private SystemAppsSnapshot mSystemAppsSnapshot;
    @Mock
    private CrossProfileAppsSnapshot mCrossProfileAppsSnapshot;
    @Mock
    private UserManager mUserManager;

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Test
    public void provision_withNullUserId_doesNotTakeSystemAppsSnapshot() {
        ProvisioningCompletedController controller = new ProvisioningCompletedController(
                UserHandle.USER_NULL,
                ACTION_PROVISION_MANAGED_USER,
                /* leaveAllSystemAppsEnabled= */ false,
                mSystemAppsSnapshot,
                mCrossProfileAppsSnapshot,
                mUserManager);

        controller.run();

        verifyZeroInteractions(mSystemAppsSnapshot);
    }

    @Test
    public void provision_leaveSystemAppsEnabled_doesNotTakeSystemAppsSnapshot() {
        ProvisioningCompletedController controller = new ProvisioningCompletedController(
                MANAGED_USER_USER_ID,
                ACTION_PROVISION_MANAGED_USER,
                /* leaveAllSystemAppsEnabled= */ true,
                mSystemAppsSnapshot,
                mCrossProfileAppsSnapshot,
                mUserManager);

        controller.run();

        verifyZeroInteractions(mSystemAppsSnapshot);
    }

    @Test
    public void provision_doNotLeaveSystemAppsEnabled_takesSystemAppsSnapshot() {
        ProvisioningCompletedController controller = new ProvisioningCompletedController(
                MANAGED_USER_USER_ID,
                ACTION_PROVISION_MANAGED_USER,
                /* leaveAllSystemAppsEnabled= */ false,
                mSystemAppsSnapshot,
                mCrossProfileAppsSnapshot,
                mUserManager);

        controller.run();

        verify(mSystemAppsSnapshot).takeNewSnapshot(MANAGED_USER_USER_ID);
    }

    @Test
    public void provision_managedProfile_takesCrossProfileAppsSnapshot() {
        UserInfo parent = new UserInfo();
        parent.id = PARENT_USER_USER_ID;
        when(mUserManager.getProfileParent(MANAGED_USER_USER_ID)).thenReturn(parent);
        ProvisioningCompletedController controller = new ProvisioningCompletedController(
                MANAGED_USER_USER_ID,
                ACTION_PROVISION_MANAGED_PROFILE,
                /* leaveAllSystemAppsEnabled= */ false,
                mSystemAppsSnapshot,
                mCrossProfileAppsSnapshot,
                mUserManager);

        controller.run();

        verify(mCrossProfileAppsSnapshot).takeNewSnapshot(PARENT_USER_USER_ID);
    }
}
