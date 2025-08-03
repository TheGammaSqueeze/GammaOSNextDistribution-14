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

package com.android.car.settings.qc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.CarSettingsApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MediaVolumeSliderWorkerTest {
    protected static final int GROUP_ID = 0;
    protected static final int FLAGS = 0;
    protected static final int TEST_ZONE_ID = 1;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private MediaVolumeSliderWorker mWorker;

    @Mock
    private CarSettingsApplication mCarSettingsApplication;
    @Mock
    protected CarAudioManager mCarAudioManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getApplicationContext()).thenReturn(mCarSettingsApplication);
        when(mCarSettingsApplication.getCarAudioManager()).thenReturn(mCarAudioManager);
        when(mCarSettingsApplication.getMyAudioZoneId()).thenReturn(TEST_ZONE_ID);

        mWorker = new MediaVolumeSliderWorker(mContext,
                SettingsQCRegistry.MEDIA_VOLUME_SLIDER_URI);
    }

    @Test
    public void onSubscribe_registerCarVolumeCallback() {
        mWorker.onQCItemSubscribe();
        verify(mCarAudioManager).registerCarVolumeCallback(any());
    }

    @Test
    public void onUnsubscribe_unregisterCarVolumeCallback() {
        mWorker.onQCItemSubscribe();
        mWorker.onQCItemUnsubscribe();
        verify(mCarAudioManager).unregisterCarVolumeCallback(any());
    }

    @Test
    public void onGroupVolumeChanged_updateVolumeAndMute() {
        mWorker.getVolumeChangeCallback().onGroupVolumeChanged(TEST_ZONE_ID, GROUP_ID, FLAGS);
        verify(mCarAudioManager).getVolumeGroupIdForUsage(eq(TEST_ZONE_ID), anyInt());
    }
}
