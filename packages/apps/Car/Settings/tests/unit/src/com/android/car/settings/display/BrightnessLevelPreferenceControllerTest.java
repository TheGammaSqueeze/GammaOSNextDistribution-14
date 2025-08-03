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

package com.android.car.settings.display;

import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGamma;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.common.SeekBarPreference;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class BrightnessLevelPreferenceControllerTest {
    private static final int WAIT_TIME_SEC = 10; // Time to ensure brightness value has been written

    private Context mContext;
    private LifecycleOwner mLifecycleOwner;
    private TestBrightnessLevelPreferenceController mController;
    private SeekBarPreference mSeekBarPreference;
    private CountDownLatch mCountDownLatch;
    private int mMin;
    private int mMax;
    private int mMid;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        mCountDownLatch = new CountDownLatch(1);

        mContext = spy(ApplicationProvider.getApplicationContext());

        mSeekBarPreference = new SeekBarPreference(mContext);
        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mController = new TestBrightnessLevelPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, carUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mSeekBarPreference);
        mMin = mController.mMinimumBacklight;
        mMax = mController.mMaximumBacklight;
        mMid = (mMax + mMin) / 2;
        mController.onCreate(mLifecycleOwner);
    }

    @Test
    public void onStart_registersContentObserver() {
        ContentResolver resolver = mock(ContentResolver.class);
        when(mContext.getContentResolver()).thenReturn(resolver);
        mController.onStart(mLifecycleOwner);
        verify(resolver).registerContentObserver(
                eq(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)), eq(false),
                any(ContentObserver.class));
    }

    @Test
    public void onStop_unregistersContentObserver() {
        ContentResolver resolver = mock(ContentResolver.class);
        when(mContext.getContentResolver()).thenReturn(resolver);
        mController.onStart(mLifecycleOwner);
        mController.onStop(mLifecycleOwner);
        verify(resolver).unregisterContentObserver(any(ContentObserver.class));
    }

    @Test
    public void testRefreshUi_maxSet() {
        mController.refreshUi();
        assertThat(mSeekBarPreference.getMax()).isEqualTo(GAMMA_SPACE_MAX);
    }

    @Test
    public void testRefreshUi_minValue() {
        mController.saveScreenBrightnessLinearValue(mMin);
        mController.refreshUi();

        assertThat(mSeekBarPreference.getValue()).isEqualTo(0);
    }

    @Test
    public void testRefreshUi_maxValue() {
        mController.saveScreenBrightnessLinearValue(mMax);
        mController.refreshUi();

        assertThat(mSeekBarPreference.getValue()).isEqualTo(GAMMA_SPACE_MAX);
    }

    @Test
    public void testRefreshUi_midValue() {
        mController.saveScreenBrightnessLinearValue(mMid);
        mController.refreshUi();

        assertThat(mSeekBarPreference.getValue()).isEqualTo(convertLinearToGamma(mMid, mMin, mMax));
    }

    @Test
    public void testHandlePreferenceChanged_minValue() {
        mSeekBarPreference.callChangeListener(0);

        assertThat(mController.getScreenBrightnessLinearValue()).isEqualTo(mMin);
    }

    @Test
    public void testHandlePreferenceChanged_maxValue() {
        mSeekBarPreference.callChangeListener(GAMMA_SPACE_MAX);

        assertThat(mController.getScreenBrightnessLinearValue()).isEqualTo(mMax);
    }

    @Test
    public void testHandlePreferenceChanged_midValue() {
        mSeekBarPreference.callChangeListener(convertLinearToGamma(mMid, mMin, mMax));

        assertThat(mController.getScreenBrightnessLinearValue()).isEqualTo(mMid);
    }

    private static class TestBrightnessLevelPreferenceController extends
            BrightnessLevelPreferenceController {
        // Using Settings.System.putIntForUser() led to flaky tests because other android classes
        // could write to the value as well.
        private int mScreenBrightnessLinearValue;

        TestBrightnessLevelPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        int getScreenBrightnessLinearValue() {
            return mScreenBrightnessLinearValue;
        }

        @Override
        void saveScreenBrightnessLinearValue(int linear) {
            mScreenBrightnessLinearValue = linear;
        }
    }
}
