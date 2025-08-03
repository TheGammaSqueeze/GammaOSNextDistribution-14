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

package com.android.car.settings.applications.specialaccess;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MoreSpecialAccessPreferenceControllerTest {

    private static final String PACKAGE = "test.package";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUiPreference mPreference;
    private Intent mIntent;
    private ResolveInfo mResolveInfo;
    private MoreSpecialAccessPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private PackageManager mMockPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mIntent = new Intent(Intent.ACTION_MANAGE_SPECIAL_APP_ACCESSES);
        mIntent.setPackage(PACKAGE);

        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = PACKAGE;
        applicationInfo.name = "TestClass";
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;

        mResolveInfo = new ResolveInfo();
        mResolveInfo.activityInfo = activityInfo;
    }

    @Test
    public void getAvailabilityStatus_noPermissionController_returnsUnsupportedOnDevice() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(null);
        setUpPreferenceController();
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noPermissionController_zoneWrite() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(null);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noPermissionController_zoneRead() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(null);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noPermissionController_zoneHidden() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(null);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noResolvedActivity_returnsUnsupportedOnDevice() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(null);
        setUpPreferenceController();

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noResolvedActivity_returnsUnsupportedOnDevice_zoneWrite() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(null);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noResolvedActivity_returnsUnsupportedOnDevice_zoneRead() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(null);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noResolvedActivity_returnsUnsupportedOnDevice_zoneHidden() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(null);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_resolvedActivity_returnsAvailable() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(mResolveInfo);
        setUpPreferenceController();

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_resolvedActivity_returnsAvailable_zoneWrite() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(mResolveInfo);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_resolvedActivity_returnsAvailable_zoneRead() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(mResolveInfo);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_resolvedActivity_returnsAvailable_zoneHidden() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(mResolveInfo);
        setUpPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void preferenceClicked_startsResolvedActivity() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(PACKAGE);
        when(mMockPackageManager.resolveActivity(any(), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(mResolveInfo);
        doNothing().when(mContext).startActivity(any());
        setUpPreferenceController();

        mPreference.performClick();


        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());
        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MANAGE_SPECIAL_APP_ACCESSES);
        assertThat(intent.getPackage()).isEqualTo(PACKAGE);
    }

    private void setUpPreferenceController() {
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new MoreSpecialAccessPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockPackageManager);

        mPreference = new CarUiPreference(mContext);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }
}
