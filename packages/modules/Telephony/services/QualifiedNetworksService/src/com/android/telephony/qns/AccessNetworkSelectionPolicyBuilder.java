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

import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.SignalThresholdInfo;
import android.util.Log;

import com.android.telephony.qns.AccessNetworkSelectionPolicy.PreCondition;
import com.android.telephony.qns.QnsCarrierConfigManager.QnsConfigArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

class AccessNetworkSelectionPolicyBuilder {

    static final int WLAN = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
    static final int WWAN = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    static final int ROVE_IN = QnsConstants.ROVE_IN;
    static final int ROVE_OUT = QnsConstants.ROVE_OUT;
    static final int IDLE = QnsConstants.CALL_TYPE_IDLE;
    static final int VOICE = QnsConstants.CALL_TYPE_VOICE;
    static final int VIDEO = QnsConstants.CALL_TYPE_VIDEO;
    static final int WIFI_PREF = QnsConstants.WIFI_PREF;
    static final int CELL_PREF = QnsConstants.CELL_PREF;
    static final int HOME = QnsConstants.COVERAGE_HOME;
    static final int ROAM = QnsConstants.COVERAGE_ROAM;
    static final int IWLAN = AccessNetworkConstants.AccessNetworkType.IWLAN;
    static final int GUARDING_NONE = QnsConstants.GUARDING_NONE;
    static final int GUARDING_CELL = QnsConstants.GUARDING_CELLULAR;
    static final int GUARDING_WIFI = QnsConstants.GUARDING_WIFI;

    static final HashMap<AnspKey, String[]> sPolicyMap;

    static {
        // Default policy map
        sPolicyMap = new HashMap<>();
        sPolicyMap.put(new AnspKey(ROVE_IN, WIFI_PREF), new String[] {"Condition:WIFI_GOOD"});
        sPolicyMap.put(new AnspKey(ROVE_OUT, WIFI_PREF), new String[] {"Condition:WIFI_BAD"});
        sPolicyMap.put(
                new AnspKey(ROVE_IN, CELL_PREF), new String[] {"Condition:WIFI_GOOD,CELLULAR_BAD"});
        sPolicyMap.put(
                new AnspKey(ROVE_OUT, CELL_PREF),
                new String[] {"Condition:CELLULAR_GOOD", "Condition:WIFI_BAD,CELLULAR_TOLERABLE"});
    }

    protected String[] getPolicyInMap(
            @QnsConstants.RoveDirection int direction, PreCondition preCondition) {
        return sPolicyMap.get(new AnspKey(direction, preCondition.getPreference()));
    }

    protected String[] getPolicyInInternal(
            @QnsConstants.RoveDirection int direction, PreCondition preCondition) {
        return mConfig.getPolicy(direction, preCondition);
    }

