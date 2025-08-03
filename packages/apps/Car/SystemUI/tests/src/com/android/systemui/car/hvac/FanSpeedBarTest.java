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

import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_FAN_SPEED;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyValue;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.hvac.referenceui.FanSpeedBar;
import com.android.systemui.car.hvac.referenceui.FanSpeedBarSegment;
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
public class FanSpeedBarTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int PROPERTY_ID = HVAC_FAN_SPEED;

    private FanSpeedBar mFanSpeedBar;
    private TextView mOffButton;
    private FanSpeedBarSegment mFanSpeed1;
    private FanSpeedBarSegment mFanSpeed2;
    private FanSpeedBarSegment mFanSpeed3;
    private FanSpeedBarSegment mFanSpeed4;
    private TextView mMaxButton;

    @Mock
    private CarPropertyValue mHvacPowerProperty;
    @Mock
    private CarPropertyValue mHvacAutoProperty;
    @Mock
    private HvacPropertySetter mHvacPropertySetter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mFanSpeedBar = LayoutInflater.from(getContext()).inflate(
                R.layout.fan_speed_bar_test, /* root= */ null).findViewById(R.id.fan_speed_bar);
        mOffButton = mFanSpeedBar.findViewById(com.android.systemui.R.id.fan_off);
        mFanSpeed1 = mFanSpeedBar.findViewById(com.android.systemui.R.id.fan_speed_1);
        mFanSpeed2 = mFanSpeedBar.findViewById(com.android.systemui.R.id.fan_speed_2);
        mFanSpeed3 = mFanSpeedBar.findViewById(com.android.systemui.R.id.fan_speed_3);
        mFanSpeed4 = mFanSpeedBar.findViewById(com.android.systemui.R.id.fan_speed_4);
        mMaxButton = mFanSpeedBar.findViewById(com.android.systemui.R.id.fan_max);
        mFanSpeedBar.setHvacPropertySetter(mHvacPropertySetter);
    }

    @Test
    public void onClickOffButton_allowsControl_setsFanSpeedToMin() {
        setPowerPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mOffButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID, /* val= */ 1);
    }

    @Test
    public void onClickSpeedOneButton_allowsControl_setsFanSpeedToTwo() {
        setPowerPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed1.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID, /* val= */ 2);
    }

    @Test
    public void onClickSpeedTwoButton_allowsControl_setsFanSpeedToThree() {
        setPowerPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed2.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID, /* val= */ 3);
    }

    @Test
    public void onClickSpeedThreeButton_allowsControl_setsFanSpeedToFour() {
        setPowerPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed3.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID, /* val= */ 4);
    }

    @Test
    public void onClickSpeedFourButton_allowsControl_setsFanSpeedToFive() {
        setPowerPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed4.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID, /* val= */ 5);
    }

    @Test
    public void onClickMaxButton_allowsControl_setsFanSpeedToMax() {
        setPowerPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mMaxButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID, /* val= */ 6);
    }

    @Test
    public void onClickOffButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mOffButton.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickSpeedOneButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed1.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickSpeedTwoButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed2.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickSpeedThreeButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed3.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickSpeedFourButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mFanSpeed4.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickMaxButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanSpeedBar.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanSpeedBar.onPropertyChanged(mHvacAutoProperty);

        mMaxButton.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    private void setPowerPropertyValue(boolean value) {
        when(mHvacPowerProperty.getAreaId()).thenReturn(GLOBAL_AREA_ID);
        when(mHvacPowerProperty.getPropertyId()).thenReturn(HVAC_POWER_ON);
        when(mHvacPowerProperty.getValue()).thenReturn(value);
    }

    private void setAutoPropertyValue(boolean value) {
        when(mHvacAutoProperty.getAreaId()).thenReturn(GLOBAL_AREA_ID);
        when(mHvacAutoProperty.getPropertyId()).thenReturn(HVAC_AUTO_ON);
        when(mHvacAutoProperty.getValue()).thenReturn(value);
    }
}
