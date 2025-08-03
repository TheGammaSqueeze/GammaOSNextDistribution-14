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

package com.android.car.settings.location;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PackageTagsList;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionTextPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class AdasPrivacyPolicyDisclosurePreferenceControllerTest {
    private final Context mContext = Mockito.spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private LogicalPreferenceGroup mPreference;
    private AdasPrivacyPolicyDisclosurePreferenceController mPreferenceController;

    @Mock private FragmentController mFragmentController;
    @Mock private PackageManager mPackageManager;
    @Mock private LocationManager mLocationManager;

    @Before
    @UiThreadTest
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        CarUxRestrictions carUxRestrictions =
                new CarUxRestrictions.Builder(
                                /* reqOpt= */ true,
                                CarUxRestrictions.UX_RESTRICTIONS_BASELINE,
                                /* timestamp= */ 0)
                        .build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreference);
        mPreferenceController =
                new AdasPrivacyPolicyDisclosurePreferenceController(
                        mContext,
                        "key",
                        mFragmentController,
                        carUxRestrictions,
                        mPackageManager,
                        mLocationManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        doNothing().when(mContext).startActivity(any());

        initializePreference();
    }

    @Test
    public void refreshUi_oneAdasApp_showApp() {
        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void refreshUi_adasAppWithLocationPermission_launchLocationSettings() throws Exception {
        mPreferenceController.refreshUi();
        mPreference.getPreference(0).performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MANAGE_APP_PERMISSION);
        assertThat(intent.getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME))
                .isEqualTo(Manifest.permission_group.LOCATION);
    }

    @Test
    public void refreshUi_adasAppWithLocationPermission_launchPrivacyPolicy() throws Exception {
        mPreferenceController.refreshUi();
        CarUiTwoActionTextPreference perf =
                (CarUiTwoActionTextPreference) mPreference.getPreference(0);

        perf.performSecondaryActionClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_VIEW);
    }

    @Test
    public void refreshUi_privacyPolicyMissing_throwsNoException() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        Bundle bundle = new Bundle();
        appInfo.metaData = bundle;
        when(mPackageManager.getApplicationInfoAsUser(any(), anyInt(), anyInt()))
                .thenReturn(appInfo);

        mPreferenceController.refreshUi();
        CarUiTwoActionTextPreference perf =
                (CarUiTwoActionTextPreference) mPreference.getPreference(0);

        perf.performSecondaryActionClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, never()).startActivity(captor.capture());
    }

    @Test
    public void refreshUi_metaDataMissing_throwsNoException() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        when(mPackageManager.getApplicationInfoAsUser(any(), anyInt(), anyInt()))
                .thenReturn(appInfo);

        mPreferenceController.refreshUi();
        CarUiTwoActionTextPreference perf =
                (CarUiTwoActionTextPreference) mPreference.getPreference(0);

        perf.performSecondaryActionClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, never()).startActivity(captor.capture());
    }

    @Test
    public void refreshUi_throwsNoException() throws Exception {
        when(mPackageManager.getApplicationInfoAsUser(any(), anyInt(), anyInt()))
                .thenThrow(new NameNotFoundException());

        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
    }

    private void initializePreference() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        Bundle bundle = new Bundle();
        appInfo.metaData = bundle;
        bundle.putCharSequence("privacy_policy", "test App Url");
        when(mPackageManager.getApplicationInfoAsUser(any(), anyInt(), anyInt()))
                .thenReturn(appInfo);

        PackageTagsList list = new PackageTagsList.Builder().add("testApp").build();
        when(mLocationManager.getAdasAllowlist()).thenReturn(list);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
    }
}
