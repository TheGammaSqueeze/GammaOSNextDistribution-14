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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import android.util.Log;

import androidx.preference.Preference;

import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.car.settingslib.applications.ApplicationFeatureProvider.NumberOfAppsCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

abstract class BaseAdminGrantedPermissionsPreferenceControllerTestCase
        <C extends BaseAdminGrantedPermissionsPreferenceController> extends
        BaseEnterprisePrivacyPreferenceControllerTestCase {

    private static final String TAG = BaseAdminGrantedPermissionsPreferenceControllerTestCase.class
            .getSimpleName();

    // Must be a spy to verify refreshUi() is called
    private C mSpiedController;

    protected final String[] mPermissions;

    @Mock
    private Preference mPreference;

    BaseAdminGrantedPermissionsPreferenceControllerTestCase(String... permissions) {
        mPermissions = permissions;
    }

    @Before
    public void setController() {
        mSpiedController = spy(newController(mApplicationFeatureProvider));
    }

    protected abstract C newController(ApplicationFeatureProvider provider);

    @Test
    public void testGetAvailabilityStatus_noPermissionsGranted() {
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(0);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_noPermissionsGranted_zoneWrite() {
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();
        mSpiedController.setAvailabilityStatusForZone("write");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(0);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_noPermissionsGranted_zoneRead() {
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();
        mSpiedController.setAvailabilityStatusForZone("read");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(0);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_noPermissionsGranted_zoneHidden() {
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();
        mSpiedController.setAvailabilityStatusForZone("hidden");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(0);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
        assertUiNotRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_permissionsGranted() {
        expectUiRefreshed(mSpiedController);
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(42);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                AVAILABLE);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_permissionsGranted_zoneWrite() {
        expectUiRefreshed(mSpiedController);
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();
        mSpiedController.setAvailabilityStatusForZone("write");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(42);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                AVAILABLE);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_permissionsGranted_zoneRead() {
        expectUiRefreshed(mSpiedController);
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();
        mSpiedController.setAvailabilityStatusForZone("read");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(42);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testGetAvailabilityStatus_permissionsGranted_zoneHidden() {
        expectUiRefreshed(mSpiedController);
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();
        mSpiedController.setAvailabilityStatusForZone("hidden");

        // Assert initial state
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);

        // Unblock async call
        callbackHolder.release(42);

        // Assert post-callback result
        PreferenceControllerTestUtil.assertAvailability(mSpiedController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
        assertUiRefreshed(mSpiedController);
    }

    @Test
    public void testUpdateState() {
        expectUiRefreshed(mSpiedController);
        NumberOfAppsCallbackHolder callbackHolder =
                mockCalculateNumberOfAppsWithAdminGrantedPermissions();
        mSpiedController.getAvailabilityStatus();
        callbackHolder.release(42);
        assertUiRefreshed(mSpiedController);

        mSpiedController.updateState(mPreference);

        assertPreferenceStateSet(mPreference, 42);
    }

    private NumberOfAppsCallbackHolder mockCalculateNumberOfAppsWithAdminGrantedPermissions() {
        NumberOfAppsCallbackHolder callbackHolder = new NumberOfAppsCallbackHolder();

        doAnswer((inv) -> {
            Log.d(TAG, "answering to " + inv);
            NumberOfAppsCallback callback = (NumberOfAppsCallback) inv.getArguments()[2];
            callbackHolder.setCallback(callback);
            return null;
        }).when(mApplicationFeatureProvider)
                .calculateNumberOfAppsWithAdminGrantedPermissions(eq(mPermissions),
                        /* sync= */ eq(true), any());
        return callbackHolder;
    }
}
