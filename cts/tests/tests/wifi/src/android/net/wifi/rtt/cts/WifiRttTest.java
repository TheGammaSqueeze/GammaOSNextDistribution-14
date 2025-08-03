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
 * limitations under the License.
 */

package android.net.wifi.rtt.cts;

import static android.net.wifi.rtt.ResponderConfig.RESPONDER_AP;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.cts.WifiBuildCompat;
import android.net.wifi.cts.WifiFeature;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.ResponderLocation;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.wifi.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wi-Fi RTT CTS test: range to all available Access Points which support IEEE 802.11mc.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
public class WifiRttTest extends TestBase {
    // Number of scans to do while searching for APs supporting IEEE 802.11mc
    private static final int NUM_SCANS_SEARCHING_FOR_IEEE80211MC_AP = 5;

    // Number of RTT measurements per AP
    private static final int NUM_OF_RTT_ITERATIONS = 10;

    // Maximum failure rate of RTT measurements (percentage)
    private static final int MAX_FAILURE_RATE_PERCENT = 20;

    // Maximum variation from the average measurement (measures consistency)
    private static final int MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM = 2000;

    // Maximum failure rate of one-sided RTT measurements (percentage)
    private static final int MAX_NON11MC_FAILURE_RATE_PERCENT = 40;

    // Maximum non-8011mc variation from the average measurement (measures consistency)
    private static final int MAX_NON11MC_VARIATION_FROM_AVERAGE_DISTANCE_MM = 4000;

    // Minimum valid RSSI value
    private static final int MIN_VALID_RSSI = -100;

    // Valid Mac Address
    private static final MacAddress MAC = MacAddress.fromString("00:01:02:03:04:05");

    // Interval between two ranging request.
    private static final int INTERVAL_MS = 1000;

    /**
     * Test Wi-Fi RTT ranging operation using ScanResults in request:
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11mc)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTest11mcApUsingScanResult() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);
        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(testAp);

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    >= RangingRequest.getMinRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    <= RangingRequest.getMaxRttBurstSize());
        }

        RangingRequest request = builder.build();
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            assertEquals(1, request.getRttResponders().size());
        }
        rangeApRequest(request, testAp);
    }

    /**
     * Test Wi-Fi RTT ranging using ResponderConfig in the single responder RangingRequest API.
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11mc)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTest11mcApUsingResponderConfig() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);
        int preamble = ResponderConfig.fromScanResult(testAp).getPreamble();

        // Create a ResponderConfig from the builder API.
        ResponderConfig.Builder responderBuilder = new ResponderConfig.Builder();
        ResponderConfig responder = responderBuilder
                .setMacAddress(MacAddress.fromString(testAp.BSSID))
                .set80211mcSupported(testAp.is80211mcResponder())
                .setChannelWidth(testAp.channelWidth)
                .setFrequencyMhz(testAp.frequency)
                .setCenterFreq0Mhz(testAp.centerFreq0)
                .setCenterFreq1Mhz(testAp.centerFreq1)
                .setPreamble(preamble)
                .setResponderType(RESPONDER_AP)
                .build();

        // Validate ResponderConfig.Builder set method arguments match getter methods.
        assertTrue(responder.getMacAddress().toString().equalsIgnoreCase(testAp.BSSID)
                && responder.is80211mcSupported() == testAp.is80211mcResponder()
                && responder.getChannelWidth() == testAp.channelWidth
                && responder.getFrequencyMhz() == testAp.frequency
                && responder.getCenterFreq0Mhz() == testAp.centerFreq0
                && responder.getCenterFreq1Mhz() == testAp.centerFreq1
                && responder.getPreamble() == preamble
                && responder.getResponderType() == RESPONDER_AP);

        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addResponder(responder);

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    >= RangingRequest.getMinRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    <= RangingRequest.getMaxRttBurstSize());
        }

        RangingRequest request = builder.build();

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            assertEquals(1, request.getRttResponders().size());
        }
        rangeApRequest(request, testAp);
    }

    /**
     * Test Wi-Fi RTT ranging using ResponderConfig in the multi-Responder RangingRequest API.
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11mc)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTest11mcApUsingListResponderConfig() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);
        ResponderConfig responder = ResponderConfig.fromScanResult(testAp);
        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        List<ResponderConfig> responders = new ArrayList<>();
        responders.add(responder);
        builder.addResponders(responders);

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    >= RangingRequest.getMinRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    <= RangingRequest.getMaxRttBurstSize());
        }

        RangingRequest request = builder.build();

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            assertEquals(1, request.getRttResponders().size());
        }
        rangeApRequest(request, testAp);
    }

    /**
     * Utility method for validating a ranging request.
     *
     * @param request the ranging request that is being tested
     * @param testAp the original test scan result to provide feedback on failure conditions
     */
    private void rangeApRequest(RangingRequest request, ScanResult testAp)
            throws InterruptedException {
        Thread.sleep(5000);
        List<RangingResult> allResults = new ArrayList<>();
        int numFailures = 0;
        int distanceSum = 0;
        int distanceMin = 0;
        int distanceMax = 0;
        int[] statuses = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceStdDevMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] rssis = new int[NUM_OF_RTT_ITERATIONS];
        int[] numAttempted = new int[NUM_OF_RTT_ITERATIONS];
        int[] numSuccessful = new int[NUM_OF_RTT_ITERATIONS];
        int[] frequencies = new int[NUM_OF_RTT_ITERATIONS];
        int[] packetBws = new int[NUM_OF_RTT_ITERATIONS];
        long[] timestampsMs = new long[NUM_OF_RTT_ITERATIONS];
        byte[] lastLci = null;
        byte[] lastLcr = null;
        for (int i = 0; i < NUM_OF_RTT_ITERATIONS; ++i) {
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback on iteration " + i,
                    callback.waitForCallback());

