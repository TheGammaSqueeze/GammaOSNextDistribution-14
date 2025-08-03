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

import android.content.ComponentName;

import androidx.preference.Preference;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.settingslib.utils.StringUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ManageDeviceAdminPreferenceControllerTest
        extends BaseEnterprisePreferenceControllerTestCase {

    private Preference mPreference;

    @Test
    public void testUpdateState_noActiveAdminApps() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureEnabled();
        mockGetActiveAdmins();

        controller.updateState(mPreference);

        assertPreferenceSummary(mPreference,
                mRealContext.getString(R.string.number_of_device_admins_none));
    }

    @Test
    public void testUpdateState_singleActiveAdminApp() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureEnabled();
        mockGetActiveAdmins(ComponentName.createRelative("packageName", "className"));

        controller.updateState(mPreference);

        assertPreferenceSummary(mPreference,
                StringUtil.getIcuPluralsString(mRealContext, 1, R.string.number_of_device_admins));
    }

    @Test
    public void testUpdateState_multipleActiveAdminApps() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureEnabled();
        mockGetActiveAdmins(ComponentName.createRelative("packageName1", "className1"),
                ComponentName.createRelative("packageName2", "className2"));

        controller.updateState(mPreference);

        assertPreferenceSummary(mPreference,
                StringUtil.getIcuPluralsString(mRealContext, 2, R.string.number_of_device_admins));
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminDisabled() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureDisabled();

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminDisabled_zoneWrite() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureDisabled();
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminDisabled_zoneRead() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureDisabled();
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminDisabled_zoneHidden() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureDisabled();
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminEnabled() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureEnabled();

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminEnabled_zoneWrite() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureEnabled();
        controller.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminEnabled_zoneRead() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureEnabled();
        controller.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_deviceAdminEnabled_zoneHidden() {
        ManageDeviceAdminPreferenceController controller = newControllerWithFeatureEnabled();
        controller.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(controller.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    private ManageDeviceAdminPreferenceController newControllerWithFeatureDisabled() {
        mockNoDeviceAdminFeature();
        return newController();
    }

    private ManageDeviceAdminPreferenceController newControllerWithFeatureEnabled() {
        mockHasDeviceAdminFeature();
        ManageDeviceAdminPreferenceController controller = newController();
        return controller;
    }

    private ManageDeviceAdminPreferenceController newController() {
        mPreference = new Preference(mSpiedContext);
        return new ManageDeviceAdminPreferenceController(
                mSpiedContext, mPreferenceKey, mFragmentController, mUxRestrictions);
    }
}
