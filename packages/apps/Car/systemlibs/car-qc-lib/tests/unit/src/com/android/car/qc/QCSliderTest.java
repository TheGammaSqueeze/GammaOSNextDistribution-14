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

package com.android.car.qc;

import static com.android.car.qc.QCItem.QC_TYPE_SLIDER;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QCSliderTest extends QCItemTestCase<QCSlider> {
    private static final int MIN = 50;
    private static final int MAX = 150;
    private static final int VALUE = 75;

    @Test
    public void onCreate_hasCorrectType() {
        QCSlider slider = createSlider(/* action= */ null, /* disabledAction= */ null);
        assertThat(slider.getType()).isEqualTo(QC_TYPE_SLIDER);
    }

    @Test
    public void onBundle_nullActions_noCrash() {
        QCSlider slider = createSlider(/* action= */ null, /* disabledAction= */ null);
        writeAndLoadFromBundle(slider);
        // Test passes if this doesn't crash
    }

    @Test
    public void createFromParcel_accurateData() {
        QCSlider slider = createSlider(mDefaultAction, mDefaultDisabledAction);
        QCSlider newSlider = writeAndLoadFromBundle(slider);
        assertThat(newSlider.getType()).isEqualTo(QC_TYPE_SLIDER);
        assertThat(newSlider.getPrimaryAction()).isNotNull();
        assertThat(newSlider.getDisabledClickAction()).isNotNull();
        assertThat(newSlider.getMin()).isEqualTo(MIN);
        assertThat(newSlider.getMax()).isEqualTo(MAX);
        assertThat(newSlider.getValue()).isEqualTo(VALUE);
    }

    private QCSlider createSlider(PendingIntent action, PendingIntent disabledAction) {
        return new QCSlider.Builder()
                .setMin(MIN)
                .setMax(MAX)
                .setValue(VALUE)
                .setInputAction(action)
                .setDisabledClickAction(disabledAction)
                .build();
    }
}
