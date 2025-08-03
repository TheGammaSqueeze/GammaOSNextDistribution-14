/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.Utils;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link DpcReceivedSuccessReceiver}.
 */
public class DpcReceivedSuccessReceiverTest extends AndroidTestCase {
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final Intent TEST_INTENT = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
    private static final UserHandle MANAGED_PROFILE_USER_HANDLE = UserHandle.of(123);

    @Mock private Context mContext;
    @Mock private Utils mUtils;
    @Mock private DevicePolicyManager mDevicePolicyManager;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
    }

    @SmallTest
    public void testReceiver() {
        // GIVEN that no account migration occurred during provisioning
        final DpcReceivedSuccessReceiver receiver = new DpcReceivedSuccessReceiver(
                /* migratedAccount= */ null, MANAGED_PROFILE_USER_HANDLE,
                TEST_MDM_PACKAGE_NAME, /* callback */ null);

        // WHEN the profile provisioning complete intent was received by the DPC
        receiver.onReceive(mContext, TEST_INTENT);

        // THEN the system should be told to finalize the provisioning
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mDevicePolicyManager).finalizeWorkProfileProvisioning(
                MANAGED_PROFILE_USER_HANDLE, /* migratedAccount= */ null);
    }
}