    protected String[] getPolicy(
            @QnsConstants.RoveDirection int direction, PreCondition preCondition) {
        String[] internalPolicies = getPolicyInInternal(direction, preCondition);
        if (internalPolicies != null) {
            return internalPolicies;
        }

        if (mConfig.isTransportTypeSelWithoutSSInRoamSupported()
                && preCondition.getCoverage() == QnsConstants.COVERAGE_ROAM) {
            if (mConfig.allowImsOverIwlanCellularLimitedCase()) {
                List<Integer> supportedAccessNetworks = getSupportAccessNetworkTypes();
                List<String> policyImsOverIwlan = new ArrayList<>();
                for (int accessNetwork : supportedAccessNetworks) {
                    if (accessNetwork == IWLAN) {
                        continue;
                    }
                    if (mConfig.isAccessNetworkAllowed(accessNetwork, mNetCapability)) {
                        if (preCondition.getPreference() == QnsConstants.CELL_PREF
                                && direction == QnsConstants.ROVE_OUT) {
                            String name =
                                    QnsConstants.accessNetworkTypeToString(accessNetwork)
                                            + "_AVAILABLE";
                            policyImsOverIwlan.add("Condition:WIFI_AVAILABLE," + name);
                        } else if (preCondition.getPreference() == QnsConstants.WIFI_PREF
                                && direction == QnsConstants.ROVE_IN) {
                            String name =
                                    QnsConstants.accessNetworkTypeToString(accessNetwork)
                                            + "_AVAILABLE";
                            policyImsOverIwlan.add("Condition:WIFI_AVAILABLE," + name);
                        }
                    } else {
                        if (preCondition.getPreference() == QnsConstants.CELL_PREF
                                && direction == QnsConstants.ROVE_IN) {
                            String name =
                                    QnsConstants.accessNetworkTypeToString(accessNetwork)
                                            + "_AVAILABLE";
                            policyImsOverIwlan.add("Condition:WIFI_AVAILABLE," + name);
                        } else if (preCondition.getPreference() == QnsConstants.WIFI_PREF
                                && direction == QnsConstants.ROVE_IN) {
                            String name =
                                    QnsConstants.accessNetworkTypeToString(accessNetwork)
                                            + "_AVAILABLE";
                            policyImsOverIwlan.add("Condition:WIFI_AVAILABLE," + name);
                        }
                    }
                }
                return policyImsOverIwlan.toArray(String[]::new);
            } else {
                if (preCondition.getPreference() == QnsConstants.CELL_PREF
                        && direction == QnsConstants.ROVE_OUT) {
                    return new String[] {"Condition:WIFI_AVAILABLE"};
                } else if (preCondition.getPreference() == QnsConstants.WIFI_PREF
                        && direction == QnsConstants.ROVE_IN) {
                    return new String[] {"Condition:WIFI_AVAILABLE"};
                } else {
                    return new String[] {"Condition:"};
                }
            }
        }
        if (mConfig.isCurrentTransportTypeInVoiceCallSupported()
                && direction == QnsConstants.ROVE_OUT
                && preCondition.getCallType() == QnsConstants.CALL_TYPE_VOICE
                && preCondition.getPreference() == QnsConstants.CELL_PREF) {
            return new String[] {"Condition:WIFI_BAD"};
        }
        if (mConfig.isChooseWfcPreferredTransportInBothBadCondition(preCondition.getPreference())) {
            if (direction == QnsConstants.ROVE_OUT
                    && preCondition.getPreference() == QnsConstants.CELL_PREF) {
                return new String[] {"Condition:WIFI_BAD", "Condition:CELLULAR_GOOD"};
            } else if (direction == QnsConstants.ROVE_IN
                    && preCondition.getPreference() == QnsConstants.WIFI_PREF) {
                return new String[] {"Condition:WIFI_GOOD", "Condition:CELLULAR_BAD"};
            }
        }

        return getPolicyInMap(direction, preCondition);
    }

    protected List<Integer> getSupportAccessNetworkTypes() {
        return List.of(
                AccessNetworkType.NGRAN,
                AccessNetworkType.EUTRAN,
                AccessNetworkType.UTRAN,
                AccessNetworkType.GERAN,
                AccessNetworkType.IWLAN);
    }

    public static synchronized Map<PreCondition, List<AccessNetworkSelectionPolicy>> build(
            QnsCarrierConfigManager configManager, int netCapability) {
        AccessNetworkSelectionPolicyBuilder builder;
        if (configManager.isOverrideImsPreferenceSupported()) {
            builder = new AnspImsPreferModePolicyBuilder(configManager, netCapability);
        } else {
            builder = new AccessNetworkSelectionPolicyBuilder(configManager, netCapability);
        }
        return builder.buildAnsp();
    }

    protected void log(String log) {
        Log.d(mLogTag, log);
    }

    protected String mLogTag = "QnsAnspBuilder";
    protected final QnsCarrierConfigManager mConfig;
    protected final int mNetCapability;

    AccessNetworkSelectionPolicyBuilder(QnsCarrierConfigManager configManager, int netCapability) {
        mConfig = configManager;
        mNetCapability = netCapability;
    }

    protected Map<PreCondition, List<AccessNetworkSelectionPolicy>> buildAnsp() {
        List<Integer> directionList = List.of(ROVE_IN, ROVE_OUT);
        List<Integer> callTypeList = List.of(IDLE, VOICE, VIDEO);
        List<Integer> preferenceList = List.of(WIFI_PREF, CELL_PREF);
        List<Integer> coverageList = List.of(HOME, ROAM);
        List<Integer> guardingList = List.of(GUARDING_NONE, GUARDING_CELL, GUARDING_WIFI);

        Map<PreCondition, List<AccessNetworkSelectionPolicy>> allPolicies = new HashMap<>();
        boolean enabledGuardingPreCondition = mConfig.hasThresholdGapWithGuardTimer();
        for (int coverage : coverageList) {
            for (int preference : preferenceList) {
                for (int callType : callTypeList) {
                    for (int direction : directionList) {
                        if (enabledGuardingPreCondition) {
                            for (int guarding : guardingList) {
                                if (direction == ROVE_IN && guarding == GUARDING_CELL) {
                                    continue;
                                }
                                if (direction == ROVE_OUT && guarding == GUARDING_WIFI) {
                                    continue;
                                }
                                PreCondition preCondition =
                                        new AccessNetworkSelectionPolicy.GuardingPreCondition(
                                                callType, preference, coverage, guarding);
                                AccessNetworkSelectionPolicy ansp =
                                        buildAccessNetworkSelectionPolicy(direction, preCondition);
                                allPolicies.computeIfAbsent(
                                        ansp.getPreCondition(), k -> new ArrayList<>());
                                allPolicies.get(ansp.getPreCondition()).add(ansp);
                            }
                        } else {
                            PreCondition preCondition =
                                    new PreCondition(callType, preference, coverage);
                            AccessNetworkSelectionPolicy ansp =
                                    buildAccessNetworkSelectionPolicy(direction, preCondition);
                            allPolicies.computeIfAbsent(
                                    ansp.getPreCondition(), k -> new ArrayList<>());
                            allPolicies.get(ansp.getPreCondition()).add(ansp);
                        }
                    }
                }
            }
        }
        return allPolicies;
    }

