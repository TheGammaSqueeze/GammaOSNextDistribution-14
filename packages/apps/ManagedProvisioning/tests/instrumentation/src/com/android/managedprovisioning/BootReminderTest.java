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

package com.android.managedprovisioning;

import static android.app.admin.DevicePolicyManager.STATE_USER_PROFILE_FINALIZED;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.finalization.UserProvisioningStateHelper;
import com.android.managedprovisioning.provisioninglisteners.ManagedUserRemovalUtils;
import com.android.managedprovisioning.ota.CrossProfileAppsPregrantController;
import com.android.managedprovisioning.preprovisioning.EncryptionController;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

// TODO(b/185377693): Update tests to use the new testing library so that we don't have to rely
//  on mocks
@SmallTest
public class BootReminderTest {

    @Mock
    private Context mContext;

    @Mock
    private DevicePolicyManager mDevicePolicyManager;

    @Mock
    private UserProvisioningStateHelper mUserProvisioningStateHelper;

    @Mock
    private EncryptionController mEncryptionController;

    @Mock
    private CrossProfileAppsPregrantController mCrossProfileAppsPregrantController;

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
    public void onReceive_hasWorkProfile_userProvisioningStateIntact() {
        BootReminder bootReminder = createBootReminderWithWorkProfile();

        bootReminder.onReceive(mContext, /* intent */ null);

        verify(mUserProvisioningStateHelper, never()).resetPrimaryUserProvisioningState();
    }

    @Test
    public void onReceive_userProvisioningStateUnmanaged_userProvisioningStateIntact() {
        BootReminder bootReminder = createBootReminder();

        bootReminder.onReceive(mContext, /* intent */ null);

        verify(mUserProvisioningStateHelper, never()).resetPrimaryUserProvisioningState();
    }

    @Test
    public void onReceive_userProvisioningStateProfileFinalized_userProvisioningStateReset() {
        BootReminder bootReminder = createBootReminder();
        setUserProvisioningStateFinalized();

        bootReminder.onReceive(mContext, /* intent */ null);

        verify(mUserProvisioningStateHelper).resetPrimaryUserProvisioningState();
    }

    private void setUserProvisioningStateFinalized() {
        when(mDevicePolicyManager.getUserProvisioningState())
            .thenReturn(STATE_USER_PROFILE_FINALIZED);
    }

    private BootReminder createBootReminder() {
        return createBootReminderInternal(/* hasManagedProfile */ false);
    }

    private BootReminder createBootReminderWithWorkProfile() {
        return createBootReminderInternal(/* hasManagedProfile */ true);
    }

    private BootReminder createBootReminderInternal(boolean hasManagedProfile) {
        return new BootReminder(
                context -> hasManagedProfile,
                context -> mUserProvisioningStateHelper,
                context -> mEncryptionController,
                context -> mCrossProfileAppsPregrantController,
                mManagedUserRemovalUtils);
    }
}
