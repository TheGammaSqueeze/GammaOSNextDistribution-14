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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import android.util.Log;

import androidx.preference.PreferenceGroup;
import androidx.test.annotation.UiThreadTest;

import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.car.settingslib.applications.ApplicationFeatureProvider.ListOfAppsCallback;

import org.junit.Before;
import org.junit.Test;

public class EnterpriseInstalledPackagesListPreferenceControllerTest extends
        BaseEnterprisePrivacyPreferenceControllerTestCase {

    private static final String TAG = EnterpriseInstalledPackagesListPreferenceControllerTest.class
            .getSimpleName();

    // Must be a spy to verify refreshUi() is called
    private EnterpriseInstalledPackagesListPreferenceController mSpiedController;

    private DummyPreferenceGroup mPreferenceGroup;

    @Before
    @UiThreadTest // Needed to instantiate DummyPreferenceGroup
    public void setExtraFixtures() {
        mSpiedController = spy(newController(mApplicationFeatureProvider));
        mPreferenceGroup = new DummyPreferenceGroup(mSpiedContext);
    }

    @Test
    public void testGetPreferenceType() {
        assertWithMessage("preference type").that(mSpiedController.getPreferenceType())
                .isEqualTo(PreferenceGroup.class);
    }

    @Test
    public void testGetAvailabilityStatus_noInstalledApps() {
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release();

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_noInstalledApps_zoneWrite() {
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();
        mSpiedController.setAvailabilityStatusForZone("write");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release();

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_noInstalledApps_zoneRead() {
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();
        mSpiedController.setAvailabilityStatusForZone("read");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release();

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_noInstalledApps_zoneHidden() {
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();
        mSpiedController.setAvailabilityStatusForZone("hidden");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release();

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_withInstalledApps() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(newUserAppInfo("foo"), newUserAppInfo("bar"));

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                AVAILABLE);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_withInstalledApps_zoneWrite() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();
        mSpiedController.setAvailabilityStatusForZone("write");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(newUserAppInfo("foo"), newUserAppInfo("bar"));

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                AVAILABLE);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_withInstalledApps_zoneRead() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();
        mSpiedController.setAvailabilityStatusForZone("read");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(newUserAppInfo("foo"), newUserAppInfo("bar"));

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_withInstalledApps_zoneHidden() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();
        mSpiedController.setAvailabilityStatusForZone("hidden");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(newUserAppInfo("foo"), newUserAppInfo("bar"));

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testUpdateState() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListPolicyInstalledApps();
        mSpiedController.getAvailabilityStatus();
        callbackHolder.release(newUserAppInfo("foo"), newUserAppInfo("bar"));
        assertUiRefreshed(mSpiedController);

        mSpiedController.updateState(mPreferenceGroup);

        assertPreferenceGroupStateSet(mPreferenceGroup, "foo", "bar");
    }

    private EnterpriseInstalledPackagesListPreferenceController newController(
            ApplicationFeatureProvider provider) {
        return new EnterpriseInstalledPackagesListPreferenceController(mSpiedContext,
                mPreferenceKey, mFragmentController, mUxRestrictions, provider);
    }

    private ListOfAppsCallbackHolder mockListPolicyInstalledApps() {
        ListOfAppsCallbackHolder callbackHolder = new ListOfAppsCallbackHolder();

        doAnswer((inv) -> {
            Log.d(TAG, "answering to " + inv);
            ListOfAppsCallback callback = (ListOfAppsCallback) inv.getArguments()[0];
            callbackHolder.setCallback(callback);
            return null;
        }).when(mApplicationFeatureProvider).listPolicyInstalledApps(any());
        return callbackHolder;
    }
}
