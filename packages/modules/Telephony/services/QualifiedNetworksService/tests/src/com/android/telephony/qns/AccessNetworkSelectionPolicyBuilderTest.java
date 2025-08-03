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

package com.android.telephony.qns;

import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP;
import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ;
import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI;
import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP;

import static com.android.telephony.qns.AccessNetworkSelectionPolicyBuilder.UNAVAIL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.Rlog;

import com.android.telephony.qns.AccessNetworkSelectionPolicy.PreCondition;
import com.android.telephony.qns.QnsCarrierConfigManager.QnsConfigArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RunWith(JUnit4.class)
public class AccessNetworkSelectionPolicyBuilderTest {

    @Mock private QnsCarrierConfigManager mConfig;

    private AccessNetworkSelectionPolicyBuilder mBuilder = null;
    static final int ROVE_IN = QnsConstants.ROVE_IN;
    static final int ROVE_OUT = QnsConstants.ROVE_OUT;
    static final int IDLE = QnsConstants.CALL_TYPE_IDLE;
    static final int VOICE = QnsConstants.CALL_TYPE_VOICE;
    static final int VIDEO = QnsConstants.CALL_TYPE_VIDEO;
    static final int WIFI_PREF = QnsConstants.WIFI_PREF;
    static final int CELL_PREF = QnsConstants.CELL_PREF;
    static final int HOME = QnsConstants.COVERAGE_HOME;
    static final int ROAM = QnsConstants.COVERAGE_ROAM;
    static final int NGRAN = AccessNetworkType.NGRAN;
    static final int EUTRAN = AccessNetworkType.EUTRAN;
    static final int UTRAN = AccessNetworkType.UTRAN;
    static final int GERAN = AccessNetworkType.GERAN;
    static final int IWLAN = AccessNetworkType.IWLAN;

