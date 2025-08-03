/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.bluetooth.a2dpsink;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.avrcpcontroller.AvrcpControllerNativeInterface;
import com.android.bluetooth.avrcpcontroller.AvrcpControllerService;
import com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class A2dpSinkStreamHandlerTest {
    private static final int DUCK_PERCENT = 75;
    private HandlerThread mHandlerThread;
    private AvrcpControllerService mService;
    private A2dpSinkStreamHandler mStreamHandler;
    private Context mTargetContext;

    @Mock private A2dpSinkService mMockA2dpSink;

    @Mock private A2dpSinkNativeInterface mMockNativeInterface;
    @Mock private AvrcpControllerNativeInterface mMockAvrcpControllerNativeInterface;

    @Mock private AudioManager mMockAudioManager;

    @Mock private Resources mMockResources;

    @Mock private PackageManager mMockPackageManager;

    @Rule
    public final ServiceTestRule mBluetoothBrowserMediaServiceTestRule = new ServiceTestRule();

    @Mock
    private AdapterService mAdapterService;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        // Mock the looper
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        TestUtils.setAdapterService(mAdapterService);
        AvrcpControllerNativeInterface.setInstance(mMockAvrcpControllerNativeInterface);
        mService = new AvrcpControllerService(mTargetContext, mMockAvrcpControllerNativeInterface);
        mService.start();
        final Intent bluetoothBrowserMediaServiceStartIntent =
                TestUtils.prepareIntentToStartBluetoothBrowserMediaService();
        mBluetoothBrowserMediaServiceTestRule.startService(bluetoothBrowserMediaServiceStartIntent);

        mHandlerThread = new HandlerThread("A2dpSinkStreamHandlerTest");
        mHandlerThread.start();

        when(mMockA2dpSink.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mMockAudioManager);
        when(mMockA2dpSink.getSystemServiceName(AudioManager.class))
                .thenReturn(Context.AUDIO_SERVICE);
        when(mMockA2dpSink.getResources()).thenReturn(mMockResources);
        when(mMockResources.getInteger(anyInt())).thenReturn(DUCK_PERCENT);
        when(mMockAudioManager.requestAudioFocus(any())).thenReturn(
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        when(mMockAudioManager.abandonAudioFocus(any())).thenReturn(
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        when(mMockAudioManager.generateAudioSessionId()).thenReturn(0);
        when(mMockA2dpSink.getMainLooper()).thenReturn(mHandlerThread.getLooper());
        when(mMockA2dpSink.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(any())).thenReturn(false);

        mStreamHandler = spy(new A2dpSinkStreamHandler(mMockA2dpSink, mMockNativeInterface));
    }

    @After
    public void tearDown() throws Exception {
        mService.stop();
        AvrcpControllerNativeInterface.setInstance(null);
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void testSrcStart() {
        // Stream started without local play, expect no change in streaming.
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_STR_START));
        verify(mMockAudioManager, times(0)).requestAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(1);
        verify(mMockNativeInterface, times(0)).informAudioTrackGain(1.0f);
        assertThat(mStreamHandler.isPlaying()).isFalse();
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testSrcStop() {
        // Stream stopped without local play, expect no change in streaming.
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_STR_STOP));
        verify(mMockAudioManager, times(0)).requestAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(1);
        verify(mMockNativeInterface, times(0)).informAudioTrackGain(1.0f);
        assertThat(mStreamHandler.isPlaying()).isFalse();
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testSnkPlay() {
        // Play was pressed locally, expect streaming to start soon.
        mStreamHandler.handleMessage(mStreamHandler.obtainMessage(A2dpSinkStreamHandler.SNK_PLAY));
        verify(mMockAudioManager, times(1)).requestAudioFocus(any());
        assertThat(mStreamHandler.isPlaying()).isFalse();
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testSnkPause() {
        // Pause was pressed locally, expect streaming to stop.
        mStreamHandler.handleMessage(mStreamHandler.obtainMessage(A2dpSinkStreamHandler.SNK_PAUSE));
        verify(mMockAudioManager, times(0)).requestAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(1);
        verify(mMockNativeInterface, times(0)).informAudioTrackGain(1.0f);
        assertThat(mStreamHandler.isPlaying()).isFalse();
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testDisconnect() {
        // Remote device was disconnected, expect streaming to stop.
        testSnkPlay();
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.DISCONNECT));
        verify(mMockAudioManager, times(0)).abandonAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(0);
        assertThat(mStreamHandler.isPlaying()).isFalse();
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testSrcPlay() {
        // Play was pressed remotely, expect no streaming due to lack of audio focus.
        mStreamHandler.handleMessage(mStreamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_PLAY));
        verify(mMockAudioManager, times(0)).requestAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(1);
        verify(mMockNativeInterface, times(0)).informAudioTrackGain(1.0f);
        assertThat(mStreamHandler.isPlaying()).isFalse();
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testSrcPlayIot() {
        // Play was pressed remotely for an iot device, expect streaming to start.
        when(mMockPackageManager.hasSystemFeature(any())).thenReturn(true);
        mStreamHandler.handleMessage(mStreamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_PLAY));
        verify(mMockAudioManager, times(1)).requestAudioFocus(any());
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mStreamHandler.isPlaying()).isTrue();
    }

    @Test
    public void testSrcPause() {
        // Play was pressed locally, expect streaming to start.
        mStreamHandler.handleMessage(mStreamHandler.obtainMessage(A2dpSinkStreamHandler.SRC_PLAY));
        verify(mMockAudioManager, times(0)).requestAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(1);
        verify(mMockNativeInterface, times(0)).informAudioTrackGain(1.0f);
        assertThat(mStreamHandler.isPlaying()).isFalse();
    }

    @Test
    public void testFocusGain() {
        // Focus was gained, expect streaming to resume.
        testSnkPlay();
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE,
                        AudioManager.AUDIOFOCUS_GAIN));
        verify(mMockAudioManager, times(1)).requestAudioFocus(any());
        verify(mMockNativeInterface, times(1)).informAudioFocusState(1);
        verify(mMockNativeInterface, times(1)).informAudioTrackGain(1.0f);

        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mStreamHandler.getFocusState()).isEqualTo(AudioManager.AUDIOFOCUS_GAIN);
        assertThat(BluetoothMediaBrowserService.isActive()).isTrue();
    }

    @Test
    public void testFocusTransientMayDuck() {
        // TransientMayDuck focus was gained, expect audio stream to duck.
        testSnkPlay();
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK));
        verify(mMockNativeInterface, times(1)).informAudioTrackGain(DUCK_PERCENT / 100.0f);

        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mStreamHandler.getFocusState()).isEqualTo(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testFocusLostTransient() {
        // Focus was lost transiently, expect streaming to stop.
        testSnkPlay();
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT));
        verify(mMockAudioManager, times(0)).abandonAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(0);
        verify(mMockNativeInterface, times(1)).informAudioTrackGain(0);

        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(mStreamHandler.getFocusState()).isEqualTo(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
    }

    @Test
    public void testFocusRerequest() {
        // Focus was lost transiently, expect streaming to stop.
        testSnkPlay();
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT));
        verify(mMockAudioManager, times(0)).abandonAudioFocus(any());
        verify(mMockNativeInterface, times(0)).informAudioFocusState(0);
        verify(mMockNativeInterface, times(1)).informAudioTrackGain(0);
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.REQUEST_FOCUS, true));
        verify(mMockAudioManager, times(2)).requestAudioFocus(any());
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
    }

    @Test
    public void testFocusGainFromTransientLoss() {
        // Focus was lost transiently and then regained.
        testFocusLostTransient();

        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE,
                        AudioManager.AUDIOFOCUS_GAIN));
        verify(mMockAudioManager, times(0)).abandonAudioFocus(any());
        verify(mMockNativeInterface, times(1)).informAudioTrackGain(1.0f);

        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(BluetoothMediaBrowserService.isActive()).isTrue();
        assertThat(mStreamHandler.getFocusState()).isEqualTo(AudioManager.AUDIOFOCUS_GAIN);
    }

    @Test
    public void testFocusLost() {
        // Focus was lost permanently, expect streaming to stop.
        testSnkPlay();
        mStreamHandler.handleMessage(
                mStreamHandler.obtainMessage(A2dpSinkStreamHandler.AUDIO_FOCUS_CHANGE,
                        AudioManager.AUDIOFOCUS_LOSS));
        verify(mMockAudioManager, times(1)).abandonAudioFocus(any());
        verify(mMockNativeInterface, times(1)).informAudioFocusState(0);

        TestUtils.waitForLooperToFinishScheduledTask(mHandlerThread.getLooper());
        assertThat(BluetoothMediaBrowserService.isActive()).isFalse();
        assertThat(mStreamHandler.getFocusState()).isEqualTo(AudioManager.AUDIOFOCUS_NONE);
        assertThat(mStreamHandler.isPlaying()).isFalse();
    }
}
