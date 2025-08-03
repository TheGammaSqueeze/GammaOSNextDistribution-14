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

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class EnterprisePrivacySettingsPreferenceControllerTest
        extends BaseEnterprisePrivacyPreferenceControllerTestCase {

    private EnterprisePrivacySettingsPreferenceController mController;

    @Mock
    private Preference mPreference;

    @Before
    public void setController() {
        mController = new EnterprisePrivacySettingsPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature() {
        mockNoDeviceAdminFeature();

        // Must use new controller as availability is set on constructor
        EnterprisePrivacySettingsPreferenceController controller =
                new EnterprisePrivacySettingsPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature_zoneWrite() {
        mockNoDeviceAdminFeature();

        // Must use new controller as availability is set on constructor
        EnterprisePrivacySettingsPreferenceController controller =
                new EnterprisePrivacySettingsPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature_zoneRead() {
        mockNoDeviceAdminFeature();

        // Must use new controller as availability is set on constructor
        EnterprisePrivacySettingsPreferenceController controller =
                new EnterprisePrivacySettingsPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noFeature_zoneHidden() {
        mockNoDeviceAdminFeature();

        // Must use new controller as availability is set on constructor
        EnterprisePrivacySettingsPreferenceController controller =
                new EnterprisePrivacySettingsPreferenceController(mSpiedContext, mPreferenceKey,
                        mFragmentController, mUxRestrictions);
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }


    @Test
    public void testGetAvailabilityStatus_noDeviceOwner() {
        mockNoDeviceOwner();

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noDeviceOwner_zoneWrite() {
        mockNoDeviceOwner();
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noDeviceOwner_zoneRead() {
        mockNoDeviceOwner();
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_noDeviceOwner_zoneHidden() {
        mockNoDeviceOwner();
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_withDeviceOwner() {
        mockDeviceOwner();

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_withDeviceOwner_zoneWrite() {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_withDeviceOwner_zoneRead() {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_withDeviceOwner_zoneHidden() {
        mockDeviceOwner();
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testuUdateState() {
        mController.updateState(mPreference);

        verifyPreferenceTitleNeverSet(mPreference);
        verifyPreferenceSummarySet(mPreference,
                R.string.enterprise_privacy_settings_summary_generic);
        verifyPreferenceIconNeverSet(mPreference);
    }
}
