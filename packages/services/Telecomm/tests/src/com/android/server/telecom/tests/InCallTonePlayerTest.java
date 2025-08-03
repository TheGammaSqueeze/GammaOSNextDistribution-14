/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.AsyncRingtonePlayer;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallAudioRoutePeripheralAdapter;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.DockManager;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.WiredHeadsetManager;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class InCallTonePlayerTest extends TelecomTestCase {

    private static final long TEST_TIMEOUT = 5000L;
    private InCallTonePlayer.Factory mFactory;
    private CallAudioRoutePeripheralAdapter mCallAudioRoutePeripheralAdapter;

    @Mock private BluetoothRouteManager mBluetoothRouteManager;
    @Mock private CallAudioRouteStateMachine mCallAudioRouteStateMachine;
    @Mock private Timeouts.Adapter mTimeoutsAdapter;
    @Mock private BluetoothDeviceManager mBluetoothDeviceManager;
    @Mock private TelecomSystem.SyncRoot mLock;
    @Mock private ToneGenerator mToneGenerator;
    @Mock private InCallTonePlayer.ToneGeneratorFactory mToneGeneratorFactory;
    @Mock private WiredHeadsetManager mWiredHeadsetManager;
    @Mock private DockManager mDockManager;
    @Mock private AsyncRingtonePlayer mRingtonePlayer;
    @Mock private BluetoothDevice mDevice;
    @Mock private BluetoothAdapter mBluetoothAdapter;

    private InCallTonePlayer.MediaPlayerAdapter mMediaPlayerAdapter =
            new InCallTonePlayer.MediaPlayerAdapter() {
        private MediaPlayer.OnCompletionListener mListener;

        @Override
        public void setLooping(boolean isLooping) {
            // Do nothing.
        }

        @Override
        public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
            mListener = listener;
        }

        @Override
        public void start() {
            mListener.onCompletion(null);
        }

        @Override
        public void release() {
            // Do nothing.
        }

        @Override
        public int getDuration() {
            return 1000;
        }
    };

    @Mock
    private InCallTonePlayer.MediaPlayerFactory mMediaPlayerFactory;

    @Mock
    private InCallTonePlayer.AudioManagerAdapter mAudioManagerAdapter;

    @Mock
    private CallAudioManager mCallAudioManager;
    @Mock
    private Call mCall;

    private InCallTonePlayer mInCallTonePlayer;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        when(mToneGeneratorFactory.get(anyInt(), anyInt())).thenReturn(mToneGenerator);
        when(mMediaPlayerFactory.get(anyInt(), any())).thenReturn(mMediaPlayerAdapter);
        doNothing().when(mCallAudioManager).setIsTonePlaying(any(Call.class), anyBoolean());

        mCallAudioRoutePeripheralAdapter = new CallAudioRoutePeripheralAdapter(
                mCallAudioRouteStateMachine, mBluetoothRouteManager, mWiredHeadsetManager,
                mDockManager, mRingtonePlayer);
        mFactory = new InCallTonePlayer.Factory(mCallAudioRoutePeripheralAdapter, mLock,
                mToneGeneratorFactory, mMediaPlayerFactory, mAudioManagerAdapter);
        mFactory.setCallAudioManager(mCallAudioManager);
        mInCallTonePlayer = mFactory.createPlayer(mCall, InCallTonePlayer.TONE_CALL_ENDED);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mInCallTonePlayer.cleanup();
    }

    @SmallTest
    @Test
    public void testEndCallTonePlaysWhenRingIsSilent() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(false);
        assertTrue(mInCallTonePlayer.startTone());
        // Verify we did play a tone.
        verify(mMediaPlayerFactory, timeout(TEST_TIMEOUT)).get(anyInt(), any());
        verify(mCallAudioManager).setIsTonePlaying(any(Call.class), eq(true));

        mInCallTonePlayer.stopTone();
        // Timeouts due to threads!
        verify(mCallAudioManager, timeout(TEST_TIMEOUT)).setIsTonePlaying(any(Call.class),
                eq(false));
    }

    @SmallTest
    @Test
    public void testInterruptMediaTone() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        assertTrue(mInCallTonePlayer.startTone());
        // Verify we did play a tone.
        verify(mMediaPlayerFactory, timeout(TEST_TIMEOUT)).get(anyInt(), any());
        verify(mCallAudioManager).setIsTonePlaying(any(Call.class), eq(true));

        mInCallTonePlayer.stopTone();
        // Timeouts due to threads!
        verify(mCallAudioManager, timeout(TEST_TIMEOUT)).setIsTonePlaying(any(Call.class),
                eq(false));

        // Correctness check: ensure we can't start the tone again.
        assertFalse(mInCallTonePlayer.startTone());
    }

    @SmallTest
    @Test
    public void testInterruptToneGenerator() {
        mInCallTonePlayer = mFactory.createPlayer(mCall, InCallTonePlayer.TONE_RING_BACK);
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        assertTrue(mInCallTonePlayer.startTone());
        verify(mToneGenerator, timeout(TEST_TIMEOUT)).startTone(anyInt());
        verify(mCallAudioManager).setIsTonePlaying(any(Call.class), eq(true));

        mInCallTonePlayer.stopTone();
        // Timeouts due to threads!
        verify(mCallAudioManager, timeout(TEST_TIMEOUT)).setIsTonePlaying(any(Call.class),
                eq(false));
        // Ideally it would be nice to verify this, however release is a native method so appears to
        // cause flakiness when testing on Cuttlefish.
        // verify(mToneGenerator, timeout(TEST_TIMEOUT)).release();

        // Correctness check: ensure we can't start the tone again.
        assertFalse(mInCallTonePlayer.startTone());
    }

    @SmallTest
    @Test
    public void testEndCallToneWhenNotSilenced() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        assertTrue(mInCallTonePlayer.startTone());

        // Verify we did play a tone.
        verify(mMediaPlayerFactory, timeout(TEST_TIMEOUT)).get(anyInt(), any());
        verify(mCallAudioManager, timeout(TEST_TIMEOUT)).setIsTonePlaying(any(Call.class),
                eq(true));
    }

    @SmallTest
    @Test
    public void testRingbackToneAudioStreamHeadset() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        mBluetoothDeviceManager.setBluetoothRouteManager(mBluetoothRouteManager);
        when(mBluetoothRouteManager.getBluetoothAudioConnectedDevice()).thenReturn(mDevice);
        when(mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(true);

        when(mBluetoothRouteManager.isCachedLeAudioDevice(mDevice)).thenReturn(false);
        when(mBluetoothRouteManager.isCachedHearingAidDevice(mDevice)).thenReturn(false);

        mInCallTonePlayer = mFactory.createPlayer(mCall, InCallTonePlayer.TONE_RING_BACK);
        assertTrue(mInCallTonePlayer.startTone());
        verify(mToneGeneratorFactory, timeout(TEST_TIMEOUT))
                .get(eq(AudioManager.STREAM_BLUETOOTH_SCO), anyInt());
        verify(mCallAudioManager).setIsTonePlaying(any(Call.class), eq(true));
    }

    @SmallTest
    @Test
    public void testCallWaitingToneAudioStreamHeadset() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        mBluetoothDeviceManager.setBluetoothRouteManager(mBluetoothRouteManager);
        when(mBluetoothRouteManager.getBluetoothAudioConnectedDevice()).thenReturn(mDevice);
        when(mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(true);

        when(mBluetoothRouteManager.isCachedLeAudioDevice(mDevice)).thenReturn(false);
        when(mBluetoothRouteManager.isCachedHearingAidDevice(mDevice)).thenReturn(false);

        mInCallTonePlayer = mFactory.createPlayer(mCall, InCallTonePlayer.TONE_CALL_WAITING);
        assertTrue(mInCallTonePlayer.startTone());
        verify(mToneGeneratorFactory, timeout(TEST_TIMEOUT))
                .get(eq(AudioManager.STREAM_BLUETOOTH_SCO), anyInt());
        verify(mCallAudioManager).setIsTonePlaying(any(Call.class), eq(true));
    }

    @SmallTest
    @Test
    public void testRingbackToneAudioStreamHearingAid() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        mBluetoothDeviceManager.setBluetoothRouteManager(mBluetoothRouteManager);
        when(mBluetoothRouteManager.getBluetoothAudioConnectedDevice()).thenReturn(mDevice);
        when(mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(true);

        when(mBluetoothRouteManager.isCachedLeAudioDevice(mDevice)).thenReturn(false);
        when(mBluetoothRouteManager.isCachedHearingAidDevice(mDevice)).thenReturn(true);

        mInCallTonePlayer = mFactory.createPlayer(mCall, InCallTonePlayer.TONE_RING_BACK);
        assertTrue(mInCallTonePlayer.startTone());
        verify(mToneGeneratorFactory, timeout(TEST_TIMEOUT))
                .get(eq(AudioManager.STREAM_VOICE_CALL), anyInt());
        verify(mCallAudioManager).setIsTonePlaying(any(Call.class), eq(true));
    }

    @SmallTest
    @Test
    public void testCallWaitingToneAudioStreamHearingAid() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        mBluetoothDeviceManager.setBluetoothRouteManager(mBluetoothRouteManager);
        when(mBluetoothRouteManager.getBluetoothAudioConnectedDevice()).thenReturn(mDevice);
        when(mBluetoothRouteManager.isBluetoothAudioConnectedOrPending()).thenReturn(true);

        when(mBluetoothRouteManager.isCachedLeAudioDevice(mDevice)).thenReturn(false);
        when(mBluetoothRouteManager.isCachedHearingAidDevice(mDevice)).thenReturn(true);

        mInCallTonePlayer = mFactory.createPlayer(mCall, InCallTonePlayer.TONE_CALL_WAITING);
        assertTrue(mInCallTonePlayer.startTone());
        verify(mToneGeneratorFactory, timeout(TEST_TIMEOUT))
                .get(eq(AudioManager.STREAM_VOICE_CALL), anyInt());
        verify(mCallAudioManager).setIsTonePlaying(any(Call.class), eq(true));
    }
}
