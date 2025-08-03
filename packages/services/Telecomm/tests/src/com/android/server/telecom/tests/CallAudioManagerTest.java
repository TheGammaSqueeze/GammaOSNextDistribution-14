/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.ToneGenerator;
import android.telecom.DisconnectCause;
import android.util.SparseArray;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallAudioModeStateMachine;
import com.android.server.telecom.CallAudioModeStateMachine.MessageArgs;
import com.android.server.telecom.CallAudioModeStateMachine.MessageArgs.Builder;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.DtmfLocalTonePlayer;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.RingbackPlayer;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.bluetooth.BluetoothStateReceiver;
import com.android.server.telecom.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CallAudioManagerTest extends TelecomTestCase {
    @Mock private CallAudioRouteStateMachine mCallAudioRouteStateMachine;
    @Mock private CallsManager mCallsManager;
    @Mock private CallAudioModeStateMachine mCallAudioModeStateMachine;
    @Mock private InCallTonePlayer.Factory mPlayerFactory;
    @Mock private Ringer mRinger;
    @Mock private RingbackPlayer mRingbackPlayer;
    @Mock private DtmfLocalTonePlayer mDtmfLocalTonePlayer;
    @Mock private BluetoothStateReceiver mBluetoothStateReceiver;
    @Mock private TelecomSystem.SyncRoot mLock;

    @Mock private FeatureFlags mFlags;

    private CallAudioManager mCallAudioManager;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        doAnswer((invocation) -> {
            InCallTonePlayer mockInCallTonePlayer = mock(InCallTonePlayer.class);
            doAnswer((invocation2) -> {
                mCallAudioManager.setIsTonePlaying(invocation.getArgument(0), true);
                return true;
            }).when(mockInCallTonePlayer).startTone();
            return mockInCallTonePlayer;
        }).when(mPlayerFactory).createPlayer(any(Call.class), anyInt());
        when(mCallsManager.getLock()).thenReturn(mLock);
        when(mFlags.ensureAudioModeUpdatesOnForegroundCallChange()).thenReturn(true);
        mCallAudioManager = new CallAudioManager(
                mCallAudioRouteStateMachine,
                mCallsManager,
                mCallAudioModeStateMachine,
                mPlayerFactory,
                mRinger,
                mRingbackPlayer,
                mBluetoothStateReceiver,
                mDtmfLocalTonePlayer,
                mFlags);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @MediumTest
    @Test
    public void testUnmuteOfSecondIncomingCall() {
        // Start with a single incoming call.
        Call call = createIncomingCall();
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(false);
        when(call.getId()).thenReturn("1");

        // Answer the incoming call
        mCallAudioManager.onIncomingCallAnswered(call);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.RINGING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        CallAudioModeStateMachine.MessageArgs correctArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(true)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setHasAudioProcessingCalls(false)
                        .setIsTonePlaying(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(correctArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());

        // Mute the current ongoing call.
        mCallAudioManager.mute(true);

        // Create a second incoming call.
        Call call2 = mock(Call.class);
        when(call2.getState()).thenReturn(CallState.RINGING);
        when(call2.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(false);
        when(call2.getId()).thenReturn("2");
        mCallAudioManager.onCallAdded(call2);

        // Answer the incoming call
        mCallAudioManager.onIncomingCallAnswered(call);

        // Capture the calls to sendMessageWithSessionInfo; we want to look for mute on and off
        // messages and make sure that there was a mute on before the mute off.
        ArgumentCaptor<Integer> muteCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mCallAudioRouteStateMachine, atLeastOnce())
                .sendMessageWithSessionInfo(muteCaptor.capture());
        List<Integer> values = muteCaptor.getAllValues();
        values = values.stream()
                .filter(value -> value == CallAudioRouteStateMachine.MUTE_ON ||
                        value == CallAudioRouteStateMachine.MUTE_OFF)
                .collect(Collectors.toList());

        // Make sure we got a mute on and a mute off.
        assertTrue(values.contains(CallAudioRouteStateMachine.MUTE_ON));
        assertTrue(values.contains(CallAudioRouteStateMachine.MUTE_OFF));
        // And that the mute on happened before the off.
        assertTrue(values.indexOf(CallAudioRouteStateMachine.MUTE_ON) < values
                .lastIndexOf(CallAudioRouteStateMachine.MUTE_OFF));
    }

    @MediumTest
    @Test
    public void testSingleIncomingCallFlowWithoutMTSpeedUp() {
        Call call = createIncomingCall();
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(false);

        // Answer the incoming call
        mCallAudioManager.onIncomingCallAnswered(call);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.RINGING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        CallAudioModeStateMachine.MessageArgs correctArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(true)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setHasAudioProcessingCalls(false)
                        .setIsTonePlaying(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(correctArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());

        disconnectCall(call);
        stopTone(call);

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    @MediumTest
    @Test
    public void testSingleIncomingCallFlowWithMTSpeedUp() {
        Call call = createIncomingCall();
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(true);
        when(call.getState()).thenReturn(CallState.ANSWERED);

        // Answer the incoming call
        mCallAudioManager.onCallStateChanged(call, CallState.RINGING, CallState.ANSWERED);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        CallAudioModeStateMachine.MessageArgs correctArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(true)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setHasAudioProcessingCalls(false)
                        .setIsTonePlaying(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(correctArgs, captor.getValue());
        assertMessageArgEquality(correctArgs, captor.getValue());
        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.ANSWERED, CallState.ACTIVE);

        disconnectCall(call);
        stopTone(call);

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    @MediumTest
    @Test
    public void testSingleOutgoingCall() {
        Call call = mock(Call.class);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.getState()).thenReturn(CallState.CONNECTING);

        mCallAudioManager.onCallAdded(call);
        assertEquals(call, mCallAudioManager.getForegroundCall());
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(true)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());

        when(call.getState()).thenReturn(CallState.DIALING);
        mCallAudioManager.onCallStateChanged(call, CallState.CONNECTING, CallState.DIALING);
        verify(mCallAudioModeStateMachine, times(2)).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        if (mFlags.ensureAudioModeUpdatesOnForegroundCallChange()) {
            // Expect another invocation due to audio mode change signal.
            verify(mCallAudioModeStateMachine, times(3)).sendMessageWithArgs(
                    anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));
        } else {
            verify(mCallAudioModeStateMachine, times(2)).sendMessageWithArgs(
                    anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));
        }

        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.DIALING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine, times(3)).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        if (mFlags.ensureAudioModeUpdatesOnForegroundCallChange()) {
            verify(mCallAudioModeStateMachine, times(4)).sendMessageWithArgs(
                    anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));
        } else {
            verify(mCallAudioModeStateMachine, times(3)).sendMessageWithArgs(
                    anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));
        }
        disconnectCall(call);
        stopTone(call);

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    @Test
    public void testSingleOutgoingCallWithoutAudioModeUpdateOnForegroundCallChange() {
        when(mFlags.ensureAudioModeUpdatesOnForegroundCallChange()).thenReturn(false);
        testSingleOutgoingCall();
    }

    @MediumTest
    @Test
    public void testRingbackStartStop() {
        Call call = mock(Call.class);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.getState()).thenReturn(CallState.CONNECTING);
        when(call.isRingbackRequested()).thenReturn(true);

        mCallAudioManager.onCallAdded(call);
        assertEquals(call, mCallAudioManager.getForegroundCall());
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(true)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());

        when(call.getState()).thenReturn(CallState.DIALING);
        mCallAudioManager.onCallStateChanged(call, CallState.CONNECTING, CallState.DIALING);
        verify(mCallAudioModeStateMachine, times(2)).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        if (mFlags.ensureAudioModeUpdatesOnForegroundCallChange()) {
            // Expect an extra time due to audio mode change signal
            verify(mCallAudioModeStateMachine, times(3)).sendMessageWithArgs(
                    anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));
        } else {
            verify(mCallAudioModeStateMachine, times(2)).sendMessageWithArgs(
                    anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));
        }

        // Ensure we started ringback.
        verify(mRingbackPlayer).startRingbackForCall(any(Call.class));

        // Report state change from dialing to dialing, which happens when a call is locally
        // disconnected.
        mCallAudioManager.onCallStateChanged(call, CallState.DIALING, CallState.DIALING);
        // Should not have stopped ringback.
        verify(mRingbackPlayer, never()).stopRingbackForCall(any(Call.class));
        // Should still only have initial ringback start
        verify(mRingbackPlayer, times(1)).startRingbackForCall(any(Call.class));

        // Report state to disconnected
        when(call.getState()).thenReturn(CallState.DISCONNECTED);
        mCallAudioManager.onCallStateChanged(call, CallState.DIALING, CallState.DISCONNECTED);
        // Now we should have stopped ringback.
        verify(mRingbackPlayer).stopRingbackForCall(any(Call.class));
        // Should still only have initial ringback start recorded from before (don't restart it).
        verify(mRingbackPlayer, times(1)).startRingbackForCall(any(Call.class));
    }

    @Test
    public void testRingbackStartStopWithoutAudioModeUpdateOnForegroundCallChange() {
        when(mFlags.ensureAudioModeUpdatesOnForegroundCallChange()).thenReturn(false);
        testRingbackStartStop();
    }

    @SmallTest
    @Test
    public void testNewCallGoesToAudioProcessing() {
        Call call = mock(Call.class);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.getState()).thenReturn(CallState.NEW);

        // Make sure nothing happens when we add the NEW call
        mCallAudioManager.onCallAdded(call);

        verify(mCallAudioRouteStateMachine, never()).sendMessageWithSessionInfo(anyInt());
        verify(mCallAudioModeStateMachine, never()).sendMessageWithArgs(
                anyInt(), nullable(MessageArgs.class));

        // Transition the call to AUDIO_PROCESSING and see what happens
        when(call.getState()).thenReturn(CallState.AUDIO_PROCESSING);
        mCallAudioManager.onCallStateChanged(call, CallState.NEW, CallState.AUDIO_PROCESSING);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_AUDIO_PROCESSING_CALL), captor.capture());

        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(true)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());
    }

    @SmallTest
    @Test
    public void testRingingCallGoesToAudioProcessing() {
        Call call = mock(Call.class);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.getState()).thenReturn(CallState.RINGING);

        // Make sure appropriate messages are sent when we add a RINGING call
        mCallAudioManager.onCallAdded(call);

        assertEquals(call, mCallAudioManager.getForegroundCall());
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_RINGING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(true)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());

        // Transition the call to AUDIO_PROCESSING and see what happens
        when(call.getState()).thenReturn(CallState.AUDIO_PROCESSING);
        mCallAudioManager.onCallStateChanged(call, CallState.RINGING, CallState.AUDIO_PROCESSING);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_AUDIO_PROCESSING_CALL), captor.capture());

        CallAudioModeStateMachine.MessageArgs expectedArgs2 =
                new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(true)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs2, captor.getValue());

        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        assertMessageArgEquality(expectedArgs2, captor.getValue());
    }

    @SmallTest
    @Test
    public void testActiveCallGoesToAudioProcessing() {
        Call call = mock(Call.class);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();
        when(call.getState()).thenReturn(CallState.ACTIVE);

        // Make sure appropriate messages are sent when we add an active call
        mCallAudioManager.onCallAdded(call);

        assertEquals(call, mCallAudioManager.getForegroundCall());
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(true)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());

        // Transition the call to AUDIO_PROCESSING and see what happens
        when(call.getState()).thenReturn(CallState.AUDIO_PROCESSING);
        mCallAudioManager.onCallStateChanged(call, CallState.ACTIVE, CallState.AUDIO_PROCESSING);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_AUDIO_PROCESSING_CALL), captor.capture());

        CallAudioModeStateMachine.MessageArgs expectedArgs2 =
                new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(true)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs2, captor.getValue());

        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_ACTIVE_OR_DIALING_CALLS), captor.capture());
        assertMessageArgEquality(expectedArgs2, captor.getValue());
    }

    @SmallTest
    @Test
    public void testAudioProcessingCallDisconnects() {
        Call call = createAudioProcessingCall();
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        when(call.getState()).thenReturn(CallState.DISCONNECTED);
        when(call.getDisconnectCause()).thenReturn(new DisconnectCause(DisconnectCause.LOCAL,
                "", "", "", ToneGenerator.TONE_UNKNOWN));

        mCallAudioManager.onCallStateChanged(call, CallState.AUDIO_PROCESSING,
                CallState.DISCONNECTED);
        verify(mPlayerFactory, never()).createPlayer(any(Call.class), anyInt());
        CallAudioModeStateMachine.MessageArgs expectedArgs2 = new Builder()
                .setHasActiveOrDialingCalls(false)
                .setHasRingingCalls(false)
                .setHasHoldingCalls(false)
                .setHasAudioProcessingCalls(false)
                .setIsTonePlaying(false)
                .setForegroundCallIsVoip(false)
                .setSession(null)
                .build();
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_AUDIO_PROCESSING_CALLS), captor.capture());
        assertMessageArgEquality(expectedArgs2, captor.getValue());
        verify(mCallAudioModeStateMachine, never()).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.TONE_STARTED_PLAYING), nullable(MessageArgs.class));

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    @SmallTest
    @Test
    public void testAudioProcessingCallDoesSimulatedRing() {
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        Call call = createAudioProcessingCall();

        when(call.getState()).thenReturn(CallState.SIMULATED_RINGING);

        mCallAudioManager.onCallStateChanged(call, CallState.AUDIO_PROCESSING,
                CallState.SIMULATED_RINGING);
        verify(mPlayerFactory, never()).createPlayer(any(Call.class), anyInt());
        CallAudioModeStateMachine.MessageArgs expectedArgs = new Builder()
                .setHasActiveOrDialingCalls(false)
                .setHasRingingCalls(true)
                .setHasHoldingCalls(false)
                .setHasAudioProcessingCalls(false)
                .setIsTonePlaying(false)
                .setForegroundCallIsVoip(false)
                .setSession(null)
                .build();
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_AUDIO_PROCESSING_CALLS), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_RINGING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
    }

    @SmallTest
    @Test
    public void testAudioProcessingCallGoesActive() {
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        Call call = createAudioProcessingCall();

        when(call.getState()).thenReturn(CallState.ACTIVE);

        mCallAudioManager.onCallStateChanged(call, CallState.AUDIO_PROCESSING,
                CallState.ACTIVE);
        verify(mPlayerFactory, never()).createPlayer(any(Call.class), anyInt());
        CallAudioModeStateMachine.MessageArgs expectedArgs = new Builder()
                .setHasActiveOrDialingCalls(true)
                .setHasRingingCalls(false)
                .setHasHoldingCalls(false)
                .setHasAudioProcessingCalls(false)
                .setIsTonePlaying(false)
                .setForegroundCallIsVoip(false)
                .setSession(null)
                .build();
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_AUDIO_PROCESSING_CALLS), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
    }

    @SmallTest
    @Test
    public void testSimulatedRingingCallGoesActive() {
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        Call call = createSimulatedRingingCall();

        when(call.getState()).thenReturn(CallState.ACTIVE);

        mCallAudioManager.onCallStateChanged(call, CallState.SIMULATED_RINGING,
                CallState.ACTIVE);
        verify(mPlayerFactory, never()).createPlayer(any(Call.class), anyInt());
        CallAudioModeStateMachine.MessageArgs expectedArgs = new Builder()
                .setHasActiveOrDialingCalls(true)
                .setHasRingingCalls(false)
                .setHasHoldingCalls(false)
                .setHasAudioProcessingCalls(false)
                .setIsTonePlaying(false)
                .setForegroundCallIsVoip(false)
                .setSession(null)
                .build();
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
    }

    private Call createAudioProcessingCall() {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.AUDIO_PROCESSING);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        // Set up an AUDIO_PROCESSING call
        mCallAudioManager.onCallAdded(call);

        assertNull(mCallAudioManager.getForegroundCall());

        verify(mCallAudioRouteStateMachine, never()).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_AUDIO_PROCESSING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(true)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());

        return call;
    }

    @SmallTest
    @Test
    public void testSimulatedRingingCallDisconnects() {
        Call call = createSimulatedRingingCall();
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        when(call.getState()).thenReturn(CallState.DISCONNECTED);
        when(call.getDisconnectCause()).thenReturn(new DisconnectCause(DisconnectCause.LOCAL,
                "", "", "", ToneGenerator.TONE_UNKNOWN));

        mCallAudioManager.onCallStateChanged(call, CallState.SIMULATED_RINGING,
                CallState.DISCONNECTED);
        verify(mPlayerFactory, never()).createPlayer(any(Call.class), anyInt());
        CallAudioModeStateMachine.MessageArgs expectedArgs2 = new Builder()
                .setHasActiveOrDialingCalls(false)
                .setHasRingingCalls(false)
                .setHasHoldingCalls(false)
                .setHasAudioProcessingCalls(false)
                .setIsTonePlaying(false)
                .setForegroundCallIsVoip(false)
                .setSession(null)
                .build();
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        assertMessageArgEquality(expectedArgs2, captor.getValue());
        verify(mCallAudioModeStateMachine, never()).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.TONE_STARTED_PLAYING), nullable(MessageArgs.class));

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    @SmallTest
    @Test
    public void testGetVoipMode() {
        Call child = mock(Call.class);
        when(child.getIsVoipAudioMode()).thenReturn(true);

        Call conference = mock(Call.class);
        when(conference.isConference()).thenReturn(true);
        when(conference.getIsVoipAudioMode()).thenReturn(false);
        when(conference.getChildCalls()).thenReturn(Arrays.asList(child));

        assertTrue(mCallAudioManager.isCallVoip(conference));
        assertTrue(mCallAudioManager.isCallVoip(child));
    }

    @SmallTest
    @Test
    public void testOnCallStreamingStateChanged() {
        Call call = mock(Call.class);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        // non-streaming call
        mCallAudioManager.onCallStreamingStateChanged(call, false /* isStreamingCall */);
        verify(mCallAudioModeStateMachine, never()).sendMessageWithArgs(anyInt(),
                any(MessageArgs.class));

        // start streaming
        mCallAudioManager.onCallStreamingStateChanged(call, true /* isStreamingCall */);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.START_CALL_STREAMING), captor.capture());
        assertTrue(captor.getValue().isStreaming);

        // stop streaming
        mCallAudioManager.onCallStreamingStateChanged(call, false /* isStreamingCall */);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.STOP_CALL_STREAMING), captor.capture());
        assertFalse(captor.getValue().isStreaming);
    }

    @SmallTest
    @Test
    public void testTriggerAudioManagerModeChange() {
        if (!mFlags.ensureAudioModeUpdatesOnForegroundCallChange()) {
            // Skip if the new behavior isn't in use.
            return;
        }
        // Start with an incoming PSTN call
        Call pstnCall = mock(Call.class);
        when(pstnCall.getState()).thenReturn(CallState.RINGING);
        when(pstnCall.getIsVoipAudioMode()).thenReturn(false);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        // Add the call
        mCallAudioManager.onCallAdded(pstnCall);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.FOREGROUND_VOIP_MODE_CHANGE), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(true)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .setForegroundCallIsVoip(false)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());
        clearInvocations(mCallAudioModeStateMachine); // Avoid verifying for previous calls

        // Make call active; don't expect there to be an audio mode transition.
        when(pstnCall.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(pstnCall, CallState.RINGING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine, never()).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.FOREGROUND_VOIP_MODE_CHANGE),
                any(CallAudioModeStateMachine.MessageArgs.class));
        clearInvocations(mCallAudioModeStateMachine); // Avoid verifying for previous calls

        // Add a new Voip call in ringing state; this should not result in a direct audio mode
        // change.
        Call voipCall = mock(Call.class);
        when(voipCall.getState()).thenReturn(CallState.RINGING);
        when(voipCall.getIsVoipAudioMode()).thenReturn(true);
        mCallAudioManager.onCallAdded(voipCall);
        verify(mCallAudioModeStateMachine, never()).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.FOREGROUND_VOIP_MODE_CHANGE),
                any(CallAudioModeStateMachine.MessageArgs.class));
        clearInvocations(mCallAudioModeStateMachine); // Avoid verifying for previous calls

        // Make voip call active and set the PSTN call to locally disconnecting; the new foreground
        // call will be the voip call.
        when(pstnCall.isLocallyDisconnecting()).thenReturn(true);
        when(voipCall.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(voipCall, CallState.RINGING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.FOREGROUND_VOIP_MODE_CHANGE), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs2 =
                new Builder()
                        .setHasActiveOrDialingCalls(true)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .setForegroundCallIsVoip(true)
                        .build();
        assertMessageArgEquality(expectedArgs2, captor.getValue());
    }

    private Call createSimulatedRingingCall() {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.SIMULATED_RINGING);
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor = makeNewCaptor();

        mCallAudioManager.onCallAdded(call);

        assertEquals(call, mCallAudioManager.getForegroundCall());

        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_RINGING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(true)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setHasAudioProcessingCalls(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        assertMessageArgEquality(expectedArgs, captor.getValue());

        return call;
    }

    private Call createIncomingCall() {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.RINGING);

        mCallAudioManager.onCallAdded(call);
        assertEquals(call, mCallAudioManager.getForegroundCall());
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_RINGING_CALL), captor.capture());
        assertMessageArgEquality(new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(true)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build(),
                captor.getValue());

        return call;
    }

    private void disconnectCall(Call call) {
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        CallAudioModeStateMachine.MessageArgs correctArgs;

        when(call.getState()).thenReturn(CallState.DISCONNECTED);
        when(call.getDisconnectCause()).thenReturn(new DisconnectCause(DisconnectCause.LOCAL,
                "", "", "", ToneGenerator.TONE_PROP_PROMPT));

        mCallAudioManager.onCallStateChanged(call, CallState.ACTIVE, CallState.DISCONNECTED);
        verify(mPlayerFactory).createPlayer(any(Call.class), eq(InCallTonePlayer.TONE_CALL_ENDED));
        correctArgs = new Builder()
                .setHasActiveOrDialingCalls(false)
                .setHasRingingCalls(false)
                .setHasHoldingCalls(false)
                .setIsTonePlaying(true)
                .setForegroundCallIsVoip(false)
                .setSession(null)
                .build();
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_ACTIVE_OR_DIALING_CALLS), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.TONE_STARTED_PLAYING), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());
    }

    private void stopTone(Call call) {
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        mCallAudioManager.setIsTonePlaying(call, false);
        CallAudioModeStateMachine.MessageArgs correctArgs = new Builder()
                        .setHasActiveOrDialingCalls(false)
                        .setHasRingingCalls(false)
                        .setHasHoldingCalls(false)
                        .setIsTonePlaying(false)
                        .setForegroundCallIsVoip(false)
                        .setSession(null)
                        .build();
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.TONE_STOPPED_PLAYING), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());
    }

    private void verifyProperCleanup() {
        assertEquals(0, mCallAudioManager.getTrackedCalls().size());
        SparseArray<LinkedHashSet<Call>> callStateToCalls = mCallAudioManager.getCallStateToCalls();
        for (int i = 0; i < callStateToCalls.size(); i++) {
            assertEquals(0, callStateToCalls.valueAt(i).size());
        }
    }

    private ArgumentCaptor<MessageArgs> makeNewCaptor() {
        return ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
    }

    private void assertMessageArgEquality(CallAudioModeStateMachine.MessageArgs expected,
            CallAudioModeStateMachine.MessageArgs actual) {
        assertEquals(expected.hasActiveOrDialingCalls, actual.hasActiveOrDialingCalls);
        assertEquals(expected.hasHoldingCalls, actual.hasHoldingCalls);
        assertEquals(expected.hasRingingCalls, actual.hasRingingCalls);
        assertEquals(expected.isTonePlaying, actual.isTonePlaying);
        assertEquals(expected.foregroundCallIsVoip, actual.foregroundCallIsVoip);
    }
}