    protected AccessNetworkSelectionPolicy buildAccessNetworkSelectionPolicy(
            @QnsConstants.RoveDirection int direction, PreCondition preCondition) {
        int transportType = direction == ROVE_IN ? WLAN : WWAN;
        return new AccessNetworkSelectionPolicy(
                mNetCapability,
                transportType,
                preCondition,
                makeThresholdGroups(direction, preCondition));
    }

    protected List<ThresholdGroup> makeThresholdGroups(
            @QnsConstants.RoveDirection int direction, PreCondition preCondition) {
        String[] policy = getPolicy(direction, preCondition);
        List<ThresholdGroup> thresholdGroups = new ArrayList<>();
        if (policy == null) {
            return thresholdGroups;
        }

        for (String condition : policy) {
            List<AnspItem> anspItems = parseCondition(condition, preCondition);
            addThresholdGroup(thresholdGroups, anspItems, direction, preCondition);
        }

        List<Threshold> wifiWithoutThs = makeThresholdsWifiWithoutCellular(direction, preCondition);
        if (!wifiWithoutThs.isEmpty()) {
            addThresholdGroup(thresholdGroups, wifiWithoutThs);
        }

        return thresholdGroups;
    }

    protected List<AnspItem> parseCondition(String condition, PreCondition preCondition) {
        List<AnspItem> anspItems = AnspItem.parseToPrimitives(condition);
        List<Integer> supportedAccessNetworkTypes = getSupportAccessNetworkTypes();
        List<AnspItem> wifiAnspItems = new ArrayList<>();
        List<AnspItem> cellAnspItems = new ArrayList<>();
        List<AnspItem> wifiAvailableAnspItems = new ArrayList<>();
        List<AnspItem> cellAvailableAnspItems = new ArrayList<>();
        for (int supportedAccessNetwork : supportedAccessNetworkTypes) {
            boolean bHasThreshold = false;
            boolean bAddAvailable = false;
            for (AnspItem anspItem : anspItems) {
                if (supportedAccessNetwork != anspItem.getAccessNetwork()) {
                    continue;
                }
                if (hasThreshold(anspItem, preCondition)) {
                    bHasThreshold = true;
                    if (supportedAccessNetwork == IWLAN) {
                        wifiAnspItems.add(anspItem);
                    } else {
                        cellAnspItems.add(anspItem);
                    }
                } else {
                    bAddAvailable = true;
                }
            }

            if (!bHasThreshold && bAddAvailable) {
                String itemName =
                        QnsConstants.accessNetworkTypeToString(supportedAccessNetwork)
                                + "_AVAILABLE";
                if (supportedAccessNetwork == IWLAN) {
                    wifiAvailableAnspItems.add(AnspItem.find(itemName));
                } else {
                    cellAvailableAnspItems.add(AnspItem.find(itemName));
                }
            }
        }
        if (!wifiAnspItems.isEmpty() && !cellAvailableAnspItems.isEmpty()) {
            cellAnspItems.addAll(cellAvailableAnspItems);
        }
        if (!cellAnspItems.isEmpty() && !wifiAvailableAnspItems.isEmpty()) {
            wifiAnspItems.addAll(wifiAvailableAnspItems);
        }

        wifiAnspItems.addAll(cellAnspItems);
        return wifiAnspItems;
    }

