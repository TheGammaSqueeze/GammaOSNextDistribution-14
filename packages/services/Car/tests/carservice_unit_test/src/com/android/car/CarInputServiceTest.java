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

package com.android.car;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;
import static android.car.input.CustomInputEvent.INPUT_CODE_F1;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarProjectionManager;
import android.car.VehicleAreaSeat;
import android.car.builtin.util.AssistUtilsHelper;
import android.car.builtin.util.AssistUtilsHelper.VoiceInteractionSessionShowCallbackHelper;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.ICarInputCallback;
import android.car.input.RotaryEvent;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.audio.CarAudioService;
import com.android.car.bluetooth.CarBluetoothService;
import com.android.car.hal.InputHalService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;

import com.google.common.collect.Range;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class CarInputServiceTest extends AbstractExtendedMockitoTestCase {

    @Mock InputHalService mInputHalService;
    @Mock TelecomManager mTelecomManager;
    @Mock CarInputService.KeyEventListener mDefaultKeyEventMainListener;
    @Mock CarInputService.MotionEventListener mDefaultMotionEventMainListener;
    @Mock CarInputService.KeyEventListener mInstrumentClusterKeyListener;
    @Mock Supplier<String> mLastCallSupplier;
    @Mock IntSupplier mLongPressDelaySupplier;
    @Mock BooleanSupplier mShouldCallButtonEndOngoingCallSupplier;
    @Mock InputCaptureClientController mCaptureController;
    @Mock CarOccupantZoneService mCarOccupantZoneService;
    @Mock CarBluetoothService mCarBluetoothService;
    @Mock CarPowerManagementService mCarPowerManagementService;
    @Mock SystemInterface mSystemInterface;
    @Mock CarAudioService mCarAudioService;
    @Mock CarMediaService mCarMediaService;

    @Spy Context mContext = ApplicationProvider.getApplicationContext();
    @Mock private Resources mMockResources;
    @Spy Handler mHandler = new Handler(Looper.getMainLooper());

    @Mock CarUserService mCarUserService;
    private CarInputService mCarInputService;

    private static final int DRIVER_USER_ID = 111;
    private static final int PASSENGER_USER_ID = 112;

    private static final int DRIVER_DISPLAY_ID = 121;
    private static final int PASSENGER_DISPLAY_ID = 122;

    private static final int DRIVER_ZONE_ID = 131;
    private static final int PASSENGER_ZONE_ID = 132;

    private static final int UNKNOWN_SEAT = VehicleAreaSeat.SEAT_UNKNOWN;
    private static final int DRIVER_SEAT = VehicleAreaSeat.SEAT_ROW_1_LEFT;
    private static final int PASSENGER_SEAT = VehicleAreaSeat.SEAT_ROW_1_RIGHT;

    // CarInputService#sDefaultShowCallback() prints out some Slog message which can be conflicted
    // with AbstractExtendedMockitoTestCase#interceptWtfCalls (b/294138315).
    private static final VoiceInteractionSessionShowCallbackHelper sShowCallback =
            new VoiceInteractionSessionShowCallbackHelper() {
                @Override public void onFailed() {
                    // No op
                }
                @Override public void onShown() {
                    // No op
                }
            };

    public CarInputServiceTest() {
        super(CarInputService.TAG);
    }

    @Before
    public void setUp() {
        mCarInputService = new CarInputService(mContext, mInputHalService, mCarUserService,
                mCarOccupantZoneService, mCarBluetoothService, mCarPowerManagementService,
                mSystemInterface, mHandler, mTelecomManager, mDefaultKeyEventMainListener,
                mDefaultMotionEventMainListener, mLastCallSupplier, mLongPressDelaySupplier,
                mShouldCallButtonEndOngoingCallSupplier, mCaptureController,
                sShowCallback);

        mCarInputService.setInstrumentClusterKeyListener(mInstrumentClusterKeyListener);

        when(mInputHalService.isKeyInputSupported()).thenReturn(true);

        // Delay Handler callbacks until flushHandler() is called.
        doReturn(true).when(mHandler).sendMessageAtTime(any(), anyLong());

        when(mShouldCallButtonEndOngoingCallSupplier.getAsBoolean()).thenReturn(false);

        when(mContext.getResources()).thenReturn(mMockResources);

        setUpCarOccupantZoneService();
        setUpService();
        mCarInputService.init();
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AssistUtilsHelper.class);
        session.spyStatic(CarServiceUtils.class);
    }

    private void setUpCarOccupantZoneService() {
        when(mCarOccupantZoneService.getDriverSeat()).thenReturn(DRIVER_SEAT);

        when(mCarOccupantZoneService.getOccupantZoneIdForSeat(eq(DRIVER_SEAT)))
                .thenReturn(DRIVER_ZONE_ID);
        when(mCarOccupantZoneService.getOccupantZoneIdForSeat(eq(PASSENGER_SEAT)))
                .thenReturn(PASSENGER_ZONE_ID);

        when(mCarOccupantZoneService.getUserForOccupant(eq(DRIVER_ZONE_ID)))
                .thenReturn(DRIVER_USER_ID);
        when(mCarOccupantZoneService.getUserForOccupant(eq(PASSENGER_ZONE_ID)))
                .thenReturn(PASSENGER_USER_ID);

        when(mCarOccupantZoneService.getDisplayForOccupant(eq(DRIVER_ZONE_ID),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN))).thenReturn(DRIVER_DISPLAY_ID);
        when(mCarOccupantZoneService.getDisplayForOccupant(eq(PASSENGER_ZONE_ID),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN))).thenReturn(PASSENGER_DISPLAY_ID);
    }

    private void setUpService() {
        CarLocalServices.removeServiceForTest(CarAudioService.class);
        CarLocalServices.addService(CarAudioService.class, mCarAudioService);
        CarLocalServices.removeServiceForTest(CarMediaService.class);
        CarLocalServices.addService(CarMediaService.class, mCarMediaService);
    }

    @After
    public void tearDown() {
        if (mCarInputService != null) {
            mCarInputService.release();
        }

        CarLocalServices.removeServiceForTest(CarAudioService.class);
        CarLocalServices.removeServiceForTest(CarMediaService.class);
    }

    @Test
    public void testConstantValueMatching() {
        assertWithMessage(
                "CarInputService.LONG_PRESS_TIMEOUT ('%s') must match the string defined in "
                        + "Settings.Secure.LONG_PRESS_TIMEOUT",
                CarInputService.LONG_PRESS_TIMEOUT).that(
                CarInputService.LONG_PRESS_TIMEOUT).isEqualTo(
                Settings.Secure.LONG_PRESS_TIMEOUT);
    }

    @Test
    public void testOnRotaryEvent_injectingRotaryNavigationEvent() {
        RotaryEvent event = new RotaryEvent(
                /* inputType= */ CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION,
                /* clockwise= */ true,
                /* uptimeMillisForClicks= */ new long[]{1, 1});
        when(mCaptureController.onRotaryEvent(
                same(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), same(event))).thenReturn(true);
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(true);

        mCarInputService.onRotaryEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        // Since mCaptureController processed RotaryEvent, then no KeyEvent was generated or
        // processed
        verify(mCarOccupantZoneService, never()).getDisplayIdForDriver(anyInt());
        verify(mCaptureController, never()).onKeyEvent(anyInt(), any(KeyEvent.class));
        verify(mDefaultKeyEventMainListener, never())
                .onKeyEvent(any(KeyEvent.class), anyInt(), anyInt());
    }

    @Test
    public void testOnRotaryEvent_injectingRotaryVolumeEvent() {
        RotaryEvent event = new RotaryEvent(
                /* inputType= */ CarInputManager.INPUT_TYPE_ROTARY_VOLUME,
                /* clockwise= */ true,
                /* uptimeMillisForClicks= */ new long[]{1, 1});
        when(mCaptureController.onRotaryEvent(
                same(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), same(event))).thenReturn(false);
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(true);

        mCarInputService.onRotaryEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        // Since mCaptureController processed RotaryEvent, then KeyEvent was generated or
        // processed
        int numberOfGeneratedKeyEvents = 4;
        verify(mCarOccupantZoneService, times(numberOfGeneratedKeyEvents)).getDisplayIdForDriver(
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN));
        verify(mCaptureController, times(numberOfGeneratedKeyEvents)).onKeyEvent(anyInt(),
                any(KeyEvent.class));
        verify(mDefaultKeyEventMainListener, never())
                .onKeyEvent(any(KeyEvent.class), anyInt(), anyInt());
    }

    @Test
    public void testOnRotaryEvent_injectingRotaryNavigation_notConsumedByCaptureController() {
        RotaryEvent event = new RotaryEvent(
                /* inputType= */ CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION,
                /* clockwise= */ true,
                /* uptimeMillisForClicks= */ new long[]{1, 1});
        when(mCaptureController.onRotaryEvent(
                same(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), same(event))).thenReturn(false);

        mCarInputService.onRotaryEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        // Since mCaptureController processed RotaryEvent, then KeyEvent was generated or
        // processed
        int numberOfGeneratedKeyEvents = 4;
        verify(mCarOccupantZoneService, times(numberOfGeneratedKeyEvents)).getDisplayIdForDriver(
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN));
        verify(mCaptureController, times(numberOfGeneratedKeyEvents)).onKeyEvent(anyInt(),
                any(KeyEvent.class));
        verify(mDefaultKeyEventMainListener, times(numberOfGeneratedKeyEvents)).onKeyEvent(
                any(KeyEvent.class), eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), anyInt());
    }

    @Test
    public void testRequestInputEventCapture_delegatesToCaptureController() {
        ICarInputCallback callback = mock(ICarInputCallback.class);
        int[] inputTypes = new int[]{CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT};
        int requestFlags = CarInputManager.CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT;
        mCarInputService.requestInputEventCapture(callback,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, inputTypes, requestFlags);

        verify(mCaptureController).requestInputEventCapture(same(callback),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), same(inputTypes), eq(requestFlags));
    }

    @Test
    public void testOnCustomInputEvent_delegatesToCaptureController() {
        CustomInputEvent event = new CustomInputEvent(INPUT_CODE_F1,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, /* repeatCounter= */ 1);

        mCarInputService.onCustomInputEvent(event);

        verify(mCaptureController).onCustomInputEvent(same(event));
    }

    @Test
    public void testReleaseInputEventCapture_delegatesToCaptureController() {
        ICarInputCallback callback = mock(ICarInputCallback.class);
        mCarInputService.releaseInputEventCapture(callback,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        verify(mCaptureController).releaseInputEventCapture(same(callback),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN));
    }

    @Test
    public void ordinaryEvents_onMainDisplay_routedToInputManager() {
        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.MAIN);

        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), anyInt());
    }

    @Test
    public void ordinaryEvents_onInstrumentClusterDisplay_notRoutedToInputManager() {
        send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.INSTRUMENT_CLUSTER);

        verify(mDefaultKeyEventMainListener, never()).onKeyEvent(any(), anyInt(), anyInt());
    }

    @Test
    public void ordinaryEvents_onInstrumentClusterDisplay_routedToListener() {
        CarInputService.KeyEventListener listener = mock(CarInputService.KeyEventListener.class);
        mCarInputService.setInstrumentClusterKeyListener(listener);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.INSTRUMENT_CLUSTER);
        verify(listener).onKeyEvent(event);
    }

    @Test
    public void registerKeyEventListener_separateListenersWithSameEventsOfInterest_fails() {
        CarInputService.KeyEventListener listener1 = mock(CarInputService.KeyEventListener.class);
        CarInputService.KeyEventListener listener2 = mock(CarInputService.KeyEventListener.class);
        List<Integer> interestedEvents = List.of(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_0);
        mCarInputService.registerKeyEventListener(listener1, interestedEvents);

        IllegalArgumentException thrown = Assert.assertThrows(IllegalArgumentException.class,
                () -> mCarInputService.registerKeyEventListener(listener2, interestedEvents));

        assertWithMessage("Register key event listener exception")
                .that(thrown).hasMessageThat()
                .contains("Event " + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_HOME)
                        + " already registered to another listener");
    }

    @Test
    public void registerKeyEventListener_separateListenersWithOverlappingEventsOfInterest_fails() {
        CarInputService.KeyEventListener listener1 = mock(CarInputService.KeyEventListener.class);
        CarInputService.KeyEventListener listener2 = mock(CarInputService.KeyEventListener.class);
        List<Integer> interestedEvents1 = List.of(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_0);
        List<Integer> interestedEvents2 = List.of(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_HOME);
        mCarInputService.registerKeyEventListener(listener1, interestedEvents1);

        IllegalArgumentException thrown = Assert.assertThrows(IllegalArgumentException.class,
                () -> mCarInputService.registerKeyEventListener(listener2, interestedEvents2));

        assertWithMessage("Register key event listener")
                .that(thrown).hasMessageThat()
                .contains("Event " + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_HOME)
                        + " already registered to another listener");
    }

    @Test
    public void registerKeyEventListener_withNullListener_fails() {
        var interestedEvents = List.of(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_0);

        NullPointerException thrown = Assert.assertThrows(NullPointerException.class,
                () -> mCarInputService.registerKeyEventListener(/* listener= */ null,
                        interestedEvents));

        assertWithMessage("Register null key event listener exception")
                .that(thrown).hasMessageThat().contains("Key event listener");
    }

    @Test
    public void registerKeyEventListener_withNullKeyEventList_fails() {
        CarInputService.KeyEventListener listener = mock(CarInputService.KeyEventListener.class);

        NullPointerException thrown = Assert.assertThrows(NullPointerException.class,
                () -> mCarInputService.registerKeyEventListener(listener,
                        /* keyCodesOfInterest= */ null));

        assertWithMessage("Register null key events of interest exception")
                .that(thrown).hasMessageThat().contains("Key events of interest");
    }

    @Test
    public void registerKeyEventListener_withEmptyKeyEventList_fails() {
        CarInputService.KeyEventListener listener = mock(CarInputService.KeyEventListener.class);

        IllegalArgumentException thrown = Assert.assertThrows(IllegalArgumentException.class,
                () -> mCarInputService.registerKeyEventListener(listener,
                        /* keyCodesOfInterest= */ Collections.emptyList()));

        assertWithMessage("Register empty key events of interest exception")
                .that(thrown).hasMessageThat().contains("Key events of interest");
    }

    @Test
    public void unregisterKeyEventListener_withNullListener_fails() {
        NullPointerException thrown = Assert.assertThrows(NullPointerException.class,
                () -> mCarInputService.unregisterKeyEventListener(/* listener= */ null));

        assertWithMessage("Unregister null key event listener exception")
                .that(thrown).hasMessageThat().contains("Key event listener");
    }

    @Test
    public void onKeyEvent_afterUnregistering_doesNotCallsListener() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME);
        CarInputService.KeyEventListener listener = mock(CarInputService.KeyEventListener.class);
        var interestedEvents = List.of(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_0);
        mCarInputService.registerKeyEventListener(listener, interestedEvents);
        mCarInputService.unregisterKeyEventListener(listener);

        mCarInputService.onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);

        verify(listener, never()).onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);
    }

    @Test
    public void onKeyEvent_withSingleListener_callsListener() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME);
        CarInputService.KeyEventListener listener = mock(CarInputService.KeyEventListener.class);
        List<Integer> interestedEvents = List.of(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_0);
        mCarInputService.registerKeyEventListener(listener, interestedEvents);

        mCarInputService.onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);

        verify(listener).onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);
    }

    @Test
    public void onKeyEvent_withMultipleListeners_callToListener() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME);
        CarInputService.KeyEventListener listener1 = mock(CarInputService.KeyEventListener.class);
        CarInputService.KeyEventListener listener2 = mock(CarInputService.KeyEventListener.class);
        List<Integer> interestedEvents1 = List.of(KeyEvent.KEYCODE_0);
        List<Integer> interestedEvents2 = List.of(KeyEvent.KEYCODE_HOME);
        mCarInputService.registerKeyEventListener(listener1, interestedEvents1);
        mCarInputService.registerKeyEventListener(listener2, interestedEvents2);

        mCarInputService.onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);

        verify(listener1, never()).onKeyEvent(any(), anyInt(), anyInt());
        verify(listener2).onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);
    }

    @Test
    public void onKeyEvent_withMultipleListeners_noCallToListeners() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);
        CarInputService.KeyEventListener listener1 = mock(CarInputService.KeyEventListener.class);
        CarInputService.KeyEventListener listener2 = mock(CarInputService.KeyEventListener.class);
        List<Integer> interestedEvents1 = List.of(KeyEvent.KEYCODE_0);
        List<Integer> interestedEvents2 = List.of(KeyEvent.KEYCODE_HOME);
        mCarInputService.registerKeyEventListener(listener1, interestedEvents1);
        mCarInputService.registerKeyEventListener(listener2, interestedEvents2);

        mCarInputService.onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);

        verify(listener1, never()).onKeyEvent(any(), anyInt(), anyInt());
        verify(listener2, never()).onKeyEvent(any(), anyInt(), anyInt());
    }

    @Test
    public void customEventHandler_capturesDisplayMainEvent_capturedByInputController() {
        CarInputService.KeyEventListener instrumentClusterListener =
                setupInstrumentClusterListener();

        // Assume mCaptureController will consume every event.
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(true);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.MAIN);
        verify(instrumentClusterListener, never()).onKeyEvent(any(KeyEvent.class));
        verify(mCaptureController).onKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, event);
        verify(mDefaultKeyEventMainListener, never())
                .onKeyEvent(any(KeyEvent.class), anyInt(), anyInt());
    }

    @Test
    public void customEventHandler_capturesDisplayMainEvent_missedByInputController() {
        CarInputService.KeyEventListener instrumentClusterListener =
                setupInstrumentClusterListener();

        // Assume mCaptureController will consume every event.
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.MAIN);
        verify(instrumentClusterListener, never()).onKeyEvent(any(KeyEvent.class));
        verify(mCaptureController).onKeyEvent(anyInt(), any(KeyEvent.class));
        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), anyInt());
    }

    @Test
    public void customEventHandler_capturesClusterEvents_capturedByInstrumentCluster() {
        CarInputService.KeyEventListener instrumentClusterListener =
                setupInstrumentClusterListener();

        // Assume mCaptureController will consume every event.
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(true);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.INSTRUMENT_CLUSTER);
        verify(instrumentClusterListener).onKeyEvent(event);
        verify(mCaptureController, never()).onKeyEvent(anyInt(), any(KeyEvent.class));
        verify(mDefaultKeyEventMainListener, never())
                .onKeyEvent(any(KeyEvent.class), anyInt(), anyInt());
    }

    private CarInputService.KeyEventListener setupInstrumentClusterListener() {
        CarInputService.KeyEventListener instrumentClusterListener =
                mock(CarInputService.KeyEventListener.class);
        mCarInputService.setInstrumentClusterKeyListener(instrumentClusterListener);
        return instrumentClusterListener;
    }

    @Test
    public void voiceKey_shortPress_withRegisteredEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
    }

    @Test
    public void voiceKey_longPress_withRegisteredEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP,
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        verify(eventHandler, never()).onKeyEvent(anyInt());

        // Simulate the long-press timer expiring.
        flushHandler();
        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);

        // Ensure that the short-press handler is *not* called.
        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        flushHandler();
        verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
    }

    @Test
    public void voiceKey_shortPress_withoutRegisteredEventHandler_triggersAssistUtils() {
        doReturn(true).when(
                () -> AssistUtilsHelper.showPushToTalkSessionForActiveService(eq(mContext), any()));

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
    }

    @Test
    public void voiceKey_longPress_withoutRegisteredEventHandler_triggersAssistUtils() {
        doReturn(true).when(
                () -> AssistUtilsHelper.showPushToTalkSessionForActiveService(eq(mContext), any()));

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        flushHandler();

        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        // NOTE: extend mockito doesn't provide verifyNoMoreInteractions(), so we'd need to
        // explicitly call verify() and verify(never()). But since AssistUtilHelper only has one
        // static method, we don't need the latter.
        verify(() -> AssistUtilsHelper.showPushToTalkSessionForActiveService(eq(mContext), any()));
    }

    /**
     * Testing long press triggers Bluetooth voice recognition.
     *
     * Based on current implementation of {@link CarInputService#handleVoiceAssistLongPress},
     * long press of the button should trigger Bluetooth voice recognition if:
     *   (a) {@link CarProjectionManager.ProjectionKeyEventHandler} did not subscribe for the
     *       event, or if the key event handler does not exit; and
     *   (b) Bluetooth voice recognition is enabled.
     *
     * Preconditions:
     *     - Bluetooth voice recognition is enabled.
     *     - No {@link CarProjectionManager.ProjectionKeyEventHandler} registered for the key event.
     * Action:
     *     - Long press the voice assistant key.
     * Results:
     *     - Bluetooth voice recognition is invoked.
     */
    @Test
    public void voiceKey_longPress_bluetoothVoiceRecognitionIsEnabled_triggersBluetoothAssist() {
        mockEnableLongPressBluetoothVoiceRecognitionProperty(true);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        flushHandler();

        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        verify(mCarBluetoothService, times(1)).startBluetoothVoiceRecognition();
    }

    /**
     * Testing short press does not trigger Bluetooth voice recognition.
     *
     * Based on current implementation of {@link CarInputService#handleVoiceAssistKey},
     * short press of the button should not trigger Bluetooth, and instead launch the default
     * voice assistant handler.
     *
     * Preconditions:
     *     - Bluetooth voice recognition is enabled.
     * Action:
     *     - Short press the voice assistant key.
     * Results:
     *     - Bluetooth voice recognition is not invoked.
     *     - Default assistant handler is invoked instead.
     */
    @Test
    public void voiceKey_shortPress_bluetoothVoiceRecognitionIsEnabled_triggersAssistUtils() {
        mockEnableLongPressBluetoothVoiceRecognitionProperty(true);

        doReturn(true).when(
                () -> AssistUtilsHelper.showPushToTalkSessionForActiveService(eq(mContext), any()));

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        verify(mCarBluetoothService, never()).startBluetoothVoiceRecognition();
        verify(() -> AssistUtilsHelper.showPushToTalkSessionForActiveService(eq(mContext), any()));
    }

    @Test
    public void voiceKey_keyDown_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_KEY_DOWN);
    }

    @Test
    public void voiceKey_keyUp_afterLongPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        flushHandler();
        verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);

        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);
    }

    @Test
    public void voiceKey_repeatedEvents_ignored() {
        // Pressing a key starts the long-press timer.
        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        verify(mHandler).sendMessageAtTime(any(), anyLong());
        clearInvocations(mHandler);

        // Repeated KEY_DOWN events don't reset the timer.
        sendWithRepeat(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN, 1);
        verify(mHandler, never()).sendMessageAtTime(any(), anyLong());
    }

    @Test
    public void callKey_shortPress_withoutEventHandler_launchesDialer() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        doNothing().when(mContext).startActivityAsUser(any(), any());

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mContext).startActivityAsUser(
                intentCaptor.capture(), eq(UserHandle.CURRENT));
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(Intent.ACTION_DIAL);
    }

    @Test
    public void callKey_shortPress_withoutEventHandler_whenCallRinging_answersCall() {
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mTelecomManager).acceptRingingCall();
        // Ensure default handler does not run.
        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void callKey_shortPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        // Ensure default handlers do not run.
        verify(mTelecomManager, never()).acceptRingingCall();
        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void callKey_shortPress_withEventHandler_whenCallRinging_answersCall() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mTelecomManager).acceptRingingCall();
        verify(eventHandler, never()).onKeyEvent(anyInt());
    }

    @Test
    public void callKey_shortPress_duringCall_endCallViaCallButtonOn_endsCall() {
        when(mShouldCallButtonEndOngoingCallSupplier.getAsBoolean()).thenReturn(true);
        when(mTelecomManager.isInCall()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mTelecomManager).endCall();
    }

    @Test
    public void callKey_shortPress_duringCall_endCallViaCallButtonOff_doesNotEndCall() {
        when(mTelecomManager.isInCall()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mTelecomManager, never()).endCall();
    }

    @Test
    public void callKey_longPress_withoutEventHandler_redialsLastCall() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        when(mLastCallSupplier.get()).thenReturn("1234567890");
        doNothing().when(mContext).startActivityAsUser(any(), any());

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mContext).startActivityAsUser(
                intentCaptor.capture(), eq(UserHandle.CURRENT));

        Intent intent = intentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_CALL);
        assertThat(intent.getData()).isEqualTo(Uri.parse("tel:1234567890"));

        clearInvocations(mContext);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void callKey_longPress_withoutEventHandler_withNoLastCall_doesNothing() {
        when(mLastCallSupplier.get()).thenReturn("");

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void callKey_longPress_withoutEventHandler_whenCallRinging_answersCall() {
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mTelecomManager).acceptRingingCall();

        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        // Ensure that default handler does not run, either after accepting ringing call,
        // or as a result of key-up.
        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void callKey_longPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);
        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void callKey_longPress_withEventHandler_whenCallRinging_answersCall() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mTelecomManager).acceptRingingCall();

        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        // Ensure that event handler does not run, either after accepting ringing call,
        // or as a result of key-up.
        verify(eventHandler, never()).onKeyEvent(anyInt());
    }

    @Test
    public void callKey_longPress_duringCall_endCallViaCallButtonOn_endsCall() {
        when(mShouldCallButtonEndOngoingCallSupplier.getAsBoolean()).thenReturn(true);
        when(mTelecomManager.isInCall()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mTelecomManager).endCall();
    }

    @Test
    public void callKey_longPress_duringCall_endCallViaCallButtonOff_doesNotEndCall() {
        when(mTelecomManager.isInCall()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mTelecomManager, never()).endCall();
    }

    @Test
    public void callKey_keyDown_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
    }

    @Test
    public void callKey_keyUp_afterLongPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();
        verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);

        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);
    }

    @Test
    public void callKey_repeatedEvents_ignored() {
        // Pressing a key starts the long-press timer.
        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        verify(mHandler).sendMessageAtTime(any(), anyLong());
        clearInvocations(mHandler);

        // Repeated KEY_DOWN events don't reset the timer.
        sendWithRepeat(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN, 1);
        verify(mHandler, never()).sendMessageAtTime(any(), anyLong());
    }

    @Test
    public void longPressDelay_obeysValueFromSystem() {
        final int systemDelay = 4242;

        when(mLongPressDelaySupplier.getAsInt()).thenReturn(systemDelay);
        ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(long.class);

        long then = SystemClock.uptimeMillis();
        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        long now = SystemClock.uptimeMillis();

        verify(mHandler).sendMessageAtTime(any(), timeCaptor.capture());

        // The message time must be the expected delay time (as provided by the supplier) after
        // the time the message was sent to the handler. We don't know that exact time, but we
        // can put a bound on it - it's no sooner than immediately before the call to send(), and no
        // later than immediately afterwards. Check to make sure the actual observed message time is
        // somewhere in the valid range.

        assertThat(timeCaptor.getValue()).isIn(Range.closed(then + systemDelay, now + systemDelay));
    }

    @Test
    public void injectKeyEvent_throwsSecurityExceptionWithoutInjectEventsPermission() {
        // Arrange
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);

        long currentTime = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(/* downTime= */ currentTime,
                /* eventTime= */ currentTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER,
                /* repeat= */ 0);

        // Act and assert
        Assert.assertThrows(SecurityException.class,
                () -> mCarInputService.injectKeyEvent(event,
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN));
    }

    @Test
    public void injectKeyEvent_delegatesToOnKeyEvent() {
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(/* downTime= */ currentTime,
                /* eventTime= */ currentTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER,
                /* repeat= */ 0);
        event.setDisplayId(INVALID_DISPLAY);

        injectKeyEventAndVerify(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), anyInt());
        verify(mInstrumentClusterKeyListener, never()).onKeyEvent(same(event));
    }

    @Test
    public void injectKeyEvent_sendingKeyEventWithDefaultDisplayAgainstClusterDisplayType() {
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(/* downTime= */ currentTime,
                /* eventTime= */ currentTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER,
                /* repeat= */ 0);
        event.setDisplayId(INVALID_DISPLAY);

        injectKeyEventAndVerify(event, CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);

        verify(mDefaultKeyEventMainListener, never()).onKeyEvent(same(event), anyInt(), anyInt());
        verify(mInstrumentClusterKeyListener).onKeyEvent(same(event));
    }

    private void injectKeyEventAndVerify(KeyEvent event, @DisplayTypeEnum int displayType) {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);
        int someDisplayId = Integer.MAX_VALUE;
        when(mCarOccupantZoneService.getDisplayIdForDriver(anyInt())).thenReturn(someDisplayId);
        assertThat(event.getDisplayId()).isNotEqualTo(someDisplayId);

        mCarInputService.injectKeyEvent(event, displayType);

        verify(mCarOccupantZoneService).getDisplayIdForDriver(displayType);
        assertWithMessage("Event's display id not updated as expected").that(
                event.getDisplayId()).isEqualTo(someDisplayId);
    }

    @Test
    public void onKey_assignDisplayId_mainDisplay() {
        // Act
        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_HOME, Display.MAIN);

        // Arrange
        assertWithMessage("display id expected to be assigned with Display.DEFAULT_DISPLAY").that(
                event.getDisplayId()).isEqualTo(DEFAULT_DISPLAY);
    }

    @Test
    public void onKey_assignDisplayId_clusterDisplay() {
        // Act
        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_HOME, Display.INSTRUMENT_CLUSTER);

        // Arrange
        assertWithMessage("display id expected to be assigned with Display.DEFAULT_DISPLAY").that(
                event.getDisplayId()).isEqualTo(DEFAULT_DISPLAY);
    }

    @Test
    public void onKeyEvent_unknownSeat_throwsException() {
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(/* downTime= */ currentTime,
                /* eventTime= */ currentTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A,
                /* repeat= */ 0);

        IllegalArgumentException thrown = Assert.assertThrows(IllegalArgumentException.class,
                () -> mCarInputService.onKeyEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                UNKNOWN_SEAT));

        assertWithMessage("Sent key event with unknown seat exception")
                .that(thrown).hasMessageThat()
                .contains("Unknown seat");
    }

    @Test
    public void onKeyEvent_keyCodeAtoDriverSeat_triggersDefaultKeyEventMainListener() {
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_A, Display.MAIN, DRIVER_SEAT);

        verify(mCarPowerManagementService).notifyUserActivity(eq(DRIVER_DISPLAY_ID), anyLong());
        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), eq(DRIVER_SEAT));
    }

    @Test
    public void onKeyEvent_keyCodeAtoPassengerSeat_triggersDefaultKeyEventMainListener() {
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_A, Display.MAIN, PASSENGER_SEAT);

        verify(mCarPowerManagementService).notifyUserActivity(eq(PASSENGER_DISPLAY_ID), anyLong());
        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), eq(PASSENGER_SEAT));
    }

    @Test
    public void onKeyEvent_homeKeyUpToDriverSeat_triggersDefaultKeyEventMainListener() {
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);

        KeyEvent event = send(Key.UP, KeyEvent.KEYCODE_HOME, Display.MAIN, DRIVER_SEAT);

        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), eq(DRIVER_SEAT));
        verify(() -> CarServiceUtils.startHomeForUserAndDisplay(any(), anyInt(), anyInt()),
                never());
    }

    @Test
    public void onKeyEvent_homeKeyUpToInvalidSeat_doesNothing() {
        int invalidSeat = -1;
        when(mCarOccupantZoneService.getOccupantZoneIdForSeat(eq(invalidSeat)))
                .thenReturn(OccupantZoneInfo.INVALID_ZONE_ID);

        send(Key.UP, KeyEvent.KEYCODE_HOME, Display.MAIN, invalidSeat);

        verify(() -> CarServiceUtils.startHomeForUserAndDisplay(any(), anyInt(), anyInt()),
                never());
    }

    @Test
    public void onKeyEvent_homeKeyDownToPassengerSeat_doesNothing() {
        send(Key.DOWN, KeyEvent.KEYCODE_HOME, Display.MAIN, PASSENGER_SEAT);

        verify(() -> CarServiceUtils.startHomeForUserAndDisplay(any(), anyInt(), anyInt()),
                never());
    }

    @Test
    public void onKeyEvent_homeKeyUpToPassengerSeat_triggersStartyHome() {
        doAnswer((invocation) -> null).when(() -> CarServiceUtils.startHomeForUserAndDisplay(
                any(Context.class), anyInt(), anyInt()));

        send(Key.UP, KeyEvent.KEYCODE_HOME, Display.MAIN, PASSENGER_SEAT);

        verify(() -> CarServiceUtils.startHomeForUserAndDisplay(mContext, PASSENGER_USER_ID,
                PASSENGER_DISPLAY_ID));
    }

    @Test
    public void onKeyEvent_powerKeyUpToDriverSeat_triggersDefaultKeyEventMainListener() {
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);

        KeyEvent event = send(Key.UP, KeyEvent.KEYCODE_POWER, Display.MAIN, DRIVER_SEAT);

        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), eq(DRIVER_SEAT));
        verify(mCarPowerManagementService, never()).setDisplayPowerState(anyInt(), anyBoolean());
    }

    @Test
    public void onKeyEvent_powerKeyDownToPassengerSeatWhenDisplayOn_doesNothing() {
        injectPowerKeyEventToSeat(Key.DOWN, /* isOn= */ true, PASSENGER_DISPLAY_ID, PASSENGER_SEAT);

        verify(mCarPowerManagementService, never()).setDisplayPowerState(anyInt(), anyBoolean());
    }

    @Test
    public void onKeyEvent_powerKeyDownToPassengerSeatWhenDisplayOff_setsDisplayPowerOn() {
        injectPowerKeyEventToSeat(Key.DOWN, /* isOn= */ false, PASSENGER_DISPLAY_ID,
                PASSENGER_SEAT);

        boolean expectedDisplaySetStatus = true;
        verify(mCarPowerManagementService).setDisplayPowerState(eq(PASSENGER_DISPLAY_ID),
                eq(expectedDisplaySetStatus));
    }

    @Test
    public void onKeyEvent_powerKeyUpToPassengerSeatWhenDisplayOn_setsDisplayPowerOff() {
        injectPowerKeyEventToSeat(Key.UP, /* isOn= */ true, PASSENGER_DISPLAY_ID, PASSENGER_SEAT);

        boolean expectedDisplaySetStatus = false;
        verify(mCarPowerManagementService).setDisplayPowerState(eq(PASSENGER_DISPLAY_ID),
                eq(expectedDisplaySetStatus));
    }

    @Test
    public void onKeyEvent_powerKeyUpToPassengerSeatWhenDisplayOff_doesNothing() {
        injectPowerKeyEventToSeat(Key.UP, /* isOn= */ false, PASSENGER_DISPLAY_ID, PASSENGER_SEAT);

        verify(mCarPowerManagementService, never()).setDisplayPowerState(anyInt(), anyBoolean());
    }

    @Test
    public void onKeyEvent_powerKeyDownToPassengerSeatWhenInvalidDisplayId_doesNothing() {
        when(mCarOccupantZoneService.getDisplayForOccupant(eq(PASSENGER_ZONE_ID),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN)))
                .thenReturn(INVALID_DISPLAY);

        send(Key.DOWN, KeyEvent.KEYCODE_POWER, Display.MAIN, PASSENGER_SEAT);

        verify(mCarPowerManagementService, never()).setDisplayPowerState(anyInt(), anyBoolean());
    }

    private KeyEvent injectPowerKeyEventToSeat(Key action, boolean isOn, int displayId, int seat) {
        when(mSystemInterface.isDisplayEnabled(eq(displayId))).thenReturn(isOn);

        return send(action, KeyEvent.KEYCODE_POWER, Display.MAIN, seat);
    }

    @Test
    public void onMotionEvent_injectsMotionEventToDriverSeat() {
        long currentTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ currentTime,
                /* eventTime= */ currentTime,
                MotionEvent.ACTION_DOWN,
                /* X coordinate= */ 0.0f,
                /* Y coordinate= */ 0.0f,
                /* metaState= */ 0);

        mCarInputService.onMotionEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                DRIVER_SEAT);

        verify(mDefaultMotionEventMainListener).onMotionEvent(same(event));
        verify(mCarPowerManagementService).notifyUserActivity(eq(DRIVER_DISPLAY_ID),
                eq(currentTime));
    }

    @Test
    public void onMotionEvent_injectsMotionEventToPassengerSeat() {
        long currentTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ currentTime,
                /* eventTime= */ currentTime,
                MotionEvent.ACTION_DOWN,
                /* X coordinate= */ 0.0f,
                /* Y coordinate= */ 0.0f,
                /* metaState= */ 0);

        mCarInputService.onMotionEvent(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                PASSENGER_SEAT);

        verify(mDefaultMotionEventMainListener).onMotionEvent(same(event));
        verify(mCarPowerManagementService).notifyUserActivity(eq(PASSENGER_DISPLAY_ID),
                eq(currentTime));
    }

    @Test
    public void onMotionEvent_unknownSeat_throwsException() {
        long currentTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ currentTime,
                /* eventTime= */ currentTime,
                MotionEvent.ACTION_DOWN,
                /* X coordinate= */ 0.0f,
                /* Y coordinate= */ 0.0f,
                /* metaState= */ 0);

        IllegalArgumentException thrown = Assert.assertThrows(IllegalArgumentException.class,
                () -> mCarInputService.onMotionEvent(event,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN, UNKNOWN_SEAT));

        assertWithMessage("Sent motion event with unknown seat exception")
                .that(thrown).hasMessageThat()
                .contains("Unknown seat");
    }

    @Test
    public void injectKeyEventForDriver_delegatesToOnKeyEvent() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(
                /* downTime= */ currentTime,
                /* eventTime= */ currentTime,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER,
                /* repeat= */ 0);

        mCarInputService.injectKeyEventForSeat(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                DRIVER_SEAT);

        verify(mDefaultKeyEventMainListener).onKeyEvent(same(event),
                eq(CarOccupantZoneManager.DISPLAY_TYPE_MAIN), eq(DRIVER_SEAT));
    }

    @Test
    public void injectKeyEventForDriver_throwsSecurityExceptionWithoutInjectEventsPermission() {
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(
                /* downTime= */ currentTime,
                /* eventTime= */ currentTime,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER,
                /* repeat= */ 0);

        // Act and assert
        Assert.assertThrows(SecurityException.class,
                () -> mCarInputService.injectKeyEventForSeat(event,
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                        DRIVER_SEAT));
    }

    @Test
    public void injectMotionEventForDriver_delegatesToOnKeyEvent() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);
        long currentTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ currentTime,
                /* eventTime= */ currentTime,
                MotionEvent.ACTION_DOWN,
                /* X coordinate= */ 0.0f,
                /* Y coordinate= */ 0.0f,
                /* metaState= */ 0);

        mCarInputService.injectMotionEventForSeat(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                DRIVER_SEAT);

        verify(mDefaultMotionEventMainListener).onMotionEvent(event);
    }

    @Test
    public void injectMotionEventForDriver_throwsSecurityExceptionWithoutInjectEventsPermission() {
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);

        long currentTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ currentTime,
                /* eventTime= */ currentTime,
                MotionEvent.ACTION_DOWN,
                /* X coordinate= */ 0.0f,
                /* Y coordinate= */ 0.0f,
                /* metaState= */ 0);

        Assert.assertThrows(SecurityException.class,
                () -> mCarInputService.injectMotionEventForSeat(event,
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                        DRIVER_SEAT));
    }

    private enum Key {DOWN, UP}

    private enum Display {MAIN, INSTRUMENT_CLUSTER}

    private KeyEvent send(Key action, int keyCode, Display display) {
        return sendWithRepeat(action, keyCode, display, 0);
    }

    private KeyEvent sendWithRepeat(Key action, int keyCode, Display display, int repeatCount) {
        KeyEvent event = new KeyEvent(
                /* downTime= */ 0L,
                /* eventTime= */ 0L,
                action == Key.DOWN ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                keyCode,
                repeatCount);
        event.setDisplayId(INVALID_DISPLAY);
        mCarInputService.onKeyEvent(
                event,
                display == Display.MAIN
                        ? CarOccupantZoneManager.DISPLAY_TYPE_MAIN
                        : CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
        return event;
    }

    private KeyEvent send(Key action, int keyCode, Display display, int seat) {
        return sendWithRepeatAndSeat(action, keyCode, display, 0, seat);
    }

    private KeyEvent sendWithRepeatAndSeat(Key action, int keyCode, Display display,
            int repeatCount, int seat) {
        KeyEvent event = new KeyEvent(
                /* downTime= */ 0L,
                /* eventTime= */ 0L,
                action == Key.DOWN ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                keyCode,
                repeatCount);
        event.setDisplayId(INVALID_DISPLAY);
        mCarInputService.onKeyEvent(
                event,
                display == Display.MAIN
                        ? CarOccupantZoneManager.DISPLAY_TYPE_MAIN
                        : CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                seat);
        return event;
    }

    private CarProjectionManager.ProjectionKeyEventHandler registerProjectionKeyEventHandler(
            int... events) {
        BitSet eventSet = new BitSet();
        for (int event : events) {
            eventSet.set(event);
        }

        CarProjectionManager.ProjectionKeyEventHandler projectionKeyEventHandler =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);
        mCarInputService.setProjectionKeyEventHandler(projectionKeyEventHandler, eventSet);
        return projectionKeyEventHandler;
    }

    private void flushHandler() {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        verify(mHandler, atLeast(0)).sendMessageAtTime(messageCaptor.capture(), anyLong());

        for (Message message : messageCaptor.getAllValues()) {
            mHandler.dispatchMessage(message);
        }

        clearInvocations(mHandler);
    }

    private void mockEnableLongPressBluetoothVoiceRecognitionProperty(boolean enabledOrNot) {
        when(mMockResources.getBoolean(R.bool.enableLongPressBluetoothVoiceRecognition))
                .thenReturn(enabledOrNot);
    }
}
