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

public final class DeviceAdminAddSupportPreferenceControllerTest extends
        BaseEnterprisePreferenceControllerTestCase {

    @Mock
    private Preference mPreference;

    private DeviceAdminAddSupportPreferenceController mController;

    @Before
    public void setController() {
        mController = new DeviceAdminAddSupportPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions);
        mController.setDeviceAdmin(mDefaultDeviceAdminInfo);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin() throws Exception {
        DeviceAdminAddSupportPreferenceController controller =
                new DeviceAdminAddSupportPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneWrite() throws Exception {
        DeviceAdminAddSupportPreferenceController controller =
                new DeviceAdminAddSupportPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneRead() throws Exception {
        DeviceAdminAddSupportPreferenceController controller =
                new DeviceAdminAddSupportPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_noAdmin_zoneHidden() throws Exception {
        DeviceAdminAddSupportPreferenceController controller =
                new DeviceAdminAddSupportPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_nullMessage() {
        mockGetLongSupportMessageForUser(null);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_nullMessage_zoneWrite() {
        mockGetLongSupportMessageForUser(null);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_nullMessage_zoneRead() {
        mockGetLongSupportMessageForUser(null);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_nullMessage_zoneHidden() {
        mockGetLongSupportMessageForUser(null);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyMessage() {
        mockGetLongSupportMessageForUser("");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyMessage_zoneWrite() {
        mockGetLongSupportMessageForUser("");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyMessage_zoneRead() {
        mockGetLongSupportMessageForUser("");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_emptyMessage_zoneHidden() {
        mockGetLongSupportMessageForUser("");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_validMessage() {
        mockGetLongSupportMessageForUser("WHAZZZZUP");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_validMessage_zoneWrite() {
        mockGetLongSupportMessageForUser("WHAZZZZUP");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_validMessage_zoneRead() {
        mockGetLongSupportMessageForUser("WHAZZZZUP");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_validMessage_zoneHidden() {
        mockGetLongSupportMessageForUser("WHAZZZZUP");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                PreferenceController.CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testUpdateState() {
        mockGetLongSupportMessageForUser("WHAZZZZUP");
        mController.setSupportMessage();

        mController.updateState(mPreference);

        verifyPreferenceTitleSet(mPreference, "WHAZZZZUP");
    }
}
