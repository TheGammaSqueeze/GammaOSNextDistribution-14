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
import static android.car.VehiclePropertyIds.HVAC_FAN_DIRECTION;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import static com.android.systemui.car.hvac.referenceui.FanDirectionButtons.FAN_DIRECTION_FACE;
import static com.android.systemui.car.hvac.referenceui.FanDirectionButtons.FAN_DIRECTION_FACE_FLOOR;
import static com.android.systemui.car.hvac.referenceui.FanDirectionButtons.FAN_DIRECTION_FLOOR;
import static com.android.systemui.car.hvac.referenceui.FanDirectionButtons.FAN_DIRECTION_FLOOR_DEFROSTER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyValue;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.hvac.referenceui.FanDirectionButtons;
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
public class FanDirectionButtonTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int PROPERTY_ID = HVAC_FAN_DIRECTION;

    private FanDirectionButtons mFanDirectionButtons;
    private ImageView mDirectionFaceButton;
    private ImageView mDirectionFaceAndFloorButton;
    private ImageView mDirectionFloorButton;
    private ImageView mDirectionDefrostButton;

    @Mock
    private CarPropertyValue mCarPropertyValue;
    @Mock
    private CarPropertyValue mHvacPowerProperty;
    @Mock
    private CarPropertyValue mHvacAutoProperty;
    @Mock
    private HvacPropertySetter mHvacPropertySetter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFanDirectionButtons = LayoutInflater.from(getContext()).inflate(
                R.layout.fan_direction_buttons_test, /* root= */ null).findViewById(
                R.id.fan_direction_buttons);

        mDirectionFaceButton = mFanDirectionButtons.findViewById(R.id.direction_face);
        mDirectionFaceAndFloorButton = mFanDirectionButtons.findViewById(
                R.id.direction_face_and_floor);
        mDirectionFloorButton = mFanDirectionButtons.findViewById(R.id.direction_floor);
        mDirectionDefrostButton = mFanDirectionButtons.findViewById(R.id.direction_defrost);

        mFanDirectionButtons.setHvacPropertySetter(mHvacPropertySetter);
        mFanDirectionButtons.setDisableViewIfPowerOff(true);
    }

    @Test
    public void onPropertyChanged_fanDirectionFace_faceDirectionButtonSelected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FACE);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFaceButton.isSelected()).isTrue();
    }

    @Test
    public void onPropertyChanged_fanDirectionFace_otherButtonsUnselected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FACE);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFaceAndFloorButton.isSelected()).isFalse();
        assertThat(mDirectionFloorButton.isSelected()).isFalse();
        assertThat(mDirectionDefrostButton.isSelected()).isFalse();
    }

    @Test
    public void onPropertyChanged_fanDirectionFaceAndFloor_faceAndFloorDirectionButtonSelected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FACE_FLOOR);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFaceAndFloorButton.isSelected()).isTrue();
    }

    @Test
    public void onPropertyChanged_fanDirectionFaceAndFloor_otherButtonsUnselected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FACE_FLOOR);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFaceButton.isSelected()).isFalse();
        assertThat(mDirectionFloorButton.isSelected()).isFalse();
        assertThat(mDirectionDefrostButton.isSelected()).isFalse();
    }

    @Test
    public void onPropertyChanged_fanDirectionFloor_floorDirectionButtonSelected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FLOOR);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFloorButton.isSelected()).isTrue();
    }

    @Test
    public void onPropertyChanged_fanDirectionFloor_otherButtonsUnselected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FLOOR);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFaceButton.isSelected()).isFalse();
        assertThat(mDirectionFaceAndFloorButton.isSelected()).isFalse();
        assertThat(mDirectionDefrostButton.isSelected()).isFalse();
    }

    @Test
    public void onPropertyChanged_fanDirectionDefrost_defrostDirectionButtonSelected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FLOOR_DEFROSTER);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionDefrostButton.isSelected()).isTrue();
    }

    @Test
    public void onPropertyChanged_fanDirectionDefrost_otherButtonsUnselected() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);

        setCarPropertyValue(FAN_DIRECTION_FLOOR_DEFROSTER);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFaceButton.isSelected()).isFalse();
        assertThat(mDirectionFaceAndFloorButton.isSelected()).isFalse();
        assertThat(mDirectionFloorButton.isSelected()).isFalse();
    }

    @Test
    public void onPropertyChanged_fanDirectionInvalid_shouldDoNothing() {
        setCarPropertyValue(FAN_DIRECTION_FACE);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        assertThat(mDirectionFaceButton.isSelected()).isTrue();
        //set invalid property value
        setCarPropertyValue(null);
        mFanDirectionButtons.onPropertyChanged(mCarPropertyValue);
        waitForIdleSync();

        //does not change the state of Fan Direction
        assertThat(mDirectionFaceButton.isSelected()).isTrue();
    }

    @Test
    public void onClickFaceDirectionButton_allowsControl_setsFanDirectionToFace() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFaceButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID,
                FAN_DIRECTION_FACE);
    }

    @Test
    public void onClickFaceAndFloorDirectionButton_allowsControl_setsFanDirectionToFaceAndFloor() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFaceAndFloorButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID,
                FAN_DIRECTION_FACE_FLOOR);
    }

    @Test
    public void onClickFloorDirectionButton_allowsControl_setsFanDirectionToFloor() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFloorButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID,
                FAN_DIRECTION_FLOOR);
    }

    @Test
    public void onClickDefrostDirectionButton_allowsControl_setsFanDirectionToDefrost() {
        setPowerPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionDefrostButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID,
                FAN_DIRECTION_FLOOR_DEFROSTER);
    }

    @Test
    public void onClickFaceDirectionButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFaceButton.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickFaceAndFloorDirectionButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFaceAndFloorButton.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickFloorDirectionButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFloorButton.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickDefrostDirectionButton_doesNotAllowsControl_doesNotSetNewValue() {
        setPowerPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionDefrostButton.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onClickWhenNotHvacPowerDependent_autoOff_setsNewValue() {
        mFanDirectionButtons.setDisableViewIfPowerOff(false);
        setPowerPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFaceButton.performClick();

        verify(mHvacPropertySetter).setHvacProperty(PROPERTY_ID, GLOBAL_AREA_ID,
                FAN_DIRECTION_FACE);
    }

    @Test
    public void onClickWhenNotHvacPowerDependent_autoOn_doesNotSetNewValue() {
        mFanDirectionButtons.setDisableViewIfPowerOff(false);
        setPowerPropertyValue(false);
        mFanDirectionButtons.onPropertyChanged(mHvacPowerProperty);
        setAutoPropertyValue(true);
        mFanDirectionButtons.onPropertyChanged(mHvacAutoProperty);
        waitForIdleSync();

        mDirectionFaceButton.performClick();

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyInt());
    }

    private void setCarPropertyValue(@Nullable Integer value) {
        when(mCarPropertyValue.getAreaId()).thenReturn(GLOBAL_AREA_ID);
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
