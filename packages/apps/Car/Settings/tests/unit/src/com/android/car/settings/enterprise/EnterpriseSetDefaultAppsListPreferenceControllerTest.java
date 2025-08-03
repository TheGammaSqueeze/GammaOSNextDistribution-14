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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.UserInfo;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.test.annotation.UiThreadTest;

import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.BaseEnterprisePreferenceControllerTestCase.DummyPreferenceGroup;
import com.android.car.settingslib.applications.UserAppInfo;
import com.android.car.settingslib.enterprise.EnterpriseDefaultApps;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class EnterpriseSetDefaultAppsListPreferenceControllerTest
        extends BaseEnterprisePrivacyPreferenceControllerTestCase {

    private static final String TAG = EnterpriseSetDefaultAppsListPreferenceControllerTest.class
            .getSimpleName();

    private UserInfo mMyUser;
    private EnterpriseSetDefaultAppsListPreferenceController mController;

    private DummyPreferenceGroup mPreferenceGroup;

    @Before
    @UiThreadTest // Needed to instantiate DummyPreferenceGroup
    public void setUp() throws Exception {
        mController = new EnterpriseSetDefaultAppsListPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions, mApplicationFeatureProvider);
        int userId = mSpiedContext.getUserId();
        mMyUser = new UserInfo(mSpiedContext.getUserId(), "Mambo Number " + userId, /* flags= */ 0);
        Log.d(TAG, "myUser: " + mMyUser.toFullString());
        mPreferenceGroup = new DummyPreferenceGroup(mSpiedContext);
    }

    @Test
    public void testGetPreferenceType() {
        assertWithMessage("preference type").that(mController.getPreferenceType())
                .isEqualTo(PreferenceGroup.class);
    }

    @Test
    public void testGetAvailabilityStatus_none() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        // Don't need to mock anything

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_none_zoneWrite() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        // Don't need to mock anything
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_none_zoneRead() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        // Don't need to mock anything
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_none_zoneHidden() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        // Don't need to mock anything
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_one() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_one_zoneWrite() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_one_zoneRead() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_one_zoneHidden() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_cached() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testGetAvailabilityStatus_cached_zoneWrite() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testGetAvailabilityStatus_cached_zoneRead() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testGetAvailabilityStatus_cached_zoneHidden() throws Exception {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app");
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER);
        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        verifyFindPersistentPreferredActivitiesCalledOnce();
    }

    @Test
    public void testUpdateState_allSingleApps() {
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "app_4");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CALENDAR, "app_8");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CAMERA, "app_15");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CONTACTS, "app_16");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.EMAIL, "app_23");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.MAP, "app_42");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.PHONE, "app_108");
        // Must call getAvailabilityStatus() to fetch the list
        mController.getAvailabilityStatus();

        mController.updateState(mPreferenceGroup);

        List<Preference> prefs = mPreferenceGroup.getPreferences();
        assertWithMessage("children preferences").that(prefs).hasSize(7);

        // NOTE: must be called in the same order as the EnterpriseDefaultApp enums
        assertPreference(prefs, 0, getSingleAppsTitle(R.string.default_browser_title), "app_4");
        assertPreference(prefs, 1, getSingleAppsTitle(R.string.default_calendar_app_title),
                "app_8");
        assertPreference(prefs, 2, getMultipleAppsTitle(R.plurals.default_camera_app_title, 1),
                "app_15");
        assertPreference(prefs, 3, getSingleAppsTitle(R.string.default_contacts_app_title),
                "app_16");
        assertPreference(prefs, 4, getMultipleAppsTitle(R.plurals.default_email_app_title, 1),
                "app_23");
        assertPreference(prefs, 5, getSingleAppsTitle(R.string.default_map_app_title), "app_42");
        assertPreference(prefs, 6, getMultipleAppsTitle(R.plurals.default_phone_app_title, 1),
                "app_108");
    }

    @Test
    public void testUpdateState_allMultipleApps() {
        // NOTE: uses "s.." on apps that are single, and "m..." on apps that are multiple
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.BROWSER, "s1", "s2");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CALENDAR, "s1", "s2", "s3");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CAMERA, "m1", "m2");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.CONTACTS, "s1", "s2", "s3", "s4");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.EMAIL, "m1", "m2", "m3");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.MAP, "s1", "s2", "s3", "s4");
        setEnterpriseSetDefaultApps(EnterpriseDefaultApps.PHONE, "m1", "m2", "m3", "m4");
        // Must call getAvailabilityStatus() to fetch the list
        mController.getAvailabilityStatus();

        mController.updateState(mPreferenceGroup);

        List<Preference> prefs = mPreferenceGroup.getPreferences();
        assertWithMessage("children preferences").that(prefs).hasSize(7);

        // NOTE: must be called in the same order as the EnterpriseDefaultApp enums
        assertPreference(prefs, 0, getSingleAppsTitle(R.string.default_browser_title), "s1", "s2");
        assertPreference(prefs, 1, getSingleAppsTitle(R.string.default_calendar_app_title), "s1",
                "s2", "s3");
        assertPreference(prefs, 2, getMultipleAppsTitle(R.plurals.default_camera_app_title, 2),
                "m1", "m2");
        assertPreference(prefs, 3, getSingleAppsTitle(R.string.default_contacts_app_title), "s1",
                "s2", "s3", "s4");
        assertPreference(prefs, 4, getMultipleAppsTitle(R.plurals.default_email_app_title, 3),
                "m1", "m2", "m3");
        assertPreference(prefs, 5, getSingleAppsTitle(R.string.default_map_app_title), "s1", "s2",
                "s3", "s4");
        assertPreference(prefs, 6, getMultipleAppsTitle(R.plurals.default_phone_app_title, 4),
                "m1", "m2", "m3"); // caps at 3, so m4 is ignored
    }

    private void assertPreference(List<Preference> prefs, int index, CharSequence expectedTitle,
            CharSequence... expectedApps) {
        CharSequence expectedSummary = getSummary(expectedApps);
        Preference pref = prefs.get(index);
        Log.d(TAG, "assertPreference() at index #" + index + ": pref=" + pref
                + " expectedTitle: " + expectedTitle
                + " expectedSummary: " + expectedSummary);

        assertWithMessage("title of preference #%s", index).that(pref.getTitle())
                .isEqualTo(expectedTitle);
        assertWithMessage("summary of preference #%s", index).that(pref.getSummary())
                .isEqualTo(expectedSummary);
    }

    /**
     * Gets the title for a default app type that can only have one app.
     */
    private CharSequence getSingleAppsTitle(int resId) {
        return mRealContext.getString(resId);
    }

    /**
     * Gets the title for a default app type that can have multiple apps.
     */
    private CharSequence getMultipleAppsTitle(int resId, int numberApps) {
        return mRealContext.getResources().getQuantityString(resId, numberApps);
    }

    private CharSequence getSummary(CharSequence[] appNames) {
        switch (appNames.length) {
            case 1:
                return appNames[0];
            case 2:
                return mRealContext.getString(R.string.app_names_concatenation_template_2,
                        appNames[0], appNames[1]);
            default:
                return mRealContext.getString(R.string.app_names_concatenation_template_3,
                        appNames[0], appNames[1], appNames[2]);
        }
    }

    private void setEnterpriseSetDefaultApps(EnterpriseDefaultApps typeOfDefault,
            String... appNames) {
        Intent[] intents = typeOfDefault.getIntents();
        int size = appNames.length;
        List<UserAppInfo> apps = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String appName = appNames[i];
            ApplicationInfo appInfo = new ApplicationInfo();
            appInfo.packageName = appName;
            apps.add(new UserAppInfo(mMyUser, appInfo));
        }
        Log.d(TAG, "mocking " + size + " apps for " + typeOfDefault + ": " + apps);
        when(mApplicationFeatureProvider.findPersistentPreferredActivities(eq(mMyUser.id),
                argThat(matchesIntents(intents)))).thenReturn(apps);
    }
}
