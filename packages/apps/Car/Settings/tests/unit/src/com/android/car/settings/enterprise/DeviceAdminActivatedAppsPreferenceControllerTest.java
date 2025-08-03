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

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DeviceAdminActivatedAppsPreferenceControllerTest
        extends BaseEnterprisePreferenceControllerTestCase {

    private DeviceAdminAppsPreferenceController mController;
    private BaseEnterprisePreferenceControllerTestCase.DummyPreferenceGroup mPreferenceGroup;

    @Before
    @UiThreadTest
    public void setUp() throws Exception {
        mController = new DeviceAdminActivatedAppsPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions);
        mPreferenceGroup = new DummyPreferenceGroup(mSpiedContext);
    }

    @Test
    public void testUpdateState_noBroadcastReceivers() {
        mockQueryBroadcastReceivers();

        mController.updateState(mPreferenceGroup);

        verifyPreferenceTitles(mPreferenceGroup,
                mRealContext.getString(R.string.device_admin_apps_list_empty));
    }

    @Test
    public void testUpdateState_noActivatedAdminApp() {
        mockQueryBroadcastReceivers(mDefaultResolveInfo, mFancyResolveInfo);
        mockInactiveAdmin(mDefaultAdmin);
        mockInactiveAdmin(mFancyAdmin);

        mController.updateState(mPreferenceGroup);

        verifyPreferenceTitles(mPreferenceGroup,
                mRealContext.getString(R.string.device_admin_apps_list_empty));
    }

    @Test
    public void testUpdateState_singleActivatedAdminApp() {
        mockQueryBroadcastReceivers(mDefaultResolveInfo, mFancyResolveInfo);
        mockInactiveAdmin(mDefaultAdmin);
        mockActiveAdmin(mFancyAdmin);

        mController.updateState(mPreferenceGroup);

        verifyPreferenceTitles(mPreferenceGroup, mFancyDeviceAdminInfo.loadLabel(mRealPm));
    }

    @Test
    public void testUpdateState_multipleActivatedAdminApps() {
        mockQueryBroadcastReceivers(mDefaultResolveInfo, mFancyResolveInfo);
        mockActiveAdmin(mDefaultAdmin);
        mockActiveAdmin(mFancyAdmin);

        mController.updateState(mPreferenceGroup);

        verifyPreferenceTitles(mPreferenceGroup,
                mDefaultDeviceAdminInfo.loadLabel(mRealPm),
                mFancyDeviceAdminInfo.loadLabel(mRealPm));
    }
}
