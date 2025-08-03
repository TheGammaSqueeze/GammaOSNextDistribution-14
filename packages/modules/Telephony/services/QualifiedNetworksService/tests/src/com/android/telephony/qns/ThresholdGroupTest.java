/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.telephony.qns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.telephony.AccessNetworkConstants;
import android.telephony.SignalThresholdInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class ThresholdGroupTest {

    @Mock QualityMonitor mMockWifiQualityMonitor;
    @Mock QualityMonitor mMockCellularQualityMonitor;
    private List<Threshold> mThresholdList;
    private ThresholdGroup mThresholdGroup;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mThresholdList = new ArrayList<>();
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                        -117,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                        -15,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -65,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        doReturn(-120, -116)
                .when(mMockCellularQualityMonitor)
                .getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP);
        doReturn(-16, -13)
                .when(mMockCellularQualityMonitor)
                .getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR);
        doReturn(-70, -55)
                .when(mMockWifiQualityMonitor)
                .getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);
    }

    @Test
    public void testSatisfiedByThreshold_WithThresholds() {

        mThresholdGroup = new ThresholdGroup(mThresholdList);

        // Case1:
        assertTrue(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        true,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN));

        // Case2:
        mThresholdList.clear();
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                        -117,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                        -15,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -65,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdGroup = new ThresholdGroup(mThresholdList);

        assertTrue(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        true,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN));
        // Other Cases:
        assertTrue(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        false,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN));

        // no NGRAN condition case:
        assertFalse(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        false,
                        true,
                        AccessNetworkConstants.AccessNetworkType.NGRAN));
    }

    @Test
    public void testSatisfiedByThreshold_WithSignalAvailability() {
        mThresholdList = new ArrayList<>();

        // Case1:
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                        QnsConstants.SIGNAL_AVAILABLE,
                        QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdGroup = new ThresholdGroup(mThresholdList);

        assertTrue(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        true,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN));

        // Case2:
        assertFalse(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        false,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN));
    }

    @Test
    public void testSatisfiedByThreshold_WithEutranAvailability() {
        doReturn(-90, -55)
                .when(mMockWifiQualityMonitor)
                .getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);

        mThresholdList = new ArrayList<>();

        // Case1:
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -80,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                        QnsConstants.SIGNAL_AVAILABLE,
                        QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdGroup = new ThresholdGroup(mThresholdList);

        assertTrue(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        true,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN));

        assertFalse(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        true,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN));

        assertFalse(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        true,
                        true,
                        AccessNetworkConstants.AccessNetworkType.UTRAN));
    }

    @Test
    public void testSatisfiedByThreshold_WithUnavailability() {
        doReturn(-50)
                .when(mMockWifiQualityMonitor)
                .getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);

        mThresholdList = new ArrayList<>();

        // Case1:
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -80,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                        QnsConstants.SIGNAL_UNAVAILABLE,
                        QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.UTRAN,
                        QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                        QnsConstants.SIGNAL_UNAVAILABLE,
                        QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.GERAN,
                        QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                        QnsConstants.SIGNAL_UNAVAILABLE,
                        QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.NGRAN,
                        QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                        QnsConstants.SIGNAL_UNAVAILABLE,
                        QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdGroup = new ThresholdGroup(mThresholdList);

        assertTrue(
                mThresholdGroup.satisfiedByThreshold(
                        mMockWifiQualityMonitor,
                        mMockCellularQualityMonitor,
                        true,
                        false,
                        AccessNetworkConstants.AccessNetworkType.UNKNOWN));
    }

    @Test
    public void testFindUnmatchedThresholds() {
        doReturn(-12, -16)
                .when(mMockCellularQualityMonitor)
                .getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ);
        mThresholdList.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                        -15,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));

        // Case1:
        mThresholdGroup = new ThresholdGroup(mThresholdList);

        List<Threshold> output =
                mThresholdGroup.findUnmatchedThresholds(
                        mMockWifiQualityMonitor, mMockCellularQualityMonitor);
        assertEquals(1, output.size());
        output =
                mThresholdGroup.findUnmatchedThresholds(
                        mMockWifiQualityMonitor, mMockCellularQualityMonitor);
        assertEquals(3, output.size());
    }

    @Test
    public void testGetThresholds() {
        mThresholdGroup = new ThresholdGroup(mThresholdList);
        assertEquals(
                2,
                mThresholdGroup
                        .getThresholds(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                        .size());
        assertEquals(
                1,
                mThresholdGroup
                        .getThresholds(AccessNetworkConstants.AccessNetworkType.IWLAN)
                        .size());
    }

    @Test
    public void testIdenticalThreshold() {
        mThresholdGroup = new ThresholdGroup(mThresholdList);
        // Case1:
        List<Threshold> ths = new ArrayList<>(mThresholdList);
        assertTrue(mThresholdGroup.identicalThreshold(ths));

        ths.remove(0);
        assertFalse(mThresholdGroup.identicalThreshold(ths));

        ths.clear();
        ths.addAll(mThresholdList);
        ths.get(0).setGroupId(123);
        ths.get(0).setThresholdId(123);
        assertTrue(mThresholdGroup.identicalThreshold(ths));

        Threshold t = ths.remove(0).copy();
        t.setThreshold(-123);
        ths.add(0, t);
        assertFalse(mThresholdGroup.identicalThreshold(ths));

        ths.clear();
        ths.addAll(mThresholdList);
        t = ths.remove(0).copy();
        t.setMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ);
        ths.add(0, t);
        assertFalse(mThresholdGroup.identicalThreshold(ths));

        ths.clear();
        ths.addAll(mThresholdList);
        t = ths.remove(0).copy();
        t.setAccessNetwork(AccessNetworkConstants.AccessNetworkType.NGRAN);
        ths.add(0, t);
        assertFalse(mThresholdGroup.identicalThreshold(ths));

        ths.clear();
        ths.addAll(mThresholdList);
        t = ths.remove(0).copy();
        ths.add(0, t);
        t.setMatchType(QnsConstants.THRESHOLD_EQUAL_OR_LARGER);
        assertFalse(mThresholdGroup.identicalThreshold(ths));

        ths.clear();
        ths.addAll(mThresholdList);
        t = ths.remove(0).copy();
        t.setWaitTime(2000);
        ths.add(0, t);
        assertFalse(mThresholdGroup.identicalThreshold(ths));
    }

    @After
    public void tearDown() {
        mThresholdList.clear();
    }
}
