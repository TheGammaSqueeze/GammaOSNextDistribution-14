/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.input;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;
import static android.hardware.automotive.vehicle.CustomInputType.CUSTOM_EVENT_F1;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.annotation.NonNull;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.RotaryEvent;
import android.hardware.automotive.vehicle.RotaryInputType;
import android.hardware.automotive.vehicle.VehicleDisplay;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;

import com.android.car.CarServiceUtils;
import com.android.car.MockedCarTestBase;
import com.android.car.hal.test.AidlVehiclePropValueBuilder;
import com.android.internal.annotations.GuardedBy;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@FlakyTest(bugId = 311029405)
@RunWith(AndroidJUnit4.class)
@MediumTest
public final class CarInputManagerTest extends MockedCarTestBase {
    private static final String TAG = CarInputManagerTest.class.getSimpleName();

    private static final int INVALID_DISPLAY_TYPE = -1;
    private static final int INVALID_INPUT_TYPE = -1;

    private CarInputManager mCarInputManager;

    private static final class CaptureCallback implements CarInputManager.CarInputCaptureCallback {

        private static final long EVENT_WAIT_TIME = 5_000;

        private final Object mLock = new Object();

        private final String mName;

        private CaptureCallback(String name) {
            mName = name;
        }

        // Stores passed events. Last one in front
        @GuardedBy("mLock")
        private final LinkedList<Pair<Integer, List<KeyEvent>>> mKeyEvents = new LinkedList<>();

        // Stores passed events. Last one in front
        @GuardedBy("mLock")
        private final LinkedList<Pair<Integer, List<RotaryEvent>>> mRotaryEvents =
                new LinkedList<>();

        // Stores passed events. Last one in front
        @GuardedBy("mLock")
        private final LinkedList<Pair<Integer, List<CustomInputEvent>>> mCustomInputEvents =
                new LinkedList<>();

        // Stores passed state changes. Last one in front
        @GuardedBy("mLock")
        private final LinkedList<Pair<Integer, int[]>> mStateChanges = new LinkedList<>();

        private final Semaphore mKeyEventWait = new Semaphore(0);
        private final Semaphore mRotaryEventWait = new Semaphore(0);
        private final Semaphore mStateChangeWait = new Semaphore(0);
        private final Semaphore mCustomInputEventWait = new Semaphore(0);

        @Override
        public void onKeyEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<KeyEvent> keyEvents) {
            Log.i(TAG, "onKeyEvents event:" + keyEvents.get(0) + " this:" + this);
            synchronized (mLock) {
                mKeyEvents.addFirst(new Pair<>(targetDisplayType, keyEvents));
            }
            mKeyEventWait.release();
        }

