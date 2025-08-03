/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher;

import static android.car.settings.CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS;
import static android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Intent;
import android.provider.Settings;
import android.testing.TestableContext;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;


/**
 * Programmatic tests for AppGridActivity and AppGridScrollBar
 */
@RunWith(AndroidJUnit4.class)
public class AppGridActivityTest {
    private ActivityScenario<AppGridActivity> mActivityScenario;
    private CarUxRestrictionsManager mCarUxRestrictionsManager;
    private PageIndicator mPageIndicator;
    @Rule
    public TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getInstrumentation().getContext());

    @After
    public void tearDown() {
        saveTosBannerDismissalTime(/* epochSecond = */ 0); // Reset tos banner dismiss time
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    @Test
    public void onCreate_appGridRecyclerView_isVisible() {
        mActivityScenario = ActivityScenario.launch(AppGridActivity.class);
        onView(withId(R.id.apps_grid)).check(matches(isDisplayed()));
        onView(withId(R.id.page_indicator_container)).check(matches(isDisplayed()));
    }

    @Test
    public void onResume_ScrollStateIsUpdated() {
        mActivityScenario = ActivityScenario.launch(AppGridActivity.class);
        mActivityScenario.onActivity(activity -> {
            mPageIndicator = mock(PageIndicator.class);
            activity.setPageIndicator(mPageIndicator);
        });
        // The activity needs to be reset to go to the RESUMED state after the
        // mock object is set up
        mActivityScenario.moveToState(Lifecycle.State.CREATED);
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);
        onView(withId(R.id.apps_grid)).check(matches(isDisplayed()));
        onView(withId(R.id.page_indicator_container)).check(matches(isDisplayed()));
        // onResumed will trigger LauncherViewModel.onChanged when the activity first started
        // which triggers this call again
        verify(mPageIndicator, atLeast(1)).updatePageCount(anyInt());
    }

    @Test
    public void onStop_CarUxRestrictionsManager_unregisterListener() {
        mActivityScenario = ActivityScenario.launch(AppGridActivity.class);
        mActivityScenario.onActivity(activity -> {
            mCarUxRestrictionsManager = mock(CarUxRestrictionsManager.class);
            activity.setCarUxRestrictionsManager(mCarUxRestrictionsManager);
        });
        mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
        verify(mCarUxRestrictionsManager, times(1)).unregisterListener();
    }

    @Test
    public void onCreate_tosIsAccepted_tosContentObserversAreNull() {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);


        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));

        mActivityScenario.onActivity(activity -> {
            assertNull(activity.mTosContentObserver); // Content observer not setup
            assertNull(activity.mTosDisabledAppsContentObserver); // Content observer not setup
        });
    }

    @Test
    public void afterTosIsAccepted_unregisterTosContentObservers() {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver); // Content observer is setup
            assertNotNull(activity.mTosDisabledAppsContentObserver); // Content observer is setup

            // Accept TOS
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);
            activity.mTosContentObserver.onChange(true);
        });

        // Content observer is null after tos is accepted
        mActivityScenario.onActivity(activity -> {
            assertNull(activity.mTosContentObserver);
            assertNull(activity.mTosDisabledAppsContentObserver);
        });
    }

    @Test
    public void tosUninitialized_changesToTosUnaccepted_doNotUnregisterTosContentObservers() {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 0);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver); // Content observer is setup
            assertNotNull(activity.mTosDisabledAppsContentObserver); // Content observer is setup

            // TOS changed to unaccepted
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
            activity.mTosContentObserver.onChange(true);
        });

        // Content observer is not null after tos is unaccepted
        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver);
            assertNotNull(activity.mTosDisabledAppsContentObserver);
        });
    }

    @Test
    public void
            tosNotAccepted_tosDisabledAppsUpdate_doNotUnregisterTosDisabledAppsContentObserver() {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
        Settings.Secure.putString(
                mContext.getContentResolver(),
                KEY_UNACCEPTED_TOS_DISABLED_APPS,
                "tos_disabled_app_one,tos_disabled_app_2");

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver); // Content observer is setup
            assertNotNull(activity.mTosDisabledAppsContentObserver); // Content observer is setup

            // TOS changed to unaccepted
            Settings.Secure.putString(mContext.getContentResolver(),
                    KEY_UNACCEPTED_TOS_DISABLED_APPS,
                    "tos_disabled_app_one");
            activity.mTosContentObserver.onChange(true);
        });

        // Content observer is not null after tos is unaccepted
        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver);
            assertNotNull(activity.mTosDisabledAppsContentObserver);
        });
    }

    @Test
    public void showTosBanner_whenTosAccepted_shouldBeFalse() {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));

        mActivityScenario.onActivity(activity -> assertFalse(activity.showTosBanner(mContext)));
    }

    @Test
    public void showTosBanner_tosUnacceptedAndCurTimeIsLessThanBannerResurfaceTime_shouldBeFalse() {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
        // Default banner resurface time is 1 day
        saveTosBannerDismissalTime(Clock.systemUTC().instant().getEpochSecond());

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));

        mActivityScenario.onActivity(activity -> assertFalse(activity.showTosBanner(mContext)));
    }

    @Test
    public void showTosBanner_tosUnacceptedAndSystemRebooted_shouldBeTrue() {
        // Setup resurface config to display banner on next reboot
        mContext.getOrCreateTestableResources()
                .addOverride(R.integer.config_tos_banner_resurface_time_days, 0);
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);

        // Simulate dismissing banner in current session
        saveTosBannerDismissalTime(Clock.systemUTC().instant().getEpochSecond());
        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));
        // Banner has been dismissed in current drive session, showTosBanner should be false
        mActivityScenario.onActivity(activity -> assertFalse(activity.showTosBanner(mContext)));


        // Banner should be visible on next drive session
        mActivityScenario.onActivity(activity -> {
            AppGridActivity spyActivity = spy(activity);
            // Mock system boot time to simulate a reboot
            when(spyActivity.getSystemBootTime())
                    .thenReturn(Clock.systemUTC().instant().getEpochSecond() + 10L);
            assertTrue(spyActivity.showTosBanner(mContext));
        });

        mContext.getOrCreateTestableResources()
                .removeOverride(R.integer.config_tos_banner_resurface_time_days);
    }

    @Test
    public void showTosBanner_whenTosNotAcceptedAndBannerNotDismissed_shouldReturnTrue() {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
        mActivityScenario = ActivityScenario.launch(new Intent(mContext, AppGridActivity.class));

        mActivityScenario.onActivity(activity -> assertTrue(activity.showTosBanner(mContext)));
    }

    private void saveTosBannerDismissalTime(long epochSecond) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit().putLong(AppGridActivity.TOS_BANNER_DISMISS_TIME_KEY, epochSecond).apply();
    }
}
