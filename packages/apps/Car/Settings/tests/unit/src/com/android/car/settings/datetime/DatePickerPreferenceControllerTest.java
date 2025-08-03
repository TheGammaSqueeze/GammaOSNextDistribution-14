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

package com.android.car.settings.datetime;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DatePickerPreferenceControllerTest {

    private Context mContext;
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private DatePickerPreferenceController mController;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        mContext = spy(ApplicationProvider.getApplicationContext());
        mPreference = new SwitchPreference(mContext);

        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mController = new DatePickerPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, carUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
    }

    @Test
    public void testRefreshUi_disabled() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 1);
        mController.refreshUi();
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testRefreshUi_enabled() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0);
        mController.refreshUi();
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void testRefreshUi_fromBroadcastReceiver_disabled() {
        mController.onStart(mLifecycleOwner);

        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 1);
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(broadcastReceiverArgumentCaptor.capture(), any(),
                eq(Context.RECEIVER_NOT_EXPORTED));
        broadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(Intent.ACTION_TIME_CHANGED));
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testRefreshUi_fromBroadcastReceiver_enabled() {
        mController.onStart(mLifecycleOwner);

        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0);
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(broadcastReceiverArgumentCaptor.capture(), any(),
                eq(Context.RECEIVER_NOT_EXPORTED));
        broadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(Intent.ACTION_TIME_CHANGED));
        assertThat(mPreference.isEnabled()).isTrue();
    }
}
