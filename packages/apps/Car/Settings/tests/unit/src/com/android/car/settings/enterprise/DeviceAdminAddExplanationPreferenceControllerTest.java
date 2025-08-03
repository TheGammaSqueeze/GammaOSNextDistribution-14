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

import androidx.preference.Preference;

import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class DeviceAdminAddExplanationPreferenceControllerTest extends
        BaseEnterprisePreferenceControllerTestCase {

    @Mock
    private Preference mPreference;

    private DeviceAdminAddExplanationPreferenceController mController;

    @Before
    public void setController() {
        mController = new DeviceAdminAddExplanationPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions);
        mController.setDeviceAdmin(mDefaultDeviceAdminInfo);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin() throws Exception {
        DeviceAdminAddExplanationPreferenceController controller =
                new DeviceAdminAddExplanationPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        mController.setExplanation("To conquer the universe");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneWrite() throws Exception {
        DeviceAdminAddExplanationPreferenceController controller =
                new DeviceAdminAddExplanationPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneRead() throws Exception {
        DeviceAdminAddExplanationPreferenceController controller =
                new DeviceAdminAddExplanationPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneHidden() throws Exception {
        DeviceAdminAddExplanationPreferenceController controller =
                new DeviceAdminAddExplanationPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_noReason() throws Exception {
        mockDeviceOwner();

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_noReason_zoneWrite() throws Exception {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_noReason_zoneRead() throws Exception {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_noReason_zoneHidden() throws Exception {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_withReason() throws Exception {
        mockDeviceOwner();
        mController.setExplanation("To conquer the universe");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_withReason_zoneWrite() throws Exception {
        mockDeviceOwner();
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_withReason_zoneRead() throws Exception {
        mockDeviceOwner();
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_deviceOwner_withReason_zoneHidden() throws Exception {
        mockDeviceOwner();
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_noReason() throws Exception {
        mockProfileOwner();

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_noReason_zoneWrite() throws Exception {
        mockProfileOwner();
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_noReason_zoneRead() throws Exception {
        mockProfileOwner();
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_noReason_zoneHidden() throws Exception {
        mockProfileOwner();
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_withReason() throws Exception {
        mockProfileOwner();
        mController.setExplanation("To conquer the universe");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_withReason_zoneWrite() throws Exception {
        mockProfileOwner();
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_withReason_zoneRead() throws Exception {
        mockProfileOwner();
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_profileOwner_withReason_zoneHidden() throws Exception {
        mockProfileOwner();
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetRealAvailabilityStatus_nullReason() throws Exception {
        mController.setExplanation(null);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetRealAvailabilityStatus_nullReason_zoneWrite() throws Exception {
        mController.setExplanation(null);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetRealAvailabilityStatus_nullReason_zoneRead() throws Exception {
        mController.setExplanation(null);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetRealAvailabilityStatus_nullReason_zoneHidden() throws Exception {
        mController.setExplanation(null);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyReason() throws Exception {
        mController.setExplanation("");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyReason_zoneWrite() throws Exception {
        mController.setExplanation("");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyReason_zoneRead() throws Exception {
        mController.setExplanation("");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyReason_zoneHidden() throws Exception {
        mController.setExplanation("");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_validReason() throws Exception {
        mController.setExplanation("To conquer the universe");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_validReason_zoneWrite() throws Exception {
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_validReason_zoneRead() throws Exception {
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_validReason_zoneHidden() throws Exception {
        mController.setExplanation("To conquer the universe");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateState() throws Exception {
        mController.setExplanation("To conquer the universe");

        mController.updateState(mPreference);

        verifyPreferenceTitleSet(mPreference, "To conquer the universe");
        verifyPreferenceSummaryNeverSet(mPreference);
        verifyPreferenceIconNeverSet(mPreference);
    }
}
