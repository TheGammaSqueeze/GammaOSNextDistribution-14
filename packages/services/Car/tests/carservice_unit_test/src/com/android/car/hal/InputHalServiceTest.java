/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.car.hal;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;
import static android.hardware.automotive.vehicle.CustomInputType.CUSTOM_EVENT_F1;

import static com.android.car.CarServiceUtils.toIntArray;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.car.CarOccupantZoneManager;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.RotaryEvent;
import android.hardware.automotive.vehicle.RotaryInputType;
import android.hardware.automotive.vehicle.VehicleDisplay;
import android.hardware.automotive.vehicle.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.VehicleHwMotionButtonStateFlag;
import android.hardware.automotive.vehicle.VehicleHwMotionInputAction;
import android.hardware.automotive.vehicle.VehicleHwMotionInputSource;
import android.hardware.automotive.vehicle.VehicleHwMotionToolType;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.filters.RequiresDevice;

import com.android.car.hal.test.AidlVehiclePropConfigBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

@RunWith(MockitoJUnitRunner.class)
public class InputHalServiceTest {
    @Mock VehicleHal mVehicleHal;
    @Mock InputHalService.InputListener mInputListener;
    @Mock LongSupplier mUptimeSupplier;
    @Mock PrintWriter mMockPrintWriter;

