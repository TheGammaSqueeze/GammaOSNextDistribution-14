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

import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_SEAT;
import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_DEFROSTER;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.VehicleUnit;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class HvacControllerTest extends SysuiTestCase {

    private static final int AREA_1 = 1;
    private static final int AREA_4 = 4;
    private static final int AREA_16 = 16;

    @Mock
    private Car mCar;
    @Mock
    private CarPropertyManager mCarPropertyManager;
    @Mock
    private CarPropertyConfig mCarPropertyConfig;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private TestHvacView mTestHvacView1;
    @Mock
    private TestHvacView mTestHvacView2;
    @Mock
    private TestHvacView mTestHvacView3;
    @Mock
    private CarPropertyValue mCarPropertyValue;
    @Mock
    private ConfigurationController mConfigurationController;

    private HvacController mHvacController;
    private FakeExecutor mExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mCar.getCarManager(Car.PROPERTY_SERVICE)).thenReturn(mCarPropertyManager);
        when(mCarPropertyManager.getCarPropertyConfig(anyInt())).thenReturn(mCarPropertyConfig);
        when(mCarPropertyConfig.getAreaIds()).thenReturn(new int[] {AREA_1, AREA_4, AREA_16});

        mExecutor = new FakeExecutor(new FakeSystemClock());
        mHvacController = new HvacController(mCarServiceProvider, mExecutor,
                getContext().getOrCreateTestableResources().getResources(),
                mConfigurationController);
        mHvacController.mCarServiceLifecycleListener.onConnected(mCar);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }

    @After
    public void tearDown() {
        unregisterAllTestHvacViews();
    }

    @Test
    public void registerHvacView_viewRegisteredInMapBySubscribingProperty() {
        when(mTestHvacView1.getAreaId()).thenReturn(AREA_1);
        when(mTestHvacView1.getHvacPropertyToView()).thenReturn(HVAC_TEMPERATURE_SET);

        mHvacController.registerHvacViews(mTestHvacView1);

        assertThat(mHvacController.getHvacPropertyViewMap().get(HVAC_TEMPERATURE_SET).get(
                AREA_1)).contains(mTestHvacView1);
    }

    @Test
    public void registerHvacView_skipHvacPropertiesToGetOnInit() {
        when(mTestHvacView1.getAreaId()).thenReturn(AREA_1);
        when(mTestHvacView1.getHvacPropertyToView()).thenReturn(HVAC_TEMPERATURE_SET);
        when(mCarPropertyManager.getCarPropertyConfig(HVAC_TEMPERATURE_SET).getAreaType())
                .thenReturn(VEHICLE_AREA_TYPE_GLOBAL);

        mHvacController.registerHvacViews(mTestHvacView1);

        assertThat(mHvacController.getHvacPropertyViewMap().get(HVAC_TEMPERATURE_SET).get(
                AREA_1)).contains(mTestHvacView1);

        verify(mTestHvacView1, never()).onPropertyChanged(mCarPropertyValue);
    }

    @Test
    public void registerHvacView_retrieveHvacPropertiesToGetOnInit() {
        when(mTestHvacView1.getAreaId()).thenReturn(AREA_1);
        when(mTestHvacView1.getHvacPropertyToView()).thenReturn(HVAC_TEMPERATURE_SET);
        when(mCarPropertyManager.getCarPropertyConfig(anyInt())).thenReturn(mCarPropertyConfig);
        when(mCarPropertyManager.getCarPropertyConfig(anyInt()).getAreaType())
                .thenReturn(VEHICLE_AREA_TYPE_SEAT);

        when(mCarPropertyManager.getCarPropertyConfig(anyInt()).getAreaIds())
                .thenReturn(new int[] {AREA_1});
        when(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, AREA_1))
                .thenReturn(mCarPropertyValue);
        when(mCarPropertyManager.getProperty(HVAC_AUTO_ON, AREA_1))
                .thenReturn(mCarPropertyValue);
        when(mCarPropertyManager.getProperty(HVAC_POWER_ON, AREA_1))
                .thenReturn(mCarPropertyValue);

        mHvacController.registerHvacViews(mTestHvacView1);

        assertThat(mHvacController.getHvacPropertyViewMap().get(HVAC_TEMPERATURE_SET).get(
                AREA_1)).contains(mTestHvacView1);

        // onPropertyChanged should be called for HVAC_TEMPERATURE_SET, HVAC_AUTO_ON,
        // and HVAC_POWER_ON.
        verify(mTestHvacView1, times(3)).onPropertyChanged(mCarPropertyValue);
    }

    @Test
    public void registerHvacView_propertyNotImplemented() {
        when(mTestHvacView1.getAreaId()).thenReturn(AREA_1);
        when(mTestHvacView1.getHvacPropertyToView()).thenReturn(HVAC_TEMPERATURE_SET);
        when(mCarPropertyManager.getCarPropertyConfig(anyInt())).thenReturn(null);

        mHvacController.registerHvacViews(mTestHvacView1);
        assertThat(mHvacController.getHvacPropertyViewMap()).isEmpty();
    }

    @Test
    public void unregisterHvacView_viewNotRegisteredInMap() {
        when(mTestHvacView1.getAreaId()).thenReturn(AREA_1);
        when(mTestHvacView1.getHvacPropertyToView()).thenReturn(HVAC_TEMPERATURE_SET);
        mHvacController.registerHvacViews(mTestHvacView1);

        mHvacController.unregisterViews(mTestHvacView1);

        assertThat(mHvacController.getHvacPropertyViewMap().get(HVAC_TEMPERATURE_SET)).isNull();
    }

    @Test
    public void hvacPropertyChanged_subscribingViewRegistered_viewHandleChange() {
        registerAllTestHvacViews();
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_1);
        when(mCarPropertyValue.getPropertyId()).thenReturn(HVAC_TEMPERATURE_SET);

        mHvacController.handleHvacPropertyChange(HVAC_TEMPERATURE_SET, mCarPropertyValue);

        verify(mTestHvacView1).onPropertyChanged(mCarPropertyValue);
    }

    @Test
    public void hvacPropertyChanged_subscribingViewRegistered_notSubscribingViewDoesNotHandle() {
        registerAllTestHvacViews();
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_1);
        when(mCarPropertyManager.getCarPropertyConfig(HVAC_TEMPERATURE_SET).getAreaType())
                .thenReturn(VEHICLE_AREA_TYPE_SEAT);

        mHvacController.handleHvacPropertyChange(HVAC_TEMPERATURE_SET, mCarPropertyValue);

        verify(mTestHvacView2, never()).onPropertyChanged(mCarPropertyValue);
    }

    @Test
    public void hvacPropertyChanged_subscribingViewRegistered_viewWithGlobalPropDoesHandle() {
        registerAllTestHvacViews();
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_1);
        when(mCarPropertyManager.getCarPropertyConfig(HVAC_TEMPERATURE_SET).getAreaType())
                .thenReturn(VEHICLE_AREA_TYPE_GLOBAL);

        mHvacController.handleHvacPropertyChange(HVAC_TEMPERATURE_SET, mCarPropertyValue);

        verify(mTestHvacView2).onPropertyChanged(mCarPropertyValue);
    }

    @Test
    public void hvacPropertyChanged_noSubscribingViewRegistered_doesNotThrowError() {
        registerAllTestHvacViews();
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_1);

        mHvacController.handleHvacPropertyChange(HVAC_DEFROSTER, mCarPropertyValue);
    }

    @Test
    public void hvacPropertyChanged_temperatureUnitChangedToFahrenheit_allViewsHandleChange() {
        registerAllTestHvacViews();
        when(mCarPropertyValue.getPropertyId()).thenReturn(HVAC_TEMPERATURE_DISPLAY_UNITS);
        when(mCarPropertyValue.getValue()).thenReturn(VehicleUnit.FAHRENHEIT);
        when(mCarPropertyValue.getAreaId()).thenReturn(0);
        reset(mTestHvacView1);
        reset(mTestHvacView2);
        reset(mTestHvacView3);

        mHvacController.handleHvacPropertyChange(HVAC_TEMPERATURE_DISPLAY_UNITS, mCarPropertyValue);

        verify(mTestHvacView1).onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);
        verify(mTestHvacView2).onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);
        verify(mTestHvacView3).onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);
    }

    @Test
    public void hvacPropertyChanged_temperatureUnitChangedToCelsius_allViewsHandleChange() {
        registerAllTestHvacViews();
        when(mCarPropertyValue.getPropertyId()).thenReturn(HVAC_TEMPERATURE_DISPLAY_UNITS);
        when(mCarPropertyValue.getValue()).thenReturn(VehicleUnit.CELSIUS);
        when(mCarPropertyValue.getAreaId()).thenReturn(0);
        reset(mTestHvacView1);
        reset(mTestHvacView2);
        reset(mTestHvacView3);

        mHvacController.handleHvacPropertyChange(HVAC_TEMPERATURE_DISPLAY_UNITS, mCarPropertyValue);

        verify(mTestHvacView1).onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);
        verify(mTestHvacView2).onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);
        verify(mTestHvacView3).onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);
    }

    private void registerAllTestHvacViews() {
        when(mTestHvacView1.getAreaId()).thenReturn(AREA_1);
        when(mTestHvacView1.getHvacPropertyToView()).thenReturn(HVAC_TEMPERATURE_SET);

        when(mTestHvacView2.getAreaId()).thenReturn(AREA_4);
        when(mTestHvacView2.getHvacPropertyToView()).thenReturn(HVAC_TEMPERATURE_SET);

        when(mTestHvacView3.getAreaId()).thenReturn(AREA_16);
        when(mTestHvacView3.getHvacPropertyToView()).thenReturn(HVAC_AUTO_ON);

        mHvacController.registerHvacViews(mTestHvacView1);
        mHvacController.registerHvacViews(mTestHvacView2);
        mHvacController.registerHvacViews(mTestHvacView3);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }

    private void unregisterAllTestHvacViews() {
        mHvacController.unregisterViews(mTestHvacView1);
        mHvacController.unregisterViews(mTestHvacView2);
        mHvacController.unregisterViews(mTestHvacView3);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }

    private static class TestHvacView extends View implements HvacView {

        public TestHvacView(Context context) {
            super(context);
        }

        @Override
        public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
            // no-op.
        }

        @Override
        public void setConfigInfo(CarPropertyConfig<?> carPropertyConfig) {
            // no-op.
        }

        @Override
        public void onHvacTemperatureUnitChanged(boolean usesFahrenheit) {
            // no-op.
        }

        @Override
        public void onPropertyChanged(CarPropertyValue value) {
            // no-op.
        }

        @Override
        public @HvacController.HvacProperty Integer getHvacPropertyToView() {
            return null;
        }

        @Override
        public @HvacController.AreaId Integer getAreaId() {
            return null;
        }
    }
}