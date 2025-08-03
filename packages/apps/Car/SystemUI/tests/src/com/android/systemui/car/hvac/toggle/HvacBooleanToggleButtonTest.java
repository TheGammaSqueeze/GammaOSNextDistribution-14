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

import static android.car.VehiclePropertyIds.HVAC_AC_ON;
import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import static org.mockito.ArgumentMatchers.anyBoolean;
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
public class HvacBooleanToggleButtonTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int AREA_ID = 1;
    private static final int PROPERTY_ID = HVAC_AC_ON;

    private View mTestLayout;
    private HvacBooleanToggleButton mDefaultButton;
    private HvacBooleanToggleButton mTurnOnWhenPowerOffButton;
    private HvacBooleanToggleButton mTurnOffWhenAutoOnButton;
    @Mock
    private HvacPropertySetter mHvacPropertySetterDefault;
    @Mock
    private HvacPropertySetter mHvacPropertySetterTurnOnWhenPowerOff;
    @Mock
    private HvacPropertySetter mHvacPropertySetterTurnOffWhenAutoOn;
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
        mDefaultButton = mTestLayout.findViewById(R.id.bool_toggle_button_default);
        mTurnOnWhenPowerOffButton = mTestLayout.findViewById(
                R.id.bool_toggle_button_do_not_turn_off_when_power_off);
        mTurnOffWhenAutoOnButton = mTestLayout.findViewById(
                R.id.bool_toggle_button_turn_off_when_auto_on);

        mDefaultButton.setHvacPropertySetter(mHvacPropertySetterDefault);
        mTurnOnWhenPowerOffButton.setHvacPropertySetter(mHvacPropertySetterTurnOnWhenPowerOff);
        mTurnOffWhenAutoOnButton.setHvacPropertySetter(mHvacPropertySetterTurnOffWhenAutoOn);
        mDefaultButton.setDisableViewIfPowerOff(true);
        mTurnOnWhenPowerOffButton.setDisableViewIfPowerOff(false);
        mTurnOffWhenAutoOnButton.setDisableViewIfPowerOff(true);
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOff_currentValueFalse_setsValueToTrue() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(false);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, true);
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOff_currentValueTrue_setsValueToFalse() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(true);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.callOnClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, false);
    }

    @Test
    public void onClickDefaultButton_powerOff_autoOff_currentValueFalse_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(false);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault, never()).setHvacProperty(anyInt(), anyInt(),
                anyBoolean());
    }

    @Test
    public void onClickDefaultButton_powerOff_autoOff_currentValueTrue_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(true);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault, never()).setHvacProperty(anyInt(), anyInt(),
                anyBoolean());
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOn_currentValueFalse_setsValueToTrue() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(false);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, true);
    }

    @Test
    public void onClickDefaultButton_powerOn_autoOn_currentValueTrue_setsValueToFalse() {
        setPowerPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mDefaultButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(true);
        mDefaultButton.onPropertyChanged(mCarPropertyValue);

        mDefaultButton.performClick();

        verify(mHvacPropertySetterDefault).setHvacProperty(PROPERTY_ID, AREA_ID, false);
    }

    @Test
    public void onClickTurnOnWhenPowerOffButton_powerOff_currentValueTrue_setsValueToFalse() {
        setPowerPropertyValue(false);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(true);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mCarPropertyValue);

        mTurnOnWhenPowerOffButton.performClick();

        verify(mHvacPropertySetterTurnOnWhenPowerOff).setHvacProperty(PROPERTY_ID, AREA_ID, false);
    }

    @Test
    public void onClickTurnOnWhenPowerOffButton_powerOff_currentValueFalse_setsValueToTrue() {
        setPowerPropertyValue(false);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(false);
        mTurnOnWhenPowerOffButton.onPropertyChanged(mCarPropertyValue);

        mTurnOnWhenPowerOffButton.performClick();

        verify(mHvacPropertySetterTurnOnWhenPowerOff).setHvacProperty(PROPERTY_ID, AREA_ID, true);
    }

    @Test
    public void onClickTurnOffWhenAutoOnButton_autoOn_currentValueTrue_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn, never()).setHvacProperty(anyInt(), anyInt(),
                anyBoolean());
    }

    @Test
    public void onClickTurnOffWhenAutoOnButton_autoOn_currentValueFalse_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn, never()).setHvacProperty(anyInt(), anyInt(),
                anyBoolean());
    }

    @Test
    public void onClickWhenNotHvacPowerDependent_autoOff_setsNewValue() {
        mTurnOffWhenAutoOnButton.setDisableViewIfPowerOff(false);
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn).setHvacProperty(PROPERTY_ID, AREA_ID, false);
    }

    @Test
    public void onClickWhenNotHvacPowerDependent_autoOn_doesNotSetNewValue() {
        mTurnOffWhenAutoOnButton.setDisableViewIfPowerOff(false);
        setPowerPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mHvacAutoProperty);
        setCarPropertyValue(false);
        mTurnOffWhenAutoOnButton.onPropertyChanged(mCarPropertyValue);

        mTurnOffWhenAutoOnButton.performClick();

        verify(mHvacPropertySetterTurnOffWhenAutoOn, never()).setHvacProperty(anyInt(), anyInt(),
                anyBoolean());
    }

    private void setCarPropertyValue(boolean value) {
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
