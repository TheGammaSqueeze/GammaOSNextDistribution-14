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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;

import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ManagedUserRemovalUtilsTest {

    @Mock
    private Context mContext;

    @Mock
    private DevicePolicyManager mDevicePolicyManager;

    @Mock
    private UserProvisioningStateHelper mUserProvisioningStateHelper;

    private final ManagedUserRemovalUtils mManagedUserRemovalUtils =
            new ManagedUserRemovalUtils();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
    }

    @Test
    public void resetPrimaryUserProvisioningStateIfNecessary_stateUserUnmanaged_userProvisioningStateIntact() {
        mManagedUserRemovalUtils.resetPrimaryUserProvisioningStateIfNecessary(
                mContext, mUserProvisioningStateHelper);

        verify(mUserProvisioningStateHelper, never()).resetPrimaryUserProvisioningState();
    }

    @Test
    public void resetPrimaryUserProvisioningStateIfNecessary_stateUserProfileFinalized_userProvisioningStateResets() {
        setUserProvisioningStateFinalized();

        mManagedUserRemovalUtils.resetPrimaryUserProvisioningStateIfNecessary(
                mContext, mUserProvisioningStateHelper);

        verify(mUserProvisioningStateHelper).resetPrimaryUserProvisioningState();
    }

    private void setUserProvisioningStateFinalized() {
        when(mDevicePolicyManager.getUserProvisioningState())
                .thenReturn(STATE_USER_PROFILE_FINALIZED);
    }
}
