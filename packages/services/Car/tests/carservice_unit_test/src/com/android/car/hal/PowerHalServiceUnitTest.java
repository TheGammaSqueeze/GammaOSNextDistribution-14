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

package com.android.car.hal;

import static android.hardware.automotive.vehicle.VehicleApPowerStateConfigFlag.CONFIG_SUPPORT_TIMER_POWER_ON_FLAG;
import static android.hardware.automotive.vehicle.VehicleApPowerStateConfigFlag.ENABLE_DEEP_SLEEP_FLAG;
import static android.hardware.automotive.vehicle.VehicleApPowerStateConfigFlag.ENABLE_HIBERNATION_FLAG;
import static android.hardware.automotive.vehicle.VehicleApPowerStateReq.ON;
import static android.hardware.automotive.vehicle.VehicleApPowerStateReq.SHUTDOWN_PREPARE;
import static android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam.CAN_HIBERNATE;
import static android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam.CAN_SLEEP;
import static android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY;
import static android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY;
import static android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY;
import static android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY;
import static android.hardware.automotive.vehicle.VehicleProperty.AP_POWER_STATE_REPORT;
import static android.hardware.automotive.vehicle.VehicleProperty.AP_POWER_BOOTUP_REASON;
import static android.hardware.automotive.vehicle.VehicleProperty.AP_POWER_STATE_REQ;
import static android.hardware.automotive.vehicle.VehicleProperty.DISPLAY_BRIGHTNESS;
import static android.hardware.automotive.vehicle.VehicleProperty.PER_DISPLAY_BRIGHTNESS;
import static android.hardware.automotive.vehicle.VehicleProperty.VEHICLE_IN_USE;

import static com.android.car.hal.PowerHalService.PowerState.SHUTDOWN_TYPE_DEEP_SLEEP;
import static com.android.car.hal.PowerHalService.PowerState.SHUTDOWN_TYPE_HIBERNATION;
import static com.android.car.hal.PowerHalService.PowerState.SHUTDOWN_TYPE_POWER_OFF;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.test.util.FakeContext;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.VehicleApPowerBootupReason;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.display.DisplayManager;
import android.os.ServiceSpecificException;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.DisplayAddress;