            List<RangingResult> currentResults = callback.getResults();
            assertNotNull("Wi-Fi RTT results: null results (onRangingFailure) on iteration " + i,
                    currentResults);
            assertEquals("Wi-Fi RTT results: unexpected # of results (expect 1) on iteration " + i,
                    1, currentResults.size());
            RangingResult result = currentResults.get(0);
            assertEquals("Wi-Fi RTT results: invalid result (wrong BSSID) entry on iteration " + i,
                    result.getMacAddress().toString(), testAp.BSSID);
            assertNull("Wi-Fi RTT results: invalid result (non-null PeerHandle) entry on iteration "
                    + i, result.getPeerHandle());

            allResults.add(result);
            int status = result.getStatus();
            statuses[i] = status;
            if (status == RangingResult.STATUS_SUCCESS) {
                if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
                    if (result.is80211mcMeasurement()) {
                        assertEquals(
                                "Wi-Fi RTT results: invalid result (wrong rttBurstSize) entry on "
                                        + "iteration " + i, result.getNumAttemptedMeasurements(),
                                RangingRequest.getMaxRttBurstSize());
                    }
                    assertTrue("Wi-Fi RTT results: should be a 802.11mc or 802.11az measurement",
                            result.is80211mcMeasurement() || result.is80211azNtbMeasurement());
                }
                distanceSum += result.getDistanceMm();
                if (i == 0) {
                    distanceMin = result.getDistanceMm();
                    distanceMax = result.getDistanceMm();
                } else {
                    distanceMin = Math.min(distanceMin, result.getDistanceMm());
                    distanceMax = Math.max(distanceMax, result.getDistanceMm());
                }

                assertTrue("Wi-Fi RTT results: invalid RSSI on iteration " + i,
                        result.getRssi() >= MIN_VALID_RSSI);

                distanceMms[i - numFailures] = result.getDistanceMm();
                distanceStdDevMms[i - numFailures] = result.getDistanceStdDevMm();
                rssis[i - numFailures] = result.getRssi();
                numAttempted[i - numFailures] = result.getNumAttemptedMeasurements();
                numSuccessful[i - numFailures] = result.getNumSuccessfulMeasurements();
                timestampsMs[i - numFailures] = result.getRangingTimestampMillis();
                frequencies[i - numFailures] = result.getMeasurementChannelFrequencyMHz();
                packetBws[i - numFailures] = result.getMeasurementBandwidth();