    private HashMap<String, QnsConfigArray> mTestConfigsMap =
            new HashMap<>() {
                {
                    put(
                            AccessNetworkType.EUTRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.WIFI_PREF,
                            new QnsConfigArray(-100, -115, -120));
                    put(
                            AccessNetworkType.EUTRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSRQ
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.WIFI_PREF,
                            new QnsConfigArray(-10, -15, -20));
                    put(
                            AccessNetworkType.IWLAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSSI
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.WIFI_PREF,
                            new QnsConfigArray(-75, -85));
                    put(
                            AccessNetworkType.NGRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_SSRSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.WIFI_PREF,
                            new QnsConfigArray(-102, -117, -122));
                    put(
                            AccessNetworkType.EUTRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_VOICE
                                    + "-"
                                    + QnsConstants.WIFI_PREF,
                            new QnsConfigArray(-95, -110, -115));
                    put(
                            AccessNetworkType.IWLAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSSI
                                    + "-"
                                    + QnsConstants.CALL_TYPE_VOICE
                                    + "-"
                                    + QnsConstants.WIFI_PREF,
                            new QnsConfigArray(-76, -86));
                    put(
                            AccessNetworkType.NGRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_SSRSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_VOICE
                                    + "-"
                                    + QnsConstants.WIFI_PREF,
                            new QnsConfigArray(-92, -102, -112));
                    put(
                            AccessNetworkType.EUTRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-103, -118, -123));
                    put(
                            AccessNetworkType.EUTRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSRQ
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-12, -16, -18));
                    put(
                            AccessNetworkType.IWLAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSSI
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-60, -75));
                    put(
                            AccessNetworkType.NGRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_SSRSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_IDLE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-100, -110, -120));
                    put(
                            AccessNetworkType.EUTRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_VOICE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-101, -116, -121));
                    put(
                            AccessNetworkType.EUTRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSRQ
                                    + "-"
                                    + QnsConstants.CALL_TYPE_VOICE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-11, -16, -20));
                    put(
                            AccessNetworkType.IWLAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_RSSI
                                    + "-"
                                    + QnsConstants.CALL_TYPE_VOICE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-70, -80));
                    put(
                            AccessNetworkType.NGRAN
                                    + "-"
                                    + SIGNAL_MEASUREMENT_TYPE_SSRSRP
                                    + "-"
                                    + QnsConstants.CALL_TYPE_VOICE
                                    + "-"
                                    + QnsConstants.CELL_PREF,
                            new QnsConfigArray(-90, -100, -110));
                }
            };

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBuilder =
                new AccessNetworkSelectionPolicyBuilder(
                        mConfig, NetworkCapabilities.NET_CAPABILITY_IMS);
        stubConfigManager();
    }

    private void stubConfigManager() {
        when(mConfig.getThresholdByPref(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenAnswer((Answer<QnsConfigArray>) invocation -> {
                        Object[] args = invocation.getArguments();
                        return mTestConfigsMap.get(
                                args[0] + "-" + args[2] + "-" + args[1] + "-"
                                        + args[3]);
                    });
        // stub threshold gap of 5 dBm
        when(mConfig.getThresholdGapWithGuardTimer(anyInt(), anyInt())).thenReturn(5);
    }

    protected static void slog(String log) {
        Rlog.d(AccessNetworkSelectionPolicyBuilderTest.class.getSimpleName(), log);
    }

    protected String[] getPolicy(int direction, int callType, int preference, int coverage) {
        PreCondition condition = new PreCondition(callType, preference, coverage);
        return mBuilder.getPolicy(direction, condition);
    }

    @Test
    public void test_PolicyMap_Default() {
        doReturn(null).when(mConfig).getPolicy(anyInt(), any());
        doReturn(false).when(mConfig).isTransportTypeSelWithoutSSInRoamSupported();
        doReturn(false).when(mConfig).isCurrentTransportTypeInVoiceCallSupported();
        doReturn(false).when(mConfig).isChooseWfcPreferredTransportInBothBadCondition(anyInt());

        String[] conditions;

        List<Integer> callTypeList = List.of(IDLE, VOICE, VIDEO);
        List<Integer> coverageList = List.of(HOME, ROAM);

        for (int callType : callTypeList) {
            for (int coverage : coverageList) {
                conditions = getPolicy(ROVE_IN, callType, WIFI_PREF, coverage);
                assertTrue(conditions.length == 1 && "Condition:WIFI_GOOD".equals(conditions[0]));
            }
        }

        for (int callType : callTypeList) {
            for (int coverage : coverageList) {
                conditions = getPolicy(ROVE_OUT, callType, WIFI_PREF, coverage);
                assertEquals(1, conditions.length);
                assertEquals("Condition:WIFI_BAD", conditions[0]);
            }
        }

        for (int callType : callTypeList) {
            for (int coverage : coverageList) {
                conditions = getPolicy(ROVE_IN, callType, CELL_PREF, coverage);
                assertEquals(1, conditions.length);
                assertEquals("Condition:WIFI_GOOD,CELLULAR_BAD", conditions[0]);
            }
        }

        for (int callType : callTypeList) {
            for (int coverage : coverageList) {
                conditions = getPolicy(ROVE_OUT, callType, CELL_PREF, coverage);
                assertEquals(2, conditions.length);
                assertEquals("Condition:CELLULAR_GOOD", conditions[0]);
                assertEquals("Condition:WIFI_BAD,CELLULAR_TOLERABLE", conditions[1]);
            }
        }
    }

    @Test
    public void test_PolicyMap_isTransportTypeSelWithoutSSInRoamSupported() {
        doReturn(null).when(mConfig).getPolicy(anyInt(), any());
        doReturn(true).when(mConfig).isTransportTypeSelWithoutSSInRoamSupported();
        doReturn(false).when(mConfig).isCurrentTransportTypeInVoiceCallSupported();
        doReturn(false).when(mConfig).isChooseWfcPreferredTransportInBothBadCondition(anyInt());
        doReturn(false).when(mConfig).allowImsOverIwlanCellularLimitedCase();

        String[] conditions;
        List<Integer> directionList = List.of(ROVE_IN, ROVE_OUT);
        List<Integer> callTypeList = List.of(IDLE, VOICE, VIDEO);
        List<Integer> preferenceList = List.of(WIFI_PREF, CELL_PREF);

        for (int preference : preferenceList) {
            for (int callType : callTypeList) {
                for (int direction : directionList) {
                    conditions = getPolicy(direction, callType, preference, ROAM);
                    if ((preference == CELL_PREF && direction == ROVE_OUT)
                            || (preference == WIFI_PREF && direction == ROVE_IN)) {
                        assertEquals(1, conditions.length);
                        assertEquals("Condition:WIFI_AVAILABLE", conditions[0]);
                    } else {
                        assertEquals(1, conditions.length);
                        assertEquals("Condition:", conditions[0]);
                    }
                }
            }
        }
    }

    @Test
    public void test_PolicyMap_isCurrentTransportTypeInVoiceCallSupported() {
        doReturn(null).when(mConfig).getPolicy(anyInt(), any());
        doReturn(false).when(mConfig).isTransportTypeSelWithoutSSInRoamSupported();
        doReturn(true).when(mConfig).isCurrentTransportTypeInVoiceCallSupported();
        doReturn(false).when(mConfig).isChooseWfcPreferredTransportInBothBadCondition(anyInt());

        String[] conditions;
        List<Integer> coverageList = List.of(HOME, ROAM);

        for (int coverage : coverageList) {
            conditions = getPolicy(ROVE_OUT, VOICE, CELL_PREF, coverage);
            assertEquals(1, conditions.length);
            assertEquals("Condition:WIFI_BAD", conditions[0]);
        }
    }

    @Test
    public void test_PolicyMap_isRoveOutOnCellularWifiBothBadSupported() {
        doReturn(null).when(mConfig).getPolicy(anyInt(), any());
        doReturn(false).when(mConfig).isTransportTypeSelWithoutSSInRoamSupported();
        doReturn(false).when(mConfig).isCurrentTransportTypeInVoiceCallSupported();
        doReturn(true).when(mConfig).isChooseWfcPreferredTransportInBothBadCondition(CELL_PREF);

        String[] conditions;
        List<Integer> callTypeList = List.of(IDLE, VOICE, VIDEO);
        List<Integer> coverageList = List.of(HOME, ROAM);

        for (int callType : callTypeList) {
            for (int coverage : coverageList) {
                conditions = getPolicy(ROVE_OUT, callType, CELL_PREF, coverage);
                assertEquals(2, conditions.length);
                assertEquals("Condition:WIFI_BAD", conditions[0]);
                assertEquals("Condition:CELLULAR_GOOD", conditions[1]);
            }
        }
    }

    @Test
    public void test_PolicyMap_isRoveInOnCellularWifiBothBadSupported() {
        doReturn(null).when(mConfig).getPolicy(anyInt(), any());
        doReturn(false).when(mConfig).isTransportTypeSelWithoutSSInRoamSupported();
        doReturn(false).when(mConfig).isCurrentTransportTypeInVoiceCallSupported();
        doReturn(true).when(mConfig).isChooseWfcPreferredTransportInBothBadCondition(WIFI_PREF);

        String[] conditions;
        List<Integer> callTypeList = List.of(IDLE, VOICE, VIDEO);
        List<Integer> coverageList = List.of(HOME, ROAM);

        for (int callType : callTypeList) {
            for (int coverage : coverageList) {
                conditions = getPolicy(ROVE_IN, callType, WIFI_PREF, coverage);
                assertEquals(2, conditions.length);
                assertEquals("Condition:WIFI_GOOD", conditions[0]);
                assertEquals("Condition:CELLULAR_BAD", conditions[1]);
            }
        }
    }

    @Test
    public void test_PolicyMap_allowImsOverIwlanCellularLimitedCase() {
        doReturn(null).when(mConfig).getPolicy(anyInt(), any());
        doReturn(true).when(mConfig).isTransportTypeSelWithoutSSInRoamSupported();
        doReturn(false).when(mConfig).isCurrentTransportTypeInVoiceCallSupported();
        doReturn(false).when(mConfig).isChooseWfcPreferredTransportInBothBadCondition(anyInt());
        doReturn(true).when(mConfig).allowImsOverIwlanCellularLimitedCase();
        doReturn(true)
                .when(mConfig)
                .isAccessNetworkAllowed(NGRAN, NetworkCapabilities.NET_CAPABILITY_IMS);
        doReturn(true)
                .when(mConfig)
                .isAccessNetworkAllowed(EUTRAN, NetworkCapabilities.NET_CAPABILITY_IMS);
        doReturn(false)
                .when(mConfig)
                .isAccessNetworkAllowed(UTRAN, NetworkCapabilities.NET_CAPABILITY_IMS);
        doReturn(false)
                .when(mConfig)
                .isAccessNetworkAllowed(GERAN, NetworkCapabilities.NET_CAPABILITY_IMS);
        doReturn(false)
                .when(mConfig)
                .isAccessNetworkAllowed(IWLAN, NetworkCapabilities.NET_CAPABILITY_IMS);

        String[] conditions;
        List<Integer> directionList = List.of(ROVE_IN, ROVE_OUT);
        List<Integer> callTypeList = List.of(IDLE, VOICE, VIDEO);

        for (int callType : callTypeList) {
            for (int direction : directionList) {
                int preference = WIFI_PREF;
                conditions = getPolicy(direction, callType, preference, ROAM);
                if (direction == ROVE_IN) {
                    assertEquals(4, conditions.length);
                    assertEquals("Condition:WIFI_AVAILABLE,NGRAN_AVAILABLE", conditions[0]);
                    assertEquals("Condition:WIFI_AVAILABLE,EUTRAN_AVAILABLE", conditions[1]);
                    assertEquals("Condition:WIFI_AVAILABLE,UTRAN_AVAILABLE", conditions[2]);
                    assertEquals("Condition:WIFI_AVAILABLE,GERAN_AVAILABLE", conditions[3]);
                } else {
                    assertEquals(0, conditions.length);
                }
                preference = CELL_PREF;
                conditions = getPolicy(direction, callType, preference, ROAM);
                if (direction == ROVE_IN) {
                    assertEquals(2, conditions.length);
                    assertEquals("Condition:WIFI_AVAILABLE,UTRAN_AVAILABLE", conditions[0]);
                    assertEquals("Condition:WIFI_AVAILABLE,GERAN_AVAILABLE", conditions[1]);
                } else {
                    assertEquals(2, conditions.length);
                    assertEquals("Condition:WIFI_AVAILABLE,NGRAN_AVAILABLE", conditions[0]);
                    assertEquals("Condition:WIFI_AVAILABLE,EUTRAN_AVAILABLE", conditions[1]);
                }
            }
        }
    }

    @Test
    public void testAddThresholdGroup_RoveIn() {
        List<ThresholdGroup> thresholdGroupList = new ArrayList<>();
        List<AccessNetworkSelectionPolicyBuilder.AnspItem> anspItemList = new ArrayList<>();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRP_TOLERABLE);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_AVAILABLE);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_IN,
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME));

        assertFalse(thresholdGroupList.isEmpty());
        assertEquals(1, thresholdGroupList.size());
        assertEquals(
                -120,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                1,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());

        anspItemList.clear();
        thresholdGroupList.clear();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRP_BAD);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.NGRAN_SSRSRP_BAD);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_RSSI_GOOD);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_IN,
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_HOME));

        assertFalse(thresholdGroupList.isEmpty());
        assertEquals(2, thresholdGroupList.size());
        assertEquals(
                -118,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -110,
                thresholdGroupList
                        .get(1)
                        .getThresholds(AccessNetworkType.NGRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -60,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -60,
                thresholdGroupList
                        .get(1)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());

        anspItemList.clear();
        thresholdGroupList.clear();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRQ_BAD);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_RSSI_GOOD);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_IN,
                new AccessNetworkSelectionPolicy.GuardingPreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_ROAM,
                        QnsConstants.GUARDING_WIFI));

        assertFalse(thresholdGroupList.isEmpty());
        assertEquals(1, thresholdGroupList.size());
        assertEquals(
                -11,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -55,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());

        anspItemList.clear();
        thresholdGroupList.clear();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRQ_TOLERABLE);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.NGRAN_SSRSRP_BAD);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_RSSI_GOOD);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_IN,
                new AccessNetworkSelectionPolicy.GuardingPreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.GUARDING_CELLULAR));

        assertFalse(thresholdGroupList.isEmpty());
        assertEquals(2, thresholdGroupList.size());
        assertEquals(
                -20,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -100,
                thresholdGroupList
                        .get(1)
                        .getThresholds(AccessNetworkType.NGRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -70,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -70,
                thresholdGroupList
                        .get(1)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());

        anspItemList.clear();
        thresholdGroupList.clear();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRP_TOLERABLE);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_IN,
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_HOME));
        assertEquals(
                -123,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());

        anspItemList.clear();
        thresholdGroupList.clear();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_RSSI_GOOD);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_IN,
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_HOME));
        assertEquals(
                -60,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());
    }

    @Test
    public void testAddThresholdGroup_RoveOut() {
        List<ThresholdGroup> thresholdGroupList = new ArrayList<>();
        List<AccessNetworkSelectionPolicyBuilder.AnspItem> anspItemList = new ArrayList<>();

        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_AVAILABLE);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_RSSI_BAD);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_OUT,
                new PreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME));

        assertFalse(thresholdGroupList.isEmpty());
        assertEquals(1, thresholdGroupList.size());
        assertEquals(
                1,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -86,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());

        anspItemList.clear();
        thresholdGroupList.clear();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRQ_GOOD);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRP_GOOD);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_RSSI_BAD);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_OUT,
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME));

        assertFalse(thresholdGroupList.isEmpty());
        assertEquals(1, thresholdGroupList.size());
        assertEquals(2, thresholdGroupList.get(0).getThresholds(AccessNetworkType.EUTRAN).size());
        assertEquals(
                -10,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -100,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(1)
                        .getThreshold());
        assertEquals(
                -85,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());

        anspItemList.clear();
        thresholdGroupList.clear();
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.EUTRAN_RSRP_GOOD);
        anspItemList.add(AccessNetworkSelectionPolicyBuilder.AnspItem.IWLAN_RSSI_BAD);
        mBuilder.addThresholdGroup(
                thresholdGroupList,
                anspItemList,
                QnsConstants.ROVE_OUT,
                new AccessNetworkSelectionPolicy.GuardingPreCondition(
                        QnsConstants.CALL_TYPE_VOICE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_ROAM,
                        QnsConstants.GUARDING_WIFI));

        assertFalse(thresholdGroupList.isEmpty());
        assertEquals(1, thresholdGroupList.size());
        assertEquals(
                -95,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.EUTRAN)
                        .get(0)
                        .getThreshold());
        assertEquals(
                -86,
                thresholdGroupList
                        .get(0)
                        .getThresholds(AccessNetworkType.IWLAN)
                        .get(0)
                        .getThreshold());
    }

    @Test
    public void testMakeUnavailableThreshold() {
        when(mConfig.getCellularSSBackHaulTimer()).thenReturn(20000);
        when(mConfig.getWIFIRssiBackHaulTimer()).thenReturn(50000);
        verifyUnavailableThreshold(
                mBuilder.makeUnavailableThreshold(AccessNetworkType.EUTRAN), 20000);
        verifyUnavailableThreshold(
                mBuilder.makeUnavailableThreshold(AccessNetworkType.IWLAN), 50000);
    }

    private void verifyUnavailableThreshold(Threshold threshold, int waitTime) {
        assertEquals(QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY, threshold.getMeasurementType());
        assertEquals(QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO, threshold.getMatchType());
        assertEquals(UNAVAIL, threshold.getThreshold());
        assertEquals(waitTime, threshold.getWaitTime());
    }

    @Test
    public void testMakeThresholdsWifiWithoutCellular() {
        when(mConfig.getWifiRssiThresholdWithoutCellular(anyInt()))
                .thenReturn(null, new QnsConfigArray(-60, -70));
        List<Threshold> thsList =
                mBuilder.makeThresholdsWifiWithoutCellular(
                        QnsConstants.ROVE_IN,
                        new PreCondition(
                                QnsConstants.CALL_TYPE_VOICE,
                                QnsConstants.WIFI_PREF,
                                QnsConstants.COVERAGE_HOME));
        assertTrue(thsList.isEmpty());

        thsList =
                mBuilder.makeThresholdsWifiWithoutCellular(
                        QnsConstants.ROVE_IN,
                        new PreCondition(
                                QnsConstants.CALL_TYPE_VOICE,
                                QnsConstants.WIFI_PREF,
                                QnsConstants.COVERAGE_HOME));
        assertNotNull(thsList.get(0));
        assertEquals(-60, thsList.get(0).getThreshold());

        thsList =
                mBuilder.makeThresholdsWifiWithoutCellular(
                        QnsConstants.ROVE_OUT,
                        new PreCondition(
                                QnsConstants.CALL_TYPE_VOICE,
                                QnsConstants.WIFI_PREF,
                                QnsConstants.COVERAGE_HOME));
        assertNotNull(thsList.get(0));
        assertEquals(-70, thsList.get(0).getThreshold());
    }
}