import com.android.car.hal.PowerHalService.PowerState;
import com.android.car.hal.test.AidlVehiclePropConfigBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class PowerHalServiceUnitTest {

    private static final String TAG = PowerHalServiceUnitTest.class.getSimpleName();

    private static final SparseBooleanArray CAN_POSTPONE_SHUTDOWN = new SparseBooleanArray(6);
    static {
        CAN_POSTPONE_SHUTDOWN.put(CAN_HIBERNATE, true);
        CAN_POSTPONE_SHUTDOWN.put(HIBERNATE_IMMEDIATELY, false);
        CAN_POSTPONE_SHUTDOWN.put(CAN_SLEEP, true);
        CAN_POSTPONE_SHUTDOWN.put(SLEEP_IMMEDIATELY, false);
        CAN_POSTPONE_SHUTDOWN.put(SHUTDOWN_ONLY, true);
        CAN_POSTPONE_SHUTDOWN.put(SHUTDOWN_IMMEDIATELY, false);
    }
    private static final SparseBooleanArray CAN_SUSPEND = new SparseBooleanArray(6);
    static {
        CAN_SUSPEND.put(CAN_HIBERNATE, true);
        CAN_SUSPEND.put(HIBERNATE_IMMEDIATELY, true);
        CAN_SUSPEND.put(CAN_SLEEP, true);
        CAN_SUSPEND.put(SLEEP_IMMEDIATELY, true);
        CAN_SUSPEND.put(SHUTDOWN_ONLY, false);
        CAN_SUSPEND.put(SHUTDOWN_IMMEDIATELY, false);
    }
    private static final SparseIntArray SUSPEND_TYPE = new SparseIntArray(7);
    static {
        SUSPEND_TYPE.put(CAN_HIBERNATE, SHUTDOWN_TYPE_HIBERNATION);
        SUSPEND_TYPE.put(HIBERNATE_IMMEDIATELY, SHUTDOWN_TYPE_HIBERNATION);
        SUSPEND_TYPE.put(CAN_SLEEP, SHUTDOWN_TYPE_DEEP_SLEEP);
        SUSPEND_TYPE.put(SLEEP_IMMEDIATELY, SHUTDOWN_TYPE_DEEP_SLEEP);
        SUSPEND_TYPE.put(SHUTDOWN_ONLY, SHUTDOWN_TYPE_POWER_OFF);
        SUSPEND_TYPE.put(SHUTDOWN_IMMEDIATELY, SHUTDOWN_TYPE_POWER_OFF);
        SUSPEND_TYPE.put(0, SHUTDOWN_TYPE_POWER_OFF);  // for no flag.
    }

    private final FakeContext mFakeContext = new FakeContext();

    private final HalPropValueBuilder mPropValueBuilder =
            new HalPropValueBuilder(/* isAidl= */ true);
    private final PowerEventListenerImpl mEventListener = new PowerEventListenerImpl();

    @Mock
    private VehicleHal mHal;
    @Mock
    private DisplayManager mDisplayManager;
    private PowerHalService mPowerHalService;

    @Before
    public void setUp() {
        mFakeContext.setSystemService(DisplayManager.class, mDisplayManager);

        mPowerHalService = new PowerHalService(mFakeContext, mHal);
        mPowerHalService.setListener(mEventListener);
    }

    private void initPowerHal() {
        int[] propertyIds = new int[]{
            AP_POWER_STATE_REQ,
            AP_POWER_STATE_REPORT,
            DISPLAY_BRIGHTNESS,
            PER_DISPLAY_BRIGHTNESS,
            VEHICLE_IN_USE,
        };
        List<HalPropConfig> configs = new ArrayList<>();
        for (int propertyId : propertyIds) {
            configs.add(new AidlHalPropConfig(
                    AidlVehiclePropConfigBuilder.newBuilder(propertyId).build()));
        }
        mPowerHalService.takeProperties(configs);
        mPowerHalService.init();
    }

    @Test
    public void testInit() throws Exception {
        initPowerHal();

        verify(mHal).subscribeProperty(any(), eq(AP_POWER_STATE_REQ));
        verify(mHal).subscribeProperty(any(), eq(DISPLAY_BRIGHTNESS));
        verify(mHal).subscribeProperty(any(), eq(PER_DISPLAY_BRIGHTNESS));
    }

    @Test
    public void testInit_subscribeFailure() throws Exception {
        doThrow(new IllegalArgumentException("")).when(mHal)
                .subscribeProperty(any(), eq(AP_POWER_STATE_REQ));

        assertThrows(IllegalArgumentException.class, () -> initPowerHal());
    }

    @Test
    public void testReleaseUnsubscribe() throws Exception {
        initPowerHal();

        mPowerHalService.release();

        verify(mHal).unsubscribePropertySafe(any(), eq(AP_POWER_STATE_REQ));
        verify(mHal).unsubscribePropertySafe(any(), eq(DISPLAY_BRIGHTNESS));
        verify(mHal).unsubscribePropertySafe(any(), eq(PER_DISPLAY_BRIGHTNESS));
    }

    @Test
    public void testCanPostponeShutdown() throws Exception {
        for (int i = 0; i < CAN_POSTPONE_SHUTDOWN.size(); i++) {
            int param = CAN_POSTPONE_SHUTDOWN.keyAt(i);
            boolean expected = CAN_POSTPONE_SHUTDOWN.valueAt(i);
            PowerHalService.PowerState powerState = createShutdownPrepare(param);
            assertWithMessage("canPostponeShutdown with %s flag", shutdownParamToString(param))
                    .that(powerState.canPostponeShutdown()).isEqualTo(expected);
        }
    }

    @Test
    public void testCanSuspend() throws Exception {
        for (int i = 0; i < CAN_SUSPEND.size(); i++) {
            int param = CAN_SUSPEND.keyAt(i);
            boolean expected = CAN_SUSPEND.valueAt(i);
            PowerHalService.PowerState powerState = createShutdownPrepare(param);
            assertWithMessage("canPostponeShutdown with %s flag", shutdownParamToString(param))
                    .that(powerState.canSuspend()).isEqualTo(expected);
        }
    }

    @Test
    public void testGetSuspendType() throws Exception {
        for (int i = 0; i < SUSPEND_TYPE.size(); i++) {
            int param = SUSPEND_TYPE.keyAt(i);
            int expected = SUSPEND_TYPE.valueAt(i);
            PowerHalService.PowerState powerState = createShutdownPrepare(param);
            assertWithMessage("canPostponeShutdown with %s flag", shutdownParamToString(param))
                    .that(powerState.getShutdownType()).isEqualTo(expected);
        }
    }

    @Test
    public void testGetCurrentPowerState() {
        HalPropValue value = mPropValueBuilder.build(AP_POWER_STATE_REQ, /* areaId= */ 0,
                new int[]{ON, 0});
        when(mHal.get(AP_POWER_STATE_REQ)).thenReturn(value);

        PowerHalService.PowerState powerState = mPowerHalService.getCurrentPowerState();

        assertWithMessage("Current power state").that(powerState.mState).isEqualTo(ON);
        assertWithMessage("Current power param").that(powerState.mParam).isEqualTo(0);
    }

    @Test
    public void testGetCurrentPowerState_serviceSpecificError() {
        when(mHal.get(AP_POWER_STATE_REQ)).thenThrow(
                new ServiceSpecificException(StatusCode.INTERNAL_ERROR));

        PowerHalService.PowerState powerState = mPowerHalService.getCurrentPowerState();

        assertWithMessage("Current power state").that(powerState).isNull();
    }

    @Test
    public void testHalEventListenerPowerStateChange() {
        mPowerHalService.setListener(mEventListener);

        HalPropValue value = mPropValueBuilder.build(AP_POWER_STATE_REQ, /* areaId= */ 0,
                new int[]{SHUTDOWN_PREPARE, CAN_SLEEP});
        mPowerHalService.onHalEvents(List.of(value));

        assertWithMessage("Current requested power state").that(mEventListener.getPowerState())
                .isEqualTo(SHUTDOWN_PREPARE);
        assertWithMessage("Current requested power param").that(mEventListener.getPowerParam())
                .isEqualTo(CAN_SLEEP);
    }

    @Test
    public void testHalEventListenerDisplayBrightnessChange() {
        AidlHalPropConfig config = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 100).build());
        mPowerHalService.takeProperties(List.of(config));
        mPowerHalService.init();
        mPowerHalService.setListener(mEventListener);

        int expectedBrightness = 73;
        HalPropValue value = mPropValueBuilder.build(DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                expectedBrightness);
        mPowerHalService.onHalEvents(List.of(value));

        assertWithMessage("Display brightness").that(mEventListener.getDisplayBrightness())
                .isEqualTo(expectedBrightness);
    }

    @Test
    public void testHalEventListenerDisplayBrightnessChange_non100MaxBrightness() {
        AidlHalPropConfig config = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 50).build());
        mPowerHalService.takeProperties(List.of(config));
        mPowerHalService.init();
        mPowerHalService.setListener(mEventListener);

        int brightness = 24;
        // Max brightness is 50, so the expected brightness should be multiplied 2 times to fit in
        // 100 scale.
        int expectedBrightness = brightness * 2;
        HalPropValue value = mPropValueBuilder.build(DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                brightness);
        mPowerHalService.onHalEvents(List.of(value));

        assertWithMessage("Display brightness").that(mEventListener.getDisplayBrightness())
                .isEqualTo(expectedBrightness);
    }

    @Test
    public void testHalEventListenerPerDisplayBrightnessChange() {
        AidlHalPropConfig config = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(PER_DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 100).build());
        mPowerHalService.takeProperties(List.of(config));
        mPowerHalService.init();
        mPowerHalService.setListener(mEventListener);

        int displayId, displayPort;
        displayPort = displayId = 11;
        Display display = createMockDisplay(displayId, displayPort);
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{display});
        when(mDisplayManager.getDisplay(displayId)).thenReturn(display);

        int expectedBrightness = 73;
        HalPropValue value = mPropValueBuilder.build(PER_DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                new int[]{displayPort, expectedBrightness});
        mPowerHalService.onHalEvents(List.of(value));

        assertWithMessage("Display brightness")
                .that(mEventListener.getDisplayBrightness(displayPort))
                .isEqualTo(expectedBrightness);
    }

    @Test
    public void testHalEventListenerPerDisplayBrightnessChange_non100MaxBrightness() {
        AidlHalPropConfig config = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(PER_DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 50).build());
        mPowerHalService.takeProperties(List.of(config));
        mPowerHalService.init();
        mPowerHalService.setListener(mEventListener);

        int displayId, displayPort;
        displayPort = displayId = 5;
        Display display = createMockDisplay(displayId, displayPort);
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{display});
        when(mDisplayManager.getDisplay(displayId)).thenReturn(display);

        int brightness = 24;
        // Max brightness is 50, so the expected brightness should be multiplied 2 times to fit in
        // 100 scale.
        int expectedBrightness = brightness * 2;
        HalPropValue value = mPropValueBuilder.build(PER_DISPLAY_BRIGHTNESS, /* areaId= */ 0,
                new int[]{displayPort, brightness});
        mPowerHalService.onHalEvents(List.of(value));

        assertWithMessage("Display brightness")
                .that(mEventListener.getDisplayBrightness(displayPort))
                .isEqualTo(expectedBrightness);
    }

    @Test
    public void testSendDisplayBrightness() {
        VehicleHal.HalPropValueSetter propValueSetter = mock(VehicleHal.HalPropValueSetter.class);
        when(mHal.set(VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 0))
                .thenReturn(propValueSetter);

        AidlHalPropConfig config = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 100).build());
        mPowerHalService.takeProperties(List.of(config));
        mPowerHalService.init();

        int brightnessToSet = 77;
        mPowerHalService.sendDisplayBrightness(brightnessToSet);

        verify(propValueSetter).to(brightnessToSet);
    }

    @Test
    public void testSendDisplayBrightness_perDisplayBrightnessSupported() {
        VehicleHal.HalPropValueSetter propValueSetter = mock(VehicleHal.HalPropValueSetter.class);
        when(mHal.set(VehicleProperty.DISPLAY_BRIGHTNESS, /* areaId= */ 0))
                .thenReturn(propValueSetter);

        AidlHalPropConfig configDisplayBrightness = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 100).build());
        AidlHalPropConfig configPerDisplayBrightness = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(PER_DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 100).build());
        mPowerHalService.takeProperties(
                List.of(configDisplayBrightness, configPerDisplayBrightness));
        mPowerHalService.init();

        mPowerHalService.sendDisplayBrightness(10);

        verify(propValueSetter, never()).to(anyInt());
    }

    @Test
    public void testSendDisplayBrightnessPerDisplay() {
        VehicleHal.HalPropValueSetter propValueSetter = mock(VehicleHal.HalPropValueSetter.class);
        when(mHal.set(VehicleProperty.PER_DISPLAY_BRIGHTNESS, /* areaId= */ 0))
                .thenReturn(propValueSetter);

        int displayId, displayPort;
        displayId = displayPort = 2;
        Display display = createMockDisplay(displayId, displayPort);
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{display});
        when(mDisplayManager.getDisplay(displayId)).thenReturn(display);

        int brightnessToSet = 41;
        HalPropValueBuilder propValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue message = propValueBuilder.build(VehicleProperty.PER_DISPLAY_BRIGHTNESS,
                /* areaId= */ 0, new int[]{displayPort, brightnessToSet});
        when(mHal.getHalPropValueBuilder()).thenReturn(propValueBuilder);

        AidlHalPropConfig config = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(PER_DISPLAY_BRIGHTNESS)
                .addAreaConfig(/* areaId= */ 0, /* minValue= */ 0, /* maxValue= */ 100).build());
        mPowerHalService.takeProperties(List.of(config));
        mPowerHalService.init();

        mPowerHalService.sendDisplayBrightness(displayId, brightnessToSet);

        verify(mHal).set(eq(message));
    }

    @Test
    public void testIsDeepSleepAllowed() {
        AidlHalPropConfig config = new AidlHalPropConfig(
                AidlVehiclePropConfigBuilder.newBuilder(AP_POWER_STATE_REQ)
                .setConfigArray(List.of(ENABLE_DEEP_SLEEP_FLAG)).build());
        mPowerHalService.takeProperties(List.of(config));

        assertWithMessage("Deep sleep enabled").that(mPowerHalService.isDeepSleepAllowed())
                .isTrue();
    }

    @Test
    public void testIsDeepSleepAllowed_notSupported() {
        assertWithMessage("Deep sleep enabled").that(mPowerHalService.isDeepSleepAllowed())
                .isFalse();
    }

    @Test
    public void testIsHibernationAllowed() {
        AidlHalPropConfig config = new AidlHalPropConfig(AidlVehiclePropConfigBuilder
                .newBuilder(AP_POWER_STATE_REQ).setConfigArray(List.of(ENABLE_HIBERNATION_FLAG))
                .build());
        mPowerHalService.takeProperties(List.of(config));

        assertWithMessage("Hibernation enabled").that(mPowerHalService.isHibernationAllowed())
                .isTrue();
    }

    @Test
    public void testIsHibernationAllowed_notSupported() {
        assertWithMessage("Hibernation enabled").that(mPowerHalService.isHibernationAllowed())
                .isFalse();
    }

    @Test
    public void testIsTimedWakeupAllowed() {
        AidlHalPropConfig config = new AidlHalPropConfig(AidlVehiclePropConfigBuilder
                .newBuilder(AP_POWER_STATE_REQ)
                .setConfigArray(List.of(CONFIG_SUPPORT_TIMER_POWER_ON_FLAG)).build());
        mPowerHalService.takeProperties(List.of(config));

        assertWithMessage("Timed wakeup enabled").that(mPowerHalService.isTimedWakeupAllowed())
                .isTrue();
    }

    @Test
    public void testIsTimedWakeupAllowed_notSupported() {
        assertWithMessage("Timed wakeup enabled").that(mPowerHalService.isTimedWakeupAllowed())
                .isFalse();
    }

    @Test
    public void testRequestShutdownAp() {
        VehicleHal.HalPropValueSetter propValueSetter = mock(VehicleHal.HalPropValueSetter.class);
        when(mHal.set(VehicleProperty.SHUTDOWN_REQUEST, /* areaId= */ 0))
                .thenReturn(propValueSetter);
        ArrayMap<Pair<Integer, Boolean>, Integer> testCases = new ArrayMap<>();
        testCases.put(new Pair<>(PowerState.SHUTDOWN_TYPE_POWER_OFF, true),
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        testCases.put(new Pair<>(PowerState.SHUTDOWN_TYPE_POWER_OFF, false),
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
        testCases.put(new Pair<>(PowerState.SHUTDOWN_TYPE_DEEP_SLEEP, true),
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        testCases.put(new Pair<>(PowerState.SHUTDOWN_TYPE_DEEP_SLEEP, false),
                VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);
        testCases.put(new Pair<>(PowerState.SHUTDOWN_TYPE_HIBERNATION, true),
                VehicleApPowerStateShutdownParam.CAN_HIBERNATE);
        testCases.put(new Pair<>(PowerState.SHUTDOWN_TYPE_HIBERNATION, false),
                VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY);

        for (int i = 0; i < testCases.size(); i++) {
            Pair<Integer, Boolean> request = testCases.keyAt(i);
            int powerState = request.first;
            boolean runGarageMode = request.second;
            Log.i(TAG, "Testing requestShutdownAp(" + powerState + ", " + runGarageMode + ")");
            mPowerHalService.requestShutdownAp(powerState, runGarageMode);
            verify(propValueSetter).to(testCases.valueAt(i).intValue());
            clearInvocations(propValueSetter);
        }
    }

    @Test
    public void testRequestShutdownAp_invalidInput() {
        VehicleHal.HalPropValueSetter propValueSetter = mock(VehicleHal.HalPropValueSetter.class);
        when(mHal.set(VehicleProperty.SHUTDOWN_REQUEST, /* areaId= */ 0))
                .thenReturn(propValueSetter);

        mPowerHalService.requestShutdownAp(/* powerState= */ 99999, /* runGarageMode= */ true);

        verify(propValueSetter, never()).to(anyInt());
    }

    @Test
    public void testVehicleInUse_true() {
        when(mHal.get(VEHICLE_IN_USE)).thenReturn(
                mPropValueBuilder.build(VEHICLE_IN_USE, /* areaId= */ 0, new int[]{1}));

        assertThat(mPowerHalService.isVehicleInUse()).isTrue();
    }

    @Test
    public void testVehicleInUse_false() {
        when(mHal.get(VEHICLE_IN_USE)).thenReturn(
                mPropValueBuilder.build(VEHICLE_IN_USE, /* areaId= */ 0, new int[]{0}));

        assertThat(mPowerHalService.isVehicleInUse()).isFalse();
    }

    @Test
    public void testVehicleInUse_serviceSpecificException() {
        when(mHal.get(VEHICLE_IN_USE)).thenThrow(new ServiceSpecificException(0));

        assertThat(mPowerHalService.isVehicleInUse()).isTrue();
    }

    @Test
    public void testVehicleInUse_illegalArgumentException() {
        when(mHal.get(VEHICLE_IN_USE)).thenThrow(new IllegalArgumentException());

        assertThat(mPowerHalService.isVehicleInUse()).isTrue();
    }

    @Test
    public void testGetVehicleApBootupReason_UserPowerOn() {
        when(mHal.get(AP_POWER_BOOTUP_REASON)).thenReturn(
                mPropValueBuilder.build(AP_POWER_BOOTUP_REASON, /* areaId= */ 0,
                        new int[] {VehicleApPowerBootupReason.USER_POWER_ON}));

        assertThat(mPowerHalService.getVehicleApBootupReason()).isEqualTo(
                PowerHalService.BOOTUP_REASON_USER_POWER_ON);
    }

    @Test
    public void testGetVehicleApBootupReason_SystemUserDetection() {
        when(mHal.get(AP_POWER_BOOTUP_REASON)).thenReturn(
                mPropValueBuilder.build(AP_POWER_BOOTUP_REASON, /* areaId= */ 0,
                        new int[] {VehicleApPowerBootupReason.SYSTEM_USER_DETECTION}));

        assertThat(mPowerHalService.getVehicleApBootupReason()).isEqualTo(
                PowerHalService.BOOTUP_REASON_SYSTEM_USER_DETECTION);
    }

    @Test
    public void testGetVehicleApBootupReason_SystemRemoteAccess() {
        when(mHal.get(AP_POWER_BOOTUP_REASON)).thenReturn(
                mPropValueBuilder.build(AP_POWER_BOOTUP_REASON, /* areaId= */ 0,
                        new int[] {VehicleApPowerBootupReason.SYSTEM_REMOTE_ACCESS}));

        assertThat(mPowerHalService.getVehicleApBootupReason()).isEqualTo(
                PowerHalService.BOOTUP_REASON_SYSTEM_REMOTE_ACCESS);
    }

    @Test
    public void testGetVehicleApBootupReason_SystemEnterGarageMode() {
        when(mHal.get(AP_POWER_BOOTUP_REASON)).thenReturn(
                mPropValueBuilder.build(AP_POWER_BOOTUP_REASON, /* areaId= */ 0,
                        new int[] {VehicleApPowerBootupReason.SYSTEM_ENTER_GARAGE_MODE}));

        assertThat(mPowerHalService.getVehicleApBootupReason()).isEqualTo(
                PowerHalService.BOOTUP_REASON_SYSTEM_ENTER_GARAGE_MODE);
    }

    @Test
    public void testGetVehicleApBootupReason_unknownReason() {
        when(mHal.get(AP_POWER_BOOTUP_REASON)).thenReturn(
                mPropValueBuilder.build(AP_POWER_BOOTUP_REASON, /* areaId= */ 0,
                        new int[] {123456}));

        assertThat(mPowerHalService.getVehicleApBootupReason()).isEqualTo(
                PowerHalService.BOOTUP_REASON_UNKNOWN);
    }

    @Test
    public void testGetVehicleApBootupReason_exception() {
        when(mHal.get(AP_POWER_BOOTUP_REASON)).thenThrow(new ServiceSpecificException(0));

        assertThat(mPowerHalService.getVehicleApBootupReason()).isEqualTo(
                PowerHalService.BOOTUP_REASON_UNKNOWN);
    }

    @Test
    public void testGetVehicleApBootupReason_noValue() {
        when(mHal.get(AP_POWER_BOOTUP_REASON)).thenReturn(
                mPropValueBuilder.build(AP_POWER_BOOTUP_REASON, /* areaId= */ 0, new int[] {}));

        assertThat(mPowerHalService.getVehicleApBootupReason()).isEqualTo(
                PowerHalService.BOOTUP_REASON_UNKNOWN);
    }

    private PowerHalService.PowerState createShutdownPrepare(int flag) {
        return new PowerHalService.PowerState(SHUTDOWN_PREPARE, flag);
    }

    private static String shutdownParamToString(int param) {
        switch (param) {
            case CAN_HIBERNATE:
                return "CAN_HIBERNATE";
            case HIBERNATE_IMMEDIATELY:
                return "HIBERNATE_IMMEDIATELY";
            case CAN_SLEEP:
                return "CAN_SLEEP";
            case SLEEP_IMMEDIATELY:
                return "SLEEP_IMMEDIATELY";
            case SHUTDOWN_ONLY:
                return "SHUTDOWN_ONLY";
            case SHUTDOWN_IMMEDIATELY:
                return "SHUTDOWN_IMMEDIATELY";
            default:
                return "UNKNOWN(" + param + ")";
        }
    }

    private Display createMockDisplay(int displayId, int displayPort) {
        Display display = mock(Display.class);
        DisplayAddress.Physical displayAddress = mock(DisplayAddress.Physical.class);
        when(displayAddress.getPort()).thenReturn(displayPort);
        when(display.getDisplayId()).thenReturn(displayId);
        when(display.getAddress()).thenReturn(displayAddress);
        return display;
    }

    private static final class PowerEventListenerImpl
            implements PowerHalService.PowerEventListener {
        private static final int INVALID_VALUE = -1;

        private PowerHalService.PowerState mPowerState;
        // Key: displayId, Value: brightness of the display.
        private SparseIntArray mPerDisplayBrightness = new SparseIntArray();

        @Override
        public void onApPowerStateChange(PowerHalService.PowerState state) {
            mPowerState = state;
        }

        @Override
        public void onDisplayBrightnessChange(int brightness) {
            mPerDisplayBrightness.put(Display.DEFAULT_DISPLAY, brightness);
        }

        @Override
        public void onDisplayBrightnessChange(int displayId, int brightness) {
            mPerDisplayBrightness.put(displayId, brightness);
        }

        public int getPowerState() {
            return mPowerState != null ? mPowerState.mState : INVALID_VALUE;
        }

        public int getPowerParam() {
            return mPowerState != null ? mPowerState.mParam : INVALID_VALUE;
        }

        public int getDisplayBrightness() {
            return mPerDisplayBrightness.get(Display.DEFAULT_DISPLAY);
        }

        public int getDisplayBrightness(int displayId) {
            return mPerDisplayBrightness.get(displayId);
        }
    }
}
