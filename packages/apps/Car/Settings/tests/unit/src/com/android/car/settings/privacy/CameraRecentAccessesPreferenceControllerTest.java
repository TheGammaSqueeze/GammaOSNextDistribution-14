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

package com.android.car.settings.privacy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.hardware.SensorPrivacyManager;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.RecentAppOpsAccess;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CameraRecentAccessesPreferenceControllerTest {
    private static final int RECENT_APPS_MAX_COUNT = 2;

    private final Context mContext = Mockito.spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceCategory mPreference;
    private CameraRecentAccessesPreferenceController mPreferenceController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private RecentAppOpsAccess mRecentCameraAccesses;
    @Mock
    private SensorPrivacyManager mMockSensorPrivacyManager;
    @Captor
    private ArgumentCaptor<SensorPrivacyManager.OnSensorPrivacyChangedListener> mListener;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new PreferenceCategory(mContext);
        screen.addPreference(mPreference);
        mPreferenceController = new CameraRecentAccessesPreferenceController(mContext,
                "key", mFragmentController, carUxRestrictions,
                mRecentCameraAccesses, RECENT_APPS_MAX_COUNT, mMockSensorPrivacyManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        doNothing().when(mContext).startActivity(any());
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void cameraMuted_preferenceIsHidden() {
        setIsSensorPrivacyEnabled(true);
        initializePreference();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void cameraNotMuted_preferenceIsShown() {
        setIsSensorPrivacyEnabled(false);
        initializePreference();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void onListenerUpdate_cameraEnabled_shouldShow() {
        initializePreference();

        setIsSensorPrivacyEnabled(false);
        mListener.getValue().onSensorPrivacyChanged(SensorPrivacyManager.Sensors.CAMERA, false);

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void onListenerUpdate_cameraDisabled_shouldHide() {
        initializePreference();

        setIsSensorPrivacyEnabled(true);
        mListener.getValue().onSensorPrivacyChanged(SensorPrivacyManager.Sensors.CAMERA, true);

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_noRecentRequests_messageDisplayed() {
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(Collections.emptyList());
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreference(0).getTitle()).isEqualTo(
                mContext.getString(R.string.camera_no_recent_access));
    }

    @Test
    public void refreshUi_noRecentRequests_exceptForSomeRecentSystemAppRequests_showsViewAll() {
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(Collections.emptyList());
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ true))
                .thenReturn(Collections.singletonList(mock(RecentAppOpsAccess.Access.class)));
        mPreferenceController.refreshUi();

        // includes preference for "View all"
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);
        assertThat(mPreference.getPreference(1).getTitle()).isEqualTo(
                mContext.getString(R.string.camera_settings_recent_requests_view_all_title));
    }

    @Test
    @UiThreadTest
    public void refreshUi_clickViewAll_launchesFragment() {
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ true))
                .thenReturn(Collections.singletonList(mock(RecentAppOpsAccess.Access.class)));
        mPreferenceController.refreshUi();

        // click on the "View all" preference
        mPreference.getPreference(1).performClick();
        verify(mFragmentController)
                .launchFragment(any(CameraRecentAccessViewAllFragment.class));
    }

    @Test
    public void refreshUi_noRecentRequests_includingNoSystemAppRequests_doesNotShowViewAll() {
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(Collections.emptyList());
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ true))
                .thenReturn(Collections.emptyList());
        mPreferenceController.refreshUi();

        // no preference for "View all"
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreference.getPreference(0).getTitle()).isEqualTo(
                mContext.getString(R.string.camera_no_recent_access));
    }

    @Test
    public void refreshUi_someRecentRequests_displaysAppInformation() {
        String fakeLabel = "Test app 1";
        RecentAppOpsAccess.Access fakeAccess = new RecentAppOpsAccess.Access("com.test",
                UserHandle.CURRENT, mock(Drawable.class), fakeLabel,
                "fake contentDescription", Clock.systemDefaultZone().millis());
        List<RecentAppOpsAccess.Access> list = Collections.singletonList(fakeAccess);
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreference(0).getTitle()).isEqualTo(fakeLabel);
        assertThat(mPreference.getPreference(0).getSummary().toString()).contains("min. ago");
    }

    @Test
    public void refreshUi_someRecentRequests_preferencesAddedToScreen_capsAtMax() {
        List<RecentAppOpsAccess.Access> list = Arrays.asList(
                mock(RecentAppOpsAccess.Access.class),
                mock(RecentAppOpsAccess.Access.class),
                mock(RecentAppOpsAccess.Access.class));
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(RECENT_APPS_MAX_COUNT);
    }

    @Test
    public void refreshUi_recentRequests_launchCameraSettings() {
        List<RecentAppOpsAccess.Access> list = Collections.singletonList(
                mock(RecentAppOpsAccess.Access.class));
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list);
        mPreferenceController.refreshUi();

        mPreference.getPreference(0).performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MANAGE_APP_PERMISSION);
    }

    @Test
    public void refreshUi_newRecentRequests_listIsUpdated() {
        List<RecentAppOpsAccess.Access> list1 = Collections.singletonList(
                mock(RecentAppOpsAccess.Access.class));
        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list1);

        List<RecentAppOpsAccess.Access> list2 = new ArrayList<>(list1);
        list2.add(mock(RecentAppOpsAccess.Access.class));

        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(list1.size());

        when(mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list2);
        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(list2.size());
    }

    private void initializePreference() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        verify(mMockSensorPrivacyManager).addSensorPrivacyListener(
                eq(SensorPrivacyManager.Sensors.CAMERA), mListener.capture());
    }

    private void setIsSensorPrivacyEnabled(boolean isMuted) {
        when(mMockSensorPrivacyManager.isSensorPrivacyEnabled(
                eq(SensorPrivacyManager.Sensors.CAMERA))).thenReturn(isMuted);
    }
}