        @Override
        public void onRotaryEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<RotaryEvent> events) {
            Log.i(TAG, "onRotaryEvents event:" + events.get(0) + " this:" + this);
            synchronized (mLock) {
                mRotaryEvents.addFirst(new Pair<>(targetDisplayType, events));
            }
            mRotaryEventWait.release();
        }

        @Override
        public void onCustomInputEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<CustomInputEvent> events) {
            Log.i(TAG, "onCustomInputEvents event:" + events.get(0) + " this:" + this);
            synchronized (mLock) {
                mCustomInputEvents.addFirst(new Pair<>(targetDisplayType, events));
            }
            mCustomInputEventWait.release();
        }

        @Override
        public void onCaptureStateChanged(@DisplayTypeEnum int targetDisplayType,
                @NonNull @CarInputManager.InputTypeEnum int[] activeInputTypes) {
            Log.i(TAG, "onCaptureStateChanged types:" + Arrays.toString(activeInputTypes)
                    + " this:" + this);
            synchronized (mLock) {
                mStateChanges.addFirst(new Pair<>(targetDisplayType, activeInputTypes));
            }
            mStateChangeWait.release();
        }

        private void resetAllEventsWaiting() {
            mStateChangeWait.drainPermits();
            mKeyEventWait.drainPermits();
            mRotaryEventWait.drainPermits();
        }

        private void waitForStateChange() throws Exception {
            assertWithMessage("Failed to acquire semaphore in %s ms", EVENT_WAIT_TIME).that(
                    mStateChangeWait.tryAcquire(EVENT_WAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
        }

        private void waitForKeyEvent() throws Exception {
            assertWithMessage("Failed to acquire semaphore in %s ms", EVENT_WAIT_TIME).that(
                    mKeyEventWait.tryAcquire(EVENT_WAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
        }

        private void waitForRotaryEvent() throws Exception {
            assertWithMessage("Failed to acquire semaphore in %s ms", EVENT_WAIT_TIME).that(
                    mRotaryEventWait.tryAcquire(EVENT_WAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
        }

        private void waitForCustomInputEvent() throws Exception {
            assertWithMessage("Failed to acquire semaphore in %s ms", EVENT_WAIT_TIME).that(
                    mCustomInputEventWait.tryAcquire(
                            EVENT_WAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
        }

        private LinkedList<Pair<Integer, List<KeyEvent>>> getkeyEvents() {
            synchronized (mLock) {
                LinkedList<Pair<Integer, List<KeyEvent>>> r =
                        new LinkedList<>(mKeyEvents);
                Log.i(TAG, "getKeyEvents size:" + r.size() + ",this:" + this);
                return r;
            }
        }

        private LinkedList<Pair<Integer, List<RotaryEvent>>> getRotaryEvents() {
            synchronized (mLock) {
                LinkedList<Pair<Integer, List<RotaryEvent>>> r =
                        new LinkedList<>(mRotaryEvents);
                Log.i(TAG, "getRotaryEvents size:" + r.size() + ",this:" + this);
                return r;
            }
        }

        private LinkedList<Pair<Integer, int[]>> getStateChanges() {
            synchronized (mLock) {
                return new LinkedList<>(mStateChanges);
            }
        }

        private LinkedList<Pair<Integer, List<CustomInputEvent>>> getCustomInputEvents() {
            synchronized (mLock) {
                LinkedList<Pair<Integer, List<CustomInputEvent>>> r =
                        new LinkedList<>(mCustomInputEvents);
                Log.i(TAG, "getCustomInputEvents size:" + r.size() + ",this:" + this);
                return r;
            }
        }

        @Override
        public String toString() {
            return "CaptureCallback{mName='" + mName + "'}";
        }
    }

    private final CaptureCallback mCallback0 = new CaptureCallback("callback0");
    private final CaptureCallback mCallback1 = new CaptureCallback("callback1");
    private final CaptureCallback mCallback2 = new CaptureCallback("callback2");

    @Override
    protected void configureMockedHal() {
        addAidlProperty(VehicleProperty.HW_KEY_INPUT,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                        .addIntValues(0, 0, 0)
                        .build());
        addAidlProperty(VehicleProperty.HW_ROTARY_INPUT,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.HW_ROTARY_INPUT)
                        .addIntValues(0, 1, 0)
                        .build());
        addAidlProperty(VehicleProperty.HW_CUSTOM_INPUT,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.HW_CUSTOM_INPUT)
                        .addIntValues(0)
                        .build());
    }

    @Override
    protected void configureResourceOverrides(MockResources resources) {
        super.configureResourceOverrides(resources);
        resources.overrideResource(com.android.car.R.string.config_clusterHomeActivity,
                getTestContext().getPackageName() + "/" + CarInputManagerTest.class.getName());
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mCarInputManager = (CarInputManager) getCar().getCarManager(Car.CAR_INPUT_SERVICE);
        assertThat(mCarInputManager).isNotNull();
    }

    @Test
    public void testInvalidArgs() {
        // Invalid display
        assertThrows(IllegalArgumentException.class,
                () -> mCarInputManager.requestInputEventCapture(INVALID_DISPLAY_TYPE,
                        new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback0));

        // Invalid input types
        assertThrows(IllegalArgumentException.class,
                () -> mCarInputManager.requestInputEventCapture(
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                        new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, INVALID_INPUT_TYPE},
                        0, mCallback0));
        assertThrows(IllegalArgumentException.class,
                () -> mCarInputManager.requestInputEventCapture(
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                        new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION},
                        CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0));
        assertThrows(IllegalArgumentException.class,
                () -> mCarInputManager.requestInputEventCapture(
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN, new int[]{INVALID_INPUT_TYPE},
                        0, mCallback0));
        assertThrows(IllegalArgumentException.class,
                () -> mCarInputManager.requestInputEventCapture(
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                        new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, INVALID_INPUT_TYPE},
                        0, mCallback0));
    }

    private CarInputManager createAnotherCarInputManager() {
        return (CarInputManager) createNewCar().getCarManager(Car.CAR_INPUT_SERVICE);
    }

    @Test
    public void testInjectKeyEvent_mainDisplay() throws Exception {
        int r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        KeyEvent keyEvent = newKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);

        mCarInputManager.injectKeyEvent(keyEvent, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        mCallback0.waitForKeyEvent();
        assertThat(mCallback0.getkeyEvents()).containsExactly(
                new Pair<>(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                        Collections.singletonList(keyEvent)));
    }

    @Test
    public void testInjectKeyEvent_instrumentClusterDisplay() throws Exception {
        int r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        KeyEvent keyEvent = newKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);

        mCarInputManager.injectKeyEvent(keyEvent,
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);

        mCallback0.waitForKeyEvent();
        assertThat(mCallback0.getkeyEvents()).containsExactly(
                new Pair<>(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                        Collections.singletonList(keyEvent)));
    }

    private static KeyEvent newKeyEvent(int action, int code) {
        long currentTime = SystemClock.uptimeMillis();
        return new KeyEvent(/* downTime= */ currentTime,
                /* eventTime= */ currentTime, action, code,
                /* repeat= */ 0);
    }

    @Test
    public void testFailWithFullCaptureHigherPriority() {
        CarInputManager carInputManager0 = createAnotherCarInputManager();
        int r = carInputManager0.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        //TODO(b/151225008) test event

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_FAILED);

        carInputManager0.releaseInputEventCapture(CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        //TODO(b/151225008) test event
    }

    @Test
    public void testDelayedGrantWithFullCapture() throws Exception {
        mCallback1.resetAllEventsWaiting();
        CarInputManager carInputManager0 = createAnotherCarInputManager();
        int r = carInputManager0.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        injectKeyEvent(true, KeyEvent.KEYCODE_NAVIGATE_NEXT);
        waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, true,
                KeyEvent.KEYCODE_NAVIGATE_NEXT, mCallback0);

        injectKeyEvent(true, KeyEvent.KEYCODE_DPAD_CENTER);
        waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, true,
                KeyEvent.KEYCODE_DPAD_CENTER, mCallback0);

        int numClicks = 3;
        injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);
        waitAndAssertLastRotaryEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, numClicks, mCallback0);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION},
                CarInputManager.CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_DELAYED);

        injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);
        waitForDispatchToMain();
        assertNumberOfOnRotaryEvents(0, mCallback1);

        carInputManager0.releaseInputEventCapture(CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        // Now capture should be granted back
        waitAndAssertLastStateChange(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION},
                mCallback1);
        assertNoStateChange(mCallback0);

        injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);
        waitAndAssertLastRotaryEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, numClicks, mCallback1);
    }

    @Test
    public void testOneClientTransitionFromFullToNonFull() throws Exception {
        CarInputManager carInputManager0 = createAnotherCarInputManager();

        int r = carInputManager0.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION,
                        CarInputManager.INPUT_TYPE_NAVIGATE_KEYS},
                CarInputManager.CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_DELAYED);

        r = carInputManager0.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION,
                        CarInputManager.INPUT_TYPE_DPAD_KEYS}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        waitForDispatchToMain();
        waitAndAssertLastStateChange(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_NAVIGATE_KEYS},
                mCallback1);
        assertNoStateChange(mCallback0);
    }

    @Test
    public void testSwitchFromFullCaptureToPerTypeCapture() {
        int r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT}, 0, mCallback2);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);
    }

    @Test
    public void testIndependentTwoCaptures() throws Exception {
        int r = createAnotherCarInputManager().requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        int numClicks = 3;
        injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);
        waitAndAssertLastRotaryEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, numClicks, mCallback0);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_NAVIGATE_KEYS}, 0, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        injectKeyEvent(true, KeyEvent.KEYCODE_NAVIGATE_NEXT);
        waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, true,
                KeyEvent.KEYCODE_NAVIGATE_NEXT, mCallback1);
    }

    @Test
    public void testTwoClientsOverwrap() throws Exception {
        CarInputManager carInputManager0 = createAnotherCarInputManager();
        CarInputManager carInputManager1 = createAnotherCarInputManager();

        mCallback0.resetAllEventsWaiting();
        int r = carInputManager0.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_DPAD_KEYS,
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        injectKeyEvent(true, KeyEvent.KEYCODE_DPAD_CENTER);
        waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, true,
                KeyEvent.KEYCODE_DPAD_CENTER, mCallback0);

        int numClicks = 3;
        injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);
        waitAndAssertLastRotaryEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, numClicks, mCallback0);

        r = carInputManager1.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION,
                        CarInputManager.INPUT_TYPE_NAVIGATE_KEYS}, 0, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        waitAndAssertLastStateChange(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_DPAD_KEYS},
                mCallback0);
        assertNoStateChange(mCallback1);

        injectKeyEvent(true, KeyEvent.KEYCODE_NAVIGATE_NEXT);
        waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, true,
                KeyEvent.KEYCODE_NAVIGATE_NEXT, mCallback1);
        assertNumberOfOnKeyEvents(1, mCallback0);

        injectKeyEvent(true, KeyEvent.KEYCODE_DPAD_CENTER);
        waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, true,
                KeyEvent.KEYCODE_DPAD_CENTER, mCallback0);
        assertNumberOfOnKeyEvents(2, mCallback0);

        injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);
        waitAndAssertLastRotaryEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, numClicks, mCallback1);
        assertNumberOfOnRotaryEvents(1, mCallback0);

        mCallback0.resetAllEventsWaiting();
        carInputManager1.releaseInputEventCapture(CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        waitAndAssertLastStateChange(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_DPAD_KEYS,
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION},
                mCallback0);
        assertNoStateChange(mCallback1);

        injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);
        waitAndAssertLastRotaryEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, numClicks, mCallback0);
    }

    @Test
    public void testInteractionWithFullCapturer() throws Exception {
        CarInputManager carInputManager0 = createAnotherCarInputManager();
        CarInputManager carInputManager1 = createAnotherCarInputManager();

        // Request rotary and dpad input event capture for display main (mCallback0)
        int r = carInputManager0.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_DPAD_KEYS,
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        // Request all input event capture for display main (mCallback1)
        r = carInputManager1.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback1);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        waitForDispatchToMain();
        waitAndAssertLastStateChange(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[0], mCallback0);
        assertNoStateChange(mCallback1);

        // Release input event capture for main display
        carInputManager1.releaseInputEventCapture(CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        waitForDispatchToMain();
        waitAndAssertLastStateChange(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_DPAD_KEYS,
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION},
                mCallback0);
        assertNoStateChange(mCallback1);
    }

    @Test
    public void testFullCapturerAcceptsNotMappedKey() throws Exception {
        int r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        injectKeyEvent(true, KeyEvent.KEYCODE_MENU, VehicleDisplay.INSTRUMENT_CLUSTER);
        waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER, true,
                KeyEvent.KEYCODE_MENU, mCallback0);
    }

    @Test
    public void testSingleClientUpdates() {
        int r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_DPAD_KEYS,
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_DPAD_KEYS,
                        CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        waitForDispatchToMain();
        assertNoStateChange(mCallback0);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{
                        CarInputManager.INPUT_TYPE_DPAD_KEYS,
                        CarInputManager.INPUT_TYPE_NAVIGATE_KEYS}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        waitForDispatchToMain();
        assertNoStateChange(mCallback0);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        waitForDispatchToMain();
        assertNoStateChange(mCallback0);

        r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        waitForDispatchToMain();
        assertNoStateChange(mCallback0);
    }

    @Test
    public void testInjectingRotaryEventAndExecutor() throws Exception {
        // Arrange executors to process events
        ExecutorService rotaryExecutor = Executors.newSingleThreadExecutor();
        try {
            // Arrange: register callback
            CarInputManager carInputManager = createAnotherCarInputManager();
            int r = carInputManager.requestInputEventCapture(
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, rotaryExecutor,
                    mCallback0);
            assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

            // Act: inject RotaryEvent
            int numClicks = 3;
            injectRotaryNavigationEvent(VehicleDisplay.MAIN, numClicks);

            // Assert: ensure RotaryEvent was delivered
            waitAndAssertLastRotaryEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION, numClicks, mCallback0);
        } finally {
            rotaryExecutor.shutdown();
        }
    }

    @Test
    public void testInjectingKeyEventAndExecutor() throws Exception {
        // Arrange executors to process events
        ExecutorService keyEventExecutor = Executors.newSingleThreadExecutor();
        try {
            // Arrange: register callback
            CarInputManager carInputManager = createAnotherCarInputManager();
            int r = carInputManager.requestInputEventCapture(
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    new int[]{CarInputManager.INPUT_TYPE_NAVIGATE_KEYS}, 0, keyEventExecutor,
                    mCallback0);
            assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

            // Act: inject KeyEvent
            injectKeyEvent(true, KeyEvent.KEYCODE_NAVIGATE_NEXT);

            // Assert: ensure KeyEvent was delivered
            waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, true,
                    KeyEvent.KEYCODE_NAVIGATE_NEXT, mCallback0);
        } finally {
            keyEventExecutor.shutdown();
        }
    }

    @Test
    public void testInjectingVoiceAssistKeyEventAndExecutor() throws Exception {
        // Arrange executors to process events
        ExecutorService keyEventExecutor = Executors.newSingleThreadExecutor();
        try {
            // Arrange: register callback
            CarInputManager carInputManager = createAnotherCarInputManager();
            int r = carInputManager.requestInputEventCapture(
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    new int[]{CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS}, 0, keyEventExecutor,
                    mCallback0);
            assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

            // Act: inject KeyEvent
            injectKeyEvent(false, KeyEvent.KEYCODE_VOICE_ASSIST);

            // Assert: ensure KeyEvent was delivered
            waitAndAssertLastKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, false,
                    KeyEvent.KEYCODE_VOICE_ASSIST, mCallback0);
        } finally {
            keyEventExecutor.shutdown();
        }
    }


    @Test
    public void testInjectingCustomInputEventAndExecutor() throws Exception {
        // Arrange executors to process events
        ExecutorService customInputEventExecutor = Executors.newSingleThreadExecutor();
        try {
            // Arrange: register callback
            CarInputManager carInputManager = createAnotherCarInputManager();
            int r = carInputManager.requestInputEventCapture(
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    new int[]{CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT}, 0,
                    customInputEventExecutor, mCallback0);
            assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

            // Act: inject CustomInputEvent
            int repeatedCounter = 1;
            injectCustomInputEvent(CUSTOM_EVENT_F1, VehicleDisplay.MAIN,
                    /* repeatCounter= */ repeatedCounter);

            // Assert: ensure CustomInputEvent was delivered
            waitAndAssertLastCustomInputEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                    CUSTOM_EVENT_F1,
                    repeatedCounter, mCallback0);
        } finally {
            customInputEventExecutor.shutdown();
        }
    }

    @Test
    public void testRotaryVolumeTypeIsNotSupportedYet() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarInputManager.requestInputEventCapture(
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                        new int[]{CarInputManager.INPUT_TYPE_ROTARY_VOLUME}, 0, mCallback0));
    }

    @Test
    public void testCallbacksForDifferentDisplays_keyInputEvents_mainDisplay() throws Exception {
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                mCallback1);

        sendAndAssertKeyEvent(KeyEvent.KEYCODE_HOME, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                mCallback0);
        assertNoKeyEventSent(mCallback1);
    }

    @Test
    public void testCallbacksForDifferentDisplays_keyInputEvents_clusterDisplay() throws Exception {
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                mCallback1);

        sendAndAssertKeyEvent(KeyEvent.KEYCODE_HOME,
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                mCallback1);
        assertNoKeyEventSent(mCallback0);
    }

    @Test
    public void testCallbacksForDifferentDisplays_customInputEvents_mainDisplay() throws Exception {
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                mCallback1);

        sendAndAssertCustomInputEvent(CUSTOM_EVENT_F1,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, /* repeatedCounter= */ 1,
                mCallback0);
        assertNoCustomInputEventSent(mCallback1);
    }

    @Test
    public void testCallbacksForDifferentDisplays_customInputEvents_clusterDisplay()
            throws Exception {
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                mCallback1);

        sendAndAssertCustomInputEvent(CUSTOM_EVENT_F1,
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER, /* repeatedCounter= */ 1,
                mCallback1);
        assertNoCustomInputEventSent(mCallback0);
    }

    @Test
    public void testCallbacksForDifferentDisplays_rotaryEvents_mainDisplay() throws Exception {
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                mCallback1);

        sendAndAssertRotaryNavigationEvent(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, /* numClicks= */ 1,
                mCallback0);
        assertNoRotaryEventSent(mCallback1);
    }

    @Test
    public void testCallbacksForDifferentDisplays_rotaryEvents_clusterDisplay() throws Exception {
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        registerCallbackForAllInputs(CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                mCallback1);

        sendAndAssertRotaryNavigationEvent(
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER, /* numClicks= */ 1,
                mCallback1);
        assertNoRotaryEventSent(mCallback0);
    }

    private void registerCallbackForAllInputs(int displayType, CaptureCallback callback) {
        int respMain = mCarInputManager.requestInputEventCapture(
                displayType,
                new int[]{CarInputManager.INPUT_TYPE_ALL_INPUTS},
                CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY, callback);
        assertThat(respMain).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);
    }

    @Test
    public void testInputTypeSystemNavigateKeys() throws Exception {
        int r = mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS}, 0, mCallback0);
        assertThat(r).isEqualTo(CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED);

        sendAndAssertKeyEvent(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        sendAndAssertKeyEvent(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        sendAndAssertKeyEvent(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
        sendAndAssertKeyEvent(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, mCallback0);
    }

    private void sendAndAssertKeyEvent(int keyCode, int displayType, CaptureCallback callback)
            throws Exception {
        KeyEvent keyEvent = newKeyEvent(KeyEvent.ACTION_DOWN, keyCode);

        mCarInputManager.injectKeyEvent(keyEvent, displayType);

        callback.waitForKeyEvent();
        assertThat(callback.getkeyEvents()).contains(
                new Pair<>(displayType,
                        Collections.singletonList(keyEvent)));
    }

    private void sendAndAssertCustomInputEvent(int inputCode, int displayType, int repeatedCounter,
            CaptureCallback callback)
            throws Exception {
        injectCustomInputEvent(inputCode,
                convertToVehicleDisplay(displayType), repeatedCounter);

        callback.waitForCustomInputEvent();
        assertThat(callback.getCustomInputEvents()).hasSize(1);
        Pair<Integer, List<CustomInputEvent>> customInputEvents =
                callback.getCustomInputEvents().get(0);
        assertThat(customInputEvents.second).hasSize(1);
        CustomInputEvent customInputEvent = customInputEvents.second.get(0);
        assertThat(customInputEvent.getInputCode()).isEqualTo(inputCode);
        assertThat(customInputEvent.getTargetDisplayType()).isEqualTo(displayType);
        assertThat(customInputEvent.getRepeatCounter()).isEqualTo(repeatedCounter);
    }

    /**
     * Utility method to convert CarOccupantZoneManager display type to Vehicle HAL display type
     */
    private static int convertToVehicleDisplay(int vehicleDisplayType) {
        switch (vehicleDisplayType) {
            case CarOccupantZoneManager.DISPLAY_TYPE_MAIN:
                return VehicleDisplay.MAIN;
            case CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER:
                return VehicleDisplay.INSTRUMENT_CLUSTER;
            default:
                throw new IllegalArgumentException(
                        "CarOccupantZone display type {" + vehicleDisplayType
                                + "} has no equivalent in VehicleDisplay display type");
        }
    }

    private void sendAndAssertRotaryNavigationEvent(int displayType, int numClicks,
            CaptureCallback callback)
            throws Exception {
        injectRotaryNavigationEvent(convertToVehicleDisplay(displayType),
                numClicks);

        callback.waitForRotaryEvent();
        assertThat(callback.getRotaryEvents()).hasSize(1);
        Pair<Integer, List<RotaryEvent>> capturedEvents = callback.getRotaryEvents().get(0);
        assertThat(capturedEvents.second).hasSize(1);
        RotaryEvent rotaryEvent = capturedEvents.second.get(0);
        assertThat(rotaryEvent.getNumberOfClicks()).isEqualTo(numClicks);
    }

    /**
     * Events dispatched to main, so this should guarantee that all event dispatched are completed.
     */
    private void waitForDispatchToMain() {
        // Needs to be invoked twice as it is dispatched to main inside car service once, and it is
        // dispatched to main inside CarInputManager once.
        CarServiceUtils.runOnMainSync(() -> {});
        CarServiceUtils.runOnMainSync(() -> {});
    }

    private void waitAndAssertLastKeyEvent(int displayTarget, boolean down, int keyCode,
            CaptureCallback callback) throws Exception {
        // Wait for key event first.
        callback.waitForKeyEvent();

        LinkedList<Pair<Integer, List<KeyEvent>>> events = callback.getkeyEvents();
        assertThat(events).isNotEmpty();
        Pair<Integer, List<KeyEvent>> lastEvent = events.getFirst();
        assertThat(lastEvent.first).isEqualTo(displayTarget);
        assertThat(lastEvent.second).hasSize(1);
        KeyEvent keyEvent = lastEvent.second.get(0);
        assertThat(keyEvent.getAction()).isEqualTo(
                down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP);
        assertThat(keyEvent.getKeyCode()).isEqualTo(keyCode);
    }

    private void assertNumberOfOnKeyEvents(int expectedNumber, CaptureCallback callback) {
        LinkedList<Pair<Integer, List<KeyEvent>>> events = callback.getkeyEvents();
        assertThat(events).hasSize(expectedNumber);
    }

    private void waitAndAssertLastRotaryEvent(int displayTarget, int rotaryType, int numberOfClicks,
            CaptureCallback callback) throws Exception {
        // Wait for rotary event first.
        callback.waitForRotaryEvent();

        LinkedList<Pair<Integer, List<RotaryEvent>>> rotaryEvents = callback.getRotaryEvents();
        assertThat(rotaryEvents).isNotEmpty();
        Pair<Integer, List<RotaryEvent>> lastEvent = rotaryEvents.getFirst();
        assertThat(lastEvent.first).isEqualTo(displayTarget);
        assertThat(lastEvent.second).hasSize(1);
        RotaryEvent rotaryEvent = lastEvent.second.get(0);
        assertThat(rotaryEvent.getInputType()).isEqualTo(rotaryType);
        assertThat(rotaryEvent.getNumberOfClicks()).isEqualTo(numberOfClicks);
        // TODO(b/151225008) Test timestamp
    }

    private void assertNumberOfOnRotaryEvents(int expectedNumber, CaptureCallback callback) {
        LinkedList<Pair<Integer, List<RotaryEvent>>> rotaryEvents = callback.getRotaryEvents();
        assertThat(rotaryEvents).hasSize(expectedNumber);
    }

    private void waitAndAssertLastStateChange(int expectedTargetDisplayTarget,
            int[] expectedInputTypes, CaptureCallback callback) throws Exception {
        // Wait for state change event first.
        callback.waitForStateChange();

        LinkedList<Pair<Integer, int[]>> changes = callback.getStateChanges();
        assertThat(changes).isNotEmpty();
        Pair<Integer, int[]> lastChange = changes.getFirst();
        assertStateChange(expectedTargetDisplayTarget, expectedInputTypes, lastChange);
    }

    private void assertNoStateChange(CaptureCallback callback) {
        assertThat(callback.getStateChanges()).isEmpty();
    }

    private void assertNoKeyEventSent(CaptureCallback callback) {
        assertThat(callback.getkeyEvents()).isEmpty();
    }

    private void assertNoCustomInputEventSent(CaptureCallback callback) {
        assertThat(callback.getCustomInputEvents()).isEmpty();
    }

    private void assertNoRotaryEventSent(CaptureCallback callback) {
        assertThat(callback.getRotaryEvents()).isEmpty();
    }

    private void assertStateChange(int expectedTargetDisplayTarget, int[] expectedInputTypes,
            Pair<Integer, int[]> actual) {
        Arrays.sort(expectedInputTypes);
        assertThat(actual.first).isEqualTo(expectedTargetDisplayTarget);
        assertThat(actual.second).isEqualTo(expectedInputTypes);
    }

    private void waitAndAssertLastCustomInputEvent(int expectedDisplayType,
            int expectedCustomEventFunction, int expectedRepeatedCounter,
            CaptureCallback callback) throws Exception {
        // Wait for custom input event first.
        callback.waitForCustomInputEvent();

        LinkedList<Pair<Integer, List<CustomInputEvent>>> events = callback.getCustomInputEvents();
        assertThat(events).isNotEmpty();

        Pair<Integer, List<CustomInputEvent>> lastEvent = events.getFirst();
        assertThat(lastEvent.first).isEqualTo(expectedDisplayType);
        assertThat(lastEvent.second).hasSize(1);

        CustomInputEvent event = lastEvent.second.get(0);
        assertThat(event.getInputCode()).isEqualTo(expectedCustomEventFunction);
        assertThat(event.getRepeatCounter()).isEqualTo(expectedRepeatedCounter);
        assertThat(event.getTargetDisplayType()).isEqualTo(expectedDisplayType);
    }

    private void injectKeyEvent(boolean down, int keyCode) {
        injectKeyEvent(down, keyCode, VehicleDisplay.MAIN);
    }

    private void injectKeyEvent(boolean down, int keyCode, int vehicleDisplayType) {
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.HW_KEY_INPUT)
                        .addIntValues(down ? 0 : 1, keyCode, vehicleDisplayType)
                        .build());
    }

    private void injectRotaryNavigationEvent(int displayTarget, int numClicks) {
        AidlVehiclePropValueBuilder builder = AidlVehiclePropValueBuilder.newBuilder(
                VehicleProperty.HW_ROTARY_INPUT)
                .addIntValues(RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION, numClicks,
                        displayTarget);
        for (int i = 0; i < numClicks - 1; i++) {
            builder.addIntValues(0);
        }
        getAidlMockedVehicleHal().injectEvent(builder.build());
    }

    private void injectCustomInputEvent(int inputCode, int targetDisplayType, int repeatCounter) {
        AidlVehiclePropValueBuilder builder = AidlVehiclePropValueBuilder.newBuilder(
                VehicleProperty.HW_CUSTOM_INPUT)
                .addIntValues(inputCode).addIntValues(targetDisplayType)
                .addIntValues(repeatCounter);
        getAidlMockedVehicleHal().injectEvent(builder.build());
    }
}
