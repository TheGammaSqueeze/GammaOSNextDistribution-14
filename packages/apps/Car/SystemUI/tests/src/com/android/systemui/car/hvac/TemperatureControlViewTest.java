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

import static android.car.VehiclePropertyIds.HVAC_POWER_ON;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.os.SystemClock;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@CarSystemUiTest
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TemperatureControlViewTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int AREA_ID = 99;
    private static final int PROPERTY_ID = HVAC_TEMPERATURE_SET;
    private static final List<Integer> CONFIG_ARRAY = List.of(160, 280, 5, 600, 840, 10);

    private TemperatureControlView mTemperatureControlView;
    @Mock
    private HvacPropertySetter mHvacPropertySetter;
    @Mock
    private CarPropertyConfig mCarPropertyConfig;
    @Mock
    private CarPropertyValue mCarPropertyValue;
    @Mock
    private CarPropertyValue mHvacPowerProperty;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTemperatureControlView = LayoutInflater.from(getContext()).inflate(
                R.layout.temperature_control_view, /* root= */ null).findViewById(R.id.test_hvac);
        mTemperatureControlView.setHvacPropertySetter(mHvacPropertySetter);
        mTemperatureControlView.onFinishInflate();

        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_ID);
        when(mCarPropertyValue.getPropertyId()).thenReturn(HVAC_TEMPERATURE_SET);
        when(mCarPropertyValue.getStatus()).thenReturn(CarPropertyValue.STATUS_AVAILABLE);
        when(mCarPropertyValue.getValue()).thenReturn(20f);
        when(mCarPropertyConfig.getConfigArray()).thenReturn(CONFIG_ARRAY);
        mTemperatureControlView.setConfigInfo(mCarPropertyConfig);
    }

    @Test
    public void onPropertyChanged_inCelsius_displaysSetTemperatureInCelsius()
            throws InterruptedException {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);

        assertThat(mTemperatureControlView.getTempInDisplay()).isEqualTo(
                String.format(mTemperatureControlView.getTempFormatInCelsius(), 20f));
    }

    @Test
    public void onPropertyChanged_inFahrenheit_displaysSetTemperatureInFahrenheit() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);

        assertThat(mTemperatureControlView.getTempInDisplay()).isEqualTo(
                String.format(mTemperatureControlView.getTempFormatInFahrenheit(),
                        68f));
    }

    @Test
    public void onHoldIncreaseButtonDown_inCelsius_increasesThreeDegreesCelsius() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View increaseButton = mTemperatureControlView.findViewById(R.id.hvac_increase_button);
        int intervalTimes = 3;
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);
        float increment = mTemperatureControlView.getCelsiusTemperatureIncrement();
        float expectedNewTemperature = (float) mCarPropertyValue.getValue() + increment;

        // Hold the button down for more than BUTTON_REPEAT_INTERVAL_MS * 2 but less than
        // BUTTON_REPEAT_INTERVAL_MS * 3 so that the temperature is incremented twice after the
        // initial increment when the button is first pressed.
        touchViewForDurationMs(increaseButton,
                (long) (TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * (intervalTimes - 0.5)));

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(eq(HVAC_TEMPERATURE_SET),
                eq(AREA_ID), AdditionalMatchers.eq(expectedNewTemperature, 0.1f));
    }

    @Test
    public void onHoldDecreaseButtonDown_inCelsius_decreasesThreeDegreesCelsius() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View decreaseButton = mTemperatureControlView.findViewById(R.id.hvac_decrease_button);
        int intervalTimes = 3;
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);
        float increment = mTemperatureControlView.getCelsiusTemperatureIncrement();
        float expectedNewTemperature = (float) mCarPropertyValue.getValue() - increment;

        // Hold the button down for more than BUTTON_REPEAT_INTERVAL_MS * 2 but less than
        // BUTTON_REPEAT_INTERVAL_MS * 3 so that the temperature is decremented twice after the
        // initial decrement when the button is first pressed.
        touchViewForDurationMs(decreaseButton,
                (long) (TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * (intervalTimes - 0.5)));

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(eq(HVAC_TEMPERATURE_SET),
                eq(AREA_ID), AdditionalMatchers.eq(expectedNewTemperature, 0.1f));
    }

    @Test
    public void onHoldIncreaseButtonDown_inFahrenheit_increasesThreeDegreesFahrenheit() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View increaseButton = mTemperatureControlView.findViewById(R.id.hvac_increase_button);
        int intervalTimes = 3;
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);
        float increment = mTemperatureControlView.getCelsiusTemperatureIncrement();
        float expectedNewTemperature = (float) mCarPropertyValue.getValue() + increment;

        // Hold the button down for more than BUTTON_REPEAT_INTERVAL_MS * 2 but less than
        // BUTTON_REPEAT_INTERVAL_MS * 3 so that the temperature is incremented twice after the
        // initial increment when the button is first pressed.
        touchViewForDurationMs(increaseButton,
                (long) (TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * (intervalTimes - 0.5)));

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(eq(HVAC_TEMPERATURE_SET),
                eq(AREA_ID), AdditionalMatchers.eq(expectedNewTemperature, 0.1f));
    }

    @Test
    public void onHoldDecreaseButtonDown_inFahrenheit_decreasesThreeDegreesFahrenheit() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View decreaseButton = mTemperatureControlView.findViewById(R.id.hvac_decrease_button);
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);
        int intervalTimes = 3;
        float increment = mTemperatureControlView.getCelsiusTemperatureIncrement();
        float expectedNewTemperature = (float) mCarPropertyValue.getValue() - increment;

        // Hold the button down for more than BUTTON_REPEAT_INTERVAL_MS * 2 but less than
        // BUTTON_REPEAT_INTERVAL_MS * 3 so that the temperature is decremented twice after the
        // initial decrement when the button is first pressed.
        touchViewForDurationMs(decreaseButton,
                (long) (TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * (intervalTimes - 0.5)));

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(eq(HVAC_TEMPERATURE_SET),
                eq(AREA_ID), AdditionalMatchers.eq(expectedNewTemperature, 0.1f));
    }

    @Test
    public void onHoldIncreaseButtonDown_powerOff_doesNotSetNewValues() {
        setPowerPropertyValue(false);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View increaseButton = mTemperatureControlView.findViewById(R.id.hvac_increase_button);
        int intervalTimes = 3;

        // Hold the button down for more than BUTTON_REPEAT_INTERVAL_MS * 2 but less than
        // BUTTON_REPEAT_INTERVAL_MS * 3 so that the temperature is incremented twice after the
        // initial increment when the button is first pressed.
        touchViewForDurationMs(increaseButton,
                (long) (TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * (intervalTimes - 0.5)));

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyFloat());
    }

    @Test
    public void onHoldDecreaseButtonDown_powerOff_doesNotSetNewValues() {
        setPowerPropertyValue(false);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View decreaseButton = mTemperatureControlView.findViewById(R.id.hvac_decrease_button);
        int intervalTimes = 3;

        // Hold the button down for more than BUTTON_REPEAT_INTERVAL_MS * 2 but less than
        // BUTTON_REPEAT_INTERVAL_MS * 3 so that the temperature is decremented twice after the
        // initial decrement when the button is first pressed.
        touchViewForDurationMs(decreaseButton,
                (long) (TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * (intervalTimes - 0.5)));

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyFloat());
    }

    private void touchViewForDurationMs(View view, long duration) {
        view.dispatchTouchEvent(createMotionEvent(ACTION_DOWN));
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        view.dispatchTouchEvent(createMotionEvent(ACTION_UP));
        waitForIdleSync();
    }

    private MotionEvent createMotionEvent(int action) {
        long eventTime = SystemClock.uptimeMillis();
        return MotionEvent.obtain(0, eventTime, action, 0, 0, 0);
    }

    private void setPowerPropertyValue(boolean value) {
        when(mHvacPowerProperty.getAreaId()).thenReturn(GLOBAL_AREA_ID);
        when(mHvacPowerProperty.getPropertyId()).thenReturn(HVAC_POWER_ON);
        when(mHvacPowerProperty.getValue()).thenReturn(value);
    }
}
