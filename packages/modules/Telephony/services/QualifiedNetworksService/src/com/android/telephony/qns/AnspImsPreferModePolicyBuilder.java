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

import java.util.HashMap;
import java.util.List;

class AnspImsPreferModePolicyBuilder extends AccessNetworkSelectionPolicyBuilder {

    static final HashMap<AnspKey, String[]> sImsPreferModePolicyMap;
    AnspImsPreferModePolicyBuilder(
            QnsCarrierConfigManager configManager, int netCapability) {
        super(configManager, netCapability);
        mLogTag = "QnsAnspImsPrefBuilder";
    }

    static {
        sImsPreferModePolicyMap = new HashMap<>();
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, IDLE, WIFI_PREF), new String[] {"Condition:WIFI_AVAILABLE"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, IDLE, WIFI_PREF),
                new String[] {"Condition:EUTRAN_TOLERABLE"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, VOICE, WIFI_PREF), new String[] {"Condition:WIFI_GOOD"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, VOICE, WIFI_PREF),
                new String[] {"Condition:WIFI_BAD,EUTRAN_TOLERABLE"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, VIDEO, WIFI_PREF), new String[] {"Condition:WIFI_GOOD"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, VIDEO, WIFI_PREF),
                new String[] {"Condition:WIFI_BAD,EUTRAN_TOLERABLE"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, IDLE, CELL_PREF, ROAM),
                new String[] {"Condition:WIFI_GOOD,CELLULAR_BAD"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, IDLE, CELL_PREF, ROAM),
                new String[] {"Condition:CELLULAR_GOOD"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, VOICE, CELL_PREF, ROAM),
                new String[] {"Condition:WIFI_GOOD,CELLULAR_BAD"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, VOICE, CELL_PREF, ROAM),
                new String[] {"Condition:WIFI_BAD,EUTRAN_TOLERABLE"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, VIDEO, CELL_PREF, ROAM),
                new String[] {"Condition:WIFI_GOOD,CELLULAR_BAD"});
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, VIDEO, CELL_PREF, ROAM),
                new String[] {"Condition:WIFI_BAD,EUTRAN_TOLERABLE"});
        // Overridden rules to ims preference from cellular preference
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, IDLE, CELL_PREF, HOME),
                new String[] {
                    "Condition:WIFI_GOOD,EUTRAN_BAD",
                    "Condition:WIFI_GOOD,UTRAN_AVAILABLE",
                    "Condition:WIFI_GOOD,GERAN_AVAILABLE"
                });
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, IDLE, CELL_PREF, HOME),
                new String[] {
                    "Condition:EUTRAN_GOOD",
                    "Condition:WIFI_BAD,EUTRAN_TOLERABLE",
                    "Condition:WIFI_BAD,UTRAN_GOOD",
                    "Condition:WIFI_BAD,GERAN_GOOD"
                });
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, VOICE, CELL_PREF, HOME),
                new String[] {
                    "Condition:WIFI_GOOD,EUTRAN_BAD",
                    "Condition:WIFI_GOOD,UTRAN_AVAILABLE",
                    "Condition:WIFI_GOOD,GERAN_AVAILABLE"
                });
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, VOICE, CELL_PREF, HOME),
                new String[] {
                    "Condition:EUTRAN_GOOD",
                    "Condition:WIFI_BAD,EUTRAN_TOLERABLE",
                    "Condition:WIFI_BAD,UTRAN_GOOD",
                    "Condition:WIFI_BAD,GERAN_GOOD"
                });
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_IN, VIDEO, CELL_PREF, HOME),
                new String[] {
                    "Condition:WIFI_GOOD,EUTRAN_BAD",
                    "Condition:WIFI_GOOD,UTRAN_AVAILABLE",
                    "Condition:WIFI_GOOD,GERAN_AVAILABLE"
                });
        sImsPreferModePolicyMap.put(
                new AnspKey(ROVE_OUT, VIDEO, CELL_PREF, HOME),
                new String[] {
                    "Condition:EUTRAN_GOOD",
                    "Condition:WIFI_BAD,EUTRAN_TOLERABLE",
                    "Condition:WIFI_BAD,UTRAN_GOOD",
                    "Condition:WIFI_BAD,GERAN_GOOD"
                });
    }

    @Override
    protected String[] getPolicyInMap(
            @QnsConstants.RoveDirection int direction,
            AccessNetworkSelectionPolicy.PreCondition preCondition) {
        if (preCondition.getPreference() == WIFI_PREF) {
            return sImsPreferModePolicyMap.get(
                    new AnspKey(
                            direction, preCondition.getCallType(), preCondition.getPreference()));
        }
        return sImsPreferModePolicyMap.get(
                new AnspKey(
                        direction,
                        preCondition.getCallType(),
                        preCondition.getPreference(),
                        preCondition.getCoverage()));
    }

    @Override
    protected List<Integer> getSupportAccessNetworkTypes() {
        return List.of(
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                AccessNetworkConstants.AccessNetworkType.UTRAN,
                AccessNetworkConstants.AccessNetworkType.GERAN,
                AccessNetworkConstants.AccessNetworkType.IWLAN);
    }
}
