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
package com.android.car.settings.enterprise;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.verify;

import android.app.admin.DeviceAdminInfo.PolicyInfo;
import android.util.Log;

import androidx.preference.Preference;

import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.ArrayList;

public final class DeviceAdminAddPoliciesPreferenceControllerTest extends
        BaseEnterprisePreferenceControllerTestCase {

    private static final String TAG = DeviceAdminAddPoliciesPreferenceControllerTest.class
            .getSimpleName();

    private DeviceAdminAddPoliciesPreferenceController mController;

    @Mock
    private Preference mPreference;
    @Captor
    private ArgumentCaptor<CharSequence> mTitleCaptor;

    @Before
    public void setController() {
        mController = new DeviceAdminAddPoliciesPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions);
        mController.setDeviceAdmin(mDefaultDeviceAdminInfo);
    }

    @Test
    public void testGetPreferenceType() throws Exception {
        assertWithMessage("preference type").that(mController.getPreferenceType())
                .isEqualTo(Preference.class);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin() throws Exception {
        DeviceAdminAddPoliciesPreferenceController controller =
                new DeviceAdminAddPoliciesPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneWrite() throws Exception {
        DeviceAdminAddPoliciesPreferenceController controller =
                new DeviceAdminAddPoliciesPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneRead() throws Exception {
        DeviceAdminAddPoliciesPreferenceController controller =
                new DeviceAdminAddPoliciesPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneHidden() throws Exception {
        DeviceAdminAddPoliciesPreferenceController controller =
                new DeviceAdminAddPoliciesPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner() throws Exception {
        mockDeviceOwner();

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_zoneWrite() throws Exception {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_zoneRead() throws Exception {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_zoneHidden() throws Exception {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner() throws Exception {
        mockProfileOwner();

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_zoneWrite() throws Exception {
        mockProfileOwner();
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_zoneRead() throws Exception {
        mockProfileOwner();
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_zoneHidden() throws Exception {
        mockProfileOwner();
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_regularAdmin() throws Exception {
        // Admin is neither PO nor DO

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_regularAdmin_zoneWrite() throws Exception {
        // Admin is neither PO nor DO
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_regularAdmin_zoneRead() throws Exception {
        // Admin is neither PO nor DO
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_regularAdmin_zoneHidden() throws Exception {
        // Admin is neither PO nor DO
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateState_systemUser() throws Exception {
        updateStateTest(/* isSystemUser= */ true);
    }

    @Test
    public void testUpdateState_nonSystemUser() throws Exception {
        updateStateTest(/* isSystemUser= */ false);
    }

    private void updateStateTest(boolean isSystemUser) {
        // Arrange
        if (isSystemUser) {
            mockSystemUser();
        } else {
            mockNonSystemUser();
        }
        ArrayList<PolicyInfo> usedPolicies = mFancyDeviceAdminInfo.getUsedPolicies();
        Log.d(TAG, "Admin policies: " + usedPolicies);
        mController.setDeviceAdmin(mFancyDeviceAdminInfo);

        // Act
        mController.updateState(mPreference);

        // Assert
        verify(mPreference).setTitle(mTitleCaptor.capture());
        Log.d(TAG, "Preference title: " + mTitleCaptor.getValue());
        for (PolicyInfo policy : usedPolicies) {
            CharSequence itemTitle = mRealContext
                    .getText(isSystemUser ? policy.label : policy.labelForSecondaryUsers);
            assertWithMessage("policy item title")
                    .that(mTitleCaptor.getValue().toString()).contains(itemTitle);
        }
    }
}
