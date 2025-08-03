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

package com.android.systemui.car.hvac;

import static android.car.VehiclePropertyIds.HVAC_SEAT_TEMPERATURE;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
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
public class SeatTemperatureLevelButtonTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int AREA_ID = 1;
    private static final int PROPERTY_ID = HVAC_SEAT_TEMPERATURE;

    private SeatTemperatureLevelButton mSeatTemperatureLevelButton;

    @Mock
    private HvacPropertySetter mHvacPropertySetter;
    @Mock
    private CarPropertyConfig mCarPropertyConfig;
    @Mock
    private AreaIdConfig mAreaIdConfig;
    @Mock
    private CarPropertyValue mCarPropertyValue;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        View testView = LayoutInflater.from(getContext()).inflate(
                R.layout.seat_heat_level_button_test, /* root= */ null);
        mSeatTemperatureLevelButton = testView.findViewById(R.id.seat_heat_button);
        mSeatTemperatureLevelButton.setHvacPropertySetter(mHvacPropertySetter);
        when(mCarPropertyConfig.getAreaIdConfig(AREA_ID)).thenReturn(mAreaIdConfig);
        when(mAreaIdConfig.getMaxValue()).thenReturn(3);
        mSeatTemperatureLevelButton.setConfigInfo(mCarPropertyConfig);
    }

    @Test
    public void onClick_currentLevelZero_setsHeatLevelToOne() {
        setCarPropertyValue(0);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 1);
    }

    @Test
    public void onClick_currentLevelOne_setsHeatLevelToTwo() {
        setCarPropertyValue(1);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 2);
    }

    @Test
    public void onClick_currentLevelTwo_setsHeatLevelToThree() {
        setCarPropertyValue(2);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 3);
    }

    @Test
    public void onClick_currentLevelThree_setsHeatLevelToZero() {
        setCarPropertyValue(3);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 0);
    }

    @Test
    public void onLongClick_currentLevelZero_setsHeatLevelToThree() {
        setCarPropertyValue(0);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performLongClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 3);
    }

    @Test
    public void onLongClick_currentLevelOne_setsHeatLevelToZero() {
        setCarPropertyValue(1);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performLongClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 0);
    }

    @Test
    public void onLongClick_currentLevelTwo_setsHeatLevelToZero() {
        setCarPropertyValue(2);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performLongClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 0);
    }

    @Test
    public void onLongClick_currentLevelThree_setsHeatLevelToZero() {
        setCarPropertyValue(3);
        mSeatTemperatureLevelButton.onPropertyChanged(mCarPropertyValue);

        mSeatTemperatureLevelButton.performLongClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, AREA_ID, 0);
    }

    private void setCarPropertyValue(int value) {
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_ID);
        when(mCarPropertyValue.getPropertyId()).thenReturn(PROPERTY_ID);
        when(mCarPropertyValue.getValue()).thenReturn(value);
    }
}
