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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.telephony.AccessNetworkConstants;
import android.telephony.SignalThresholdInfo;

import com.android.telephony.qns.AccessNetworkSelectionPolicy.GuardingPreCondition;
import com.android.telephony.qns.AccessNetworkSelectionPolicy.PreCondition;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class AccessNetworkSelectionPolicyTest extends QnsTest {
    int mNetCapability = NetworkCapabilities.NET_CAPABILITY_IMS;
    int mTargetTransportType = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    private PreCondition mPreCondition =
            new PreCondition(
                    QnsConstants.CALL_TYPE_IDLE,
                    QnsConstants.CELL_PREF,
                    QnsConstants.COVERAGE_HOME);
    List<Threshold> mThresholds = new ArrayList<>();
    List<ThresholdGroup> mThresholdGroups = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
    }

    @Test
    public void testGetTargetTransportType() {
        AccessNetworkSelectionPolicy ansp =
                new AccessNetworkSelectionPolicy(
                        mNetCapability, mTargetTransportType, mPreCondition, mThresholdGroups);
        assertEquals(mTargetTransportType, ansp.getTargetTransportType());
        assertNotEquals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, ansp.getTargetTransportType());
    }

    @Test
    public void testSatisfyPrecondition() {
        AccessNetworkSelectionPolicy ansp =
                new AccessNetworkSelectionPolicy(
                        mNetCapability, mTargetTransportType, mPreCondition, mThresholdGroups);
        assertTrue(
                ansp.satisfyPrecondition(
                        new PreCondition(
                                QnsConstants.CALL_TYPE_IDLE,
                                QnsConstants.CELL_PREF,
                                QnsConstants.COVERAGE_HOME)));
        assertFalse(
                ansp.satisfyPrecondition(
                        new PreCondition(
                                QnsConstants.CALL_TYPE_VOICE,
                                QnsConstants.CELL_PREF,
                                QnsConstants.COVERAGE_HOME)));
        assertFalse(
                ansp.satisfyPrecondition(
                        new PreCondition(
                                QnsConstants.CALL_TYPE_IDLE,
                                QnsConstants.CELL_PREF,
                                QnsConstants.COVERAGE_ROAM)));
        assertFalse(
                ansp.satisfyPrecondition(
                        new PreCondition(
                                QnsConstants.CALL_TYPE_IDLE,
                                QnsConstants.WIFI_ONLY,
                                QnsConstants.COVERAGE_HOME)));
        assertFalse(
                ansp.satisfyPrecondition(
                        new PreCondition(
                                QnsConstants.CALL_TYPE_IDLE,
                                QnsConstants.WIFI_PREF,
                                QnsConstants.COVERAGE_HOME)));
    }

    @Test
    public void testSatisfiedByThreshold_thresholdGroup() {
        List<Threshold> ths = new ArrayList<>();
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                        -117,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                        -13,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        mThresholdGroups.add(new ThresholdGroup(ths));

        when(mMockCellularQm.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP))
                .thenReturn(-120);
        when(mMockCellularQm.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ))
                .thenReturn(-15)
                .thenReturn(-10);

        AccessNetworkSelectionPolicy ansp =
                new AccessNetworkSelectionPolicy(
                        mNetCapability, mTargetTransportType, mPreCondition, mThresholdGroups);

        assertFalse(
                ansp.satisfiedByThreshold(
                        null, null, false, true, AccessNetworkConstants.AccessNetworkType.EUTRAN));

        boolean result =
                ansp.satisfiedByThreshold(
                        mMockWifiQm,
                        mMockCellularQm,
                        false,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN);
        assertFalse(result);

        result =
                ansp.satisfiedByThreshold(
                        mMockWifiQm,
                        mMockCellularQm,
                        false,
                        true,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN);
        assertTrue(result);
    }

    @Test
    public void testFindUnmatchedThresholds() {
        mTargetTransportType = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        int expected = 3;
        mThresholdGroups = generateTestThresholdGroups();
        when(mMockCellularQm.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP))
                .thenReturn(-90);
        when(mMockCellularQm.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ))
                .thenReturn(-15);
        when(mMockCellularQm.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR))
                .thenReturn(-5);
        when(mMockWifiQm.getCurrentQuality(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI))
                .thenReturn(-80);
        AccessNetworkSelectionPolicy ansp =
                new AccessNetworkSelectionPolicy(
                        mNetCapability, mTargetTransportType, mPreCondition, mThresholdGroups);

        assertNull(ansp.findUnmatchedThresholds(null, mMockCellularQm));
        assertNull(ansp.findUnmatchedThresholds(mMockWifiQm, null));

        List<Threshold> unmatched = ansp.findUnmatchedThresholds(mMockWifiQm, mMockCellularQm);
        assertEquals(expected, unmatched.size());
    }

    private List<ThresholdGroup> generateTestThresholdGroups() {
        List<ThresholdGroup> thgroups = new ArrayList<>();
        List<Threshold> ths = new ArrayList<>();
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                        -117,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                        -15,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -65,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        thgroups.add(new ThresholdGroup(ths));

        ths = new ArrayList<>();
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                        -91,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
                        -1,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -85,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        thgroups.add(new ThresholdGroup(ths));
        return thgroups;
    }

    @Test
    public void testGuardingPreconditionSatisfied() {
        GuardingPreCondition guardingPreCondition =
                new GuardingPreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.GUARDING_WIFI);

        PreCondition preCondition =
                new PreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME);
        assertTrue(guardingPreCondition.satisfied(preCondition));

        preCondition =
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME);
        assertFalse(guardingPreCondition.satisfied(preCondition));

        preCondition =
                new PreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_HOME);
        assertFalse(guardingPreCondition.satisfied(preCondition));

        preCondition =
                new PreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_ROAM);
        assertFalse(guardingPreCondition.satisfied(preCondition));

        preCondition =
                new GuardingPreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.GUARDING_WIFI);
        assertTrue(guardingPreCondition.satisfied(preCondition));

        preCondition =
                new GuardingPreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.GUARDING_CELLULAR);
        assertFalse(guardingPreCondition.satisfied(preCondition));
    }

    @Test
    public void testGuardingPreconditionEquals() {
        GuardingPreCondition guardingPreCondition =
                new GuardingPreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_ROAM,
                        QnsConstants.GUARDING_CELLULAR);

        GuardingPreCondition copy = guardingPreCondition;
        assertEquals(guardingPreCondition, copy);

        PreCondition preCondition = guardingPreCondition;
        assertEquals(guardingPreCondition, preCondition);

        preCondition =
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_ROAM);
        assertNotEquals(guardingPreCondition, preCondition);

        preCondition =
                new GuardingPreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.GUARDING_CELLULAR);
        assertNotEquals(guardingPreCondition, preCondition);
    }

    @Test
    public void testHasWifiThresholdWithoutCellularCondition() {
        AccessNetworkSelectionPolicy ansp =
                new AccessNetworkSelectionPolicy(
                        mNetCapability, mTargetTransportType, mPreCondition, null);
        AccessNetworkSelectionPolicy.PostCondition postCondition = ansp.new PostCondition(null);
        assertFalse(postCondition.hasWifiThresholdWithoutCellularCondition());

        List<ThresholdGroup> thgroups = new ArrayList<>();
        List<Threshold> ths = new ArrayList<>();
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                        -117,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -65,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        thgroups.add(new ThresholdGroup(ths));

        postCondition = ansp.new PostCondition(thgroups);
        assertFalse(postCondition.hasWifiThresholdWithoutCellularCondition());

        thgroups.clear();
        ths.clear();
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        -65,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        ths.add(
                new Threshold(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                        QnsConstants.SIGNAL_UNAVAILABLE,
                        QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        thgroups.add(new ThresholdGroup(ths));

        postCondition = ansp.new PostCondition(thgroups);
        assertTrue(postCondition.hasWifiThresholdWithoutCellularCondition());
    }

    @Test
    public void testSatisfiedWithWifiLowSignalStrength() {}

    @After
    public void tearDown() {
        mThresholds.clear();
        mThresholdGroups.clear();
    }
}
