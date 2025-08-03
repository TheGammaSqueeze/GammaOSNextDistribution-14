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

package com.android.car.settings.applications.appinfo;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_DEFAULT;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED;
import static android.provider.DeviceConfig.NAMESPACE_APP_HIBERNATION;

import static com.android.car.settings.applications.ApplicationsUtils.PROPERTY_APP_HIBERNATION_ENABLED;
import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.DeviceConfig;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class HibernationSwitchPreferenceControllerTest {
    private static final int PACKAGE_UID = 1;
    private static final String INVALID_PACKAGE_NAME = "invalid_package";
    private static final String KEY = "key";
    private static final String VALID_PACKAGE_NAME = "package";
    private static final String EXEMPTED_PACKAGE_NAME = "exempted_package";
    private static final String UNEXEMPTED_PACKAGE_NAME = "unexempted_package";
    @Mock
    private FragmentController mFragmentController;
    @Mock
    private AppOpsManager mAppOpsManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private SwitchPreference mPreference;

    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private HibernationSwitchPreferenceController mController;
    private Context mContext;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        mLifecycleOwner = new TestLifecycleOwner();
        mContext = spy(ApplicationProvider.getApplicationContext());

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        when(mContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManager);
        when(mPackageManager.getPackageUid(eq(VALID_PACKAGE_NAME), anyInt()))
                .thenReturn(PACKAGE_UID);
        when(mPackageManager.getPackageUid(eq(INVALID_PACKAGE_NAME), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());
        when(mPackageManager.getTargetSdkVersion(eq(EXEMPTED_PACKAGE_NAME)))
                .thenReturn(android.os.Build.VERSION_CODES.Q);
        when(mPackageManager.getTargetSdkVersion(eq(UNEXEMPTED_PACKAGE_NAME)))
                .thenReturn(android.os.Build.VERSION_CODES.S);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, PROPERTY_APP_HIBERNATION_ENABLED,
                /* value= */ "true", /* makeDefault= */ true);
        mController = new HibernationSwitchPreferenceController(
                mContext, KEY, mFragmentController, mCarUxRestrictions);
    }

    @Test
    public void getAvailabilityStatus_featureNotEnabled_shouldNotReturnAvailable() {
        mController.setPackageName(VALID_PACKAGE_NAME);
        DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, PROPERTY_APP_HIBERNATION_ENABLED,
                /* value= */ "false", /* makeDefault= */ true);

        assertThat(mController.getAvailabilityStatus()).isNotEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_featureNotEnabled_shouldNotReturnAvailable_zoneWrite() {
        mController.setAvailabilityStatusForZone("write");
        mController.setPackageName(VALID_PACKAGE_NAME);
        DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, PROPERTY_APP_HIBERNATION_ENABLED,
                /* value= */ "false", /* makeDefault= */ true);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_featureNotEnabled_shouldNotReturnAvailable_zoneRead() {
        mController.setAvailabilityStatusForZone("read");
        mController.setPackageName(VALID_PACKAGE_NAME);
        DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, PROPERTY_APP_HIBERNATION_ENABLED,
                /* value= */ "false", /* makeDefault= */ true);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_featureNotEnabled_shouldNotReturnAvailable_zoneHidden() {
        mController.setAvailabilityStatusForZone("hidden");
        mController.setPackageName(VALID_PACKAGE_NAME);
        DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, PROPERTY_APP_HIBERNATION_ENABLED,
                /* value= */ "false", /* makeDefault= */ true);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_invalidPackage_shouldReturnNotAvailable() {
        mController.setPackageName(INVALID_PACKAGE_NAME);

        assertThat(mController.getAvailabilityStatus()).isNotEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_invalidPackage_shouldReturnNotAvailable_zoneWrite() {
        mController.setAvailabilityStatusForZone("write");
        mController.setPackageName(INVALID_PACKAGE_NAME);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_invalidPackage_shouldReturnNotAvailable_zoneRead() {
        mController.setAvailabilityStatusForZone("read");
        mController.setPackageName(INVALID_PACKAGE_NAME);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_invalidPackage_shouldReturnNotAvailable_zoneHidden() {
        mController.setAvailabilityStatusForZone("hidden");
        mController.setPackageName(INVALID_PACKAGE_NAME);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_validPackage_shouldReturnAvailable() {
        mController.setPackageName(VALID_PACKAGE_NAME);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_validPackage_shouldReturnAvailable_zoneWrite() {
        mController.setAvailabilityStatusForZone("write");
        mController.setPackageName(VALID_PACKAGE_NAME);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_validPackage_shouldReturnAvailable_zoneRead() {
        mController.setAvailabilityStatusForZone("read");
        mController.setPackageName(VALID_PACKAGE_NAME);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_validPackage_shouldReturnAvailable_zoneHidden() {
        mController.setAvailabilityStatusForZone("hidden");
        mController.setPackageName(VALID_PACKAGE_NAME);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void updateState_exemptedByDefaultPackage_shouldNotCheck() {
        assignPreference();
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                eq(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED), anyInt(), eq(EXEMPTED_PACKAGE_NAME)))
                .thenReturn(MODE_DEFAULT);
        mController.setPackageName(EXEMPTED_PACKAGE_NAME);

        mController.onCreate(mLifecycleOwner);

        verify(mPreference).setChecked(false);
    }

    @Test
    public void updateState_exemptedPackageOverrideByUser_shouldCheck() {
        assignPreference();
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                eq(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED), anyInt(), eq(EXEMPTED_PACKAGE_NAME)))
                .thenReturn(MODE_ALLOWED);
        mController.setPackageName(EXEMPTED_PACKAGE_NAME);

        mController.onCreate(mLifecycleOwner);

        verify(mPreference).setChecked(true);
    }

    @Test
    public void updateState_unexemptedPackageOverrideByUser_shouldNotCheck() {
        assignPreference();
        when(mAppOpsManager.unsafeCheckOpNoThrow(
                eq(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED), anyInt(), eq(UNEXEMPTED_PACKAGE_NAME)))
                .thenReturn(MODE_IGNORED);
        mController.setPackageName(UNEXEMPTED_PACKAGE_NAME);

        mController.onCreate(mLifecycleOwner);

        verify(mPreference).setChecked(false);
    }

    private void assignPreference() {
        PreferenceControllerTestUtil.assignPreference(mController,
                mPreference);
    }
}

