/*
 * Copyright 2021 HIMSA II K/S - www.himsa.dk. Represented by EHIMA - www.ehima.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.bluetooth.tbs;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Mockito.*;

import android.bluetooth.*;
import android.bluetooth.IBluetoothLeCallControlCallback;
import android.content.Context;
import android.os.ParcelUuid;
import android.os.RemoteException;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.le_audio.LeAudioService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TbsGenericTest {
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mCurrentDevice;

    private TbsGeneric mTbsGeneric;

    private @Mock TbsGatt mTbsGatt;
    private @Mock IBluetoothLeCallControlCallback mIBluetoothLeCallControlCallback;
    private @Captor ArgumentCaptor<Integer> mGtbsCcidCaptor;
    private @Captor ArgumentCaptor<String> mGtbsUciCaptor;
    private @Captor ArgumentCaptor<List> mDefaultGtbsUriSchemesCaptor =
            ArgumentCaptor.forClass(List.class);
    private @Captor ArgumentCaptor<String> mDefaultGtbsProviderNameCaptor;
    private @Captor ArgumentCaptor<Integer> mDefaultGtbsTechnologyCaptor;

    private @Captor ArgumentCaptor<TbsGatt.Callback> mTbsGattCallback;
    private static Context mContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = getInstrumentation().getTargetContext();

        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();

        // Default TbsGatt mock behavior
        doReturn(true).when(mTbsGatt).init(mGtbsCcidCaptor.capture(), mGtbsUciCaptor.capture(),
                mDefaultGtbsUriSchemesCaptor.capture(), anyBoolean(), anyBoolean(),
                mDefaultGtbsProviderNameCaptor.capture(), mDefaultGtbsTechnologyCaptor.capture(),
                mTbsGattCallback.capture());
        doReturn(true).when(mTbsGatt).setBearerProviderName(anyString());
        doReturn(true).when(mTbsGatt).setBearerTechnology(anyInt());
        doReturn(true).when(mTbsGatt).setBearerUriSchemesSupportedList(any());
        doReturn(true).when(mTbsGatt).setCallState(any());
        doReturn(true).when(mTbsGatt).setBearerListCurrentCalls(any());
        doReturn(true).when(mTbsGatt).setInbandRingtoneFlag(any());
        doReturn(true).when(mTbsGatt).clearInbandRingtoneFlag(any());
        doReturn(true).when(mTbsGatt).setSilentModeFlag();
        doReturn(true).when(mTbsGatt).clearSilentModeFlag();
        doReturn(true).when(mTbsGatt).setTerminationReason(anyInt(), anyInt());
        doReturn(true).when(mTbsGatt).setIncomingCall(anyInt(), anyString());
        doReturn(true).when(mTbsGatt).clearIncomingCall();
        doReturn(true).when(mTbsGatt).setCallFriendlyName(anyInt(), anyString());
        doReturn(true).when(mTbsGatt).clearFriendlyName();
        doReturn(mContext).when(mTbsGatt).getContext();

        mTbsGeneric = new TbsGeneric();
        mTbsGeneric.init(mTbsGatt);
    }

    @After
    public void tearDown() throws Exception {
        mTbsGeneric = null;
    }

    private Integer prepareTestBearer() {
        String uci = "testUci";
        List<String> uriSchemes = Arrays.asList("tel", "xmpp");
        Integer capabilities =
                BluetoothLeCallControl.CAPABILITY_HOLD_CALL | BluetoothLeCallControl.CAPABILITY_JOIN_CALLS;
        String providerName = "testProviderName";
        int technology = 0x02;

        assertThat(mTbsGeneric.addBearer("testBearer", mIBluetoothLeCallControlCallback, uci, uriSchemes,
                capabilities, providerName, technology)).isTrue();

        ArgumentCaptor<Integer> ccidCaptor = ArgumentCaptor.forClass(Integer.class);
        try {
            // Check proper callback call on the profile's binder
            verify(mIBluetoothLeCallControlCallback).onBearerRegistered(ccidCaptor.capture());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return ccidCaptor.getValue();
    }

    @Test
    public void testSetClearInbandRingtone() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        prepareTestBearer();

        mTbsGeneric.setInbandRingtoneSupport(mCurrentDevice);
        verify(mTbsGatt).setInbandRingtoneFlag(mCurrentDevice);

        mTbsGeneric.clearInbandRingtoneSupport(mCurrentDevice);
        verify(mTbsGatt).clearInbandRingtoneFlag(mCurrentDevice);
    }

    @Test
    public void testAddBearer() {
        prepareTestBearer();

        verify(mTbsGatt).setBearerProviderName(eq("testProviderName"));
        verify(mTbsGatt).setBearerTechnology(eq(0x02));

        ArgumentCaptor<List> uriSchemesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mTbsGatt).setBearerUriSchemesSupportedList(uriSchemesCaptor.capture());
        List<String> capturedUriSchemes = uriSchemesCaptor.getValue();
        assertThat(capturedUriSchemes.contains("tel")).isTrue();
        assertThat(capturedUriSchemes.contains("xmpp")).isTrue();
    }

    @Test
    public void testRemoveBearer() {
        prepareTestBearer();
        reset(mTbsGatt);

        mTbsGeneric.removeBearer("testBearer");

        verify(mTbsGatt).setBearerProviderName(not(eq("testProviderName")));
        verify(mTbsGatt).setBearerTechnology(not(eq(0x02)));

        ArgumentCaptor<List> uriSchemesCaptor = ArgumentCaptor.forClass(List.class);
        verify(mTbsGatt).setBearerUriSchemesSupportedList(uriSchemesCaptor.capture());
        List<String> capturedUriSchemes = uriSchemesCaptor.getValue();
        assertThat(capturedUriSchemes.contains("tel")).isFalse();
        assertThat(capturedUriSchemes.contains("xmpp")).isFalse();
    }

    @Test
    public void testCallAdded() {
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        BluetoothLeCall tbsCall = new BluetoothLeCall(UUID.randomUUID(), "tel:987654321",
                "aFriendlyCaller", BluetoothLeCall.STATE_INCOMING, 0);
        mTbsGeneric.callAdded(ccid, tbsCall);

        ArgumentCaptor<Integer> callIndexCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mTbsGatt).setIncomingCall(callIndexCaptor.capture(), eq("tel:987654321"));
        Integer capturedCallIndex = callIndexCaptor.getValue();
        verify(mTbsGatt).setCallFriendlyName(eq(capturedCallIndex), eq("aFriendlyCaller"));
        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(1);
        TbsCall capturedTbsCall = capturedCurrentCalls.get(capturedCallIndex);
        assertThat(capturedTbsCall).isNotNull();
        assertThat(capturedTbsCall.getState()).isEqualTo(BluetoothLeCall.STATE_INCOMING);
        assertThat(capturedTbsCall.getUri()).isEqualTo("tel:987654321");
        assertThat(capturedTbsCall.getFlags()).isEqualTo(0);
        assertThat(capturedTbsCall.isIncoming()).isTrue();
        assertThat(capturedTbsCall.getFriendlyName()).isEqualTo("aFriendlyCaller");
    }

    @Test
    public void testCallRemoved() {
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        UUID callUuid = UUID.randomUUID();
        BluetoothLeCall tbsCall = new BluetoothLeCall(callUuid, "tel:987654321",
                "aFriendlyCaller", BluetoothLeCall.STATE_INCOMING, 0);

        mTbsGeneric.callAdded(ccid, tbsCall);
        ArgumentCaptor<Integer> callIndexCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mTbsGatt).setIncomingCall(callIndexCaptor.capture(), eq("tel:987654321"));
        Integer capturedCallIndex = callIndexCaptor.getValue();
        reset(mTbsGatt);

        doReturn(capturedCallIndex).when(mTbsGatt).getCallFriendlyNameIndex();
        doReturn(capturedCallIndex).when(mTbsGatt).getIncomingCallIndex();

        mTbsGeneric.callRemoved(ccid, callUuid, 0x01);
        verify(mTbsGatt).clearIncomingCall();
        verify(mTbsGatt).clearFriendlyName();
        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(0);
        verify(mTbsGatt).setBearerListCurrentCalls(currentCallsCaptor.capture());
        assertThat(capturedCurrentCalls.size()).isEqualTo(0);
    }

    @Test
    public void testCallStateChanged() {
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        UUID callUuid = UUID.randomUUID();
        BluetoothLeCall tbsCall = new BluetoothLeCall(callUuid, "tel:987654321",
                "aFriendlyCaller", BluetoothLeCall.STATE_INCOMING, 0);

        mTbsGeneric.callAdded(ccid, tbsCall);
        ArgumentCaptor<Integer> callIndexCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mTbsGatt).setIncomingCall(callIndexCaptor.capture(), eq("tel:987654321"));
        Integer capturedCallIndex = callIndexCaptor.getValue();
        reset(mTbsGatt);

        mTbsGeneric.callStateChanged(ccid, callUuid, BluetoothLeCall.STATE_ACTIVE);
        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(1);
        verify(mTbsGatt).setBearerListCurrentCalls(currentCallsCaptor.capture());
        assertThat(capturedCurrentCalls.size()).isEqualTo(1);
        TbsCall capturedTbsCall = capturedCurrentCalls.get(capturedCallIndex);
        assertThat(capturedTbsCall).isNotNull();
        assertThat(capturedTbsCall.getState()).isEqualTo(BluetoothLeCall.STATE_ACTIVE);
    }

    @Test
    public void testNetworkStateChanged() {
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        mTbsGeneric.networkStateChanged(ccid, "changed provider name", 0x01);
        verify(mTbsGatt).setBearerProviderName(eq("changed provider name"));
        verify(mTbsGatt).setBearerTechnology(eq(0x01));
    }

    @Test
    public void testCurrentCallsList() {
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        List<BluetoothLeCall> tbsCalls = new ArrayList<>();
        tbsCalls.add(new BluetoothLeCall(UUID.randomUUID(), "tel:987654321", "anIncomingCaller",
                BluetoothLeCall.STATE_INCOMING, 0));
        tbsCalls.add(new BluetoothLeCall(UUID.randomUUID(), "tel:123456789", "anOutgoingCaller",
                BluetoothLeCall.STATE_ALERTING, BluetoothLeCall.FLAG_OUTGOING_CALL));

        mTbsGeneric.currentCallsList(ccid, tbsCalls);
        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(2);
        verify(mTbsGatt).setBearerListCurrentCalls(currentCallsCaptor.capture());
        assertThat(capturedCurrentCalls.size()).isEqualTo(2);
    }

    @Test
    public void testCallAccept() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        LeAudioService leAudioService = mock(LeAudioService.class);
        mTbsGeneric.setLeAudioServiceForTesting(leAudioService);

        // Prepare the incoming call
        UUID callUuid = UUID.randomUUID();
        List<BluetoothLeCall> tbsCalls = new ArrayList<>();
        tbsCalls.add(new BluetoothLeCall(callUuid, "tel:987654321", "aFriendlyCaller",
                BluetoothLeCall.STATE_INCOMING, 0));
        mTbsGeneric.currentCallsList(ccid, tbsCalls);

        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(1);
        Integer callIndex = capturedCurrentCalls.entrySet().iterator().next().getKey();
        reset(mTbsGatt);

        byte args[] = new byte[1];
        args[0] = (byte) (callIndex & 0xFF);
        mTbsGattCallback.getValue().onCallControlPointRequest(mCurrentDevice,
                TbsGatt.CALL_CONTROL_POINT_OPCODE_ACCEPT, args);

        ArgumentCaptor<Integer> requestIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ParcelUuid> callUuidCaptor = ArgumentCaptor.forClass(ParcelUuid.class);
        try {
            verify(mIBluetoothLeCallControlCallback).onAcceptCall(requestIdCaptor.capture(),
                    callUuidCaptor.capture());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        assertThat(callUuidCaptor.getValue().getUuid()).isEqualTo(callUuid);
        // Active device should be changed
        verify(leAudioService).setActiveDevice(mCurrentDevice);

        // Respond with requestComplete...
        mTbsGeneric.requestResult(ccid, requestIdCaptor.getValue(), BluetoothLeCallControl.RESULT_SUCCESS);
        mTbsGeneric.callStateChanged(ccid, callUuid, BluetoothLeCall.STATE_ACTIVE);

        // ..and verify if GTBS control point is updated to notifier the peer about the result
        verify(mTbsGatt).setCallControlPointResult(eq(mCurrentDevice),
                eq(TbsGatt.CALL_CONTROL_POINT_OPCODE_ACCEPT), eq(callIndex),
                eq(BluetoothLeCallControl.RESULT_SUCCESS));
    }

    @Test
    public void testCallTerminate() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        // Prepare the incoming call
        UUID callUuid = UUID.randomUUID();
        List<BluetoothLeCall> tbsCalls = new ArrayList<>();
        tbsCalls.add(new BluetoothLeCall(callUuid, "tel:987654321", "aFriendlyCaller",
                BluetoothLeCall.STATE_ACTIVE, 0));
        mTbsGeneric.currentCallsList(ccid, tbsCalls);

        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(1);
        Integer callIndex = capturedCurrentCalls.entrySet().iterator().next().getKey();
        reset(mTbsGatt);

        byte args[] = new byte[1];
        args[0] = (byte) (callIndex & 0xFF);
        mTbsGattCallback.getValue().onCallControlPointRequest(mCurrentDevice,
                TbsGatt.CALL_CONTROL_POINT_OPCODE_TERMINATE, args);

        ArgumentCaptor<Integer> requestIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ParcelUuid> callUuidCaptor = ArgumentCaptor.forClass(ParcelUuid.class);
        try {
            verify(mIBluetoothLeCallControlCallback).onTerminateCall(requestIdCaptor.capture(),
                    callUuidCaptor.capture());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        assertThat(callUuidCaptor.getValue().getUuid()).isEqualTo(callUuid);

        // Respond with requestComplete...
        mTbsGeneric.requestResult(ccid, requestIdCaptor.getValue(), BluetoothLeCallControl.RESULT_SUCCESS);
        mTbsGeneric.callRemoved(ccid, callUuid, 0x01);

        // ..and verify if GTBS control point is updated to notifier the peer about the result
        verify(mTbsGatt).setCallControlPointResult(eq(mCurrentDevice),
                eq(TbsGatt.CALL_CONTROL_POINT_OPCODE_TERMINATE), eq(callIndex),
                eq(BluetoothLeCallControl.RESULT_SUCCESS));
    }

    @Test
    public void testCallHold() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        // Prepare the incoming call
        UUID callUuid = UUID.randomUUID();
        List<BluetoothLeCall> tbsCalls = new ArrayList<>();
        tbsCalls.add(new BluetoothLeCall(callUuid, "tel:987654321", "aFriendlyCaller",
                BluetoothLeCall.STATE_ACTIVE, 0));
        mTbsGeneric.currentCallsList(ccid, tbsCalls);

        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(1);
        Integer callIndex = capturedCurrentCalls.entrySet().iterator().next().getKey();
        reset(mTbsGatt);

        byte args[] = new byte[1];
        args[0] = (byte) (callIndex & 0xFF);
        mTbsGattCallback.getValue().onCallControlPointRequest(mCurrentDevice,
                TbsGatt.CALL_CONTROL_POINT_OPCODE_LOCAL_HOLD, args);

        ArgumentCaptor<Integer> requestIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ParcelUuid> callUuidCaptor = ArgumentCaptor.forClass(ParcelUuid.class);
        try {
            verify(mIBluetoothLeCallControlCallback).onHoldCall(requestIdCaptor.capture(),
                    callUuidCaptor.capture());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        assertThat(callUuidCaptor.getValue().getUuid()).isEqualTo(callUuid);

        // Respond with requestComplete...
        mTbsGeneric.requestResult(ccid, requestIdCaptor.getValue(), BluetoothLeCallControl.RESULT_SUCCESS);
        mTbsGeneric.callStateChanged(ccid, callUuid, BluetoothLeCall.STATE_LOCALLY_HELD);

        // ..and verify if GTBS control point is updated to notifier the peer about the result
        verify(mTbsGatt).setCallControlPointResult(eq(mCurrentDevice),
                eq(TbsGatt.CALL_CONTROL_POINT_OPCODE_LOCAL_HOLD), eq(callIndex),
                eq(BluetoothLeCallControl.RESULT_SUCCESS));
    }

    @Test
    public void testCallRetrieve() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        // Prepare the incoming call
        UUID callUuid = UUID.randomUUID();
        List<BluetoothLeCall> tbsCalls = new ArrayList<>();
        tbsCalls.add(new BluetoothLeCall(callUuid, "tel:987654321", "aFriendlyCaller",
                BluetoothLeCall.STATE_LOCALLY_HELD, 0));
        mTbsGeneric.currentCallsList(ccid, tbsCalls);

        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(1);
        Integer callIndex = capturedCurrentCalls.entrySet().iterator().next().getKey();
        reset(mTbsGatt);

        byte args[] = new byte[1];
        args[0] = (byte) (callIndex & 0xFF);
        mTbsGattCallback.getValue().onCallControlPointRequest(mCurrentDevice,
                TbsGatt.CALL_CONTROL_POINT_OPCODE_LOCAL_RETRIEVE, args);

        ArgumentCaptor<Integer> requestIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ParcelUuid> callUuidCaptor = ArgumentCaptor.forClass(ParcelUuid.class);
        try {
            verify(mIBluetoothLeCallControlCallback).onUnholdCall(requestIdCaptor.capture(),
                    callUuidCaptor.capture());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        assertThat(callUuidCaptor.getValue().getUuid()).isEqualTo(callUuid);

        // Respond with requestComplete...
        mTbsGeneric.requestResult(ccid, requestIdCaptor.getValue(), BluetoothLeCallControl.RESULT_SUCCESS);
        mTbsGeneric.callStateChanged(ccid, callUuid, BluetoothLeCall.STATE_ACTIVE);

        // ..and verify if GTBS control point is updated to notifier the peer about the result
        verify(mTbsGatt).setCallControlPointResult(eq(mCurrentDevice),
                eq(TbsGatt.CALL_CONTROL_POINT_OPCODE_LOCAL_RETRIEVE), eq(callIndex),
                eq(BluetoothLeCallControl.RESULT_SUCCESS));
    }

    @Test
    public void testCallOriginate() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        LeAudioService leAudioService = mock(LeAudioService.class);
        mTbsGeneric.setLeAudioServiceForTesting(leAudioService);

        // Act as if peer originates a call via Gtbs
        String uri = "xmpp:123456789";
        mTbsGattCallback.getValue().onCallControlPointRequest(mCurrentDevice,
                TbsGatt.CALL_CONTROL_POINT_OPCODE_ORIGINATE, uri.getBytes());

        ArgumentCaptor<Integer> requestIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<ParcelUuid> callUuidCaptor = ArgumentCaptor.forClass(ParcelUuid.class);
        try {
            verify(mIBluetoothLeCallControlCallback).onPlaceCall(requestIdCaptor.capture(),
                    callUuidCaptor.capture(), eq(uri));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        // Active device should be changed
        verify(leAudioService).setActiveDevice(mCurrentDevice);

        // Respond with requestComplete...
        mTbsGeneric.requestResult(ccid, requestIdCaptor.getValue(), BluetoothLeCallControl.RESULT_SUCCESS);
        mTbsGeneric.callAdded(ccid,
                new BluetoothLeCall(callUuidCaptor.getValue().getUuid(), uri, "anOutgoingCaller",
                        BluetoothLeCall.STATE_ALERTING, BluetoothLeCall.FLAG_OUTGOING_CALL));

        // ..and verify if GTBS control point is updated to notifier the peer about the result
        verify(mTbsGatt).setCallControlPointResult(eq(mCurrentDevice),
                eq(TbsGatt.CALL_CONTROL_POINT_OPCODE_ORIGINATE), anyInt(),
                eq(BluetoothLeCallControl.RESULT_SUCCESS));
    }

    @Test
    public void testCallJoin() {
        mCurrentDevice = TestUtils.getTestDevice(mAdapter, 0);
        Integer ccid = prepareTestBearer();
        reset(mTbsGatt);

        // Prepare the incoming call
        List<UUID> callUuids = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        List<BluetoothLeCall> tbsCalls = new ArrayList<>();
        tbsCalls.add(new BluetoothLeCall(callUuids.get(0), "tel:987654321", "aFriendlyCaller",
                BluetoothLeCall.STATE_LOCALLY_HELD, 0));
        tbsCalls.add(new BluetoothLeCall(callUuids.get(1), "tel:123456789", "a2ndFriendlyCaller",
                BluetoothLeCall.STATE_ACTIVE, 0));
        mTbsGeneric.currentCallsList(ccid, tbsCalls);

        ArgumentCaptor<Map> currentCallsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mTbsGatt).setCallState(currentCallsCaptor.capture());
        Map<Integer, TbsCall> capturedCurrentCalls = currentCallsCaptor.getValue();
        assertThat(capturedCurrentCalls.size()).isEqualTo(2);
        reset(mTbsGatt);

        byte args[] = new byte[capturedCurrentCalls.size()];
        int i = 0;
        for (Integer callIndex : capturedCurrentCalls.keySet()) {
            args[i++] = (byte) (callIndex & 0xFF);
        }
        mTbsGattCallback.getValue().onCallControlPointRequest(mCurrentDevice,
                TbsGatt.CALL_CONTROL_POINT_OPCODE_JOIN, args);

        ArgumentCaptor<Integer> requestIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<List<ParcelUuid>> callUuidCaptor = ArgumentCaptor.forClass(List.class);
        try {
            verify(mIBluetoothLeCallControlCallback).onJoinCalls(requestIdCaptor.capture(),
                    callUuidCaptor.capture());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        List<ParcelUuid> callParcelUuids = callUuidCaptor.getValue();
        assertThat(callParcelUuids.size()).isEqualTo(2);
        for (ParcelUuid callParcelUuid : callParcelUuids) {
            assertThat(callUuids.contains(callParcelUuid.getUuid())).isEqualTo(true);
        }

        // // Respond with requestComplete...
        mTbsGeneric.requestResult(ccid, requestIdCaptor.getValue(), BluetoothLeCallControl.RESULT_SUCCESS);
        mTbsGeneric.callStateChanged(ccid, callUuids.get(0), BluetoothLeCall.STATE_ACTIVE);

        // ..and verify if GTBS control point is updated to notifier the peer about the result
        verify(mTbsGatt).setCallControlPointResult(eq(mCurrentDevice),
                eq(TbsGatt.CALL_CONTROL_POINT_OPCODE_JOIN), anyInt(),
                eq(BluetoothLeCallControl.RESULT_SUCCESS));
    }
}
