/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher.calmmode;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.VehicleUnit;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.icu.util.MeasureUnit;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.apps.common.testutils.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class TemperatureViewModelTest extends AbstractExtendedMockitoTestCase {
    private static final int TEST_AREA_ID = 1234;
    private static final float TEST_OUTSIDE_TEMPERATURE_C = 25f;
    private static final float TEST_OUTSIDE_TEMPERATURE_F = 77f;
    private TemperatureViewModel mTemperatureViewModel;
    @Mock
    private Car mCar;
    @Mock
    private CarPropertyManager mCarPropertyManager;
    @Mock
    private CarPropertyValue mUnitCarPropVal;
    @Mock
    private CarPropertyValue mValueCarPropVal;
    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mCar.getCarManager(CarPropertyManager.class)).thenReturn(mCarPropertyManager);
        when(mCar.isConnected()).thenReturn(true);
        when(mUnitCarPropVal.getPropertyId())
                .thenReturn(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS);
        when(mUnitCarPropVal.getValue()).thenReturn(VehicleUnit.CELSIUS);
        when(mValueCarPropVal.getPropertyId())
                .thenReturn(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE);
        when(mValueCarPropVal.getValue()).thenReturn(TEST_OUTSIDE_TEMPERATURE_C);
        when(mCarPropertyManager.getAreaId(anyInt(), anyInt())).thenReturn(TEST_AREA_ID);
        when(mCarPropertyManager.getProperty(
                        eq(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS), anyInt()))
                .thenReturn(mUnitCarPropVal);
        when(mCarPropertyManager.getProperty(
                        eq(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE), anyInt()))
                .thenReturn(mValueCarPropVal);
        when(Car.createCar(any())).thenReturn(mCar);
        mTemperatureViewModel =
                new TemperatureViewModel(ApplicationProvider.getApplicationContext());
        mTemperatureViewModel = spy(mTemperatureViewModel);
    }

    @Override
    protected void onSessionBuilder(@NonNull CustomMockitoSessionBuilder builder) {
        builder.mockStatic(Car.class);
    }


    @Test
    public void newTemperatureViewModel_nullCar_throwsException() {
        when(Car.createCar(any())).thenReturn(null);

        assertThrows(
                NullPointerException.class,
                () -> new TemperatureViewModel(ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void newTemperatureViewModel_nullCarPropertyManager_throwsException() {
        when(mCar.getCarManager(CarPropertyManager.class)).thenReturn(null);

        assertThrows(
                NullPointerException.class,
                () -> new TemperatureViewModel(ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void onChangeEvent_propEnvOutsideTemp_handleValueChangeInvoked() {
        doNothing().when(mTemperatureViewModel).handleValueChange(any());

        mTemperatureViewModel.onChangeEvent(mValueCarPropVal);

        verify(mTemperatureViewModel).handleValueChange(eq(mValueCarPropVal));
    }

    @Test
    public void onChangeEvent_propHvacDisplayUnits_handleUnitChangeInvoked() {
        doNothing().when(mTemperatureViewModel).handleUnitChange(any());

        mTemperatureViewModel.onChangeEvent(mUnitCarPropVal);

        verify(mTemperatureViewModel).handleUnitChange(mUnitCarPropVal);
    }

    @Test
    public void onChangeEvent_propNull_nothingInvoked() {
        mTemperatureViewModel.onChangeEvent(null);

        verify(mTemperatureViewModel, never()).handleValueChange(any());
        verify(mTemperatureViewModel, never()).handleUnitChange(any());
    }

    @Test
    public void testOnCleared_expectedMethodsInvoked() {
        mTemperatureViewModel.onCleared();

        verify(mCar).disconnect();
        verify(mCarPropertyManager, times(2)).unregisterCallback(any(), anyInt());
    }

    @Test
    public void testGetCarPropertyValue_notNull() {
        assertNotNull(mTemperatureViewModel.getCarPropertyValue(
                        VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE));
        assertNotNull(mTemperatureViewModel.getCarPropertyValue(
                        VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS));
    }

    @Test
    public void testHandleUnitChange_changeUnitFromCtoF_dataChanged() {
        mTemperatureViewModel.onChangeEvent(mValueCarPropVal);
        mTemperatureViewModel.onChangeEvent(mUnitCarPropVal);
        when(mUnitCarPropVal.getValue()).thenReturn(VehicleUnit.FAHRENHEIT);

        mTemperatureViewModel.handleUnitChange(mUnitCarPropVal);

        assertNotNull(mTemperatureViewModel.getTemperatureData().getValue());
        assertEquals(TEST_OUTSIDE_TEMPERATURE_F,
                mTemperatureViewModel.getTemperatureData().getValue().getValue());
        assertEquals(MeasureUnit.FAHRENHEIT,
                mTemperatureViewModel.getTemperatureData().getValue().getUnit());
    }

    @Test
    public void testHandleUnitChange_changeUnitFromFtoC_dataChanged() {
        when(mUnitCarPropVal.getValue()).thenReturn(VehicleUnit.FAHRENHEIT);
        when(mValueCarPropVal.getValue()).thenReturn(TEST_OUTSIDE_TEMPERATURE_F);
        mTemperatureViewModel.onChangeEvent(mValueCarPropVal);
        mTemperatureViewModel.onChangeEvent(mUnitCarPropVal);
        when(mUnitCarPropVal.getValue()).thenReturn(VehicleUnit.CELSIUS);

        mTemperatureViewModel.handleUnitChange(mUnitCarPropVal);

        assertNotNull(mTemperatureViewModel.getTemperatureData().getValue());
        assertEquals(TEST_OUTSIDE_TEMPERATURE_C,
                mTemperatureViewModel.getTemperatureData().getValue().getValue());
        assertEquals(MeasureUnit.CELSIUS,
                mTemperatureViewModel.getTemperatureData().getValue().getUnit());
    }

    @Test
    public void testHandleUnitChange_changeUnitFromCtoC_dataSame() {
        mTemperatureViewModel.onChangeEvent(mValueCarPropVal);
        mTemperatureViewModel.onChangeEvent(mUnitCarPropVal);
        when(mUnitCarPropVal.getValue()).thenReturn(VehicleUnit.CELSIUS);

        mTemperatureViewModel.handleUnitChange(mUnitCarPropVal);

        assertNotNull(mTemperatureViewModel.getTemperatureData().getValue());
        assertEquals(TEST_OUTSIDE_TEMPERATURE_C,
                mTemperatureViewModel.getTemperatureData().getValue().getValue());
        assertEquals(MeasureUnit.CELSIUS,
                mTemperatureViewModel.getTemperatureData().getValue().getUnit());
    }

    @Test
    public void testHandleValueChange_changeValue_dataChanges() {
        float testValueUpdate = 60f;
        when(mValueCarPropVal.getValue()).thenReturn(testValueUpdate);

        mTemperatureViewModel.onChangeEvent(mValueCarPropVal);

        assertNotNull(mTemperatureViewModel.getTemperatureData().getValue());
        assertEquals(testValueUpdate,
                mTemperatureViewModel.getTemperatureData().getValue().getValue());
    }

}
