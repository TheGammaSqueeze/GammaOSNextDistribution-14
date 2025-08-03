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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.RecentAppOpsAccess;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MicrophoneRecentAccessViewAllPreferenceControllerTest {

    private final Context mContext = Mockito.spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private LogicalPreferenceGroup mPreference;
    private MicrophoneRecentAccessViewAllPreferenceController mPreferenceController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private RecentAppOpsAccess mRecentMicrophoneAccesses;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreference);
        mPreferenceController = new MicrophoneRecentAccessViewAllPreferenceController(mContext,
                "key", mFragmentController, carUxRestrictions,
                mRecentMicrophoneAccesses);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        doNothing().when(mContext).startActivity(any());
    }

    @Test
    public void updateState_noSystemAppsByDefault() {
        initializePreference();
        verify(mRecentMicrophoneAccesses, never()).getAppListSorted(/* showSystem= */ true);
    }

    @Test
    public void setShowSystem_showsSystemApps() {
        initializePreference();
        mPreferenceController.setShowSystem(true);
        verify(mRecentMicrophoneAccesses).getAppListSorted(/* showSystem= */ true);
    }

    @Test
    public void refreshUi_noRecentRequests_showsEmptyState() {
        initializePreference();

        when(mRecentMicrophoneAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(Collections.emptyList());
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreference(0).getTitle()).isEqualTo(
                mContext.getString(R.string.microphone_no_recent_access));
    }

    @Test
    public void refreshUi_someRecentRequests_displaysAppInformation() {
        initializePreference();

        String fakeLabel = "Test app 1";
        RecentAppOpsAccess.Access fakeAccess = new RecentAppOpsAccess.Access("com.test",
                UserHandle.CURRENT, mock(Drawable.class), fakeLabel,
                "fake contentDescription", Clock.systemDefaultZone().millis());
        List<RecentAppOpsAccess.Access> list = Collections.singletonList(fakeAccess);
        when(mRecentMicrophoneAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreference(0).getTitle()).isEqualTo(fakeLabel);
        assertThat(mPreference.getPreference(0).getSummary().toString()).contains("min. ago");
    }

    @Test
    public void refreshUi_recentRequests_launchMicrophoneSettings() {
        initializePreference();

        List<RecentAppOpsAccess.Access> list = Collections.singletonList(
                mock(RecentAppOpsAccess.Access.class));
        when(mRecentMicrophoneAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list);
        mPreferenceController.refreshUi();

        mPreference.getPreference(0).performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MANAGE_APP_PERMISSION);
        assertThat(intent.getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME))
            .isEqualTo(Manifest.permission_group.MICROPHONE);
    }

    @Test
    public void refreshUi_newRecentRequests_listIsUpdated() {
        initializePreference();

        List<RecentAppOpsAccess.Access> list1 = Collections.singletonList(
                mock(RecentAppOpsAccess.Access.class));
        when(mRecentMicrophoneAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list1);

        List<RecentAppOpsAccess.Access> list2 = new ArrayList<>(list1);
        list2.add(mock(RecentAppOpsAccess.Access.class));

        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(list1.size());

        when(mRecentMicrophoneAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(list2);
        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(list2.size());
    }

    private void initializePreference() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
    }
}

