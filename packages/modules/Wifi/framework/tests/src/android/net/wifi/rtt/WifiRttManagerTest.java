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

package android.net.wifi.rtt;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.OuiKeyedDataUtil;
import android.net.wifi.ScanResult;
import android.net.wifi.aware.PeerHandle;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Unit test harness for WifiRttManager class.
 */
@SmallTest
public class WifiRttManagerTest {
    private WifiRttManager mDut;
    private TestLooper mMockLooper;
    private Executor mMockLooperExecutor;

    private final String packageName = "some.package.name.for.rtt.app";
    private final String featureId = "some.feature.id.in.rtt.app";

    @Mock
    public Context mockContext;

    @Mock
    public IWifiRttManager mockRttService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDut = new WifiRttManager(mockContext, mockRttService);
        mMockLooper = new TestLooper();
        mMockLooperExecutor = mMockLooper.getNewExecutor();

        when(mockContext.getOpPackageName()).thenReturn(packageName);
        when(mockContext.getAttributionTag()).thenReturn(featureId);
    }

    /**
     * Validate ranging call flow with successful results.
     */
    @Test
    public void testRangeSuccess() throws Exception {
        RangingRequest request = new RangingRequest.Builder().build();
        List<RangingResult> results = new ArrayList<>();
        results.add(new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setMacAddress(MacAddress.BROADCAST_ADDRESS)
                .setDistanceMm(15)
                .setDistanceStdDevMm(5)
                .setRssi(10)
                .setNumAttemptedMeasurements(8)
                .setNumSuccessfulMeasurements(5)
                .setRangingTimestampMillis(666)
                .set80211mcMeasurement(true)
                .build());
        RangingResultCallback callbackMock = mock(RangingResultCallback.class);
        ArgumentCaptor<IRttCallback> callbackCaptor = ArgumentCaptor.forClass(IRttCallback.class);

        // verify ranging request passed to service
        mDut.startRanging(request, mMockLooperExecutor, callbackMock);
        verify(mockRttService).startRanging(any(IBinder.class), eq(packageName), eq(featureId),
                eq(null),  eq(request), callbackCaptor.capture(), any(Bundle.class));

        // service calls back with success
        callbackCaptor.getValue().onRangingResults(results);
        mMockLooper.dispatchAll();
        verify(callbackMock).onRangingResults(results);

        verifyNoMoreInteractions(mockRttService, callbackMock);
    }

    /**
     * Validate ranging call flow which failed.
     */
    @Test
    public void testRangeFail() throws Exception {
        int failureCode = RangingResultCallback.STATUS_CODE_FAIL;

        RangingRequest request = new RangingRequest.Builder().build();
        RangingResultCallback callbackMock = mock(RangingResultCallback.class);
        ArgumentCaptor<IRttCallback> callbackCaptor = ArgumentCaptor.forClass(IRttCallback.class);

        // verify ranging request passed to service
        mDut.startRanging(request, mMockLooperExecutor, callbackMock);
        verify(mockRttService).startRanging(any(IBinder.class), eq(packageName), eq(featureId),
                eq(null), eq(request), callbackCaptor.capture(), any(Bundle.class));

        // service calls back with failure code
        callbackCaptor.getValue().onRangingFailure(failureCode);
        mMockLooper.dispatchAll();
        verify(callbackMock).onRangingFailure(failureCode);

        verifyNoMoreInteractions(mockRttService, callbackMock);
    }

    /**
     * Validate that RangingRequest parcel works (produces same object on write/read).
     */
    @Test
    public void testRangingRequestParcel() {
        // Note: not validating parcel code of ScanResult (assumed to work)
        ScanResult scanResult1 = new ScanResult();
        scanResult1.BSSID = "00:01:02:03:04:05";
        scanResult1.setFlag(ScanResult.FLAG_80211mc_RESPONDER);
        ScanResult scanResult2 = new ScanResult();
        scanResult2.BSSID = "06:07:08:09:0A:0B";
        scanResult2.setFlag(ScanResult.FLAG_80211mc_RESPONDER);
        ScanResult scanResult3 = new ScanResult();
        scanResult3.BSSID = "AA:BB:CC:DD:EE:FF";
        scanResult3.setFlag(ScanResult.FLAG_80211mc_RESPONDER);
        List<ScanResult> scanResults2and3 = new ArrayList<>(2);
        scanResults2and3.add(scanResult2);
        scanResults2and3.add(scanResult3);
        MacAddress mac1 = MacAddress.fromString("00:01:02:03:04:05");
        PeerHandle peerHandle1 = new PeerHandle(12);

        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(scanResult1);
        builder.addAccessPoints(scanResults2and3);
        builder.addWifiAwarePeer(mac1);
        builder.addWifiAwarePeer(peerHandle1);
        builder.setRttBurstSize(4);
        if (SdkLevel.isAtLeastV()) {
            builder.setVendorData(OuiKeyedDataUtil.createTestOuiKeyedDataList(5));
        }
        RangingRequest request = builder.build();

        Parcel parcelW = Parcel.obtain();
        request.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        RangingRequest rereadRequest = RangingRequest.CREATOR.createFromParcel(parcelR);

        assertEquals(request, rereadRequest);
    }

    /**
     * Validate 80211mc APs cannot be used in Non-80211mc ranging request builders.
     */
    @Test
    public void test802llmcCapableAccessPointFailsForNon11mcBuilderMethods() {
        ScanResult scanResult1 = new ScanResult();
        scanResult1.BSSID = "AA:BB:CC:DD:EE:FF";
        scanResult1.setFlag(ScanResult.FLAG_80211mc_RESPONDER);

        // create request for one AP
        try {
            RangingRequest.Builder builder = new RangingRequest.Builder();
            builder.addNon80211mcCapableAccessPoint(scanResult1);
            fail("Single Access Point was 11mc capable.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        ScanResult scanResult2 = new ScanResult();
        scanResult2.BSSID = "11:BB:CC:DD:EE:FF";
        scanResult2.setFlag(ScanResult.FLAG_80211mc_RESPONDER);
        List<ScanResult> scanResults = new ArrayList<>();
        scanResults.add(scanResult1);
        scanResults.add(scanResult2);

        // create request for a list of 2 APs.
        try {
            RangingRequest.Builder builder = new RangingRequest.Builder();
            builder.addNon80211mcCapableAccessPoints(scanResults);
            fail("One Access Point in the List was 11mc capable.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Validate the rtt burst size is set correctly when in range.
     */
    @Test
    public void testRangingRequestSetBurstSize() {
        ScanResult scanResult = new ScanResult();
        scanResult.BSSID = "AA:BB:CC:DD:EE:FF";
        scanResult.setFlag(ScanResult.FLAG_80211mc_RESPONDER);

        // create request
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.setRttBurstSize(4);
        builder.addAccessPoint(scanResult);
        RangingRequest request = builder.build();

        // confirm rtt burst size is set correctly to default value
        assertEquals(request.getRttBurstSize(), 4);
    }

    /**
     * Validate the rtt burst size cannot be smaller than the minimum.
     */
    @Test
    public void testRangingRequestMinBurstSizeIsEnforced() {
        ScanResult scanResult = new ScanResult();
        scanResult.BSSID = "AA:BB:CC:DD:EE:FF";

        // create request
        try {
            RangingRequest.Builder builder = new RangingRequest.Builder();
            builder.setRttBurstSize(RangingRequest.getMinRttBurstSize() - 1);
            fail("RTT burst size was smaller than min value.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Validate the rtt burst size cannot exceed the maximum.
     */
    @Test
    public void testRangingRequestMaxBurstSizeIsEnforced() {
        ScanResult scanResult = new ScanResult();
        scanResult.BSSID = "AA:BB:CC:DD:EE:FF";

        // create request
        try {
            RangingRequest.Builder builder = new RangingRequest.Builder();
            builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize() + 1);
            fail("RTT Burst size exceeded max value.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Validate that can request as many range operation as the upper limit on number of requests.
     * Testing all methods to add 80211mc capable responders.
     */
    @Test
    public void testRangingRequestAtLimit() {
        ScanResult scanResult1 = new ScanResult();
        scanResult1.BSSID = "AA:BB:CC:DD:EE:FF";
        scanResult1.setFlag(ScanResult.FLAG_80211mc_RESPONDER);

        List<ScanResult> scanResultList = new ArrayList<>();
        for (int i = 0; i < RangingRequest.getMaxPeers() - 5; ++i) {
            scanResultList.add(scanResult1);
        }

        ScanResult scanResult2 = new ScanResult();
        scanResult2.BSSID = "11:22:33:44:55:66";
        scanResult2.setFlag(ScanResult.FLAG_80211mc_RESPONDER);

        ResponderConfig responderConfig2a = ResponderConfig.fromScanResult(scanResult2);
        int preamble = responderConfig2a.getPreamble();

        ResponderConfig.Builder responderBuilder = new ResponderConfig.Builder();
        ResponderConfig responderConfig2b = responderBuilder
                .setMacAddress(MacAddress.fromString(scanResult2.BSSID))
                .set80211mcSupported(scanResult2.is80211mcResponder())
                .setChannelWidth(scanResult2.channelWidth)
                .setFrequencyMhz(scanResult2.frequency)
                .setCenterFreq0Mhz(scanResult2.centerFreq0)
                .setCenterFreq1Mhz(scanResult2.centerFreq1)
                .setPreamble(preamble)
                .build();

        // Validate ResponderConfig.Builder setter method arguments match getter method results.
        assertTrue(responderConfig2a.getMacAddress().toString().equalsIgnoreCase(scanResult2.BSSID)
                && responderConfig2a.is80211mcSupported() == scanResult2.is80211mcResponder()
                && responderConfig2a.getChannelWidth() == scanResult2.channelWidth
                && responderConfig2a.getFrequencyMhz() == scanResult2.frequency
                && responderConfig2a.getCenterFreq0Mhz() == scanResult2.centerFreq0
                && responderConfig2a.getCenterFreq1Mhz() == scanResult2.centerFreq1
                && responderConfig2a.getPreamble() == preamble);

        ArrayList<ResponderConfig> responderList = new ArrayList<>();
        responderList.add(responderConfig2b);

        MacAddress mac1 = MacAddress.fromString("00:01:02:03:04:05");

        // create request using max RTT Peers
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(scanResult1);        // Add 1
        builder.addAccessPoints(scanResultList);    // Add MaxPeers - 5
        builder.addResponder(responderConfig2a);    // Add 1
        builder.addResponders(responderList);       // Add 1
        builder.addAccessPoint(scanResult2);        // Add 1
        builder.addWifiAwarePeer(mac1);             // Add 1
        RangingRequest request = builder.build();

        // verify request
        request.enforceValidity(true);
        // confirm rtt burst size is set correctly to default value
        assertEquals(request.getRttBurstSize(), RangingRequest.getDefaultRttBurstSize());
        // confirm the number of peers in the request is the max number of peers
        List<ResponderConfig> rttPeers = request.getRttResponders();
        int numRttPeers = rttPeers.size();
        assertEquals(RangingRequest.getMaxPeers(), numRttPeers);
        // confirm each peer has the correct mac address
        for (int i = 0; i < numRttPeers - 4; ++i) {
            assertEquals("AA:BB:CC:DD:EE:FF",
                    rttPeers.get(i).macAddress.toString().toUpperCase());
        }
        for (int i = numRttPeers - 4; i < numRttPeers - 1; ++i) {
            assertEquals("11:22:33:44:55:66",
                    rttPeers.get(i).macAddress.toString().toUpperCase());
        }
        assertEquals("00:01:02:03:04:05",
                rttPeers.get(numRttPeers - 1).macAddress.toString().toUpperCase());
    }

    /**
     * Validate that a non802llmc ranging request can have as many range operations as the upper
     * limit for number of requests. Testing all methods to add non-80211mc capable responders using
     * a mix of ScanResults and ResponderConfigs.
     */
    @Test
    public void testNon80211mcRangingRequestAtLimit() {
        ScanResult scanResult1 = new ScanResult();
        scanResult1.BSSID = "AA:BB:CC:DD:EE:FF";

        List<ScanResult> scanResultList = new ArrayList<>();
        for (int i = 0; i < RangingRequest.getMaxPeers() - 5; ++i) {
            scanResultList.add(scanResult1);
        }

        ScanResult scanResult2 = new ScanResult();
        scanResult2.BSSID = "11:22:33:44:55:66";

        ResponderConfig responderConfig2 = ResponderConfig.fromScanResult(scanResult2);
        List<ResponderConfig> responderConfigList = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            responderConfigList.add(responderConfig2);
        }

        // create request using max RTT Peers
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addNon80211mcCapableAccessPoint(scanResult1);        // Add 1
        builder.addNon80211mcCapableAccessPoints(scanResultList);    // Add MaxPeers - 5 = 5
        builder.addResponders(responderConfigList);                  // Add 3
        builder.addNon80211mcCapableAccessPoint(scanResult2);        // Add 1
        RangingRequest request = builder.build();

        // verify request
        request.enforceValidity(true);
        // confirm rtt burst size is set correctly to default value
        assertEquals(request.getRttBurstSize(), RangingRequest.getDefaultRttBurstSize());
        // confirm the number of peers in the request is the max number of peers
        List<ResponderConfig> rttPeers = request.getRttResponders();
        int numRttPeers = rttPeers.size();
        assertEquals(RangingRequest.getMaxPeers(), numRttPeers);
        // confirm each peer has the correct mac address
        for (int i = 0; i < numRttPeers - 4; ++i) {
            assertEquals("AA:BB:CC:DD:EE:FF",
                    rttPeers.get(i).macAddress.toString().toUpperCase());
        }
        for (int i = numRttPeers - 4; i < numRttPeers; ++i) {
            assertEquals("11:22:33:44:55:66",
                    rttPeers.get(i).macAddress.toString().toUpperCase());
        }
    }

    /**
     * Validate that limit on number of requests is applied.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRangingRequestPastLimit() {
        ScanResult scanResult = new ScanResult();
        scanResult.BSSID = "00:01:02:03:04:05";
        List<ScanResult> scanResultList = new ArrayList<>();
        for (int i = 0; i < RangingRequest.getMaxPeers() - 2; ++i) {
            scanResultList.add(scanResult);
        }
        MacAddress mac1 = MacAddress.fromString("00:01:02:03:04:05");

        // create request
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(scanResult);
        builder.addAccessPoints(scanResultList);
        builder.addAccessPoint(scanResult);
        builder.addWifiAwarePeer(mac1);
        RangingRequest request = builder.build();

        // verify request
        request.enforceValidity(true);
    }

    /**
     * Validate that Aware requests are invalid on devices which do not support Aware
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRangingRequestWithAwareWithNoAwareSupport() {
        // create request
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addWifiAwarePeer(new PeerHandle(10));
        RangingRequest request = builder.build();

        // verify request
        request.enforceValidity(false);
    }

    /**
     * Validate that RangingResults parcel works (produces same object on write/read).
     */
    @Test
    public void testRangingResultsParcel() {
        int status = RangingResult.STATUS_SUCCESS;
        final MacAddress mac = MacAddress.fromString("00:01:02:03:04:05");
        PeerHandle peerHandle = new PeerHandle(10);
        int distanceCm = 105;
        int distanceStdDevCm = 10;
        int rssi = 5;
        int numAttemptedMeasurements = 8;
        int numSuccessfulMeasurements = 3;
        long timestamp = System.currentTimeMillis();
        byte[] lci = { 0x5, 0x6, 0x7 };
        byte[] lcr = { 0x1, 0x2, 0x3, 0xA, 0xB, 0xC };
        List<OuiKeyedData> vendorData = OuiKeyedDataUtil.createTestOuiKeyedDataList(5);

        // RangingResults constructed with a MAC address
        RangingResult.Builder resultBuilder = new RangingResult.Builder()
                .setStatus(status)
                .setMacAddress(mac)
                .setDistanceMm(distanceCm)
                .setDistanceStdDevMm(distanceStdDevCm)
                .setRssi(rssi)
                .setNumAttemptedMeasurements(numAttemptedMeasurements)
                .setNumSuccessfulMeasurements(numSuccessfulMeasurements)
                .setLci(lci)
                .setLcr(lcr)
                .setRangingTimestampMillis(timestamp)
                .set80211mcMeasurement(true);
        if (SdkLevel.isAtLeastV()) {
            resultBuilder.setVendorData(vendorData);
        }
        RangingResult result = resultBuilder.build();

        Parcel parcelW = Parcel.obtain();
        result.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        RangingResult rereadResult = RangingResult.CREATOR.createFromParcel(parcelR);

        assertEquals(result, rereadResult);

        // RangingResults constructed with a PeerHandle
        resultBuilder = new RangingResult.Builder()
                .setStatus(status)
                .setPeerHandle(peerHandle)
                .setDistanceMm(distanceCm)
                .setDistanceStdDevMm(distanceStdDevCm)
                .setRssi(rssi)
                .setNumAttemptedMeasurements(numAttemptedMeasurements)
                .setNumSuccessfulMeasurements(numSuccessfulMeasurements)
                .setRangingTimestampMillis(timestamp);
        if (SdkLevel.isAtLeastV()) {
            resultBuilder.setVendorData(vendorData);
        }
        result = resultBuilder.build();

        parcelW = Parcel.obtain();
        result.writeToParcel(parcelW, 0);
        bytes = parcelW.marshall();
        parcelW.recycle();

        parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        rereadResult = RangingResult.CREATOR.createFromParcel(parcelR);

        assertEquals(result, rereadResult);
    }

    /**
     * Validate that RangingResults tests equal even if LCI/LCR is empty (length == 0) and null.
     */
    @Test
    public void testRangingResultsEqualityLciLcr() {
        int status = RangingResult.STATUS_SUCCESS;
        final MacAddress mac = MacAddress.fromString("00:01:02:03:04:05");
        PeerHandle peerHandle = new PeerHandle(10);
        int distanceCm = 105;
        int distanceStdDevCm = 10;
        int rssi = 5;
        int numAttemptedMeasurements = 10;
        int numSuccessfulMeasurements = 3;
        long timestamp = System.currentTimeMillis();
        byte[] lci = { };
        byte[] lcr = { };

        RangingResult rr1 = new RangingResult.Builder()
                .setStatus(status)
                .setMacAddress(mac)
                .setDistanceMm(distanceCm)
                .setDistanceStdDevMm(distanceStdDevCm)
                .setRssi(rssi)
                .setNumAttemptedMeasurements(numAttemptedMeasurements)
                .setNumSuccessfulMeasurements(numSuccessfulMeasurements)
                .setLci(lci)
                .setLcr(lcr)
                .setRangingTimestampMillis(timestamp)
                .set80211mcMeasurement(true)
                .build();
        RangingResult rr2 = new RangingResult.Builder()
                .setStatus(status)
                .setMacAddress(mac)
                .setDistanceMm(distanceCm)
                .setDistanceStdDevMm(distanceStdDevCm)
                .setRssi(rssi)
                .setNumAttemptedMeasurements(numAttemptedMeasurements)
                .setNumSuccessfulMeasurements(numSuccessfulMeasurements)
                .setRangingTimestampMillis(timestamp)
                .set80211mcMeasurement(true)
                .build();
        assertEquals(rr1, rr2);
    }

    /**
     * Validate that ResponderConfig parcel works (produces same object on write/read).
     */
    @Test
    public void testResponderConfigParcel() {
        // ResponderConfig constructed with a MAC address
        ResponderConfig config = new ResponderConfig.Builder()
                .setMacAddress(MacAddress.fromString("00:01:02:03:04:05"))
                .set80211mcSupported(true)
                .setChannelWidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                .setFrequencyMhz(2134)
                .setCenterFreq0Mhz(2345)
                .setCenterFreq1Mhz(2555)
                .setPreamble(ScanResult.PREAMBLE_LEGACY)
                .build();

        Parcel parcelW = Parcel.obtain();
        config.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        ResponderConfig rereadConfig = ResponderConfig.CREATOR.createFromParcel(parcelR);

        assertEquals(config, rereadConfig);

        // ResponderConfig constructed with a PeerHandle
        config = new ResponderConfig.Builder()
                .setPeerHandle(new PeerHandle(10))
                .setResponderType(ResponderConfig.RESPONDER_AWARE)
                .set80211mcSupported(false)
                .setChannelWidth(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ)
                .setFrequencyMhz(5555)
                .setCenterFreq0Mhz(6666)
                .setCenterFreq1Mhz(7777)
                .setPreamble(ScanResult.PREAMBLE_VHT)
                .build();

        parcelW = Parcel.obtain();
        config.writeToParcel(parcelW, 0);
        bytes = parcelW.marshall();
        parcelW.recycle();

        parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        rereadConfig = ResponderConfig.CREATOR.createFromParcel(parcelR);

        assertEquals(config, rereadConfig);
    }

    /**
     * Validate preamble selection from ScanResults.
     */
    @Test
    public void testResponderPreambleSelection() {
        ScanResult.InformationElement htCap = new ScanResult.InformationElement();
        htCap.id = ScanResult.InformationElement.EID_HT_CAPABILITIES;

        ScanResult.InformationElement vhtCap = new ScanResult.InformationElement();
        vhtCap.id = ScanResult.InformationElement.EID_VHT_CAPABILITIES;

        ScanResult.InformationElement vsa = new ScanResult.InformationElement();
        vsa.id = ScanResult.InformationElement.EID_VSA;

        ScanResult.InformationElement heCap = new ScanResult.InformationElement();
        heCap.id = ScanResult.InformationElement.EID_EXTENSION_PRESENT;
        heCap.idExt = ScanResult.InformationElement.EID_EXT_HE_CAPABILITIES;

        // no IE
        ScanResult scan = new ScanResult();
        scan.BSSID = "00:01:02:03:04:05";
        scan.informationElements = null;
        scan.channelWidth = ResponderConfig.CHANNEL_WIDTH_80MHZ;

        ResponderConfig config = ResponderConfig.fromScanResult(scan);

        assertEquals(ResponderConfig.PREAMBLE_VHT, config.preamble);

        // IE with HT & VHT
        scan.channelWidth = ResponderConfig.CHANNEL_WIDTH_40MHZ;

        scan.informationElements = new ScanResult.InformationElement[2];
        scan.informationElements[0] = htCap;
        scan.informationElements[1] = vhtCap;

        config = ResponderConfig.fromScanResult(scan);

        assertEquals(ResponderConfig.PREAMBLE_VHT, config.preamble);

        // IE with some entries but no HT or VHT
        scan.informationElements[0] = vsa;
        scan.informationElements[1] = vsa;

        config = ResponderConfig.fromScanResult(scan);

        assertEquals(ResponderConfig.PREAMBLE_LEGACY, config.preamble);

        // IE with HT
        scan.informationElements[0] = vsa;
        scan.informationElements[1] = htCap;

        config = ResponderConfig.fromScanResult(scan);

        assertEquals(ResponderConfig.PREAMBLE_HT, config.preamble);

        // IE with VHT and HE on 5G

        scan.frequency = 5200;
        scan.informationElements[0] = vhtCap;
        scan.informationElements[1] = heCap;

        config = ResponderConfig.fromScanResult(scan);

        assertEquals(ResponderConfig.PREAMBLE_VHT, config.preamble);

        // IE with VHT and HE on 6G
        scan.frequency = 5935;
        scan.informationElements[0] = vhtCap;
        scan.informationElements[1] = heCap;

        config = ResponderConfig.fromScanResult(scan);

        assertEquals(ResponderConfig.PREAMBLE_HE, config.preamble);
    }

    @Test
    public void testGetRttCharacteristics() throws Exception {
        when(mockRttService.getRttCharacteristics()).thenReturn(new Bundle());
        Bundle characteristics = mDut.getRttCharacteristics();
        verify(mockRttService).getRttCharacteristics();
        assertEquals(0, characteristics.size());
    }
}
