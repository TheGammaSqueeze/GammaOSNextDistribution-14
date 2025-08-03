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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.QCSlider;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BrightnessSliderTest extends BrightnessSliderTestCase {
    @Override
    protected BrightnessSlider getBrightnessSlider() {
        return new BrightnessSlider(mContext);
    }

    @Test
    public void getQCItem_createsSlider_zoneWrite() {
        BrightnessSlider brightnessSlider = getBrightnessSlider();
        brightnessSlider.setAvailabilityStatusForZone("write");
        QCItem item = brightnessSlider.getQCItem();
        QCList list = (QCList) item;
        QCRow row = list.getRows().get(0);
        QCSlider slider = row.getSlider();
        assertThat(slider).isNotNull();
        assertThat(slider.isEnabled()).isTrue();
    }

    @Test
    public void getQCItem_createsSlider_zoneRead() {
        BrightnessSlider brightnessSlider = getBrightnessSlider();
        brightnessSlider.setAvailabilityStatusForZone("read");
        QCItem item = brightnessSlider.getQCItem();
        QCList list = (QCList) item;
        QCRow row = list.getRows().get(0);
        QCSlider slider = row.getSlider();
        assertThat(slider).isNotNull();
        assertThat(slider.isEnabled()).isFalse();
        assertThat(slider.isClickableWhileDisabled()).isTrue();
    }

    @Test
    public void getQCItem_createsSlider_zoneHidden() {
        BrightnessSlider brightnessSlider = getBrightnessSlider();
        brightnessSlider.setAvailabilityStatusForZone("hidden");
        QCItem item = brightnessSlider.getQCItem();
        assertThat(item).isNull();
    }
}
