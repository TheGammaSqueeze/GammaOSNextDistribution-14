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

import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.SignalThresholdInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ThresholdGroup {
    private static final AtomicInteger sGid = new AtomicInteger();

    private final List<Threshold> mThresholds;
    private final String mTag;

    ThresholdGroup(List<Threshold> ths) {
        int groupId = sGid.getAndIncrement();
        mThresholds = alignGroupId(groupId, ths);
        mTag = ThresholdGroup.class.getSimpleName() + groupId;
    }

    private List<Threshold> alignGroupId(int groupId, List<Threshold> ths) {
        if (ths == null) {
            return new ArrayList<>();
        }
        ArrayList<Threshold> newList = new ArrayList<>(ths);
        for (Threshold th : newList) {
            th.setGroupId(groupId);
        }
        return newList;
    }

    boolean satisfiedByThreshold(
            QualityMonitor wifiMonitor,
            QualityMonitor cellMonitor,
            boolean iwlanAvailable,
            boolean cellAvailable,
            int cellularAccessNetworkType) {
        if (mThresholds == null || mThresholds.isEmpty()) {
            return false;
        }
        boolean omittedCellularAvailable = true;
        for (Threshold th : mThresholds) {
            if (th.getAccessNetwork() == AccessNetworkType.IWLAN) {
                if (!satisfy(th, wifiMonitor, iwlanAvailable, AccessNetworkType.IWLAN)) {
                    return false;
                }
            }
            if (th.getAccessNetwork() != AccessNetworkType.IWLAN) {
                omittedCellularAvailable = false;
                if (!satisfy(th, cellMonitor, cellAvailable, cellularAccessNetworkType)) {
                    return false;
                }
            }
        }
        if (omittedCellularAvailable && !cellAvailable) {
            return false;
        }
        return true;
    }

    private boolean satisfy(Threshold th, QualityMonitor monitor, boolean available, int an) {
        // availability
        if (th.getMeasurementType() == QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY) {
            if (th.getThreshold() == QnsConstants.SIGNAL_AVAILABLE
                    && available
                    && an == th.getAccessNetwork()) {
                Log.d(mTag, "satisfy " + th.toShortString() + " currentQuality:Available");
                return true;
            }
            if (th.getThreshold() == QnsConstants.SIGNAL_UNAVAILABLE
                    && (!available || an != th.getAccessNetwork())) {
                Log.d(mTag, "satisfy " + th.toShortString() + " currentQuality:Unavailable");
                return true;
            }
            Log.d(mTag, "not satisfy " + th.toShortString() + " available:" + available);
            return false;
        }

        // measurement matching
        if (th.getAccessNetwork() != an) {
            return false;
        }
        int cq = monitor.getCurrentQuality(th.getAccessNetwork(), th.getMeasurementType());
        if (th.isMatching(cq)) {
            Log.d(mTag, "satisfy " + th.toShortString() + " currentQuality:" + cq);
            return true;
        } else {
            Log.d(mTag, "not satisfy " + th.toShortString() + " currentQuality:" + cq);
            return false;
        }
    }

    List<Threshold> findUnmatchedThresholds(
            QualityMonitor wifiMonitor, QualityMonitor cellMonitor) {
        List<Threshold> tl = new ArrayList<>();
        if (mThresholds == null || mThresholds.isEmpty()) {
            return tl;
        }

        for (Threshold th : mThresholds) {
            if (th.getMeasurementType() == QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY) {
                continue;
            }

            boolean isIwlan = th.getAccessNetwork() == AccessNetworkType.IWLAN;
            QualityMonitor monitor = isIwlan ? wifiMonitor : cellMonitor;
            int cq = monitor.getCurrentQuality(th.getAccessNetwork(), th.getMeasurementType());

            if (th.isMatching(cq)) {
                Log.d(mTag, "Threshold " + th.toShortString() + " is matched. current:" + cq);
            } else {
                Log.d(mTag, "Threshold " + th.toShortString() + " is not matched. current:" + cq);
                tl.add(th);
            }
        }
        return tl;
    }

    boolean hasWifiThresholdWithoutCellularCondition() {
        if (mThresholds == null || mThresholds.isEmpty()) {
            return false;
        }
        boolean foundIwlanRssiThreshold = false;
        boolean foundCellularUnavailable = false;
        for (Threshold th : mThresholds) {
            if (th.getMeasurementType() == SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI
                    && th.getAccessNetwork() == AccessNetworkType.IWLAN) {
                foundIwlanRssiThreshold = true;
            }
            if (th.getMeasurementType() == QnsConstants.SIGNAL_MEASUREMENT_AVAILABILITY
                    && th.getThreshold() == QnsConstants.SIGNAL_UNAVAILABLE
                    && th.getAccessNetwork() != AccessNetworkType.IWLAN) {
                foundCellularUnavailable = true;
            }
        }
        return foundIwlanRssiThreshold && foundCellularUnavailable;
    }

    List<Threshold> getThresholds(int accessNetworkType) {
        List<Threshold> accessNetworkTypeThresholdList = new ArrayList<>();
        for (Threshold t : mThresholds) {
            if (t.getAccessNetwork() == accessNetworkType) {
                accessNetworkTypeThresholdList.add(t);
            }
        }
        return accessNetworkTypeThresholdList;
    }

    String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (mThresholds != null && mThresholds.size() > 0) {
            for (Threshold th : mThresholds) {
                sb.append(th.toShortString()).append(",");
            }
            sb.deleteCharAt(sb.lastIndexOf(","));
        }
        sb.append(")");
        return sb.toString();
    }

    boolean identicalThreshold(List<Threshold> o) {
        if (mThresholds == o) return true;
        if (mThresholds == null || o == null) return false;
        if (mThresholds.size() != o.size()) return false;
        for (Threshold th : mThresholds) {
            boolean found = false;
            for (Threshold tho : o) {
                if (th.identicalThreshold(tho)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
