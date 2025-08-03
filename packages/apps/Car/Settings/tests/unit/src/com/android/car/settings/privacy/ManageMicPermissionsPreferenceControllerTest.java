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

package com.android.car.settings.privacy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionControllerManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.utils.StringUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ManageMicPermissionsPreferenceControllerTest {

    private final Context mContext = Mockito.spy(ApplicationProvider.getApplicationContext());

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private SensorPrivacyManager mMockSensorPrivacyManager;
    @Mock
    private UserManager mUserManager;
    @Mock
    private UserHandle mUserHandle1;
    @Mock
    private UserHandle mUserHandle2;
    @Captor
    private ArgumentCaptor<SensorPrivacyManager.OnSensorPrivacyChangedListener> mListener;

    private ManageMicPermissionsPreferenceController mPreferenceController;
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new Preference(mContext);
        screen.addPreference(mPreference);
        mPreferenceController = new ManageMicPermissionsPreferenceController(mContext,
                "key", mFragmentController, carUxRestrictions,
                mMockSensorPrivacyManager, mUserManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        when(mContext.getPackageName()).thenReturn("test.package");
        when(mUserHandle1.getIdentifier()).thenReturn(1);
        when(mUserHandle1.getIdentifier()).thenReturn(2);
    }

    @Test
    public void getSummary_whenMicrophoneIsOff_shouldReturnStringForOff() throws Exception {
        setIsSensorPrivacyEnabled(true);
        initializePreference();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.microphone_app_permission_summary_microphone_off));
    }

    @Test
    public void getSummary_whenLocationIsOn_shouldReturnLoadingString() throws Exception {
        setIsSensorPrivacyEnabled(false);
        initializePreference();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.microphone_settings_loading_app_permission_stats));
    }

    @Test
    public void getSummary_whenLocationAppCountIsOne_shouldShowSingularString() throws Exception {
        setIsSensorPrivacyEnabled(false);

        when(mUserManager.getUserProfiles()).thenReturn(List.of(mUserHandle1));
        PermissionControllerManager user1PermissionControllerManager = setupUser(mUserHandle1);
        initializePreference();
        setAppsForUser(1, 1, user1PermissionControllerManager);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", 1);
        arguments.put("total_count", 1);

        assertThat(mPreference.getSummary()).isEqualTo(StringUtil.getIcuPluralsString(mContext,
                arguments, R.string.microphone_app_permission_summary_microphone_on));
    }

    @Test
    public void getSummary_whenLocationAppCountIsGreaterThanOne_shouldShowPluralString()
            throws Exception {
        setIsSensorPrivacyEnabled(false);

        when(mUserManager.getUserProfiles()).thenReturn(List.of(mUserHandle1));
        PermissionControllerManager user1PermissionControllerManager = setupUser(mUserHandle1);
        initializePreference();
        setAppsForUser(5, 10, user1PermissionControllerManager);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", 5);
        arguments.put("total_count", 10);

        assertThat(mPreference.getSummary()).isEqualTo(StringUtil.getIcuPluralsString(mContext,
                arguments, R.string.microphone_app_permission_summary_microphone_on));
    }

    @Test
    public void getSummary_multipleUsers_addsAppCounts()
            throws Exception {
        setIsSensorPrivacyEnabled(false);

        when(mUserManager.getUserProfiles()).thenReturn(List.of(mUserHandle1, mUserHandle2));
        PermissionControllerManager user1PermissionControllerManager = setupUser(mUserHandle1);
        PermissionControllerManager user2PermissionControllerManager = setupUser(mUserHandle2);
        initializePreference();
        setAppsForUser(2, 4, user1PermissionControllerManager);

        // verify that loading state persists until all callbacks have returned
        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.microphone_settings_loading_app_permission_stats));

        setAppsForUser(1, 1, user2PermissionControllerManager);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", 3);
        arguments.put("total_count", 5);

        assertThat(mPreference.getSummary()).isEqualTo(StringUtil.getIcuPluralsString(mContext,
                arguments, R.string.microphone_app_permission_summary_microphone_on));
    }

    @Test
    public void getSummary_initializeTwiceBeforeCallbacksReturn_callbacksOnlyCalledOnce()
            throws Exception {
        setIsSensorPrivacyEnabled(false);

        when(mUserManager.getUserProfiles()).thenReturn(List.of(mUserHandle1));
        PermissionControllerManager user1PermissionControllerManager = setupUser(mUserHandle1);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onCreate(mLifecycleOwner);

        // verifies that callbacks are only called once due to the loading state
        setAppsForUser(2, 4, user1PermissionControllerManager);
    }

    private PermissionControllerManager setupUser(UserHandle userHandle)
            throws Exception {
        Context userContext = mock(Context.class);
        doReturn(userContext).when(mContext).createPackageContextAsUser(anyString(),
                anyInt(), eq(userHandle));
        PermissionControllerManager permissionControllerManager = mock(
                PermissionControllerManager.class);
        when(userContext.getSystemService(PermissionControllerManager.class)).thenReturn(
                permissionControllerManager);
        return permissionControllerManager;
    }

    private void setAppsForUser(int grantedCount, int totalCount,
            PermissionControllerManager permissionControllerManagerForUser) {
        ArgumentCaptor<PermissionControllerManager.OnCountPermissionAppsResultCallback>
                permissionAllAppsCountCallback = ArgumentCaptor.forClass(
                PermissionControllerManager.OnCountPermissionAppsResultCallback.class);
        ArgumentCaptor<PermissionControllerManager.OnCountPermissionAppsResultCallback>
                permissionAccessCountCallback = ArgumentCaptor.forClass(
                PermissionControllerManager.OnCountPermissionAppsResultCallback.class);
        verify(permissionControllerManagerForUser).countPermissionApps(
                eq(Collections.singletonList(Manifest.permission.RECORD_AUDIO)), eq(/* flags= */ 0),
                permissionAllAppsCountCallback.capture(), eq(null));
        verify(permissionControllerManagerForUser).countPermissionApps(
                eq(Collections.singletonList(Manifest.permission.RECORD_AUDIO)),
                eq(PermissionControllerManager.COUNT_ONLY_WHEN_GRANTED),
                permissionAccessCountCallback.capture(), eq(null));
        permissionAllAppsCountCallback.getValue().onCountPermissionApps(totalCount);
        permissionAccessCountCallback.getValue().onCountPermissionApps(grantedCount);
    }

    private void initializePreference() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        verify(mMockSensorPrivacyManager).addSensorPrivacyListener(
                eq(SensorPrivacyManager.Sensors.MICROPHONE), mListener.capture());
    }

    private void setIsSensorPrivacyEnabled(boolean isMuted) {
        when(mMockSensorPrivacyManager.isSensorPrivacyEnabled(
                eq(SensorPrivacyManager.Sensors.MICROPHONE))).thenReturn(isMuted);
    }
}
