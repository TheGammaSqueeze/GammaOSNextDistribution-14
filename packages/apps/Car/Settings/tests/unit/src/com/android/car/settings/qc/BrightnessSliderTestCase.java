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

import static com.android.car.qc.QCItem.QC_ACTION_SLIDER_VALUE;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.convertGammaToLinear;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGamma;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.QCSlider;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public abstract class BrightnessSliderTestCase extends BaseSettingsQCItemTestCase {
    private BrightnessSlider mBrightnessSlider;
    private int mMaximumBacklight;
    private int mMinimumBacklight;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBrightnessSlider = getBrightnessSlider();
        PowerManager powerManager = mContext.getSystemService(PowerManager.class);
        mMaximumBacklight = powerManager.getMaximumScreenBrightnessSetting();
        mMinimumBacklight = powerManager.getMinimumScreenBrightnessSetting();
    }

    @Test
    public void getQCItem_titleSet() {
        QCRow row = getBrightnessRow();
        assertThat(row.getTitle()).isEqualTo(mContext.getString(R.string.qc_display_brightness));
    }

    @Test
    public void getQCItem_createsSlider() {
        QCRow row = getBrightnessRow();
        QCSlider slider = row.getSlider();
        assertThat(slider).isNotNull();
        assertThat(slider.getMax()).isEqualTo(GAMMA_SPACE_MAX);
        assertThat(slider.getValue()).isEqualTo(
                convertLinearToGamma(getScreenBrightness(), mMinimumBacklight, mMaximumBacklight));
    }

    @Test
    public void getQCItem_hasBaseUmRestriction_sliderDisabled() {
        setBaseUserRestriction(UserManager.DISALLOW_CONFIG_BRIGHTNESS, /* restricted= */ true);
        QCRow row = getBrightnessRow();
        QCSlider slider = row.getSlider();
        assertThat(slider.isEnabled()).isFalse();
        assertThat(slider.isClickableWhileDisabled()).isFalse();
    }

    @Test
    public void getQCItem_hasUmRestriction_sliderClickableWhileDisabled() {
        setUserRestriction(UserManager.DISALLOW_CONFIG_BRIGHTNESS, /* restricted= */ true);
        QCRow row = getBrightnessRow();
        QCSlider slider = row.getSlider();
        assertThat(slider.isEnabled()).isFalse();
        assertThat(slider.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void onNotifyChange_updatesBrightness() {
        int oldValue = getScreenBrightness();
        int newValue = oldValue == 50 ? 51 : 50; // ensure we use a different value
        Intent intent = new Intent();
        intent.putExtra(QC_ACTION_SLIDER_VALUE, newValue);
        mBrightnessSlider.onNotifyChange(intent);
        assertThat(getScreenBrightness()).isEqualTo(
                convertGammaToLinear(newValue, mMinimumBacklight, mMaximumBacklight));
        // restore old value
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, oldValue, UserHandle.myUserId());
    }

    protected abstract BrightnessSlider getBrightnessSlider();

    protected QCRow getBrightnessRow() {
        QCItem item = mBrightnessSlider.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        QCList list = (QCList) item;
        assertThat(list.getRows().size()).isEqualTo(1);
        return list.getRows().get(0);
    }

    private int getScreenBrightness() {
        int gamma = mBrightnessSlider.getSeekbarValue();
        return convertGammaToLinear(gamma, mMinimumBacklight, mMaximumBacklight);
    }
}