    protected void addThresholdGroup(
            List<ThresholdGroup> thresholdGroups, List<Threshold> thresholds) {
        for (ThresholdGroup thresholdGroup : thresholdGroups) {
            if (thresholdGroup.identicalThreshold(thresholds)) {
                return;
            }
        }
        thresholdGroups.add(new ThresholdGroup(thresholds));
    }

    protected void addThresholdGroup(
            List<ThresholdGroup> thresholdGroups,
            List<AnspItem> anspItems,
            @QnsConstants.RoveDirection int direction,
            PreCondition preCondition) {
        if (anspItems == null || anspItems.isEmpty()) {
            return;
        }

        List<AnspItem> wifiAnspItems = new ArrayList<>();
        List<AnspItem> cellAnspItems = new ArrayList<>();
        List<Integer> supportedAccessNetworkTypes = getSupportAccessNetworkTypes();
        for (AnspItem anspItem : anspItems) {
            if (anspItem.getAccessNetwork() == IWLAN) {
                wifiAnspItems.add(anspItem);
            } else {
                cellAnspItems.add(anspItem);
            }
        }

        if (direction == QnsConstants.ROVE_IN) {
            if (!wifiAnspItems.isEmpty() && !cellAnspItems.isEmpty()) {
                for (AnspItem wifi : wifiAnspItems) {
                    for (AnspItem cell : cellAnspItems) {
                        Threshold wifiTh = makeThreshold(wifi, direction, preCondition);
                        Threshold cellTh = makeThreshold(cell, direction, preCondition);
                        addThresholdGroup(thresholdGroups, List.of(wifiTh, cellTh));
                    }
                }
            } else {
                for (AnspItem cell : cellAnspItems) {
                    Threshold cellTh = makeThreshold(cell, direction, preCondition);
                    addThresholdGroup(thresholdGroups, List.of(cellTh));
                }
                for (AnspItem wifi : wifiAnspItems) {
                    Threshold wifiTh = makeThreshold(wifi, direction, preCondition);
                    addThresholdGroup(thresholdGroups, List.of(wifiTh));
                }
            }
        } else { // ROVE_OUT
            for (int supportedAccessNetwork : supportedAccessNetworkTypes) {
                if (supportedAccessNetwork == IWLAN) {
                    continue;
                }
                List<Threshold> thresholdList = new ArrayList<>();
                for (AnspItem wifi : wifiAnspItems) {
                    Threshold wifiTh = makeThreshold(wifi, direction, preCondition);
                    thresholdList.add(wifiTh);
                }
                if (!cellAnspItems.isEmpty()) {
                    boolean isAddedThreshold = false;
                    for (AnspItem cell : cellAnspItems) {
                        if (cell.getAccessNetwork() == supportedAccessNetwork) {
                            Threshold cellTh = makeThreshold(cell, direction, preCondition);
                            thresholdList.add(cellTh);
                            isAddedThreshold = true;
                        }
                    }
                    if (!isAddedThreshold) {
                        continue;
                    }
                }
                if (!thresholdList.isEmpty()) {
                    addThresholdGroup(thresholdGroups, thresholdList);
                }
            }
        }
    }

    private Threshold makeThreshold(
            AnspItem anspItem,
            @QnsConstants.RoveDirection int direction,
            PreCondition preCondition) {
        int adjustThreshold = 0;
        if (preCondition instanceof AccessNetworkSelectionPolicy.GuardingPreCondition) {
            AccessNetworkSelectionPolicy.GuardingPreCondition guardingPreCondition =
                    (AccessNetworkSelectionPolicy.GuardingPreCondition) preCondition;
            if (direction == ROVE_IN && guardingPreCondition.getGuarding() == GUARDING_WIFI) {
                adjustThreshold =
                        mConfig.getThresholdGapWithGuardTimer(
                                anspItem.getAccessNetwork(), anspItem.getMeasurementType());
            }
        }
        return new Threshold(
                anspItem.getAccessNetwork(),
                anspItem.getMeasurementType(),
                getThreshold(anspItem, preCondition) + adjustThreshold,
                anspItem.getMatchType(),
                getBackHaulTimer(anspItem.getAccessNetwork()));
    }

    protected boolean hasThreshold(AnspItem anspItem, PreCondition preCondition) {
        return getThreshold(anspItem, preCondition) != QnsConfigArray.INVALID;
    }

