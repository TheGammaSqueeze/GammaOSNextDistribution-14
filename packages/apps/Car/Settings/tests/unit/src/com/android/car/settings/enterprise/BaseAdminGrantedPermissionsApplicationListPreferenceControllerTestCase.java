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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.Log;

import androidx.preference.PreferenceGroup;
import androidx.test.annotation.UiThreadTest;

import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.car.settingslib.applications.ApplicationFeatureProvider.ListOfAppsCallback;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

abstract class BaseAdminGrantedPermissionsApplicationListPreferenceControllerTestCase
        <C extends BaseAdminGrantedPermissionsApplicationListPreferenceController>
        extends BaseEnterprisePrivacyPreferenceControllerTestCase {

    private static final String TAG =
            BaseAdminGrantedPermissionsApplicationListPreferenceControllerTestCase.class
                    .getSimpleName();

    private static final UserInfo DEFAULT_USER_INFO = new UserInfo(42, "Groot", /* flags= */ 0);

    private static final Map<CharSequence, Drawable> ICONS_BY_LABEL = new ArrayMap<>();

    // Must be a spy to verify refreshUi() is called
    private C mSpiedController;

    protected final String[] mPermissions;

    private DummyPreferenceGroup mPreferenceGroup;

    BaseAdminGrantedPermissionsApplicationListPreferenceControllerTestCase(String... permissions) {
        mPermissions = permissions;
    }

    @Before
    @UiThreadTest // Needed to instantiate DummyPreferenceGroup
    public void setExtraFixtures() {
        mSpiedController = spy(newController(mApplicationFeatureProvider));
        mPreferenceGroup = new DummyPreferenceGroup(mSpiedContext);
    }

    protected abstract C newController(ApplicationFeatureProvider provider);

    @Test
    public void testGetPreferenceType() {
        assertWithMessage("preference type").that(mSpiedController.getPreferenceType())
                .isEqualTo(PreferenceGroup.class);
    }

    @Test
    public void testGetAvailabilityStatus_noPermissionsGranted() {
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();

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
    public void testGetAvailabilityStatus_noPermissionsGranted_zoneWrite() {
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();
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
    public void testGetAvailabilityStatus_noPermissionsGranted_zoneRead() {
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();
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
    public void testGetAvailabilityStatus_noPermissionsGranted_zoneHidden() {
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();
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
    public void testGetAvailabilityStatus_permissionsGranted() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();

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
    public void testGetAvailabilityStatus_permissionsGranted_zoneWrite() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();
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
    public void testGetAvailabilityStatus_permissionsGranted_zoneRead() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();
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
    public void testGetAvailabilityStatus_permissionsGranted_zoneHidden() {
        expectUiRefreshed(mSpiedController);
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();
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
        ListOfAppsCallbackHolder callbackHolder = mockListAppsWithAdminGrantedPermissions();
        mSpiedController.getAvailabilityStatus();
        callbackHolder.release(newUserAppInfo("foo"), newUserAppInfo("bar"));
        assertUiRefreshed(mSpiedController);

        mSpiedController.updateState(mPreferenceGroup);

        assertPreferenceGroupStateSet(mPreferenceGroup, "foo", "bar");
    }

    private ListOfAppsCallbackHolder mockListAppsWithAdminGrantedPermissions() {
        ListOfAppsCallbackHolder callbackHolder = new ListOfAppsCallbackHolder();

        doAnswer((inv) -> {
            Log.d(TAG, "answering to " + inv);
            ListOfAppsCallback callback = (ListOfAppsCallback) inv.getArguments()[1];
            callbackHolder.setCallback(callback);
            return null;
        }).when(mApplicationFeatureProvider)
            .listAppsWithAdminGrantedPermissions(eq(mPermissions), any());
        return callbackHolder;
    }
}
