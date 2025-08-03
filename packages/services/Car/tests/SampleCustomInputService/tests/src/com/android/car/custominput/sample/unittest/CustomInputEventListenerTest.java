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
package com.android.car.custominput.sample.unittest;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER;
import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.AttributeUsage;

import static com.android.car.custominput.sample.CustomInputEventListener.EventAction.INJECT_VOICE_ASSIST_ACTION_DOWN;
import static com.android.car.custominput.sample.CustomInputEventListener.EventAction.INJECT_VOICE_ASSIST_ACTION_UP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.CarOccupantZoneManager;
import android.car.input.CustomInputEvent;
import android.car.media.CarAudioManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;

import com.android.car.custominput.sample.CustomInputEventListener;
import com.android.car.custominput.sample.R;
import com.android.car.custominput.sample.SampleCustomInputService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CustomInputEventListenerTest {

    // Some arbitrary display type
    private static final int SOME_DISPLAY_TYPE = CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

    // Some arbitrary display id
    private static final int SOME_DISPLAY_ID = 0;

    // Some arbitrary media group id for testing purposes only
    private static final int SOME_MEDIA_GROUP_ID = 1;

    // Maximum allowed volume for alert and media
    private static final int MAX_VOLUME = 38;

    // Minimum volume for alert and media
    private static final int MIN_VOLUME = 0;

    private CustomInputEventListener mEventHandler;

    @Mock
    private Context mContext;
    @Mock
    private CarAudioManager mCarAudioManager;
    @Mock
    private CarOccupantZoneManager mCarOccupantZoneManager;
    @Mock
    private SampleCustomInputService mService;

    @Before
    public void setUp() {
        when(mContext.getString(R.string.maps_app_package)).thenReturn(
                "com.google.android.apps.maps");
        when(mContext.getString(R.string.maps_activity_class)).thenReturn(
                "com.google.android.maps.MapsActivity");
        when(mCarOccupantZoneManager.getDisplayIdForDriver(eq(DISPLAY_TYPE_MAIN))).thenReturn(0);

        mEventHandler = new CustomInputEventListener(mContext, mCarAudioManager,
                mCarOccupantZoneManager, mService);
    }

    @Test
    public void testHandleEvent_launchingMaps() {
        // Arrange
        CustomInputEvent event = new CustomInputEvent(
                // In this implementation, INPUT_TYPE_CUSTOM_EVENT_F1 represents the action of
                // launching maps.
                /* inputCode= */ CustomInputEvent.INPUT_CODE_F1,
                /* targetDisplayType= */ SOME_DISPLAY_TYPE,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mService).startActivity(intentCaptor.capture(), bundleCaptor.capture());

        // Assert intent parameter
        Intent actualIntent = intentCaptor.getValue();
        assertThat(actualIntent.getAction()).isEqualTo(Intent.ACTION_VIEW);
        assertThat(actualIntent.getFlags()).isEqualTo(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        assertThat(actualIntent.getComponent()).isEqualTo(
                new ComponentName("com.google.android.apps.maps",
                        "com.google.android.maps.MapsActivity"));

        // Assert bundle and user parameters
        assertThat(bundleCaptor.getValue().getInt("android.activity.launchDisplayId")).isEqualTo(
                /* displayId= */
                0);  // TODO(b/159623196): displayId is currently hardcoded to 0, see missing
    }

    @Test
    public void testHandleEvent_acceptIncomingCallAction() {
        // Arrange
        CustomInputEvent event = new CustomInputEvent(
                // In this implementation, INPUT_TYPE_CUSTOM_EVENT_F2 represents the accept CALL
                // action.
                /* inputCode= */ CustomInputEvent.INPUT_CODE_F2,
                /* targetDisplayType= */ SOME_DISPLAY_TYPE,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        assertKeyEvents(/* expectedDisplayType= */ DISPLAY_TYPE_MAIN,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CALL),
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CALL));

    }

    @Test
    public void testHandleEvent_rejectIncomingCallAction() {
        // Arrange
        CustomInputEvent event = new CustomInputEvent(
                // In this implementation, INPUT_TYPE_CUSTOM_EVENT_F3 represents the reject CALL
                // action.
                /* inputCode= */ CustomInputEvent.INPUT_CODE_F3,
                /* targetDisplayType= */ SOME_DISPLAY_TYPE,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        assertKeyEvents(/* expectedDisplayType= */ DISPLAY_TYPE_MAIN,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL),
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL));
    }

    @Test
    public void testHandleEvent_launchHomeAction() {
        // Arrange
        CustomInputEvent event = new CustomInputEvent(
                // In this implementation, INPUT_TYPE_CUSTOM_EVENT_F6 represents the back HOME
                // action.
                /* inputCode= */ CustomInputEvent.INPUT_CODE_F8,
                /* targetDisplayType= */ SOME_DISPLAY_TYPE,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        assertKeyEvents(/* expectedDisplayType= */ DISPLAY_TYPE_MAIN,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME),
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME));
    }

    @Test
    public void testHandleEvent_ignoringEventsForNonMainDisplay() {
        CustomInputEvent event = new CustomInputEvent(CustomInputEvent.INPUT_CODE_F1,
                DISPLAY_TYPE_INSTRUMENT_CLUSTER,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(DISPLAY_TYPE_INSTRUMENT_CLUSTER, event);

        // Assert
        verify(mService, never()).startActivity(any(Intent.class), any(Bundle.class));
    }

    @Test
    public void testHandleEvent_increaseMediaVolume() {
        doTestIncreaseVolumeAndAssert(AudioAttributes.USAGE_MEDIA, CustomInputEvent.INPUT_CODE_F4);
    }

    @Test
    public void testHandleEvent_increaseAlarmVolume() {
        doTestIncreaseVolumeAndAssert(AudioAttributes.USAGE_ALARM, CustomInputEvent.INPUT_CODE_F6);
    }

    private void doTestIncreaseVolumeAndAssert(@AttributeUsage int usage, int inputCode) {
        // Arrange
        when(mCarAudioManager.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, usage)).thenReturn(
                SOME_MEDIA_GROUP_ID);
        when(mCarAudioManager.getGroupMaxVolume(PRIMARY_AUDIO_ZONE,
                SOME_MEDIA_GROUP_ID)).thenReturn(MAX_VOLUME);
        int currentVolume = 5;
        when(mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, SOME_MEDIA_GROUP_ID)).thenReturn(
                currentVolume);

        CustomInputEvent event = new CustomInputEvent(inputCode,
                SOME_DISPLAY_ID,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        verify(mCarAudioManager).setGroupVolume(SOME_MEDIA_GROUP_ID, currentVolume + 1,
                AudioManager.FLAG_SHOW_UI);
    }

    @Test
    public void testHandleEvent_increaseMediaVolumeMax() {
        doTestIncreaseMaxVolumeAndAssert(AudioAttributes.USAGE_MEDIA,
                CustomInputEvent.INPUT_CODE_F4);
    }

    @Test
    public void testHandleEvent_increaseAlarmVolumeMax() {
        doTestIncreaseMaxVolumeAndAssert(AudioAttributes.USAGE_ALARM,
                CustomInputEvent.INPUT_CODE_F6);
    }

    private void doTestIncreaseMaxVolumeAndAssert(@AttributeUsage int usage, int inputCode) {
        // Arrange
        when(mCarAudioManager.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, usage)).thenReturn(
                SOME_MEDIA_GROUP_ID);
        when(mCarAudioManager.getGroupMaxVolume(PRIMARY_AUDIO_ZONE,
                SOME_MEDIA_GROUP_ID)).thenReturn(MAX_VOLUME);
        when(mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, SOME_MEDIA_GROUP_ID)).thenReturn(
                MAX_VOLUME);

        CustomInputEvent event = new CustomInputEvent(inputCode,
                SOME_DISPLAY_ID,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        verify(mCarAudioManager, never()).setGroupVolume(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testHandleEvent_decreaseMediaVolume() {
        doTestDecreaseVolumeAndAssert(AudioAttributes.USAGE_MEDIA, CustomInputEvent.INPUT_CODE_F5);
    }

    @Test
    public void testHandleEvent_decreaseAlarmVolume() {
        doTestDecreaseVolumeAndAssert(AudioAttributes.USAGE_ALARM, CustomInputEvent.INPUT_CODE_F7);
    }

    private void doTestDecreaseVolumeAndAssert(@AttributeUsage int usage, int inputCode) {
        // Arrange
        when(mCarAudioManager.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, usage)).thenReturn(
                SOME_MEDIA_GROUP_ID);
        when(mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE,
                SOME_MEDIA_GROUP_ID)).thenReturn(MIN_VOLUME);
        int currentVolume = 5;
        when(mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, SOME_MEDIA_GROUP_ID)).thenReturn(
                currentVolume);

        CustomInputEvent event = new CustomInputEvent(inputCode,
                SOME_DISPLAY_ID,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        verify(mCarAudioManager).setGroupVolume(SOME_MEDIA_GROUP_ID, currentVolume - 1,
                AudioManager.FLAG_SHOW_UI);
    }

    @Test
    public void testHandleEvent_decreaseMediaMinVolume() {
        doTestDecreaseMinVolumeAndAssert(AudioAttributes.USAGE_MEDIA,
                CustomInputEvent.INPUT_CODE_F5);
    }

    @Test
    public void testHandleEvent_decreaseAlarmMinVolume() {
        doTestDecreaseMinVolumeAndAssert(AudioAttributes.USAGE_ALARM,
                CustomInputEvent.INPUT_CODE_F7);
    }

    private void doTestDecreaseMinVolumeAndAssert(@AttributeUsage int usage, int inputCode) {
        // Arrange
        when(mCarAudioManager.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, usage)).thenReturn(
                SOME_MEDIA_GROUP_ID);
        when(mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE,
                SOME_MEDIA_GROUP_ID)).thenReturn(MIN_VOLUME);
        when(mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, SOME_MEDIA_GROUP_ID)).thenReturn(
                MIN_VOLUME);

        CustomInputEvent event = new CustomInputEvent(inputCode,
                SOME_DISPLAY_ID,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        verify(mCarAudioManager, never()).setGroupVolume(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testHandleEvent_injectVoiceAssistActionUp() {
        // Arrange
        CustomInputEvent event = new CustomInputEvent(
                /* inputCode= */ INJECT_VOICE_ASSIST_ACTION_UP,
                /* targetDisplayType= */ SOME_DISPLAY_TYPE,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        assertKeyEvents(DISPLAY_TYPE_MAIN,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST));
    }

    @Test
    public void testHandleEvent_injectVoiceAssistActionDown() {
        // Arrange
        CustomInputEvent event = new CustomInputEvent(
                /* inputCode= */ INJECT_VOICE_ASSIST_ACTION_DOWN,
                /* targetDisplayType= */ SOME_DISPLAY_TYPE,
                /* repeatCounter= */ 1);

        // Act
        mEventHandler.handle(SOME_DISPLAY_TYPE, event);

        // Assert
        assertKeyEvents(DISPLAY_TYPE_MAIN,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST));
    }

    private void assertKeyEvents(int expectedDisplayType, KeyEvent... events) {
        ArgumentCaptor<KeyEvent> keyEventCaptor = ArgumentCaptor.forClass(KeyEvent.class);
        ArgumentCaptor<Integer> displayTypeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mService, times(events.length)).injectKeyEvent(keyEventCaptor.capture(),
                displayTypeCaptor.capture());

        assertThat(displayTypeCaptor.getValue()).isEqualTo(expectedDisplayType);

        List<KeyEvent> actualEvents = keyEventCaptor.getAllValues();
        for (int i = 0; i < actualEvents.size(); i++) {
            KeyEvent actual = actualEvents.get(i);
            assertThat(actual.getAction()).isEqualTo(events[i].getAction());
            assertThat(actual.getKeyCode()).isEqualTo(events[i].getKeyCode());
            // Make sure display id was not explicitly set
            assertThat(actual.getDisplayId()).isEqualTo(Display.INVALID_DISPLAY);
        }
    }
}
