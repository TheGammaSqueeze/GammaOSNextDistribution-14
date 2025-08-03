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
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.UserInfo;
import android.util.Log;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settingslib.applications.UserAppInfo;
import com.android.car.settingslib.enterprise.EnterpriseDefaultApps;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

public final class EnterpriseSetDefaultAppsPreferenceControllerTest
        extends BaseEnterprisePrivacyPreferenceControllerTestCase {

    private static final String TAG = EnterpriseSetDefaultAppsPreferenceControllerTest.class
            .getSimpleName();

    private UserInfo mMyUser;
    private EnterpriseSetDefaultAppsPreferenceController mController;

    @Mock
    private Preference mPreference;

    @Before
    public void setUp() throws Exception {
        mController = new EnterpriseSetDefaultAppsPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions, mApplicationFeatureProvider);
        int userId = mSpiedContext.getUserId();
        mMyUser = new UserInfo(mSpiedContext.getUserId(), "Mambo Number " + userId, /* flags= */ 0);
        Log.d(TAG, "myUser: " + mMyUser.toFullString());
    }

    @Test
    public void testGetAvailabilityStatus_none() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_none_zoneWrite() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_none_zoneRead() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_none_zoneHidden() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_one() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_one_zoneWrite() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_one_zoneRead() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_one_zoneHidden() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_cached() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testGetAvailabilityStatus_cached_zoneWrite() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testGetAvailabilityStatus_cached_zoneRead() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testGetAvailabilityStatus_cached_zoneHidden() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 1);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 0);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testUpdateState() {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, 4);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CALENDAR, 8);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CAMERA, 15);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CONTACTS, 16);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.EMAIL, 23);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.MAP, 42);
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.PHONE, 108);
        // Must call getAvailabilityStatus() to set the count
        mController.getAvailabilityStatus();

        mController.updateState(mPreference);

        int count = EnterpriseDefaultApps.values().length;
        String summary = mRealContext.getResources()
                .getQuantityString(R.plurals.enterprise_privacy_number_packages, count, count);

        verifyPreferenceTitleNeverSet(mPreference);
        verifyPreferenceSummarySet(mPreference, summary);
        verifyPreferenceIconNeverSet(mPreference);
    }

    private void setEnterpriseSetDefaultApps(EnterpriseDefaultApps typeOfDefault, int number) {
        Intent[] intents = typeOfDefault.getIntents();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = "app";
        for (int i = 0; i < number; i++) {
            List<UserAppInfo> apps = new ArrayList<>(number);
            apps.add(new UserAppInfo(mMyUser, appInfo));
            when(mApplicationFeatureProvider.findPersistentPreferredActivities(eq(mMyUser.id),
                    argThat(matchesIntents(intents)))).thenReturn(apps);
        }
    }
}
