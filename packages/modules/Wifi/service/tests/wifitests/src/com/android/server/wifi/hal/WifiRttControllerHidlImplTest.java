/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wifi.hal;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiRttControllerEventCallback;
import android.hardware.wifi.V1_0.RttBw;
import android.hardware.wifi.V1_0.RttCapabilities;
import android.hardware.wifi.V1_0.RttConfig;
import android.hardware.wifi.V1_0.RttPeerType;
import android.hardware.wifi.V1_0.RttPreamble;
import android.hardware.wifi.V1_0.RttResult;
import android.hardware.wifi.V1_0.RttStatus;
import android.hardware.wifi.V1_0.RttType;
import android.hardware.wifi.V1_0.WifiChannelWidthInMhz;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.rtt.RttTestUtils;

import org.hamcrest.core.IsNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class WifiRttControllerHidlImplTest extends WifiBaseTest {
    private WifiRttController mDut;
    private WifiStatus mStatusSuccess;

    private ArgumentCaptor<ArrayList> mRttConfigCaptor = ArgumentCaptor.forClass(ArrayList.class);
    private ArgumentCaptor<ArrayList> mRttResultCaptor = ArgumentCaptor.forClass(ArrayList.class);
    private ArgumentCaptor<IWifiRttController.getCapabilitiesCallback> mGetCapCbCatpr =
            ArgumentCaptor.forClass(IWifiRttController.getCapabilitiesCallback.class);
    private ArgumentCaptor<IWifiRttControllerEventCallback.Stub> mEventCallbackArgumentCaptor =
            ArgumentCaptor.forClass(IWifiRttControllerEventCallback.Stub.class);
    private ArgumentCaptor<ArrayList> mArrayListCaptor = ArgumentCaptor.forClass(ArrayList.class);

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    @Mock
    public IWifiRttController mockRttController;

    @Mock
    public WifiRttController.RttControllerRangingResultsCallback mockRangingResultsCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStatusSuccess = new WifiStatus();
        mStatusSuccess.code = WifiStatusCode.SUCCESS;

        when(mockRttController.registerEventCallback(mEventCallbackArgumentCaptor.capture()))
                .thenReturn(mStatusSuccess);
        when(mockRttController.rangeRequest(anyInt(), any(ArrayList.class))).thenReturn(
                mStatusSuccess);
        when(mockRttController.rangeCancel(anyInt(), any(ArrayList.class))).thenReturn(
                mStatusSuccess);

        mDut = new WifiRttController(mockRttController);
        mDut.setup();
        mDut.registerRangingResultsCallback(mockRangingResultsCallback);
        verify(mockRttController).getCapabilities(mGetCapCbCatpr.capture());
        verify(mockRttController).registerEventCallback(any());
        // will override capabilities (just call cb again) for specific tests
        mGetCapCbCatpr.getValue().onValues(mStatusSuccess, getFullRttCapabilities());
        // This is for the castFrom() call
        verify(mockRttController, times(2)).asBinder();
    }

    /**
     * Validate successful ranging flow.
     */
    @Test
    public void testRangeRequest() throws Exception {
        int cmdId = 55;
        RangingRequest request = RttTestUtils.getDummyRangingRequest((byte) 0);

        // (1) issue range request
        mDut.rangeRequest(cmdId, request);

        // (2) verify HAL call and parameters
        verify(mockRttController).rangeRequest(eq(cmdId), mRttConfigCaptor.capture());

        // verify contents of HAL request (hard codes knowledge from getDummyRangingRequest()).
        ArrayList<RttConfig> halRequest = mRttConfigCaptor.getValue();

        collector.checkThat("number of entries", halRequest.size(),
                equalTo(request.mRttPeers.size()));

        RttConfig rttConfig = halRequest.get(0);
        collector.checkThat("entry 0: MAC", rttConfig.addr,
                equalTo(MacAddress.fromString("00:01:02:03:04:00").toByteArray()));
        collector.checkThat("entry 0: rtt type", rttConfig.type, equalTo(RttType.TWO_SIDED));
        collector.checkThat("entry 0: peer type", rttConfig.peer, equalTo(RttPeerType.AP));
        collector.checkThat("entry 0: lci", rttConfig.mustRequestLci, equalTo(true));
        collector.checkThat("entry 0: lcr", rttConfig.mustRequestLcr, equalTo(true));
        collector.checkThat("entry 0: rtt burst size", rttConfig.numFramesPerBurst,
                equalTo(RangingRequest.getMaxRttBurstSize()));

        rttConfig = halRequest.get(1);
        collector.checkThat("entry 1: MAC", rttConfig.addr,
                equalTo(MacAddress.fromString("0A:0B:0C:0D:0E:00").toByteArray()));
        collector.checkThat("entry 1: rtt type", rttConfig.type, equalTo(RttType.ONE_SIDED));
        collector.checkThat("entry 1: peer type", rttConfig.peer, equalTo(RttPeerType.AP));
        collector.checkThat("entry 1: lci", rttConfig.mustRequestLci, equalTo(true));
        collector.checkThat("entry 1: lcr", rttConfig.mustRequestLcr, equalTo(true));
        collector.checkThat("entry 1: rtt burst size", rttConfig.numFramesPerBurst,
                equalTo(RangingRequest.getMaxRttBurstSize()));

        rttConfig = halRequest.get(2);
        collector.checkThat("entry 2: MAC", rttConfig.addr,
                equalTo(MacAddress.fromString("08:09:08:07:06:05").toByteArray()));
        collector.checkThat("entry 2: rtt type", rttConfig.type, equalTo(RttType.TWO_SIDED));
        collector.checkThat("entry 2: peer type", rttConfig.peer, equalTo(RttPeerType.NAN));
        collector.checkThat("entry 2: lci", rttConfig.mustRequestLci, equalTo(false));
        collector.checkThat("entry 2: lcr", rttConfig.mustRequestLcr, equalTo(false));
        collector.checkThat("entry 2: rtt burst size", rttConfig.numFramesPerBurst,
                equalTo(RangingRequest.getMaxRttBurstSize()));

        verifyNoMoreInteractions(mockRttController);
    }

    /**
     * Validate successful ranging flow - with privileges access but with limited capabilities:
     * - No single-sided RTT
     * - No LCI/LCR
     * - Limited BW
     * - Limited Preamble
     */
    @Test
    public void testRangeRequestWithLimitedCapabilities() throws Exception {
        int cmdId = 55;
        RangingRequest request = RttTestUtils.getDummyRangingRequest((byte) 0);

        // update capabilities to a limited set
        RttCapabilities cap = getFullRttCapabilities();
        cap.rttOneSidedSupported = false;
        cap.lciSupported = false;
        cap.lcrSupported = false;
        cap.bwSupport = RttBw.BW_10MHZ | RttBw.BW_160MHZ;
        cap.preambleSupport = RttPreamble.LEGACY;
        mGetCapCbCatpr.getValue().onValues(mStatusSuccess, cap);

        // Note: request 1: BW = 40MHz --> 10MHz, Preamble = HT (since 40MHz) -> Legacy

        // (1) issue range request
        mDut.rangeRequest(cmdId, request);

        // (2) verify HAL call and parameters
        verify(mockRttController).rangeRequest(eq(cmdId), mRttConfigCaptor.capture());

        // verify contents of HAL request (hard codes knowledge from getDummyRangingRequest()).
        ArrayList<RttConfig> halRequest = mRttConfigCaptor.getValue();

        assertEquals("number of entries", halRequest.size(), 2);

        RttConfig rttConfig = halRequest.get(0);
        collector.checkThat("entry 0: MAC", rttConfig.addr,
                equalTo(MacAddress.fromString("00:01:02:03:04:00").toByteArray()));
        collector.checkThat("entry 0: rtt type", rttConfig.type, equalTo(RttType.TWO_SIDED));
        collector.checkThat("entry 0: peer type", rttConfig.peer, equalTo(RttPeerType.AP));
        collector.checkThat("entry 0: lci", rttConfig.mustRequestLci, equalTo(false));
        collector.checkThat("entry 0: lcr", rttConfig.mustRequestLcr, equalTo(false));
        collector.checkThat("entry 0: channel.width", rttConfig.channel.width, equalTo(
                WifiChannelWidthInMhz.WIDTH_40));
        collector.checkThat("entry 0: bw", rttConfig.bw, equalTo(RttBw.BW_10MHZ));
        collector.checkThat("entry 0: preamble", rttConfig.preamble, equalTo(RttPreamble.LEGACY));

        rttConfig = halRequest.get(1);
        collector.checkThat("entry 1: MAC", rttConfig.addr,
                equalTo(MacAddress.fromString("08:09:08:07:06:05").toByteArray()));
        collector.checkThat("entry 1: rtt type", rttConfig.type, equalTo(RttType.TWO_SIDED));
        collector.checkThat("entry 1: peer type", rttConfig.peer, equalTo(RttPeerType.NAN));
        collector.checkThat("entry 1: lci", rttConfig.mustRequestLci, equalTo(false));
        collector.checkThat("entry 1: lcr", rttConfig.mustRequestLcr, equalTo(false));

        verifyNoMoreInteractions(mockRttController);
    }

    /**
     * Validate successful ranging flow - with privileges access but with limited capabilities:
     * - Very limited BW
     * - Very limited Preamble
     */
    @Test
    public void testRangeRequestWithLimitedCapabilitiesNoOverlap() throws Exception {
        int cmdId = 55;
        RangingRequest request = RttTestUtils.getDummyRangingRequest((byte) 0);

        // update capabilities to a limited set
        RttCapabilities cap = getFullRttCapabilities();
        cap.bwSupport = RttBw.BW_80MHZ;
        cap.preambleSupport = RttPreamble.VHT;
        mGetCapCbCatpr.getValue().onValues(mStatusSuccess, cap);

        // Note: request 1: BW = 40MHz --> no overlap -> dropped
        // Note: request 2: BW = 160MHz --> 160MHz, preamble = VHT (since 160MHz) -> no overlap,
        //                                                                           dropped

        // (1) issue range request
        mDut.rangeRequest(cmdId, request);

        // (2) verify HAL call and parameters
        verify(mockRttController).rangeRequest(eq(cmdId), mRttConfigCaptor.capture());

        // verify contents of HAL request (hard codes knowledge from getDummyRangingRequest()).
        ArrayList<RttConfig> halRequest = mRttConfigCaptor.getValue();

        collector.checkThat("number of entries", halRequest.size(), equalTo(1));

        RttConfig rttConfig = halRequest.get(0);
        collector.checkThat("entry 0: MAC", rttConfig.addr,
                equalTo(MacAddress.fromString("08:09:08:07:06:05").toByteArray()));
        collector.checkThat("entry 0: rtt type", rttConfig.type, equalTo(RttType.TWO_SIDED));
        collector.checkThat("entry 0: peer type", rttConfig.peer, equalTo(RttPeerType.NAN));
        collector.checkThat("entry 0: lci", rttConfig.mustRequestLci, equalTo(false));
        collector.checkThat("entry 0: lcr", rttConfig.mustRequestLcr, equalTo(false));

        verifyNoMoreInteractions(mockRttController);
    }

    /**
     * Validate ranging cancel flow.
     */
    @Test
    public void testRangeCancel() throws Exception {
        int cmdId = 66;
        ArrayList<MacAddress> macAddresses = new ArrayList<>();
        MacAddress mac1 = MacAddress.fromString("00:01:02:03:04:05");
        MacAddress mac2 = MacAddress.fromString("0A:0B:0C:0D:0E:0F");
        macAddresses.add(mac1);
        macAddresses.add(mac2);

        // (1) issue cancel request
        mDut.rangeCancel(cmdId, macAddresses);

        // (2) verify HAL call and parameters
        verify(mockRttController).rangeCancel(eq(cmdId), mArrayListCaptor.capture());
        assertArrayEquals(mac1.toByteArray(), (byte[]) mArrayListCaptor.getValue().get(0));
        assertArrayEquals(mac2.toByteArray(), (byte[]) mArrayListCaptor.getValue().get(1));

        verifyNoMoreInteractions(mockRttController);
    }

    /**
     * Validate correct result conversion from HAL to framework.
     */
    @Test
    public void testRangeResults() throws Exception {
        int cmdId = 55;
        ArrayList<RttResult> results = new ArrayList<>();
        RttResult res = new RttResult();
        res.addr[0] = 5;
        res.addr[1] = 6;
        res.addr[2] = 7;
        res.addr[3] = 8;
        res.addr[4] = 9;
        res.addr[5] = 10;
        res.status = RttStatus.SUCCESS;
        res.distanceInMm = 1500;
        res.timeStampInUs = 6000;
        results.add(res);

        // (1) have the HAL call us with results
        mEventCallbackArgumentCaptor.getValue().onResults(cmdId, results);

        // (2) verify call to framework
        verify(mockRangingResultsCallback).onRangingResults(eq(cmdId), mRttResultCaptor.capture());

        // verify contents of the framework results
        List<RangingResult> rttR = mRttResultCaptor.getValue();

        collector.checkThat("number of entries", rttR.size(), equalTo(1));

        RangingResult rttResult = rttR.get(0);
        collector.checkThat("status", rttResult.getStatus(),
                equalTo(WifiRttController.FRAMEWORK_RTT_STATUS_SUCCESS));
        collector.checkThat("mac", rttResult.getMacAddress().toByteArray(),
                equalTo(MacAddress.fromString("05:06:07:08:09:0A").toByteArray()));
        collector.checkThat("distanceCm", rttResult.getDistanceMm(), equalTo(1500));
        collector.checkThat("timestamp", rttResult.getRangingTimestampMillis(), equalTo(6L));

        verifyNoMoreInteractions(mockRttController);
    }

    /**
     * Validate correct cleanup when a null array of results is provided by HAL.
     */
    @Test
    public void testRangeResultsNullArray() throws Exception {
        int cmdId = 66;

        mEventCallbackArgumentCaptor.getValue().onResults(cmdId, null);
        verify(mockRangingResultsCallback).onRangingResults(eq(cmdId), mRttResultCaptor.capture());

        collector.checkThat("number of entries", mRttResultCaptor.getValue().size(), equalTo(0));
    }

    /**
     * Validate correct cleanup when an array of results containing null entries is provided by HAL.
     */
    @Test
    public void testRangeResultsSomeNulls() throws Exception {
        int cmdId = 77;

        ArrayList<RttResult> results = new ArrayList<>();
        results.add(null);
        results.add(new RttResult());
        results.add(null);
        results.add(null);
        results.add(new RttResult());
        results.add(null);

        mEventCallbackArgumentCaptor.getValue().onResults(cmdId, results);
        verify(mockRangingResultsCallback).onRangingResults(eq(cmdId), mRttResultCaptor.capture());

        List<RttResult> rttR = mRttResultCaptor.getValue();
        collector.checkThat("number of entries", rttR.size(), equalTo(2));
        for (int i = 0; i < rttR.size(); ++i) {
            collector.checkThat("entry", rttR.get(i), IsNull.notNullValue());
        }
    }

    /**
     * Validation ranging with invalid bw and preamble combination will be ignored.
     */
    @Test
    public void testRangingWithInvalidParameterCombination() throws Exception {
        int cmdId = 88;
        RangingRequest request = new RangingRequest.Builder().build();
        ResponderConfig invalidConfig = new ResponderConfig.Builder()
                .setMacAddress(MacAddress.fromString("08:09:08:07:06:88"))
                .setResponderType(ResponderConfig.RESPONDER_AP)
                .set80211mcSupported(true)
                .setChannelWidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                .setPreamble(ScanResult.PREAMBLE_HT)
                .build();
        ResponderConfig config = new ResponderConfig.Builder()
                .setMacAddress(MacAddress.fromString("08:09:08:07:06:89"))
                .setResponderType(ResponderConfig.RESPONDER_AP)
                .set80211mcSupported(true)
                .setChannelWidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                .setPreamble(ScanResult.PREAMBLE_VHT)
                .build();

        // Add a ResponderConfig with invalid parameter, should be ignored.
        request.mRttPeers.add(invalidConfig);
        request.mRttPeers.add(config);
        mDut.rangeRequest(cmdId, request);
        verify(mockRttController).rangeRequest(eq(cmdId), mRttConfigCaptor.capture());
        assertEquals(request.mRttPeers.size() - 1, mRttConfigCaptor.getValue().size());
    }


    // Utilities

    /**
     * Return an RttCapabilities structure with all features enabled and support for all
     * preambles and bandwidths. The purpose is to enable any request. The returned structure can
     * then be modified to disable specific features.
     */
    RttCapabilities getFullRttCapabilities() {
        RttCapabilities cap = new RttCapabilities();

        cap.rttOneSidedSupported = true;
        cap.rttFtmSupported = true;
        cap.lciSupported = true;
        cap.lcrSupported = true;
        cap.responderSupported = true; // unused
        cap.preambleSupport = RttPreamble.LEGACY | RttPreamble.HT | RttPreamble.VHT
                | android.hardware.wifi.V1_4.RttPreamble.HE;
        cap.bwSupport =
                RttBw.BW_5MHZ | RttBw.BW_10MHZ | RttBw.BW_20MHZ | RttBw.BW_40MHZ | RttBw.BW_80MHZ
                        | RttBw.BW_160MHZ;
        cap.mcVersion = 1; // unused

        return cap;
    }
}
