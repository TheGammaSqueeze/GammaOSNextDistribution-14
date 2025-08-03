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

package com.android.car.settings.qc;

import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;

import static com.android.car.qc.QCItem.QC_ACTION_SLIDER_VALUE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.content.Intent;

import com.android.car.qc.QCRow;
import com.android.car.qc.QCSlider;
import com.android.car.settings.CarSettingsApplication;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public abstract class VolumeSliderTestCase extends BaseSettingsQCItemTestCase {
    protected static final int GROUP_ID = 0;
    protected static final int TEST_MIN_VOLUME = 0;
    protected static final int TEST_VOLUME = 40;
    protected static final int TEST_NEW_VOLUME = 80;
    protected static final int TEST_MAX_VOLUME = 100;
    protected static final int TEST_ZONE_ID = 1;

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
        when(mCarAudioManager.getVolumeGroupIdForUsage(eq(TEST_ZONE_ID), anyInt()))
                .thenReturn(GROUP_ID);
        when(mCarAudioManager.getGroupMinVolume(TEST_ZONE_ID, GROUP_ID))
                .thenReturn(TEST_MIN_VOLUME);
        when(mCarAudioManager.getGroupVolume(TEST_ZONE_ID, GROUP_ID))
                .thenReturn(TEST_VOLUME);
        when(mCarAudioManager.getGroupMaxVolume(TEST_ZONE_ID, GROUP_ID))
                .thenReturn(TEST_MAX_VOLUME);
        when(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING)).thenReturn(
                true);
        when(mCarAudioManager.isVolumeGroupMuted(TEST_ZONE_ID, GROUP_ID)).thenReturn(false);
    }

    protected void verifyTitleSet(QCRow row, int titleResId) {
        assertThat(row.getTitle()).isEqualTo(mContext.getString(titleResId));
    }

    protected void verifyIconSet(QCRow row, int iconResId) {
        assertThat(row.getStartIcon().getResId()).isEqualTo(iconResId);
    }

    protected void verifyIconNotSet(QCRow row) {
        assertThat(row.getStartIcon()).isNull();
    }

    protected void verifySliderCreated(QCRow row) {
        QCSlider slider = row.getSlider();
        assertThat(slider).isNotNull();
        assertThat(slider.getMin()).isEqualTo(TEST_MIN_VOLUME);
        assertThat(slider.getMax()).isEqualTo(TEST_MAX_VOLUME);
        assertThat(slider.getValue()).isEqualTo(TEST_VOLUME);
    }

    protected void verifyVolumeChanged(BaseVolumeSlider slider, int groupId) {
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_SLIDER_VALUE, TEST_NEW_VOLUME);
        intent.putExtra(BaseVolumeSlider.EXTRA_GROUP_ID, groupId);
        slider.onNotifyChange(intent);
        verify(mCarAudioManager).setGroupVolume(
                eq(TEST_ZONE_ID), eq(groupId), eq(TEST_NEW_VOLUME), anyInt());
    }

    protected void verifyBaseUmRestriction(QCRow row) {
        QCSlider slider = row.getSlider();
        assertThat(slider).isNotNull();
        assertThat(slider.isEnabled()).isFalse();
        assertThat(slider.isClickableWhileDisabled()).isFalse();
    }

    protected void verifyUmRestriction(QCRow row) {
        QCSlider slider = row.getSlider();
        assertThat(slider).isNotNull();
        assertThat(slider.isEnabled()).isFalse();
        assertThat(slider.isClickableWhileDisabled()).isTrue();
    }
}