                byte[] currentLci = result.getLci();
                byte[] currentLcr = result.getLcr();
                if (i - numFailures > 0) {
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCI mismatch) on iteration " + i,
                            currentLci, lastLci);
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCR mismatch) on iteration " + i,
                            currentLcr, lastLcr);
                }
                lastLci = currentLci;
                lastLcr = currentLcr;
            } else {
                numFailures++;
            }
            // Sleep a while to avoid stress AP.
            Thread.sleep(INTERVAL_MS);
        }

        // Save results to log
        int numGoodResults = NUM_OF_RTT_ITERATIONS - numFailures;
        DeviceReportLog reportLog = new DeviceReportLog(TAG, "testRangingToTestAp");
        reportLog.addValues("status_codes", statuses, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_mm", Arrays.copyOf(distanceMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_stddev_mm", Arrays.copyOf(distanceStdDevMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("rssi_dbm", Arrays.copyOf(rssis, numGoodResults), ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValues("num_attempted", Arrays.copyOf(numAttempted, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("num_successful", Arrays.copyOf(numSuccessful, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("timestamps", Arrays.copyOf(timestampsMs, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("frequencies", Arrays.copyOf(frequencies, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("packetBws", Arrays.copyOf(packetBws, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();

        // Analyze results
        assertTrue("Wi-Fi RTT failure rate exceeds threshold: FAIL=" + numFailures + ", ITERATIONS="
                        + NUM_OF_RTT_ITERATIONS + ", AP=" + testAp,
                numFailures <= NUM_OF_RTT_ITERATIONS * MAX_FAILURE_RATE_PERCENT / 100);
        if (numFailures != NUM_OF_RTT_ITERATIONS) {
            double distanceAvg = (double) distanceSum / (NUM_OF_RTT_ITERATIONS - numFailures);
            assertTrue("Wi-Fi RTT: Variation (max direction) exceeds threshold, Variation ="
                            + (distanceMax - distanceAvg),
                    (distanceMax - distanceAvg) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
            assertTrue("Wi-Fi RTT: Variation (min direction) exceeds threshold, Variation ="
                            + (distanceAvg - distanceMin),
                    (distanceAvg - distanceMin) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
            for (int i = 0; i < numGoodResults; ++i) {
                assertNotEquals("Number of attempted measurements is 0", 0, numAttempted[i]);
                assertNotEquals("Number of successful measurements is 0", 0, numSuccessful[i]);
            }
        }
    }

    /**
     * Validate that when a request contains more range operations than allowed (by API) that we
     * get an exception.
     */
    @Test
    public void testRequestTooLarge() throws InterruptedException {
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);

        RangingRequest.Builder builder = new RangingRequest.Builder();
        List<ScanResult> scanResults = new ArrayList<>();
        for (int i = 0; i < RangingRequest.getMaxPeers() - 2; ++i) {
            scanResults.add(testAp);
        }
        builder.addAccessPoints(scanResults);

        ScanResult testApNon80211mc = null;
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            testApNon80211mc = getLegacyScanResult();
        }
        if (testApNon80211mc == null) {
            builder.addAccessPoints(List.of(testAp, testAp, testAp));
        } else {
            builder.addNon80211mcCapableAccessPoints(List.of(testApNon80211mc, testApNon80211mc,
                    testApNon80211mc));
        }

        try {
            mWifiRttManager.startRanging(builder.build(), mExecutor, new ResultCallback());
        } catch (IllegalArgumentException e) {
            return;
        }

        fail("Did not receive expected IllegalArgumentException when tried to range to too "
                + "many peers");
    }

    /**
     * Verify ResponderLocation API
     */
    @Test
    public void testRangingToTestApWithResponderLocation() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);

        // Perform RTT operations
        RangingRequest request = new RangingRequest.Builder().addAccessPoint(testAp).build();
        ResultCallback callback = new ResultCallback();
        mWifiRttManager.startRanging(request, mExecutor, callback);
        assertTrue("Wi-Fi RTT results: no callback! ",
                callback.waitForCallback());

        RangingResult result = callback.getResults().get(0);
        assertEquals("Ranging request not success",
                result.getStatus(), RangingResult.STATUS_SUCCESS);
        ResponderLocation responderLocation = result.getUnverifiedResponderLocation();
        if (responderLocation == null) {
            return;
        }
        assertTrue("ResponderLocation is not valid", responderLocation.isLciSubelementValid());

        // Check LCI related APIs
        int exceptionCount = 0;
        int apiCount = 0;
        try {
            apiCount++;
            responderLocation.getLatitudeUncertainty();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLatitude();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLongitudeUncertainty();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLongitude();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getAltitudeType();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getAltitudeUncertainty();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getAltitude();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getDatum();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getRegisteredLocationAgreementIndication();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLciVersion();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            assertNotNull(responderLocation.toLocation());
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        // If LCI is not valid, all APIs should throw exception, otherwise no exception.
        assertEquals("Exception number should equal to API number",
                responderLocation.isLciSubelementValid()? 0 : apiCount, exceptionCount);

        // Verify ZaxisSubelement APIs
        apiCount = 0;
        exceptionCount = 0;

        try {
            apiCount++;
            responderLocation.getExpectedToMove();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }

        try {
            apiCount++;
            responderLocation.getFloorNumber();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }

        try {
            apiCount++;
            responderLocation.getHeightAboveFloorMeters();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }

        try {
            apiCount++;
            responderLocation.getHeightAboveFloorUncertaintyMeters();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        // If Zaxis is not valid, all APIs should throw exception, otherwise no exception.
        assertEquals("Exception number should equal to API number",
                responderLocation.isZaxisSubelementValid() ? 0 : apiCount, exceptionCount);
        // Verify civic location
        if (responderLocation.toCivicLocationAddress() == null) {
            assertNull(responderLocation.toCivicLocationSparseArray());
        } else {
            assertNotNull(responderLocation.toCivicLocationSparseArray());
        }
        // Verify map image
        if (responderLocation.getMapImageUri() == null) {
            assertNull(responderLocation.getMapImageMimeType());
        } else {
            assertNotNull(responderLocation.getMapImageMimeType());
        }
        boolean extraInfoOnAssociationIndication =
                responderLocation.getExtraInfoOnAssociationIndication();
        assertNotNull("ColocatedBSSID list should be nonNull",
                responderLocation.getColocatedBssids());
    }

    /**
     * Verify ranging request with aware peer Mac address and peer handle.
     */
    @Test
    public void testAwareRttWithMacAddress() throws InterruptedException {
        if (!WifiFeature.isAwareSupported(getContext())) {
            return;
        }
        RangingRequest request = new RangingRequest.Builder()
                .addWifiAwarePeer(MAC).build();
        ResultCallback callback = new ResultCallback();
        mWifiRttManager.startRanging(request, mExecutor, callback);
        assertTrue("Wi-Fi RTT results: no callback",
                callback.waitForCallback());
        List<RangingResult> rangingResults = callback.getResults();
        assertNotNull("Wi-Fi RTT results: null results", rangingResults);
        assertEquals(1, rangingResults.size());
        assertEquals(RangingResult.STATUS_FAIL, rangingResults.get(0).getStatus());
    }

    /**
     * Verify ranging request with aware peer handle.
     */
    @Test
    public void testAwareRttWithPeerHandle() throws InterruptedException {
        if (!WifiFeature.isAwareSupported(getContext())) {
            return;
        }
        PeerHandle peerHandle = mock(PeerHandle.class);
        RangingRequest request = new RangingRequest.Builder()
                .addWifiAwarePeer(peerHandle).build();
        ResultCallback callback = new ResultCallback();
        mWifiRttManager.startRanging(request, mExecutor, callback);
        assertTrue("Wi-Fi RTT results: no callback",
                callback.waitForCallback());
        List<RangingResult> rangingResults = callback.getResults();
        assertNotNull("Wi-Fi RTT results: null results", rangingResults);
        assertEquals("Invalid peerHandle should return 0 result", 0, rangingResults.size());
    }

    /**
     * Test Wi-Fi One-sided RTT ranging operation using ScanResult in request:
     * - Scan for visible APs for the test AP (which do not support IEEE 802.11mc) and are operating
     * - in the 5GHz band.
     * - Perform N (constant) RTT operations
     * - Remove outliers while insuring greater than 50% of the results still remain
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTestNon11mcApUsingScanResult() throws InterruptedException {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            return;
        }

        // Scan for Non-IEEE 802.11mc supporting APs
        ScanResult testAp = getLegacyScanResult();
        assertNotNull(
                "Cannot find any test APs which are Non-IEEE 802.11mc - please verify that"
                        + " your test setup includes them!", testAp);

        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addNon80211mcCapableAccessPoint(testAp);
        builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
        RangingRequest request = builder.build();

        // Perform the request
        rangeNon11mcApRequest(request, testAp, MAX_NON11MC_VARIATION_FROM_AVERAGE_DISTANCE_MM);
    }

    /**
     * Test Wi-Fi one-sided RTT ranging operation using ResponderConfig in request:
     * - Scan for visible APs for the test AP (which do not support IEEE 802.11mc) and are operating
     * - in the 5GHz band.
     * - Perform N (constant) RTT operations
     * - Remove outliers while insuring greater than 50% of the results still remain
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTestNon11mcApUsingResponderConfig() throws InterruptedException {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            return;
        }

        // Scan for Non-IEEE 802.11mc supporting APs
        ScanResult testAp = getLegacyScanResult();
        assertNotNull(
                "Cannot find any test APs which are Non-IEEE 802.11mc - please verify that"
                        + " your test setup includes them!", testAp);

        ResponderConfig responder = ResponderConfig.fromScanResult(testAp);

        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addResponder(responder);
        builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
        RangingRequest request = builder.build();



        // Perform the request
        rangeNon11mcApRequest(request, testAp, MAX_NON11MC_VARIATION_FROM_AVERAGE_DISTANCE_MM);
    }

    /**
     * Utility method for validating a ranging request to a non-80211mc AP.
     *
     * @param request the ranging request that is being tested
     * @param testAp the original test scan result to provide feedback on failure conditions
     */
    private void rangeNon11mcApRequest(RangingRequest request, ScanResult testAp,
            int variationLimit) throws InterruptedException {
        Thread.sleep(5000);
        List<RangingResult> allResults = new ArrayList<>();
        int numFailures = 0;
        int distanceSum = 0;
        int distanceMin = 0;
        int distanceMax = 0;
        int[] statuses = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceMms = new int[NUM_OF_RTT_ITERATIONS];
        boolean[] distanceInclusionMap = new boolean[NUM_OF_RTT_ITERATIONS];
        int[] distanceStdDevMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] rssis = new int[NUM_OF_RTT_ITERATIONS];
        int[] numAttempted = new int[NUM_OF_RTT_ITERATIONS];
        int[] numSuccessful = new int[NUM_OF_RTT_ITERATIONS];
        long[] timestampsMs = new long[NUM_OF_RTT_ITERATIONS];
        byte[] lastLci = null;
        byte[] lastLcr = null;
        for (int i = 0; i < NUM_OF_RTT_ITERATIONS; ++i) {
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback on iteration " + i,
                    callback.waitForCallback());

            List<RangingResult> currentResults = callback.getResults();
            assertNotNull(
                    "Wi-Fi RTT results: null results (onRangingFailure) on iteration " + i,
                    currentResults);
            assertEquals(
                    "Wi-Fi RTT results: unexpected # of results (expect 1) on iteration " + i,
                    1, currentResults.size());
            RangingResult result = currentResults.get(0);
            assertEquals(
                    "Wi-Fi RTT results: invalid result (wrong BSSID) entry on iteration " + i,
                    result.getMacAddress().toString(), testAp.BSSID);

            assertNull(
                    "Wi-Fi RTT results: invalid result (non-null PeerHandle) entry on iteration "
                            + i, result.getPeerHandle());

            allResults.add(result);
            int status = result.getStatus();
            statuses[i] = status;
            if (status == RangingResult.STATUS_SUCCESS) {
                assertFalse("Wi-Fi RTT results: should not be a 802.11MC measurement",
                        result.is80211mcMeasurement());
                distanceSum += result.getDistanceMm();

                assertTrue("Wi-Fi RTT results: invalid RSSI on iteration " + i,
                        result.getRssi() >= MIN_VALID_RSSI);

                distanceMms[i - numFailures] = result.getDistanceMm();
                distanceStdDevMms[i - numFailures] = result.getDistanceStdDevMm();
                rssis[i - numFailures] = result.getRssi();
                // For one-sided RTT the number of packets attempted in a burst is not available,
                // So we set the result to be the same as used in the request.
                numAttempted[i - numFailures] = request.getRttBurstSize();
                numSuccessful[i - numFailures] = result.getNumSuccessfulMeasurements();
                timestampsMs[i - numFailures] = result.getRangingTimestampMillis();

                byte[] currentLci = result.getLci();
                byte[] currentLcr = result.getLcr();
                if (i - numFailures > 0) {
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCI mismatch) on iteration " + i,
                            currentLci, lastLci);
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCR mismatch) on iteration " + i,
                            currentLcr, lastLcr);
                }
                lastLci = currentLci;
                lastLcr = currentLcr;
            } else {
                numFailures++;
            }
            // Sleep a while to avoid stress AP.
            Thread.sleep(INTERVAL_MS);
        }
        // Save results to log
        int numGoodResults = NUM_OF_RTT_ITERATIONS - numFailures;
        DeviceReportLog reportLog = new DeviceReportLog(TAG, "testRangingToTestAp");
        reportLog.addValues("status_codes", statuses, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_mm", Arrays.copyOf(distanceMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_stddev_mm",
                Arrays.copyOf(distanceStdDevMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("rssi_dbm", Arrays.copyOf(rssis, numGoodResults),
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValues("num_attempted", Arrays.copyOf(numAttempted, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("num_successful", Arrays.copyOf(numSuccessful, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("timestamps", Arrays.copyOf(timestampsMs, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();

        if (mCharacteristics != null && mCharacteristics.getBoolean(WifiRttManager
                .CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT)) {
            // Analyze results
            assertTrue("Wi-Fi RTT failure rate exceeds threshold: FAIL=" + numFailures
                            + ", ITERATIONS="
                            + NUM_OF_RTT_ITERATIONS + ", AP=" + testAp,
                    numFailures <= NUM_OF_RTT_ITERATIONS * MAX_NON11MC_FAILURE_RATE_PERCENT / 100);
        }

        if (numFailures != NUM_OF_RTT_ITERATIONS) {
            // Calculate an initial average using all measurements to determine distance outliers
            double distanceAvg = (double) distanceSum / (NUM_OF_RTT_ITERATIONS - numFailures);
            // Now figure out the distance outliers and mark them in the distance inclusion map
            int validDistances = 0;
            for (int i = 0; i < (NUM_OF_RTT_ITERATIONS - numFailures); i++) {
                if (distanceMms[i] - variationLimit < distanceAvg) {
                    // Distances that are in range for the distribution are included in the map
                    distanceInclusionMap[i] = true;
                    validDistances++;
                } else {
                    // Distances that are out of range for the distribution are excluded in the map
                    distanceInclusionMap[i] = false;
                }
            }

            assertTrue("After fails+outlier removal greater that 50% distances must remain: "
                    + NUM_OF_RTT_ITERATIONS / 2, validDistances > NUM_OF_RTT_ITERATIONS / 2);

            // Remove the distance outliers and find the new average, min and max.
            distanceSum = 0;
            distanceMax = Integer.MIN_VALUE;
            distanceMin = Integer.MAX_VALUE;
            for (int i = 0; i < (NUM_OF_RTT_ITERATIONS - numFailures); i++) {
                if (distanceInclusionMap[i]) {
                    distanceSum += distanceMms[i];
                    distanceMin = Math.min(distanceMin, distanceMms[i]);
                    distanceMax = Math.max(distanceMax, distanceMms[i]);
                }
            }
            distanceAvg = (double) distanceSum / validDistances;
            assertTrue("Wi-Fi RTT: Variation (max direction) exceeds threshold, Variation ="
                            + (distanceMax - distanceAvg),
                    (distanceMax - distanceAvg) <= variationLimit);
            assertTrue("Wi-Fi RTT: Variation (min direction) exceeds threshold, Variation ="
                            + (distanceAvg - distanceMin),
                    (distanceAvg - distanceMin) <= variationLimit);
            for (int i = 0; i < numGoodResults; ++i) {
                assertNotEquals("Number of attempted measurements is 0", 0, numAttempted[i]);
                assertNotEquals("Number of successful measurements is 0", 0, numSuccessful[i]);
            }
        }

    }

    /**
     * Test RangingResult.Builder
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testRangingResultBuilder() {

        byte[] lci = {1, 2, 3, 4};
        byte[] lcr = {10, 20, 30, 40};

        RangingResult rangingResult = new RangingResult.Builder()
                .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setDistanceMm(100)
                .setDistanceStdDevMm(33)
                .setLci(lci)
                .setLcr(lcr)
                .setNumAttemptedMeasurements(10)
                .setNumSuccessfulMeasurements(5)
                .setRangingTimestampMillis(12345)
                .setRssi(-77)
                .build();

        assertEquals(MacAddress.fromString("00:11:22:33:44:55"), rangingResult.getMacAddress());
        assertEquals(RangingResult.STATUS_SUCCESS, rangingResult.getStatus());
        assertEquals(100, rangingResult.getDistanceMm());
        assertEquals(33, rangingResult.getDistanceStdDevMm());
        assertArrayEquals(lci, rangingResult.getLci());
        assertArrayEquals(lcr, rangingResult.getLcr());
        assertEquals(10, rangingResult.getNumAttemptedMeasurements());
        assertEquals(5, rangingResult.getNumSuccessfulMeasurements());
        assertEquals(12345, rangingResult.getRangingTimestampMillis());
        assertEquals(-77, rangingResult.getRssi());

        try {
            rangingResult = new RangingResult.Builder()
                    .setStatus(RangingResult.STATUS_SUCCESS)
                    .setDistanceMm(100)
                    .setDistanceStdDevMm(33)
                    .build();
            assertEquals(RangingResult.STATUS_SUCCESS, rangingResult.getStatus());
            fail("RangeResult need MAC address or Peer handle");
        } catch (IllegalArgumentException e) {

        }
    }

    /**
     * Test Wi-Fi RTT ranging operation using ScanResults in request:
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11az)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    public void testRangingToTest11azApUsingScanResult() throws InterruptedException {
        assumeTrue(mCharacteristics != null && mCharacteristics.getBoolean(
                WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR));
        ScanResult testAp = getS11AzScanResult();
        assertNotNull("Cannot find any test APs which support RTT / IEEE 802.11az"
                + " - please verify that your test setup includes them!", testAp);
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(testAp);
        RangingRequest request = builder.build();
        rangeApRequest(request, testAp);
    }

    /*
     * Test that vendor data can be set and retrieved properly in RangingRequest and RangingResult.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testRangingRequestVendorData() {
        // Default value should be an empty list
        RangingRequest emptyRequest = new RangingRequest.Builder().build();
        assertNotNull(emptyRequest.getVendorData());
        assertTrue(emptyRequest.getVendorData().isEmpty());

        RangingResult emptyResult = new RangingResult.Builder().setMacAddress(MAC).build();
        assertNotNull(emptyResult.getVendorData());
        assertTrue(emptyResult.getVendorData().isEmpty());

        // Set and get vendor data
        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(0x00aabbcc, new PersistableBundle()).build();
        List<OuiKeyedData> vendorData = Arrays.asList(vendorDataElement);

        RangingRequest requestWithData = new RangingRequest.Builder()
                .setVendorData(vendorData)
                .build();
        assertTrue(vendorData.equals(requestWithData.getVendorData()));

        RangingResult resultWithData = new RangingResult.Builder()
                .setMacAddress(MAC)
                .setVendorData(vendorData)
                .build();
        assertTrue(vendorData.equals(resultWithData.getVendorData()));
    }
}
