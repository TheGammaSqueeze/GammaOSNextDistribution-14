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

import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class DeviceAdminAddWarningPreferenceControllerTest extends
        BaseEnterprisePreferenceControllerTestCase {

    @Mock
    private Preference mPreference;

    private DeviceAdminAddWarningPreferenceController mController;

    @Before
    public void setController() {
        mController = new DeviceAdminAddWarningPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions);
        mController.setDeviceAdmin(mDefaultDeviceAdminInfo);
    }

    @Test
    public void testUpdateState_adminInactive() {
        mockInactiveAdmin(mDefaultAdmin);

        mController.updateState(mPreference);

        verifyPreferenceTitleSet(mPreference,
                mRealContext.getString(R.string.device_admin_warning, mAppName));
    }

    @Test
    public void testUpdateState_adminActive() {
        mockActiveAdmin(mDefaultAdmin);

        mController.updateState(mPreference);

        verifyPreferenceTitleSet(mPreference,
                mRealContext.getString(R.string.device_admin_status, mAppName));
    }

    @Test
    public void testUpdateState_profileOwner() {
        mockProfileOwner();

        mController.updateState(mPreference);

        verifyPreferenceTitleSet(mPreference,
                mRealContext.getString(R.string.admin_profile_owner_user_message));
    }

    @Test
    public void testUpdateState_deviceOwner() {
        mockDeviceOwner();

        mController.updateState(mPreference);

        verifyPreferenceTitleSet(mPreference,
                mRealContext.getString(R.string.admin_device_owner_message));
    }

    @Test
    public void testUpdateState_deviceOwner_financialDevice() {
        mockDeviceOwner();
        mockFinancialDevice();

        mController.updateState(mPreference);

        verifyPreferenceTitleSet(mPreference,
                mRealContext.getString(R.string.admin_financed_message));
    }
}