    protected int getThreshold(AnspItem anspItem, PreCondition preCondition) {
        if (anspItem.getMeasurementType() == AVAILABILITY) {
            if (anspItem.getQualityType() == AVAIL || anspItem.getQualityType() == UNAVAIL) {
                return anspItem.getQualityType();
            }
            return QnsConfigArray.INVALID;
        }
        QnsConfigArray thresholds =
                mConfig.getThresholdByPref(
                        anspItem.getAccessNetwork(),
                        preCondition.getCallType(),
                        anspItem.getMeasurementType(),
                        preCondition.getPreference());
        if (thresholds == null) {
            return QnsConfigArray.INVALID;
        }
        switch (anspItem.getQualityType()) {
            case GOOD:
                return thresholds.mGood;
            case BAD:
                return thresholds.mBad;
            case TOLERABLE:
                if (thresholds.mWorst != QnsConfigArray.INVALID) {
                    return thresholds.mWorst;
                }
                return thresholds.mBad;
        }
        return QnsConfigArray.INVALID;
    }

    protected Threshold makeUnavailableThreshold(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork) {
        int backHaulTimer = getBackHaulTimer(accessNetwork);
        return new Threshold(
                accessNetwork,
                QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY,
                UNAVAIL,
                QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO,
                backHaulTimer);
    }

