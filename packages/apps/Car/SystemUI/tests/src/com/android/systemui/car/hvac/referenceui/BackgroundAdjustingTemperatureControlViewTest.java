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

package com.android.systemui.car.hvac.referenceui;

import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.CarPropertyValue;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class BackgroundAdjustingTemperatureControlViewTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int AREA_ID = 99;
    private static final int PROPERTY_ID = HVAC_TEMPERATURE_SET;

    private BackgroundAdjustingTemperatureControlView mTemperatureControlView;
    private int[] mUpperLimits;
    private int[] mTempColors;
    @Mock
    private HvacPropertySetter mHvacPropertySetter;
    @Mock
    private CarPropertyValue mCarPropertyValue;
    @Mock
    private CarPropertyValue mHvacPowerProperty;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        View testView = LayoutInflater.from(getContext()).inflate(
                R.layout.background_adjusting_temperature_control_view_test, /* root= */ null);

        mTemperatureControlView = testView.findViewById(R.id.temperature_control_view);
        mTemperatureControlView.onFinishInflate();
        mTemperatureControlView.setHvacPropertySetter(mHvacPropertySetter);

        mUpperLimits = mTemperatureControlView.getUpperLimits();
        mTempColors = mTemperatureControlView.getTempColors();
    }

    @Test
    public void getTemperatureColor_temperatureWithinRange_backgroundAdjustsToCorrespondingColor() {
        for (int i = 0; i < mUpperLimits.length; i++) {
            assertThat(mTemperatureControlView.getTemperatureColor(mUpperLimits[i] / 10f))
                    .isEqualTo(mTempColors[i]);
        }
    }

    @Test
    public void getTemperatureColor_temperatureExceedsRange_backgroundSetToTheLastColorDefined() {
        int lastUpperLimit = mUpperLimits[mUpperLimits.length - 1];
        int lastColor = mTempColors[mTempColors.length - 1];

        assertThat(mTemperatureControlView.getTemperatureColor((lastUpperLimit + 10f) / 10f))
                .isEqualTo(lastColor);
    }
}
