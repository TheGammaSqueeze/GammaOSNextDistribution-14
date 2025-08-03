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

package com.android.systemui.car.hvac.toggle;

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
public class HvacIntegerToggleButtonTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int AREA_ID = 1;
    private static final int PROPERTY_ID = HVAC_FAN_SPEED;
    private static final int ON_VALUE = 1;
    private static final int OFF_VALUE = 0;

    private View mTestLayout;
    private HvacIntegerToggleButton mDefaultButton;
    private HvacIntegerToggleButton mTurnOnWhenPowerOffButton;
    private HvacIntegerToggleButton mTurnOffWhenAutoOnButton;
    private HvacIntegerToggleButton mKeepOnPreventOffButton;
    @Mock
    private HvacPropertySetter mHvacPropertySetterDefault;
    @Mock
    private HvacPropertySetter mHvacPropertySetterTurnOnWhenPowerOff;
    @Mock
    private HvacPropertySetter mHvacPropertySetterTurnOffWhenAutoOn;
    @Mock
    private HvacPropertySetter mHvacPropertySetterKeepOnPreventOff;
    @Mock
    private CarPropertyValue mCarPropertyValue;
    @Mock
    private CarPropertyValue mHvacPowerProperty;
    @Mock
    private CarPropertyValue mHvacAutoProperty;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestLayout = LayoutInflater.from(getContext()).inflate(
                R.layout.hvac_toggle_button_view, /* root= */ null);
        mDefaultButton = mTestLayout.findViewById(R.id.int_toggle_button_default);
        mTurnOnWhenPowerOffButton = mTestLayout.findViewById(
                R.id.int_toggle_button_do_not_turn_off_when_power_off);
        mTurnOffWhenAutoOnButton = mTestLayout.findViewById(
                R.id.int_toggle_button_turn_off_when_auto_on);
        mKeepOnPreventOffButton = mTestLayout.findViewById(
                R.id.int_toggle_button_keep_on_prevent_off);

        mDefaultButton.setHvacPropertySetter(mHvacPropertySetterDefault);
        mTurnOnWhenPowerOffButton.setHvacPropertySetter(mHvacPropertySetterTurnOnWhenPowerOff);
        mTurnOffWhenAutoOnButton.setHvacPropertySetter(mHvacPropertySetterTurnOffWhenAutoOn);
        mKeepOnPreventOffButton.setHvacPropertySetter(mHvacPropertySetterKeepOnPreventOff);
        mDefaultButton.setDisableViewIfPowerOff(true);
        mTurnOnWhenPowerOffButton.setDisableViewIfPowerOff(false);
        mTurnOffWhenAutoOnButton.setDisableViewIfPowerOff(true);
        mKeepOnPreventOffButton.setDisableViewIfPowerOff(true);
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOff_currentValueFalse_setsValueToTrue() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, ON_VALUE);
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOff_currentValueTrue_setsValueToFalse() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(ON_VALUE);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.callOnClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, OFF_VALUE);
    }

    @Test
    public void onClickDefaultButton_powerOff_autoOff_currentValueFalse_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault, never()).setHvacProperty(anyInt(), anyInt(),
                anyInt());
    }

    @Test
    public void onClickDefaultButton_powerOff_autoOff_currentValueTrue_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(ON_VALUE);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault, never()).setHvacProperty(anyInt(), anyInt(),
                anyInt());
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOn_currentValueFalse_setsValueToTrue() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, ON_VALUE);
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOn_currentValueTrue_setsValueToFalse() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(ON_VALUE);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, OFF_VALUE);
    }

    @Test
    public void onClickTurnOnWhenPowerOffButton_powerOff_currentValueTrue_setsValueToFalse() {
        setPowerPropertyValue(false);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(ON_VALUE);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mCarPropertyValue);

        mTurnOnWhenPowerOffButton.performClick();

        verify(mHvacPropertySetterTurnOnWhenPowerOff).setHvacProperty(PROPERTY_ID, AREA_ID,
                OFF_VALUE);
    }

    @Test
    public void onClickTurnOnWhenPowerOffButton_powerOff_currentValueFalse_setsValueToTrue() {
        setPowerPropertyValue(false);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mCarPropertyValue);

        mTurnOnWhenPowerOffButton.performClick();

        verify(mHvacPropertySetterTurnOnWhenPowerOff).setHvacProperty(PROPERTY_ID, AREA_ID,
                ON_VALUE);
    }

    @Test
    public void onClickTurnOffWhenAutoOnButton_autoOn_currentValueTrue_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(ON_VALUE);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn, never()).setHvacProperty(anyInt(), anyInt(),
                anyInt());
    }

    @Test
    public void onClickTurnOffWhenAutoOnButton_autoOn_currentValueFalse_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn, never()).setHvacProperty(anyInt(), anyInt(),
                anyInt());
    }

    @Test
    public void onClickKeepOnPreventOffButton_currentValueOff_turnOn() {
        setPowerPropertyValue(true);
        mKeepOnPreventOffButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mKeepOnPreventOffButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mKeepOnPreventOffButton.onPropertyChanged(mCarPropertyValue);

        mKeepOnPreventOffButton.performClick();

        verify(mHvacPropertySetterKeepOnPreventOff).setHvacProperty(PROPERTY_ID, AREA_ID, ON_VALUE);
    }

    @Test
    public void onClickKeepOnPreventOffButton_currentValueOn_preventOff() {
        setPowerPropertyValue(true);
        mKeepOnPreventOffButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mKeepOnPreventOffButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(ON_VALUE);
        mKeepOnPreventOffButton.onPropertyChanged(mCarPropertyValue);

        mKeepOnPreventOffButton.performClick();

        verify(mHvacPropertySetterKeepOnPreventOff, never()).setHvacProperty(anyInt(), anyInt(),
                anyInt());
    }

    @Test
    public void onClickWhenNotHvacPowerDependent_autoOff_setsNewValue() {
        mTurnOffWhenAutoOnButton.setDisableViewIfPowerOff(false);
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn).setHvacProperty(PROPERTY_ID, AREA_ID,
                ON_VALUE);
    }

    @Test
    public void onClickWhenNotHvacPowerDependent_autoOn_doesNotSetNewValue() {
        mTurnOffWhenAutoOnButton.setDisableViewIfPowerOff(false);
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(OFF_VALUE);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn, never()).setHvacProperty(anyInt(), anyInt(),
                anyInt());
    }

    private void setCarPropertyValue(int value) {
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_ID);
        when(mCarPropertyValue.getPropertyId()).thenReturn(PROPERTY_ID);
        when(mCarPropertyValue.getValue()).thenReturn(value);
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