    protected List<Threshold> makeThresholdsWifiWithoutCellular(
            @QnsConstants.RoveDirection int direction, PreCondition preCondition) {
        List<Threshold> thresholds = new ArrayList<>();
        int backHaulTimer = getBackHaulTimer(AccessNetworkConstants.AccessNetworkType.IWLAN);

        QnsConfigArray threshold =
                mConfig.getWifiRssiThresholdWithoutCellular(preCondition.getCallType());
        if (threshold == null) {
            return thresholds;
        }
        if (threshold.mGood != QnsConfigArray.INVALID && direction == QnsConstants.ROVE_IN) {
            thresholds.add(
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.IWLAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                            threshold.mGood,
                            QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                            backHaulTimer));
        }
        if (threshold.mBad != QnsConfigArray.INVALID && direction == QnsConstants.ROVE_OUT) {
            thresholds.add(
                    new Threshold(
                            AccessNetworkConstants.AccessNetworkType.IWLAN,
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                            threshold.mBad,
                            QnsConstants.THRESHOLD_EQUAL_OR_SMALLER,
                            backHaulTimer));
        }
        if (!thresholds.isEmpty()) {
            for (int an : getSupportAccessNetworkTypes()) {
                if (an == IWLAN) {
                    continue;
                }
                thresholds.add(makeUnavailableThreshold(an));
            }
        }
        return thresholds;
    }

    private int getBackHaulTimer(int accessNetwork) {
        if (accessNetwork == AccessNetworkConstants.AccessNetworkType.IWLAN) {
            return mConfig.getWIFIRssiBackHaulTimer();
        }
        return mConfig.getCellularSSBackHaulTimer();
    }

    static final int AVAILABILITY = QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY;
    static final int EQUAL = QnsConstants.THRESHOLD_MATCH_TYPE_EQUAL_TO;
    static final int LARGER = QnsConstants.THRESHOLD_EQUAL_OR_LARGER;
    static final int SMALLER = QnsConstants.THRESHOLD_EQUAL_OR_SMALLER;
    static final int NGRAN = AccessNetworkConstants.AccessNetworkType.NGRAN;
    static final int EUTRAN = AccessNetworkConstants.AccessNetworkType.EUTRAN;
    static final int UTRAN = AccessNetworkConstants.AccessNetworkType.UTRAN;
    static final int GERAN = AccessNetworkConstants.AccessNetworkType.GERAN;
    static final int RSSI = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI;
    static final int SSRSRP = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP;
    static final int SSRSRQ = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ;
    static final int SSSINR = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR;
    static final int RSRP = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP;
    static final int RSRQ = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ;
    static final int RSSNR = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR;
    static final int RSCP = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP;
    static final int ECNO = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_ECNO;
    static final int AVAIL = QnsConstants.SIGNAL_AVAILABLE;
    static final int UNAVAIL = QnsConstants.SIGNAL_UNAVAILABLE;
    static final int GOOD = QnsConstants.POLICY_GOOD;
    static final int BAD = QnsConstants.POLICY_BAD;
    static final int TOLERABLE = QnsConstants.POLICY_TOLERABLE;

    enum AnspItem {
        IWLAN_AVAILABLE("IWLAN_AVAILABLE", IWLAN, AVAILABILITY, EQUAL, AVAIL),
        IWLAN_UNAVAILABLE("IWLAN_UNAVAILABLE", IWLAN, AVAILABILITY, EQUAL, UNAVAIL),
        IWLAN_RSSI_GOOD("IWLAN_RSSI_GOOD", IWLAN, RSSI, LARGER, GOOD),
        IWLAN_RSSI_BAD("IWLAN_RSSI_BAD", IWLAN, RSSI, SMALLER, BAD),

        NGRAN_AVAILABLE("NGRAN_AVAILABLE", NGRAN, AVAILABILITY, EQUAL, AVAIL),
        NGRAN_UNAVAILABLE("NGRAN_UNAVAILABLE", NGRAN, AVAILABILITY, EQUAL, UNAVAIL),
        NGRAN_SSRSRP_GOOD("NGRAN_SSRSRP_GOOD", NGRAN, SSRSRP, LARGER, GOOD),
        NGRAN_SSRSRP_BAD("NGRAN_SSRSRP_BAD", NGRAN, SSRSRP, SMALLER, BAD),
        NGRAN_SSRSRP_TOLERABLE("NGRAN_SSRSRP_TOLERABLE", NGRAN, SSRSRP, LARGER, TOLERABLE),
        NGRAN_SSRSRQ_GOOD("NGRAN_SSRSRQ_GOOD", NGRAN, SSRSRQ, LARGER, GOOD),
        NGRAN_SSRSRQ_BAD("NGRAN_SSRSRQ_BAD", NGRAN, SSRSRQ, SMALLER, BAD),
        NGRAN_SSRSRQ_TOLERABLE("NGRAN_SSRSRQ_TOLERABLE", NGRAN, SSRSRQ, LARGER, TOLERABLE),
        NGRAN_SSSINR_GOOD("NGRAN_SSSINR_GOOD", NGRAN, SSSINR, LARGER, GOOD),
        NGRAN_SSSINR_BAD("NGRAN_SSSINR_BAD", NGRAN, SSSINR, SMALLER, BAD),
        NGRAN_SSSINR_TOLERABLE("NGRAN_SSSINR_TOLERABLE", NGRAN, SSSINR, LARGER, TOLERABLE),

        EUTRAN_AVAILABLE("EUTRAN_AVAILABLE", EUTRAN, AVAILABILITY, EQUAL, AVAIL),
        EUTRAN_UNAVAILABLE("EUTRAN_UNAVAILABLE", EUTRAN, AVAILABILITY, EQUAL, UNAVAIL),
        EUTRAN_RSRP_GOOD("EUTRAN_RSRP_GOOD", EUTRAN, RSRP, LARGER, GOOD),
        EUTRAN_RSRP_BAD("EUTRAN_RSRP_BAD", EUTRAN, RSRP, SMALLER, BAD),
        EUTRAN_RSRP_TOLERABLE("EUTRAN_RSRP_TOLERABLE", EUTRAN, RSRP, LARGER, TOLERABLE),
        EUTRAN_RSRQ_GOOD("EUTRAN_RSRQ_GOOD", EUTRAN, RSRQ, LARGER, GOOD),
        EUTRAN_RSRQ_BAD("EUTRAN_RSRQ_BAD", EUTRAN, RSRQ, SMALLER, BAD),
        EUTRAN_RSRQ_TOLERABLE("EUTRAN_RSRQ_TOLERABLE", EUTRAN, RSRQ, LARGER, TOLERABLE),
        EUTRAN_RSSNR_GOOD("EUTRAN_RSSNR_GOOD", EUTRAN, RSSNR, LARGER, GOOD),
        EUTRAN_RSSNR_BAD("EUTRAN_RSSNR_BAD", EUTRAN, RSSNR, SMALLER, BAD),
        EUTRAN_RSSNR_TOLERABLE("EUTRAN_RSSNR_TOLERABLE", EUTRAN, RSSNR, LARGER, TOLERABLE),

        UTRAN_AVAILABLE("UTRAN_AVAILABLE", UTRAN, AVAILABILITY, EQUAL, AVAIL),
        UTRAN_UNAVAILABLE("UTRAN_UNAVAILABLE", UTRAN, AVAILABILITY, EQUAL, UNAVAIL),
        UTRAN_RSCP_GOOD("UTRAN_RSCP_GOOD", UTRAN, RSCP, LARGER, GOOD),
        UTRAN_RSCP_BAD("UTRAN_RSCP_BAD", UTRAN, RSCP, SMALLER, BAD),
        UTRAN_RSCP_TOLERABLE("UTRAN_RSCP_TOLERABLE", UTRAN, RSCP, LARGER, TOLERABLE),
        UTRAN_ECNO_GOOD("UTRAN_ECNO_GOOD", UTRAN, ECNO, LARGER, GOOD),
        UTRAN_ECNO_BAD("UTRAN_ECNO_BAD", UTRAN, ECNO, SMALLER, BAD),
        UTRAN_ECNO_TOLERABLE("UTRAN_ECNO_TOLERABLE", UTRAN, ECNO, LARGER, TOLERABLE),

        GERAN_AVAILABLE("GERAN_AVAILABLE", GERAN, AVAILABILITY, EQUAL, AVAIL),
        GERAN_UNAVAILABLE("GERAN_UNAVAILABLE", GERAN, AVAILABILITY, EQUAL, UNAVAIL),
        GERAN_RSSI_GOOD("GERAN_RSSI_GOOD", GERAN, RSSI, LARGER, GOOD),
        GERAN_RSSI_BAD("GERAN_RSSI_BAD", GERAN, RSSI, SMALLER, BAD),
        GERAN_RSSI_TOLERABLE("GERAN_RSSI_TOLERABLE", GERAN, RSSI, LARGER, TOLERABLE),

        IWLAN_GOOD("IWLAN_GOOD", new AnspItem[] {IWLAN_RSSI_GOOD}),
        IWLAN_BAD("IWLAN_BAD", new AnspItem[] {IWLAN_RSSI_BAD}),
        WIFI_AVAILABLE("WIFI_AVAILABLE", new AnspItem[] {IWLAN_AVAILABLE}),
        WIFI_UNAVAILABLE("WIFI_UNAVAILABLE", new AnspItem[] {IWLAN_UNAVAILABLE}),
        WIFI_GOOD("WIFI_GOOD", new AnspItem[] {IWLAN_GOOD}),
        WIFI_BAD("WIFI_BAD", new AnspItem[] {IWLAN_BAD}),

        NGRAN_GOOD(
                "NGRAN_GOOD",
                new AnspItem[] {NGRAN_SSRSRP_GOOD, NGRAN_SSRSRQ_GOOD, NGRAN_SSSINR_GOOD}),
        NGRAN_BAD(
                "NGRAN_BAD", new AnspItem[] {NGRAN_SSRSRP_BAD, NGRAN_SSRSRQ_BAD, NGRAN_SSSINR_BAD}),
        NGRAN_TOLERABLE(
                "NGRAN_TOLERABLE",
                new AnspItem[] {
                    NGRAN_SSRSRP_TOLERABLE, NGRAN_SSRSRQ_TOLERABLE, NGRAN_SSSINR_TOLERABLE
                }),

        EUTRAN_GOOD(
                "EUTRAN_GOOD",
                new AnspItem[] {EUTRAN_RSRP_GOOD, EUTRAN_RSRQ_GOOD, EUTRAN_RSSNR_GOOD}),
        EUTRAN_BAD(
                "EUTRAN_BAD", new AnspItem[] {EUTRAN_RSRP_BAD, EUTRAN_RSRQ_BAD, EUTRAN_RSSNR_BAD}),
        EUTRAN_TOLERABLE(
                "EUTRAN_TOLERABLE",
                new AnspItem[] {
                    EUTRAN_RSRP_TOLERABLE, EUTRAN_RSRQ_TOLERABLE, EUTRAN_RSSNR_TOLERABLE
                }),

        UTRAN_GOOD("UTRAN_GOOD", new AnspItem[] {UTRAN_RSCP_GOOD, UTRAN_ECNO_GOOD}),
        UTRAN_BAD("UTRAN_BAD", new AnspItem[] {UTRAN_RSCP_BAD, UTRAN_ECNO_BAD}),
        UTRAN_TOLERABLE(
                "UTRAN_TOLERABLE", new AnspItem[] {UTRAN_RSCP_TOLERABLE, UTRAN_ECNO_TOLERABLE}),

        GERAN_GOOD("GERAN_GOOD", new AnspItem[] {GERAN_RSSI_GOOD}),
        GERAN_BAD("GERAN_BAD", new AnspItem[] {GERAN_RSSI_BAD}),
        GERAN_TOLERABLE("GERAN_TOLERABLE", new AnspItem[] {GERAN_RSSI_TOLERABLE}),

        CELLULAR_AVAILABLE(
                "CELLULAR_AVAILABLE",
                new AnspItem[] {
                    NGRAN_AVAILABLE, EUTRAN_AVAILABLE, UTRAN_AVAILABLE, GERAN_AVAILABLE
                }),
        CELLULAR_UNAVAILABLE(
                "CELLULAR_UNAVAILABLE",
                new AnspItem[] {
                    NGRAN_UNAVAILABLE, EUTRAN_UNAVAILABLE, UTRAN_UNAVAILABLE, GERAN_UNAVAILABLE
                }),
        CELLULAR_GOOD(
                "CELLULAR_GOOD", new AnspItem[] {NGRAN_GOOD, EUTRAN_GOOD, UTRAN_GOOD, GERAN_GOOD}),
        CELLULAR_BAD("CELLULAR_BAD", new AnspItem[] {NGRAN_BAD, EUTRAN_BAD, UTRAN_BAD, GERAN_BAD}),
        CELLULAR_TOLERABLE(
                "CELLULAR_TOLERABLE",
                new AnspItem[] {
                    NGRAN_TOLERABLE, EUTRAN_TOLERABLE, UTRAN_TOLERABLE, GERAN_TOLERABLE
                }),
        ;
        private static final Map<String, AnspItem> sAnspItemMap;

        static {
            sAnspItemMap =
                    Collections.unmodifiableMap(
                            Arrays.stream(values())
                                    .collect(
                                            Collectors.toMap(
                                                    AnspItem::getName,
                                                    anspItem -> anspItem,
                                                    (a, b) -> b)));
        }

        private final String mName;
        private final int mAccessNetwork;
        private final int mMeasurementType;
        private final int mMatchType;
        private final int mQualityType;
        private final AnspItem[] mAnspItems;

        AnspItem(String name, AnspItem[] items) {
            mName = name;
            mAnspItems = items;
            mAccessNetwork = -1;
            mMeasurementType = -1;
            mMatchType = -1;
            mQualityType = -1;
        }

        AnspItem(
                String name,
                int accessNetwork,
                int measurementType,
                int matchType,
                int qualityType) {
            mName = name;
            mAnspItems = null;
            mAccessNetwork = accessNetwork;
            mMeasurementType = measurementType;
            mMatchType = matchType;
            mQualityType = qualityType;
        }

        private String getName() {
            return mName;
        }

        static AnspItem find(String item) {
            return sAnspItemMap.get(item);
        }

        static List<AnspItem> parseToPrimitives(String condition) {
            List<AnspItem> primitives = new ArrayList<>();
            if (condition == null) {
                return primitives;
            }

            if (condition.startsWith("Condition:")) {
                StringTokenizer st = new StringTokenizer(condition, ":,");
                st.nextToken();
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    AnspItem anspItem = AnspItem.find(token);
                    primitives.addAll(anspItem.toPrimitives());
                }
            }
            return primitives;
        }

        private Collection<AnspItem> toPrimitives() {
            if (isPrimitive()) {
                return List.of(this);
            }
            List<AnspItem> primitives = new ArrayList<>();
            for (AnspItem item : mAnspItems) {
                primitives.addAll(item.toPrimitives());
            }
            return primitives;
        }

        boolean isPrimitive() {
            return mAnspItems == null;
        }

        int getAccessNetwork() {
            return mAccessNetwork;
        }

        int getMeasurementType() {
            return mMeasurementType;
        }

        int getMatchType() {
            return mMatchType;
        }

        int getQualityType() {
            return mQualityType;
        }
    }

    /**
     * The class AnspKey is the AccessNetworkSelectionPolicy inner class that is used to store or
     * load policies in a hashmap.
     */
    static class AnspKey {
        private static final int INVALID = 0xFFFF;
        int mKey1;
        int mKey2;
        int mKey3;
        int mKey4;

        AnspKey(int k1, int k2) {
            this.mKey1 = k1;
            this.mKey2 = k2;
            this.mKey3 = INVALID;
            this.mKey4 = INVALID;
        }

        AnspKey(int k1, int k2, int k3) {
            this.mKey1 = k1;
            this.mKey2 = k2;
            this.mKey3 = k3;
            this.mKey4 = INVALID;
        }

        AnspKey(int k1, int k2, int k3, int k4) {
            this.mKey1 = k1;
            this.mKey2 = k2;
            this.mKey3 = k3;
            this.mKey4 = k4;
        }

        @Override
        public String toString() {
            return "MultiKey{"
                    + "mKey1="
                    + mKey1
                    + ", mKey2="
                    + mKey2
                    + ", mKey3="
                    + mKey3
                    + ", mKey4="
                    + mKey4
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnspKey)) return false;
            AnspKey ak = (AnspKey) o;
            return mKey1 == ak.mKey1 && mKey2 == ak.mKey2 && mKey3 == ak.mKey3 && mKey4 == ak.mKey4;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mKey1, mKey2, mKey3, mKey4);
        }
    }
}
