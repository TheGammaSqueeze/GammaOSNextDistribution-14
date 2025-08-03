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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.development.DevelopmentSettingsUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.utils.StringUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BuildNumberPreferenceControllerTest {
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private BuildNumberPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;
    private int mDebounceInterval;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private Toast mMockToast;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(UserManager.class, withSettings().lenient())
                .mockStatic(Toast.class, withSettings().lenient())
                .startMocking();
        when(UserManager.get(mContext)).thenReturn(mMockUserManager);
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        when(mMockUserManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES))
                .thenReturn(false);
        when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);

        // By default device is provisioned.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 1);

        // By default development settings is disabled.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);

        mPreference = new Preference(mContext);
        mPreferenceController = new BuildNumberPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onResume(mLifecycleOwner);

        mDebounceInterval = mContext.getResources()
                .getInteger(R.integer.config_preference_onclick_debounce_ms) + 100; // Add buffer
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testHandlePreferenceClicked_notProvisioned_returnFalse() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0);
        assertThat(mPreferenceController.handlePreferenceClicked(mPreference)).isFalse();
    }

    @Test
    public void testHandlePreferenceClicked_nonAdmin_returnFalse() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ false);

        assertThat(mPreferenceController.handlePreferenceClicked(mPreference)).isFalse();
    }

    @Test
    public void testHandlePreferenceClicked_demoUser_returnsFalse() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);

        assertThat(mPreferenceController.handlePreferenceClicked(mPreference)).isFalse();
    }

    @Test
    public void testHandlePreferenceClicked_adminUser_returnsTrue() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);

        assertThat(mPreferenceController.handlePreferenceClicked(mPreference)).isTrue();
    }

    @Test
    public void testHandlePreferenceClicked_devSettingsDisabled_firstClick_noToast() {
        mPreference.performClick();
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), anyString(), anyInt()), never());
        verify(mMockToast, never()).show();
    }

    @Test
    public void testHandlePreferenceClicked_devSettingsDisabled_someClicks_showToast()
            throws InterruptedException {
        for (int i = 0; i < getTapsToShowToast(); i++) {
            mPreference.performClick();
            mCountDownLatch.await(mDebounceInterval, TimeUnit.MILLISECONDS);
        }

        int remainingClicks = getTapsToBecomeDeveloper() - getTapsToShowToast();
        assertToastShown(StringUtil.getIcuPluralsString(mContext, remainingClicks,
                R.string.show_dev_countdown));
    }

    @Test
    public void testHandlePreferenceClicked_devSettingsDisabled_allClicks_showDevEnabledToast()
            throws InterruptedException {
        for (int i = 0; i < getTapsToBecomeDeveloper(); i++) {
            mPreference.performClick();
            mCountDownLatch.await(mDebounceInterval, TimeUnit.MILLISECONDS);
        }
        assertToastShown(mContext.getString(R.string.show_dev_on));
    }

    @Test
    public void testHandlePreferenceClicked_devSettingsDisabled_allClicks_devSettingsEnabled()
            throws InterruptedException {
        for (int i = 0; i < getTapsToBecomeDeveloper(); i++) {
            mPreference.performClick();
            mCountDownLatch.await(mDebounceInterval, TimeUnit.MILLISECONDS);
        }
        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext,
                mMockUserManager)).isTrue();
    }

    @Test
    public void testHandlePreferenceClicked_devSettingsDisabled_extraClicks_noAlreadyDevToast()
            throws InterruptedException {
        int extraClicks = 100;
        for (int i = 0; i < getTapsToBecomeDeveloper() + extraClicks; i++) {
            mPreference.performClick();
            mCountDownLatch.await(mDebounceInterval, TimeUnit.MILLISECONDS);
        }
        String alreadyDevString = mContext.getString(R.string.show_dev_already);
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), eq(alreadyDevString), anyInt()), never());
    }

    @Test
    public void testHandlePreferenceClicked_devSettingsEnabled_click_showAlreadyDevToast() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        mPreferenceController.onPause(mLifecycleOwner);
        mPreferenceController.onResume(mLifecycleOwner);
        mPreference.performClick();
        assertToastShown(mContext.getString(R.string.show_dev_already));
    }

    private void assertToastShown(String toastText) {
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), eq(toastText), anyInt()));
        verify(mMockToast, atLeastOnce()).show();
    }

    private void switchToUser(boolean isAdmin, boolean isDemo) {
        when(mMockUserManager.isAdminUser()).thenReturn(isAdmin);
        when(mMockUserManager.isDemoUser()).thenReturn(isDemo);
        when(UserManager.isDeviceInDemoMode(mContext)).thenReturn(isDemo);
    }

    private int getTapsToBecomeDeveloper() {
        return mContext.getResources().getInteger(R.integer.enable_developer_settings_click_count);
    }

    private int getTapsToShowToast() {
        return mContext.getResources().getInteger(
                R.integer.enable_developer_settings_clicks_to_show_toast_count);
    }
}
