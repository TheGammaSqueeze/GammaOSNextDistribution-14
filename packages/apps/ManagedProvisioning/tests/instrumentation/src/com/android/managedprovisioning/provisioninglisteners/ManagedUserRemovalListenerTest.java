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

import static android.app.admin.DevicePolicyManager.STATE_USER_PROFILE_FINALIZED;
import static android.content.Intent.ACTION_USER_ADDED;
import static android.content.Intent.ACTION_USER_REMOVED;
import static android.content.Intent.EXTRA_USER;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

// TODO(b/185377693): Update tests to use the new testing library so that we don't have to rely
//  on mocks
@SmallTest
public class ManagedUserRemovalListenerTest {

    private static final Intent ARBITRARY_ACTION_INTENT = new Intent().setAction(ACTION_USER_ADDED);
    private static final UserHandle WORK_PROFILE_USER = UserHandle.of(10);
    private static final Intent WORK_PROFILE_USER_REMOVED_INTENT = new Intent()
            .setAction(ACTION_USER_REMOVED)
            .putExtra(EXTRA_USER, WORK_PROFILE_USER);
    private static final UserHandle SECONDARY_USER = UserHandle.of(15);
    private static final Intent SECONDARY_USER_REMOVED_INTENT = new Intent()
            .setAction(ACTION_USER_REMOVED)
            .putExtra(EXTRA_USER, SECONDARY_USER);

    @Mock
    private Context mContext;

    @Mock
    private DevicePolicyManager mDevicePolicyManager;

    @Mock
    private UserProvisioningStateHelper mUserProvisioningStateHelper;

    private final ManagedUserRemovalUtils mManagedUserRemovalUtils = new ManagedUserRemovalUtils();


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
    }

    @Test
    public void onReceive_arbitraryAction_userProvisioningStateIntact() {
        ManagedUserRemovalListener managedUserRemovalListener = createManagedUserRemovalListener();

        managedUserRemovalListener.onReceive(mContext, ARBITRARY_ACTION_INTENT);

        verify(mUserProvisioningStateHelper, never()).resetPrimaryUserProvisioningState();
    }

    @Test
    public void onReceive_secondaryUserRemovedAction_userProvisioningStateIntact() {
        ManagedUserRemovalListener managedUserRemovalListener =
                createManagedUserRemovalListenerWithWorkProfile();

        managedUserRemovalListener.onReceive(mContext, SECONDARY_USER_REMOVED_INTENT);

        verify(mUserProvisioningStateHelper, never()).resetPrimaryUserProvisioningState();
    }

    @Test
    public void onReceive_workProfileUserRemovedAction_userProvisioningStateIntact() {
        ManagedUserRemovalListener managedUserRemovalListener =
                createManagedUserRemovalListenerWithWorkProfile();

        managedUserRemovalListener.onReceive(mContext, WORK_PROFILE_USER_REMOVED_INTENT);

        verify(mUserProvisioningStateHelper, never()).resetPrimaryUserProvisioningState();
    }

    @Test
    public void onReceive_workProfileUserRemovedAction_stateProfileFinalized_userProvisioningStateResets() {
        ManagedUserRemovalListener managedUserRemovalListener = createManagedUserRemovalListener();
        setUserProvisioningStateFinalized();

        managedUserRemovalListener.onReceive(mContext, WORK_PROFILE_USER_REMOVED_INTENT);

        verify(mUserProvisioningStateHelper).resetPrimaryUserProvisioningState();
    }

    private void setUserProvisioningStateFinalized() {
        when(mDevicePolicyManager.getUserProvisioningState())
                .thenReturn(STATE_USER_PROFILE_FINALIZED);
    }

    private ManagedUserRemovalListener createManagedUserRemovalListenerWithWorkProfile() {
        return createManagedUserRemovalListenerInternal(/* hasWorkProfile */ true);
    }

    private ManagedUserRemovalListener createManagedUserRemovalListener() {
        return createManagedUserRemovalListenerInternal(/* hasWorkProfile */ false);
    }

    private ManagedUserRemovalListener createManagedUserRemovalListenerInternal(boolean hasWorkProfile) {
        return new ManagedUserRemovalListener(
                context -> hasWorkProfile,
                context -> mUserProvisioningStateHelper,
                mManagedUserRemovalUtils);
    }
}
