/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.settings.profiles;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public final class ProfileDetailsEndSessionPreferenceControllerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private CarUiPreference mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private ProfileDetailsEndSessionPreferenceController mPreferenceController;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private DevicePolicyManager mDpm;
    @Mock
    private UserHandle mUserHandle;
    @Mock
    private ProfileHelper mProfileHelper;
    @Mock
    private UserInfo mUserInfo;

    @Before
    @UiThreadTest
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(ProfileHelper.class, withSettings().lenient())
                .startMocking();

        ExtendedMockito.when(ProfileHelper.getInstance(mContext)).thenReturn(mProfileHelper);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiPreference(mContext);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getAvailabilityStatus_nullDpm_unsupportedOnDevice() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(null);
        initPreferenceController();
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_nullDpm_unsupportedOnDevice_zoneWrite() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(null);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_nullDpm_unsupportedOnDevice_zoneRead() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(null);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_nullDpm_unsupportedOnDevice_zoneHidden() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(null);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_logoutDisabled_unsupportedOnDevice() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(false);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_logoutDisabled_unsupportedOnDevice_zoneWrite() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(false);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_logoutDisabled_unsupportedOnDevice_zoneRead() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(false);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_logoutDisabled_unsupportedOnDevice_zoneHidden() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(false);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noLogoutUser_unsupportedOnDevice() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(null);
        initPreferenceController();
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noLogoutUser_unsupportedOnDevice_zoneWrite() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(null);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noLogoutUser_unsupportedOnDevice_zoneRead() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(null);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noLogoutUser_unsupportedOnDevice_zoneHidden() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(null);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_logoutEnabled_hasLogoutUser_available() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_logoutEnabled_hasLogoutUser_available_zoneWrite() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_logoutEnabled_hasLogoutUser_available_zoneRead() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_logoutEnabled_hasLogoutUser_available_zoneHidden() {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mDpm.isLogoutEnabled()).thenReturn(true);
        when(mDpm.getLogoutUser()).thenReturn(mUserHandle);
        initPreferenceController();
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onClick_logoutProfile() {
        initPreferenceController();
        mPreferenceController.handlePreferenceClicked(mPreference);
        verify(mProfileHelper).logoutProfile();
    }

    private void initPreferenceController() {
        mPreferenceController = new ProfileDetailsEndSessionPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController, mCarUxRestrictions);
        mPreferenceController.setUserInfo(mUserInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }
}
