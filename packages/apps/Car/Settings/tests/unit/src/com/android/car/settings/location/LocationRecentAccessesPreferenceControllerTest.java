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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.PackageTagsList;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LocationRecentAccessesPreferenceControllerTest {
    private static final int RECENT_APPS_MAX_COUNT = 2;

    private final Context mContext = Mockito.spy(ApplicationProvider.getApplicationContext());
    private final LocationManager mLocationManager =
            Mockito.spy(mContext.getSystemService(LocationManager.class));
    private LifecycleOwner mLifecycleOwner;
    private PreferenceCategory mPreference;
    private LocationRecentAccessesPreferenceController mPreferenceController;
    private MockitoSession mSession;

    @Mock private FragmentController mFragmentController;
    @Mock private RecentAppOpsAccess mRecentLocationAccesses;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mSession = mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(ActivityManager.class)
                .startMocking();

        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);

        CarUxRestrictions carUxRestrictions =
                new CarUxRestrictions.Builder(
                                /* reqOpt= */ true,
                                CarUxRestrictions.UX_RESTRICTIONS_BASELINE,
                                /* timestamp= */ 0)
                        .build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new PreferenceCategory(mContext);
        screen.addPreference(mPreference);
        mPreferenceController =
                new LocationRecentAccessesPreferenceController(
                        mContext,
                        "key",
                        mFragmentController,
                        carUxRestrictions,
                        mRecentLocationAccesses,
                        RECENT_APPS_MAX_COUNT,
                        mLocationManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        doNothing().when(mContext).startActivity(any());
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void driverWithAdas_locationAndAdasOff_preferenceIsHidden() {
        setUserToDriverWithAdas();
        setIsLocationEnabled(false);
        setIsAdasGnssLocationEnabled(false);

        initializePreference();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void driverWithAdas_locationAndAdasOn_preferenceIsShown() {
        setUserToDriverWithAdas();
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        initializePreference();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void driverWithAdas_adasOnOnly_preferenceIsShown() {
        setUserToDriverWithAdas();
        setIsLocationEnabled(false);
        setIsAdasGnssLocationEnabled(true);

        initializePreference();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void driverWithNoAdas_locationAndAdasOff_preferenceIsHidden() {
        setUserToDriverWithNoAdas();
        setIsLocationEnabled(false);
        setIsAdasGnssLocationEnabled(false);

        initializePreference();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void driverWithNoAdas_locationAndAdasOn_preferenceIsShown() {
        setUserToDriverWithNoAdas();
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        initializePreference();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void driverWithNoAdas_adasOnOnly_preferenceIsHidden() {
        setUserToDriverWithNoAdas();
        setIsLocationEnabled(false);
        setIsAdasGnssLocationEnabled(true);

        initializePreference();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void passenger_locationAndAdasOff_preferenceIsHidden() {
        setUserToPassenger();
        setIsLocationEnabled(false);
        setIsAdasGnssLocationEnabled(false);

        initializePreference();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void passenger_locationAndAdasOn_preferenceIsShown() {
        setUserToPassenger();
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        initializePreference();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void passenger_adasOnOnly_preferenceIsHidden() {
        setUserToPassenger();
        setIsLocationEnabled(false);
        setIsAdasGnssLocationEnabled(true);

        initializePreference();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_noRecentRequests_messageDisplayed() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(Collections.emptyList());
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreference(0).getTitle())
                .isEqualTo(mContext.getString(R.string.location_no_recent_access));
    }

    @Test
    public void refreshUi_noRecentRequests_exceptForSomeRecentSystemAppRequests_showsViewAll() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(Collections.emptyList());
        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ true))
                .thenReturn(Collections.singletonList(mock(RecentAppOpsAccess.Access.class)));
        mPreferenceController.refreshUi();

        // includes preference for "View all"
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);
        assertThat(mPreference.getPreference(1).getTitle())
                .isEqualTo(
                        mContext.getString(
                                R.string.location_settings_recently_accessed_view_all_title));
    }

    @Test
    @UiThreadTest
    public void refreshUi_clickViewAll_launchesFragment() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ true))
                .thenReturn(Collections.singletonList(mock(RecentAppOpsAccess.Access.class)));
        mPreferenceController.refreshUi();

        // click on the "View all" preference
        mPreference.getPreference(1).performClick();
        verify(mFragmentController).launchFragment(any(LocationRecentAccessViewAllFragment.class));
    }

    @Test
    public void refreshUi_noRecentAccesses_includingNoSystemAppAccesses_doesNotShowViewAll() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false))
                .thenReturn(Collections.emptyList());
        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ true))
                .thenReturn(Collections.emptyList());
        mPreferenceController.refreshUi();

        // no preference for "View all"
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreference.getPreference(0).getTitle())
                .isEqualTo(mContext.getString(R.string.location_no_recent_access));
    }

    @Test
    public void refreshUi_someRecentAccesses_displaysAppInformation() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        String fakeLabel = "Test app 1";
        RecentAppOpsAccess.Access fakeAccess =
                new RecentAppOpsAccess.Access(
                        "com.test",
                        UserHandle.CURRENT,
                        mock(Drawable.class),
                        fakeLabel,
                        "fake contentDescription",
                        Clock.systemDefaultZone().millis());
        List<RecentAppOpsAccess.Access> list = Collections.singletonList(fakeAccess);
        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false)).thenReturn(list);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreference(0).getTitle()).isEqualTo(fakeLabel);
        assertThat(mPreference.getPreference(0).getSummary().toString()).contains("min. ago");
    }

    @Test
    public void refreshUi_someRecentAcesses_preferencesAddedToScreen_capsAtMax() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        List<RecentAppOpsAccess.Access> list =
                Arrays.asList(
                        mock(RecentAppOpsAccess.Access.class),
                        mock(RecentAppOpsAccess.Access.class),
                        mock(RecentAppOpsAccess.Access.class));
        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false)).thenReturn(list);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(RECENT_APPS_MAX_COUNT);
    }

    @Test
    public void refreshUi_recentRequests_launchLocationSettings() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        List<RecentAppOpsAccess.Access> list =
                Collections.singletonList(mock(RecentAppOpsAccess.Access.class));
        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false)).thenReturn(list);
        mPreferenceController.refreshUi();

        mPreference.getPreference(0).performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MANAGE_APP_PERMISSION);
    }

    @Test
    public void refreshUi_newRecentRequests_listIsUpdated() {
        setIsLocationEnabled(true);
        setIsAdasGnssLocationEnabled(true);

        List<RecentAppOpsAccess.Access> list1 =
                Collections.singletonList(mock(RecentAppOpsAccess.Access.class));
        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false)).thenReturn(list1);

        List<RecentAppOpsAccess.Access> list2 = new ArrayList<>(list1);
        list2.add(mock(RecentAppOpsAccess.Access.class));

        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(list1.size());

        when(mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false)).thenReturn(list2);
        mPreferenceController.refreshUi();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(list2.size());
    }

    private void initializePreference() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> intentFilterCaptor =
                ArgumentCaptor.forClass(IntentFilter.class);

        verify(mContext, times(2))
                .registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                        intentFilterCaptor.capture(), eq(Context.RECEIVER_EXPORTED));
    }

    private void setUserToDriverWithAdas() {
        int currentUser = UserHandle.myUserId();
        when(ActivityManager.getCurrentUser()).thenReturn(currentUser);
        PackageTagsList list = new PackageTagsList.Builder().add("testApp1").build();
        when(mLocationManager.getAdasAllowlist()).thenReturn(list);
    }

    private void setUserToDriverWithNoAdas() {
        int currentUser = UserHandle.myUserId();
        when(ActivityManager.getCurrentUser()).thenReturn(currentUser);
        PackageTagsList list = new PackageTagsList.Builder().build();
        when(mLocationManager.getAdasAllowlist()).thenReturn(list);
    }

    private void setUserToPassenger() {
        int nonCurrentUser = UserHandle.myUserId() + 1;
        when(ActivityManager.getCurrentUser()).thenReturn(nonCurrentUser);
    }

    private void setIsLocationEnabled(boolean isEnabled) {
        when(mLocationManager.isLocationEnabled()).thenReturn(isEnabled);
    }

    private void setIsAdasGnssLocationEnabled(boolean isEnabled) {
        when(mLocationManager.isAdasGnssLocationEnabled()).thenReturn(isEnabled);
    }
}
