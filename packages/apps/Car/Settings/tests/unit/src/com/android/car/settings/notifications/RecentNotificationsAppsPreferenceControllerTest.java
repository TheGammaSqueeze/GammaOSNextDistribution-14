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

package com.android.car.settings.notifications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.INotificationManager;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Parcel;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RecentNotificationsAppsPreferenceControllerTest {
    private static final int TIMEOUT = 5000;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceCategory;
    private RecentNotificationsAppsPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private int mMaxEntryCount;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private INotificationManager mMockManager;
    @Mock
    private ApplicationsState mMockApplicationsState;
    @Mock
    private IUsageStatsManager mMockUsageStatsManager;
    @Mock
    private ApplicationInfo mApplicationInfo;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceCategory = new PreferenceCategory(mContext);
        screen.addPreference(mPreferenceCategory);

        mPreferenceController = new RecentNotificationsAppsPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceCategory);

        mPreferenceController.mNotificationManager = mMockManager;
        mPreferenceController.mUsageStatsManager = mMockUsageStatsManager;
        mPreferenceController.setApplicationsState(mMockApplicationsState);

        mMaxEntryCount = mContext.getResources().getInteger(
                R.integer.recent_notifications_apps_list_count);
    }

    @Test
    public void noEvents_noPreferences() throws Exception {
        when(mMockUsageStatsManager.queryEventsForUser(anyLong(),
                anyLong(), anyInt(), anyString())).thenReturn(null);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void areEvents_showValidRecents() throws Exception {
        long timestamp = System.currentTimeMillis();
        List<UsageEvents.Event> events = new ArrayList<>();
        UsageEvents.Event app1 = createUsageEvent("a", timestamp);
        UsageEvents.Event app2 = createUsageEvent("com.android.settings", timestamp);
        UsageEvents.Event app3 = createUsageEvent("pkg.class2", timestamp - 1000);

        events.add(app1);
        events.add(app2);
        events.add(app3);

        ApplicationsState.AppEntry app1Entry = createAppEntry("app 1");
        ApplicationsState.AppEntry app2Entry = createAppEntry("app 2");

        // app1, app2 are valid apps. app3 is invalid.
        int userId = UserHandle.myUserId();
        when(mMockApplicationsState.getEntry(app1.getPackageName(), userId)).thenReturn(app1Entry);
        when(mMockApplicationsState.getEntry(app2.getPackageName(), userId)).thenReturn(app2Entry);
        when(mMockApplicationsState.getEntry(app3.getPackageName(), userId)).thenReturn(null);

        UsageEvents usageEvents = getUsageEvents(
                new String[] {app1.getPackageName(), app2.getPackageName(), app3.getPackageName()},
                events);
        when(mMockUsageStatsManager.queryEventsForUser(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(usageEvents);

        mPreferenceController.onCreate(mLifecycleOwner);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Only add app1 & app2. app3 skipped because it's invalid app.
        verify(mMockApplicationsState, after(TIMEOUT).times(2))
                .getEntry(eq(app1.getPackageName()), anyInt());
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo(app1Entry.label);
        assertThat(mPreferenceCategory.getPreference(1).getTitle()).isEqualTo(app2Entry.label);
    }

    @Test
    public void areEvents_showMaximumRecents() throws Exception {
        long timestamp = System.currentTimeMillis();
        List<UsageEvents.Event> events = new ArrayList<>();
        UsageEvents.Event app1 = createUsageEvent("a", timestamp);
        UsageEvents.Event app2 = createUsageEvent("com.android.settings", timestamp);
        UsageEvents.Event app3 = createUsageEvent("pkg.class2", timestamp - 1000);
        UsageEvents.Event app4 = createUsageEvent("pkg.class3", timestamp - 1000);

        events.add(app1);
        events.add(app2);
        events.add(app3);
        events.add(app4);

        ApplicationsState.AppEntry app1Entry = createAppEntry("app 1");
        ApplicationsState.AppEntry app2Entry = createAppEntry("app 2");
        ApplicationsState.AppEntry app3Entry = createAppEntry("app 3");
        ApplicationsState.AppEntry app4Entry = createAppEntry("app 4");

        // Four valid apps found
        int userId = UserHandle.myUserId();
        when(mMockApplicationsState.getEntry(app1.getPackageName(), userId)).thenReturn(app1Entry);
        when(mMockApplicationsState.getEntry(app2.getPackageName(), userId)).thenReturn(app2Entry);
        when(mMockApplicationsState.getEntry(app3.getPackageName(), userId)).thenReturn(app3Entry);
        when(mMockApplicationsState.getEntry(app4.getPackageName(), userId)).thenReturn(app4Entry);

        UsageEvents usageEvents = getUsageEvents(
                new String[] {app1.getPackageName(), app2.getPackageName(), app3.getPackageName(),
                app4.getPackageName()}, events);
        when(mMockUsageStatsManager.queryEventsForUser(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(usageEvents);

        mPreferenceController.onCreate(mLifecycleOwner);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Only max allowed apps displayed
        verify(mMockApplicationsState, after(TIMEOUT).times(2))
                .getEntry(eq(app1.getPackageName()), anyInt());
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(mMaxEntryCount);
        assertThat(mPreferenceCategory.getPreference(0).getTitle()).isEqualTo(app1Entry.label);
        assertThat(mPreferenceCategory.getPreference(1).getTitle()).isEqualTo(app2Entry.label);
        assertThat(mPreferenceCategory.getPreference(2).getTitle()).isEqualTo(app3Entry.label);
    }

    private UsageEvents getUsageEvents(String[] pkgs, List<UsageEvents.Event> events) {
        UsageEvents usageEvents = new UsageEvents(events, pkgs);
        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        usageEvents.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return UsageEvents.CREATOR.createFromParcel(parcel);
    }

    private UsageEvents.Event createUsageEvent(String packageName, long timestamp) {
        UsageEvents.Event app = new UsageEvents.Event();
        app.mEventType = UsageEvents.Event.NOTIFICATION_INTERRUPTION;
        app.mPackage = packageName;
        app.mTimeStamp = timestamp;
        return app;
    }

    private ApplicationsState.AppEntry createAppEntry(String label) {
        ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = mApplicationInfo;
        appEntry.label = label;
        return appEntry;
    }
}