    private static final HalPropConfig HW_KEY_INPUT_CONFIG = new AidlHalPropConfig(
            AidlVehiclePropConfigBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT).build());
    private static final HalPropConfig HW_ROTARY_INPUT_CONFIG = new AidlHalPropConfig(
            AidlVehiclePropConfigBuilder.newBuilder(VehicleProperty.HW_ROTARY_INPUT).build());
    private static final HalPropConfig HW_CUSTOM_INPUT_CONFIG = new AidlHalPropConfig(
            AidlVehiclePropConfigBuilder.newBuilder(VehicleProperty.HW_CUSTOM_INPUT).build());

    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);

    private enum Key {DOWN, UP}

    private InputHalService mInputHalService;

    @Before
    public void setUp() {
        when(mUptimeSupplier.getAsLong()).thenReturn(0L);
        mInputHalService = new InputHalService(mVehicleHal, mUptimeSupplier);
        mInputHalService.init();
    }

    @After
    public void tearDown() {
        mInputHalService.release();
        mInputHalService = null;
    }

    @Test
    public void ignoresSetListener_beforeKeyInputSupported() {
        assertThat(mInputHalService.isKeyInputSupported()).isFalse();

        mInputHalService.setInputListener(mInputListener);

        int anyDisplay = VehicleDisplay.MAIN;
        mInputHalService.onHalEvents(List.of(makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_ENTER,
                anyDisplay)));
        verify(mInputListener, never()).onKeyEvent(any(), anyInt());
    }

    @Test
    public void takesKeyInputProperty() {
        Set<HalPropConfig> offeredProps = Set.of(new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(VehicleProperty.ABS_ACTIVE).build()),
                HW_KEY_INPUT_CONFIG,
                new AidlHalPropConfig(AidlVehiclePropConfigBuilder.newBuilder(
                        VehicleProperty.CURRENT_GEAR).build()));

        mInputHalService.takeProperties(offeredProps);

        assertThat(mInputHalService.isKeyInputSupported()).isTrue();
        assertThat(mInputHalService.isRotaryInputSupported()).isFalse();
        assertThat(mInputHalService.isCustomInputSupported()).isFalse();
    }

    @Test
    public void takesRotaryInputProperty() {
        Set<HalPropConfig> offeredProps = Set.of(
                new AidlHalPropConfig(AidlVehiclePropConfigBuilder.newBuilder(
                        VehicleProperty.ABS_ACTIVE).build()),
                HW_ROTARY_INPUT_CONFIG,
                new AidlHalPropConfig(AidlVehiclePropConfigBuilder.newBuilder(
                        VehicleProperty.CURRENT_GEAR).build()));

        mInputHalService.takeProperties(offeredProps);

        assertThat(mInputHalService.isRotaryInputSupported()).isTrue();
        assertThat(mInputHalService.isKeyInputSupported()).isFalse();
        assertThat(mInputHalService.isCustomInputSupported()).isFalse();
    }

    @Test
    public void takesCustomInputProperty() {
        Set<HalPropConfig> offeredProps = Set.of(
                new AidlHalPropConfig(AidlVehiclePropConfigBuilder.newBuilder(
                        VehicleProperty.ABS_ACTIVE).build()),
                HW_CUSTOM_INPUT_CONFIG,
                new AidlHalPropConfig(AidlVehiclePropConfigBuilder.newBuilder(
                        VehicleProperty.CURRENT_GEAR).build()));

        mInputHalService.takeProperties(offeredProps);

        assertThat(mInputHalService.isRotaryInputSupported()).isFalse();
        assertThat(mInputHalService.isKeyInputSupported()).isFalse();
        assertThat(mInputHalService.isCustomInputSupported()).isTrue();
    }

    @Test
    public void takesKeyAndRotaryAndCustomInputProperty() {
        Set<HalPropConfig> offeredProps = Set.of(
                new AidlHalPropConfig(AidlVehiclePropConfigBuilder.newBuilder(
                        VehicleProperty.ABS_ACTIVE).build()),
                HW_KEY_INPUT_CONFIG,
                HW_ROTARY_INPUT_CONFIG,
                HW_CUSTOM_INPUT_CONFIG,
                new AidlHalPropConfig(AidlVehiclePropConfigBuilder.newBuilder(
                        VehicleProperty.CURRENT_GEAR).build()));

        mInputHalService.takeProperties(offeredProps);

        assertThat(mInputHalService.isKeyInputSupported()).isTrue();
        assertThat(mInputHalService.isRotaryInputSupported()).isTrue();
        assertThat(mInputHalService.isCustomInputSupported()).isTrue();
    }

    @Test
    public void dispatchesInputEvent_single_toListener_mainDisplay() {
        subscribeListener();

        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
    }

    @Test
    public void dispatchesInputEvent_single_toListener_clusterDisplay() {
        subscribeListener();

        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER,
                VehicleDisplay.INSTRUMENT_CLUSTER,
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
    }

    @Test
    public void dispatchesInputEvent_multiple_toListener_mainDisplay() {
        subscribeListener();

        // KeyEvents get recycled, so we can't just use ArgumentCaptor#getAllValues here.
        // We need to make a copy of the information we need at the time of the call.
        List<KeyEvent> events = new ArrayList<>();
        doAnswer(inv -> {
            KeyEvent event = inv.getArgument(0);
            events.add(event.copy());
            return null;
        }).when(mInputListener).onKeyEvent(any(), eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN));

        mInputHalService.onHalEvents(
                List.of(
                        makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN),
                        makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_MENU, VehicleDisplay.MAIN)));

        assertThat(events.get(0).getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(events.get(1).getKeyCode()).isEqualTo(KeyEvent.KEYCODE_MENU);

        events.forEach(KeyEvent::recycle);
    }

    @Test
    public void dispatchesInputEvent_multiple_toListener_clusterDisplay() {
        subscribeListener();

        // KeyEvents get recycled, so we can't just use ArgumentCaptor#getAllValues here.
        // We need to make a copy of the information we need at the time of the call.
        List<KeyEvent> events = new ArrayList<>();
        doAnswer(inv -> {
            KeyEvent event = inv.getArgument(0);
            events.add(event.copy());
            return null;
        }).when(mInputListener).onKeyEvent(any(),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER));

        mInputHalService.onHalEvents(
                List.of(
                        makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_ENTER,
                                VehicleDisplay.INSTRUMENT_CLUSTER),
                        makeKeyPropValue(Key.DOWN, KeyEvent.KEYCODE_MENU,
                                VehicleDisplay.INSTRUMENT_CLUSTER)));

        assertThat(events.get(0).getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(events.get(1).getKeyCode()).isEqualTo(KeyEvent.KEYCODE_MENU);

        events.forEach(KeyEvent::recycle);
    }

    @Test
    public void dispatchesInputEvent_invalidInputEvent() {
        subscribeListener();
        HalPropValue v = mPropValueBuilder.build(VehicleProperty.HW_KEY_INPUT, /* areaId= */ 0);
        // Missing action, code, display_type.
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onKeyEvent(any(),  anyInt());

        // Missing code, display_type.
        v = mPropValueBuilder.build(VehicleProperty.HW_KEY_INPUT, /* areaId= */ 0,
                VehicleHwKeyInputAction.ACTION_DOWN);
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onKeyEvent(any(),  anyInt());

        // Missing display_type.
        v = mPropValueBuilder.build(VehicleProperty.HW_KEY_INPUT, /* areaId= */ 0,
                new int[]{VehicleHwKeyInputAction.ACTION_DOWN, KeyEvent.KEYCODE_ENTER});
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onKeyEvent(any(),  anyInt());
    }

    @Test
    public void handlesRepeatedKeys_anyDisplay() {
        subscribeListener();

        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(0L);
        assertThat(event.getDownTime()).isEqualTo(0L);
        assertThat(event.getRepeatCount()).isEqualTo(0);

        when(mUptimeSupplier.getAsLong()).thenReturn(5L);
        event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(5L);
        assertThat(event.getDownTime()).isEqualTo(5L);
        assertThat(event.getRepeatCount()).isEqualTo(1);

        when(mUptimeSupplier.getAsLong()).thenReturn(10L);
        event = dispatchSingleEvent(Key.UP, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_UP);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(10L);
        assertThat(event.getDownTime()).isEqualTo(5L);
        assertThat(event.getRepeatCount()).isEqualTo(0);

        when(mUptimeSupplier.getAsLong()).thenReturn(15L);
        event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_ENTER);
        assertThat(event.getEventTime()).isEqualTo(15L);
        assertThat(event.getDownTime()).isEqualTo(15L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
    }

    /**
     * Test for handling rotary knob event.
     */
    @RequiresDevice
    @Test
    public void handlesRepeatedKeyWithIndents_anyDisplay() {
        subscribeListener();
        KeyEvent event = dispatchSingleEventWithIndents(KeyEvent.KEYCODE_VOLUME_UP, 5,
                VehicleDisplay.MAIN, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_VOLUME_UP);
        assertThat(event.getEventTime()).isEqualTo(0L);
        assertThat(event.getDownTime()).isEqualTo(0L);
        assertThat(event.getRepeatCount()).isEqualTo(4);

        when(mUptimeSupplier.getAsLong()).thenReturn(5L);
        event = dispatchSingleEventWithIndents(KeyEvent.KEYCODE_VOLUME_UP, 5,
                VehicleDisplay.MAIN, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_VOLUME_UP);
        assertThat(event.getEventTime()).isEqualTo(5L);
        assertThat(event.getDownTime()).isEqualTo(5L);
        assertThat(event.getRepeatCount()).isEqualTo(9);
    }

    @Test
    public void handlesKeyUp_withoutKeyDown_mainDisplay() {
        subscribeListener();

        when(mUptimeSupplier.getAsLong()).thenReturn(42L);
        KeyEvent event = dispatchSingleEvent(Key.UP, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        assertThat(event.getEventTime()).isEqualTo(42L);
        assertThat(event.getDownTime()).isEqualTo(42L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
        // event.getDisplayId is not tested since it is assigned by CarInputService
    }

    @Test
    public void handlesKeyUp_withoutKeyDown_clusterDisplay() {
        subscribeListener();

        when(mUptimeSupplier.getAsLong()).thenReturn(42L);
        KeyEvent event = dispatchSingleEvent(Key.UP, KeyEvent.KEYCODE_ENTER,
                VehicleDisplay.INSTRUMENT_CLUSTER,
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);

        assertThat(event.getEventTime()).isEqualTo(42L);
        assertThat(event.getDownTime()).isEqualTo(42L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
        // event.getDisplayId is not tested since it is assigned by CarInputService
    }

    @Test
    public void separateKeyDownEvents_areIndependent_mainDisplay() {
        subscribeListener();

        when(mUptimeSupplier.getAsLong()).thenReturn(27L);
        dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        when(mUptimeSupplier.getAsLong()).thenReturn(42L);
        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_MENU, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_MENU);
        assertThat(event.getDownTime()).isEqualTo(42L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
    }

    @Test
    public void separateKeyDownEvents_areIndependent_clusterDisplay() {
        subscribeListener();

        when(mUptimeSupplier.getAsLong()).thenReturn(27L);
        dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_ENTER, VehicleDisplay.INSTRUMENT_CLUSTER,
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);

        when(mUptimeSupplier.getAsLong()).thenReturn(42L);
        KeyEvent event = dispatchSingleEvent(Key.DOWN, KeyEvent.KEYCODE_MENU, VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_MENU);
        assertThat(event.getDownTime()).isEqualTo(42L);
        assertThat(event.getRepeatCount()).isEqualTo(0);
        // event.getDisplayid is not tested since it is assigned by CarInputService
    }

    @Test
    public void dispatchesRotaryEvent_singleVolumeUp_anyDisplay() {
        subscribeListener();

        // Arrange mInputListener to capture incoming RotaryEvent
        List<RotaryEvent> events = new ArrayList<>();
        doAnswer(invocation -> {
            RotaryEvent event = invocation.getArgument(0);
            events.add(event);
            return null;
        }).when(mInputListener).onRotaryEvent(any(), eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN));

        // Arrange
        long timestampNanos = 12_345_678_901L;

        // Act
        mInputHalService.onHalEvents(List.of(
                makeRotaryPropValue(RotaryInputType.ROTARY_INPUT_TYPE_AUDIO_VOLUME, 1,
                        timestampNanos, 0, VehicleDisplay.MAIN)));

        // Assert

        // Expected Rotary event to have only one value for uptimeMillisForClicks since the input
        // property was created with one detent only. This value will correspond to the event
        // startup time. See CarServiceUtils#getUptimeToElapsedTimeDeltaInMillis for more detailed
        // information on how this value is calculated.
        assertThat(events).containsExactly(new RotaryEvent(
                /* inputType= */ CarInputManager.INPUT_TYPE_ROTARY_VOLUME,
                /* clockwise= */ true,
                /* uptimeMillisForClicks= */ new long[]{12345L}));
    }

    @Test
    public void dispatchesRotaryEvent_multipleNavigatePrevious_anyDisplay() {
        subscribeListener();

        // Arrange mInputListener to capture incoming RotaryEvent
        List<RotaryEvent> events = new ArrayList<>();
        doAnswer(invocation -> {
            RotaryEvent event = invocation.getArgument(0);
            events.add(event);
            return null;
        }).when(mInputListener).onRotaryEvent(any(), eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN));

        // Arrange
        long timestampNanos = 12_345_000_000L;
        int deltaNanos = 2_000_000;
        int numberOfDetents = 3;

        // Act
        mInputHalService.onHalEvents(List.of(
                makeRotaryPropValue(RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION,
                        -numberOfDetents, timestampNanos, deltaNanos, VehicleDisplay.MAIN)));

        // Assert

        // Expected Rotary event to have 3 values for uptimeMillisForClicks since the input
        // property value was created with 3 detents. Each value in uptimeMillisForClicks
        // represents the calculated deltas (in nanoseconds) between pairs of consecutive detents
        // up times. See InputHalService#dispatchRotaryInput for more detailed information on how
        // delta times are calculated.
        assertThat(events).containsExactly(new RotaryEvent(
                /* inputType= */CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION,
                /* clockwise= */ false,
                /* uptimeMillisForClicks= */ new long[]{12345L, 12347L, 12349L}));
    }

    @Test
    public void dispatchesRotaryEvent_invalidInputEvent() {
        subscribeListener();
        HalPropValue v = mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 0);

        // Missing rotaryInputType, detentCount, targetDisplayType.
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onRotaryEvent(any(),  anyInt());

        // Missing detentCount, targetDisplayType.
        v = mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 0,
                RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION);
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onRotaryEvent(any(),  anyInt());

        // Missing targetDisplayType.
        v = mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 0,
                new int[]{RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION, 1});
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onRotaryEvent(any(),  anyInt());

        // Add targetDisplayType and set detentCount to 0.
        v = mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 0,
                new int[]{RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION, 0,
                        VehicleDisplay.MAIN});
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onRotaryEvent(any(),  anyInt());

        // Set detentCount to 1.
        // Add additional unnecessary arguments so that the array size does not match detentCount.
        v = mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 1,
                new int[]{RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION, 1,
                        VehicleDisplay.MAIN, 0});
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onRotaryEvent(any(),  anyInt());

        // Set invalid detentCount.
        v = mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 1,
                new int[]{RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION, Integer.MAX_VALUE,
                        VehicleDisplay.MAIN, 0});
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onRotaryEvent(any(),  anyInt());

        // Set invalid detentCount.
        v = mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 1,
                new int[]{RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION, Integer.MIN_VALUE,
                        VehicleDisplay.MAIN, 0});
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onRotaryEvent(any(),  anyInt());
    }

    @Test
    public void dispatchesCustomInputEvent_mainDisplay() {
        // Arrange mInputListener to capture incoming CustomInputEvent
        subscribeListener();

        List<CustomInputEvent> events = new ArrayList<>();
        doAnswer(invocation -> {
            CustomInputEvent event = invocation.getArgument(0);
            events.add(event);
            return null;
        }).when(mInputListener).onCustomInputEvent(any());

        // Arrange
        int repeatCounter = 1;
        HalPropValue customInputPropValue = makeCustomInputPropValue(
                CUSTOM_EVENT_F1, VehicleDisplay.MAIN, repeatCounter);

        // Act
        mInputHalService.onHalEvents(List.of(customInputPropValue));

        // Assert
        assertThat(events).containsExactly(new CustomInputEvent(
                CustomInputEvent.INPUT_CODE_F1, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                repeatCounter));
    }

    @Test
    public void dispatchesCustomInputEvent_clusterDisplay() {
        // Arrange mInputListener to capture incoming CustomInputEvent
        subscribeListener();

        List<CustomInputEvent> events = new ArrayList<>();
        doAnswer(invocation -> {
            CustomInputEvent event = invocation.getArgument(0);
            events.add(event);
            return null;
        }).when(mInputListener).onCustomInputEvent(any());

        // Arrange
        int repeatCounter = 1;
        HalPropValue customInputPropValue = makeCustomInputPropValue(
                CUSTOM_EVENT_F1, VehicleDisplay.INSTRUMENT_CLUSTER, repeatCounter);

        // Act
        mInputHalService.onHalEvents(List.of(customInputPropValue));

        // Assert
        assertThat(events).containsExactly(new CustomInputEvent(
                CustomInputEvent.INPUT_CODE_F1,
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                repeatCounter));
    }

    @Test
    public void dispatchesCustomInputEvent_InvalidEvent() {
        // Arrange mInputListener to capture incoming CustomInputEvent
        subscribeListener();

        HalPropValue v = mPropValueBuilder.build(VehicleProperty.HW_CUSTOM_INPUT, /* areaId= */ 0);

        // Missing inputCode, targetDisplayType, repeatCounter.
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onCustomInputEvent(any());

        // Missing targetDisplayType, repeatCounter.
        v = mPropValueBuilder.build(VehicleProperty.HW_CUSTOM_INPUT, /* areaId= */ 0,
                CustomInputEvent.INPUT_CODE_F1);
        mInputHalService.onHalEvents(List.of(v));
        verify(mInputListener, never()).onCustomInputEvent(any());

        // Missing repeatCounter.
        v = mPropValueBuilder.build(VehicleProperty.HW_CUSTOM_INPUT, /* areaId= */ 0,
                new int[]{CustomInputEvent.INPUT_CODE_F1,
                        CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER});
        mInputHalService.onHalEvents(List.of(v));

        verify(mInputListener, never()).onCustomInputEvent(any());
    }

    @Test
    public void dispatchesCustomInputEvent_acceptInputCodeHigherThanF10() {
        // Custom Input Events may accept input code values outside the
        // CUSTOM_EVENT_F1 to F10 range.
        int someInputCodeValueHigherThanF10 = 1000;

        // Arrange mInputListener to capture incoming CustomInputEvent
        subscribeListener();

        List<CustomInputEvent> events = new ArrayList<>();
        doAnswer(invocation -> {
            CustomInputEvent event = invocation.getArgument(0);
            events.add(event);
            return null;
        }).when(mInputListener).onCustomInputEvent(any());

        // Arrange
        int repeatCounter = 1;
        HalPropValue customInputPropValue = makeCustomInputPropValue(
                someInputCodeValueHigherThanF10, VehicleDisplay.MAIN, repeatCounter);

        // Act
        mInputHalService.onHalEvents(List.of(customInputPropValue));

        // Assert
        assertThat(events).containsExactly(new CustomInputEvent(
                someInputCodeValueHigherThanF10, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                repeatCounter));
    }

    @Test
    public void dispatchesKeyInputEvent_perSeat_anyDisplay() {
        // Arrange
        subscribeListener();

        // Act
        KeyEvent event = dispatchSinglePerSeatEvent(
                /* seat= */ 1,
                VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                KeyEvent.KEYCODE_HOME,
                Key.UP,
                /* repeatCount= */ 1,
                /* downTime= */ SECONDS.toNanos(7),
                /* timeStampNanos= */ SECONDS.toNanos(8)
        );

        // Assert
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_HOME);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_UP);
        assertThat(event.getRepeatCount()).isEqualTo(1);
        assertThat(event.getDownTime()).isEqualTo(SECONDS.toMillis(7));
        assertThat(event.getEventTime()).isEqualTo(SECONDS.toMillis(8));
    }

    @Test
    public void dispatchesKeyInputEvent_withSameEventTimeAndDownTime_perSeat_whenKeyDown() {
        // Arrange
        subscribeListener();

        // Act
        KeyEvent event = dispatchSinglePerSeatEvent(
                /* seat= */ 1,
                VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                KeyEvent.KEYCODE_HOME,
                Key.DOWN,
                /* repeatCount= */ 1,
                /* downTime= */ SECONDS.toNanos(7),
                /* timeStampNanos= */ SECONDS.toNanos(8)
        );

        // Assert
        assertThat(event.getKeyCode()).isEqualTo(KeyEvent.KEYCODE_HOME);
        assertThat(event.getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertThat(event.getRepeatCount()).isEqualTo(1);
        assertThat(event.getDownTime()).isEqualTo(SECONDS.toMillis(7));
        assertThat(event.getEventTime()).isEqualTo(SECONDS.toMillis(7));
    }

    @Test
    public void dispatchesMotionInputEvent_perSeat_anyDisplay() {
        // Arrange
        subscribeListener();

        // Act
        MotionEvent event = dispatchSinglePerSeatMotionEvent(
                /* seat= */ 1,
                VehicleDisplay.MAIN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                VehicleHwMotionInputSource.SOURCE_TOUCHSCREEN,
                VehicleHwMotionInputAction.ACTION_DOWN,
                VehicleHwMotionButtonStateFlag.BUTTON_PRIMARY,
                /* pointerCount= */ 2,
                /* pointerIds= */ new int[] {10, 20},
                /* toolTypes= */ new int[] {VehicleHwMotionToolType.TOOL_TYPE_FINGER,
                        VehicleHwMotionToolType.TOOL_TYPE_MOUSE},
                /* xData= */ new float[] {100, 200},
                /* yData= */ new float[] {300, 400},
                /* pressureData= */ new float[] {1, 2},
                /* sizeData= */ new float[] {3, 4},
                /* downTimeNanos= */ SECONDS.toNanos(7),
                /* timeStampNanos= */ SECONDS.toNanos(8)
                );

        // Assert
        assertThat(event.getSource()).isEqualTo(InputDevice.SOURCE_TOUCHSCREEN);
        assertThat(event.getAction()).isEqualTo(MotionEvent.ACTION_DOWN);
        assertThat(event.getButtonState()).isEqualTo(MotionEvent.BUTTON_PRIMARY);
        assertThat(event.getPointerCount()).isEqualTo(2);
        assertThat(event.getPointerId(0)).isEqualTo(10);
        assertThat(event.getPointerId(1)).isEqualTo(20);
        assertThat(event.getToolType(0)).isEqualTo(MotionEvent.TOOL_TYPE_FINGER);
        assertThat(event.getToolType(1)).isEqualTo(MotionEvent.TOOL_TYPE_MOUSE);
        assertThat(event.getX(0)).isEqualTo(100);
        assertThat(event.getX(1)).isEqualTo(200);
        assertThat(event.getY(0)).isEqualTo(300);
        assertThat(event.getY(1)).isEqualTo(400);
        assertThat(event.getPressure(0)).isEqualTo(1);
        assertThat(event.getPressure(1)).isEqualTo(2);
        assertThat(event.getSize(0)).isEqualTo(3);
        assertThat(event.getSize(1)).isEqualTo(4);
        assertThat(event.getDownTime()).isEqualTo(SECONDS.toMillis(7));
        assertThat(event.getEventTime()).isEqualTo(SECONDS.toMillis(8));
    }

    @Test
    public void dump_withNoLastKeyEvent_printsEmpty() {
        // Arrange
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        mInputHalService.dump(mMockPrintWriter);

        // Assert
        verify(mMockPrintWriter, atLeastOnce()).println(stringArgumentCaptor.capture());
        List<String> strings = stringArgumentCaptor.getAllValues();
        assertThat(strings.get(5)).startsWith("mLastFewDispatchedV2KeyEvents:");
    }

    @Test
    public void dump_containsLastFewKeyEvents() {
        // Arrange
        subscribeListener();
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        dispatchMultipleV2KeyEvents(KeyEvent.KEYCODE_HOME, /* times= */ 1);
        dispatchMultipleV2KeyEvents(KeyEvent.KEYCODE_BACK, /* times= */ 9);
        dispatchMultipleV2KeyEvents(KeyEvent.KEYCODE_VOLUME_UP, /* times= */ 1);

        // Act
        mInputHalService.dump(mMockPrintWriter);

        // Assert
        verify(mMockPrintWriter, atLeastOnce()).println(stringArgumentCaptor.capture());
        List<String> strings = stringArgumentCaptor.getAllValues();
        int numHomeEvents = 0;
        int numBackEvents = 0;
        int numVolUpEvents = 0;
        assertThat(strings.get(5)).startsWith("mLastFewDispatchedV2KeyEvents:");
        for (int i = 5; i < strings.size(); i++) {
            if (strings.get(i).contains("keyCode=KEYCODE_HOME")) {
                numHomeEvents++;
            } else if (strings.get(i).contains("keyCode=KEYCODE_BACK")) {
                numBackEvents++;
            } else if (strings.get(i).contains("keyCode=KEYCODE_VOLUME_UP")) {
                numVolUpEvents++;
            }
        }
        assertWithMessage("Number of home events").that(numHomeEvents).isEqualTo(0);
        assertWithMessage("Number of back events").that(numBackEvents).isEqualTo(9);
        assertWithMessage("Number of volume up events").that(numVolUpEvents).isEqualTo(1);
    }

    @Test
    public void dump_containsLastFewMotionEvents() {
        // Arrange
        subscribeListener();
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        dispatchMultipleMotionEvents(VehicleHwMotionInputAction.ACTION_DOWN, /* times= */ 1);
        dispatchMultipleMotionEvents(VehicleHwMotionInputAction.ACTION_MOVE, /* times= */ 9);
        dispatchMultipleMotionEvents(VehicleHwMotionInputAction.ACTION_UP, /* times= */ 1);

        // Act
        mInputHalService.dump(mMockPrintWriter);

        // Assert
        verify(mMockPrintWriter, atLeastOnce()).println(stringArgumentCaptor.capture());
        List<String> strings = stringArgumentCaptor.getAllValues();
        int numMoveEvents = 0;
        int numUpEvents = 0;
        int numDownEvents = 0;
        assertThat(strings.get(6)).startsWith("mLastFewDispatchedMotionEvents:");
        for (int i = 6; i < strings.size(); i++) {
            if (strings.get(i).contains("action=ACTION_DOWN")) {
                numDownEvents++;
            } else if (strings.get(i).contains("action=ACTION_MOVE")) {
                numMoveEvents++;
            } else if (strings.get(i).contains("action=ACTION_UP")) {
                numUpEvents++;
            }
        }
        // Number of down events should be 0 as only 10 recent events are stored.
        assertWithMessage("Number of down events").that(numDownEvents).isEqualTo(0);
        assertWithMessage("Number of move events").that(numMoveEvents).isEqualTo(9);
        assertWithMessage("Number of up events").that(numUpEvents).isEqualTo(1);
    }

    private void dispatchMultipleMotionEvents(@VehicleHwMotionInputAction int action, int times) {
        for (int i = 0; i < times; i++) {
            dispatchSinglePerSeatMotionEvent(
                    /* seat= */ 1,
                    VehicleDisplay.MAIN,
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    VehicleHwMotionInputSource.SOURCE_TOUCHSCREEN,
                    action,
                    VehicleHwMotionButtonStateFlag.BUTTON_PRIMARY,
                    /* pointerCount= */ 2,
                    /* pointerIds= */ new int[]{10, 20},
                    /* toolTypes= */ new int[]{VehicleHwMotionToolType.TOOL_TYPE_FINGER,
                            VehicleHwMotionToolType.TOOL_TYPE_MOUSE},
                    /* xData= */ new float[]{100, 200},
                    /* yData= */ new float[]{300, 400},
                    /* pressureData= */ new float[]{1, 2},
                    /* sizeData= */ new float[]{3, 4},
                    /* downTimeNanos= */ SECONDS.toNanos(7),
                    /* timeStampNanos= */ SECONDS.toNanos(8)
            );
        }
    }

    private void dispatchMultipleV2KeyEvents(int keyCode, int times) {
        for (int i = 0; i < times; i++) {
            dispatchSinglePerSeatEvent(
                    /* seat= */ 1,
                    VehicleDisplay.MAIN,
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    keyCode,
                    Key.DOWN,
                    /* repeatCount= */ 1,
                    /* downTime= */ SECONDS.toNanos(7),
                    /* timeStampNanos= */ SECONDS.toNanos(9)
            );
        }
    }

    private void subscribeListener() {
        mInputHalService.takeProperties(Set.of(HW_KEY_INPUT_CONFIG));
        assertThat(mInputHalService.isKeyInputSupported()).isTrue();

        mInputHalService.setInputListener(mInputListener);
        verify(mVehicleHal).subscribePropertySafe(mInputHalService, VehicleProperty.HW_KEY_INPUT);
    }

    private KeyEvent dispatchSingleEvent(Key action, int code, int actualDisplay,
            @DisplayTypeEnum int expectedDisplay) {
        ArgumentCaptor<KeyEvent> captor = ArgumentCaptor.forClass(KeyEvent.class);
        reset(mInputListener);
        mInputHalService.onHalEvents(
                List.of(makeKeyPropValue(action, code, actualDisplay)));
        verify(mInputListener).onKeyEvent(captor.capture(), eq(expectedDisplay));
        reset(mInputListener);
        return captor.getValue();
    }

    private KeyEvent dispatchSinglePerSeatEvent(int seat, int actualDisplay,
            @DisplayTypeEnum int expectedDisplay, int code, Key action, int repeatCount,
            long downTime, long timeStampNanos) {
        ArgumentCaptor<KeyEvent> captor = ArgumentCaptor.forClass(KeyEvent.class);
        reset(mInputListener);

        int actionValue = (action == Key.DOWN
                ? VehicleHwKeyInputAction.ACTION_DOWN
                : VehicleHwKeyInputAction.ACTION_UP);
        mInputHalService.onHalEvents(
                List.of(mPropValueBuilder.build(VehicleProperty.HW_KEY_INPUT_V2,
                        /* areaId= */ seat,
                        /* timestamp= */ timeStampNanos,
                        VehiclePropertyStatus.AVAILABLE,
                        /* int32Values= */ new int[] {actualDisplay, code, actionValue,
                                repeatCount},
                        /* floatValues= */ new float[] {},
                        /* int64Values= */ new long[] {downTime},
                        /* stringValue= */ "",
                        /* byteValue= */ new byte[] {}
                )));
        verify(mInputListener).onKeyEvent(captor.capture(), eq(expectedDisplay),
                eq(seat));
        reset(mInputListener);
        return captor.getValue();
    }

    private MotionEvent dispatchSinglePerSeatMotionEvent(int seat, int actualDisplay,
            @DisplayTypeEnum int expectedDisplay, int inputSource, int motionActionCode,
            int buttonState, int pointerCount, int[] pointerIds, int[] toolTypes, float[] xData,
            float[] yData, float[] pressureData, float[] sizeData, long downTimeNanos,
            long timeStampNanos) {
        ArgumentCaptor<MotionEvent> captor = ArgumentCaptor.forClass(MotionEvent.class);
        reset(mInputListener);

        int[] int32Array = new int[5 + 2 * pointerCount];
        float[] floatArray = new float[4 * pointerCount];
        int32Array[0] = actualDisplay;
        int32Array[1] = inputSource;
        int32Array[2] = motionActionCode;
        int32Array[3] = buttonState;
        int32Array[4] = pointerCount;
        for (int i = 0; i < pointerCount; i++) {
            int32Array[5 + i] = pointerIds[i];
            int32Array[5 + pointerCount + i] = toolTypes[i];
            floatArray[i] = xData[i];
            floatArray[pointerCount + i] = yData[i];
            floatArray[2 * pointerCount + i] = pressureData[i];
            floatArray[3 * pointerCount + i] = sizeData[i];
        }

        mInputHalService.onHalEvents(
                List.of(mPropValueBuilder.build(VehicleProperty.HW_MOTION_INPUT,
                        /* areaId = */ seat,
                        /* timestamp= */ timeStampNanos,
                        VehiclePropertyStatus.AVAILABLE,
                        int32Array,
                        floatArray,
                        /* int64Values= */ new long[] {downTimeNanos},
                        /* stringValue= */ "",
                        /* byteValue= */ new byte[] {}
                )));
        verify(mInputListener).onMotionEvent(captor.capture(), eq(expectedDisplay), eq(seat));
        reset(mInputListener);
        return captor.getValue();
    }

    private KeyEvent dispatchSingleEventWithIndents(int code, int indents, int actualDisplay,
            @DisplayTypeEnum int expectedDisplay) {
        ArgumentCaptor<KeyEvent> captor = ArgumentCaptor.forClass(KeyEvent.class);
        reset(mInputListener);
        mInputHalService.onHalEvents(
                List.of(makeKeyPropValueWithIndents(code, indents, actualDisplay)));
        verify(mInputListener, times(indents)).onKeyEvent(captor.capture(),
                eq(expectedDisplay));
        reset(mInputListener);
        return captor.getValue();
    }

    private HalPropValue makeKeyPropValue(Key action, int code,
            @DisplayTypeEnum int targetDisplayType) {
        int actionValue = (action == Key.DOWN
                        ? VehicleHwKeyInputAction.ACTION_DOWN
                        : VehicleHwKeyInputAction.ACTION_UP);
        return mPropValueBuilder.build(VehicleProperty.HW_KEY_INPUT, /* areaId= */ 0,
                new int[]{actionValue, code, targetDisplayType});
    }

    private HalPropValue makeKeyPropValueWithIndents(int code, int indents,
            @DisplayTypeEnum int targetDisplayType) {
        // Only Key.down can have indents.
        return mPropValueBuilder.build(VehicleProperty.HW_KEY_INPUT, /* areaId= */ 0,
                new int[]{VehicleHwKeyInputAction.ACTION_DOWN, code, targetDisplayType, indents});
    }

    private HalPropValue makeRotaryPropValue(int rotaryInputType, int detents, long timestamp,
            int delayBetweenDetents, @DisplayTypeEnum int targetDisplayType) {
        ArrayList<Integer> int32Values = new ArrayList<>();
        int32Values.add(rotaryInputType);
        int32Values.add(detents);
        int32Values.add(targetDisplayType);
        for (int i = 0; i < Math.abs(detents) - 1; i++) {
            int32Values.add(delayBetweenDetents);
        }
        return mPropValueBuilder.build(VehicleProperty.HW_ROTARY_INPUT, /* areaId= */ 0, timestamp,
                /*status=*/0, toIntArray(int32Values));
    }

    private HalPropValue makeCustomInputPropValue(int inputCode,
            @DisplayTypeEnum int targetDisplayType, int repeatCounter) {
        return mPropValueBuilder.build(VehicleProperty.HW_CUSTOM_INPUT, /* areaId= */ 0,
                new int[]{inputCode, targetDisplayType, repeatCounter});
    }
}
