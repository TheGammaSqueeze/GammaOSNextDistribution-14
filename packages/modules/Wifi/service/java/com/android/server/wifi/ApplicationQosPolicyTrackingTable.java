/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.wifi;

import android.net.wifi.QosPolicyParams;
import android.net.wifi.WifiManager;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Table containing application-added QoS policies that are being tracked by the framework.
 */
public class ApplicationQosPolicyTrackingTable {
    private Queue<Integer> mAvailableVirtualPolicyIds = new ArrayDeque<>();

    // Mapping between a policy hash and a policy object.
    // See combinePolicyIdAndUid() for more information about the policy hash format.
    private Map<Long, QosPolicyParams> mPolicyHashToPolicyMap = new HashMap<>();
    private Map<Integer, List<Long>> mUidToPolicyHashesMap = new HashMap<>();

    public ApplicationQosPolicyTrackingTable(int minVirtualPolicyId, int maxVirtualPolicyId) {
        for (int i = minVirtualPolicyId; i <= maxVirtualPolicyId; i++) {
            mAvailableVirtualPolicyIds.add(i);
        }
    }

    private List<Integer> generateStatusList(
            int size, @WifiManager.QosRequestStatus int statusCode) {
        List<Integer> statusList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            statusList.add(statusCode);
        }
        return statusList;
    }

    /**
     * Combine the provided policyId and UID into a long with the format:
     *
     * | Bits 63-32 | Bits 31-0 |
     * |------------|-----------|
     * |  policyId  |    uid    |
     *
     * Long can be used as a unique hash identifying each policy in the table.
     */
    private static long combinePolicyIdAndUid(int policyId, int uid) {
        long shiftedPolicyId = Integer.toUnsignedLong(policyId) << 32;
        return shiftedPolicyId | Integer.toUnsignedLong(uid);
    }

    private static int getPolicyIdFromCombinedLong(long combined) {
        return (int) (combined >> 32);
    }

    private static int getUidFromCombinedLong(long combined) {
        return (int) (combined & 0xFFFFFFFF);
    }

    /**
     * Add a list of QoS policies to the tracking table.
     *
     * Each accepted policy will be assigned a virtual policy ID using
     * {@link QosPolicyParams#setTranslatedPolicyId(int)}.
     *
     * @param policies List of policies to add.
     * @param uid UID of the requesting application.
     * @return List of status codes from {@link WifiManager.QosRequestStatus}. Status list will be
     *         the same length as the input list, and each status code will correspond to the
     *         policy at that index in the input list.
     */
    public List<Integer> addPolicies(List<QosPolicyParams> policies, int uid) {
        if (mAvailableVirtualPolicyIds.size() < policies.size()) {
            // Not enough space in the table.
            return generateStatusList(
                    policies.size(), WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
        }
        List<Integer> statusList = generateStatusList(
                policies.size(), WifiManager.QOS_REQUEST_STATUS_TRACKING);

        for (int i = 0; i < policies.size(); i++) {
            QosPolicyParams policy = policies.get(i);
            long policyHash = combinePolicyIdAndUid(policy.getPolicyId(), uid);
            if (mPolicyHashToPolicyMap.containsKey(policyHash)) {
                // Policy is already in the table.
                statusList.set(i, WifiManager.QOS_REQUEST_STATUS_ALREADY_ACTIVE);
                continue;
            }

            int virtualPolicyId = mAvailableVirtualPolicyIds.remove();
            policy.setTranslatedPolicyId(virtualPolicyId);
            mPolicyHashToPolicyMap.put(policyHash, policy);
            if (!mUidToPolicyHashesMap.containsKey(uid)) {
                mUidToPolicyHashesMap.put(uid, new ArrayList<>());
            }
            mUidToPolicyHashesMap.get(uid).add(policyHash);
        }
        return statusList;
    }

    /**
     * Remove a list of policies from the tracking table.
     *
     * Method should be considered best-effort. Any policies in the batch
     * that are not found will be silently ignored.
     *
     * @param policyIds List of policy IDs which should be removed.
     * @param uid UID of the requesting application.
     */
    public void removePolicies(List<Integer> policyIds, int uid) {
        for (int policyId : policyIds) {
            long policyHash = combinePolicyIdAndUid(policyId, uid);
            QosPolicyParams policy = mPolicyHashToPolicyMap.get(policyHash);
            if (policy == null) {
                continue;
            }
            int virtualPolicyId = policy.getTranslatedPolicyId();
            mAvailableVirtualPolicyIds.add(virtualPolicyId);
            mPolicyHashToPolicyMap.remove(policyHash);
            mUidToPolicyHashesMap.get(uid).remove(Long.valueOf(policyHash));
            if (mUidToPolicyHashesMap.get(uid).isEmpty()) {
                mUidToPolicyHashesMap.remove(uid);
            }
        }
    }

    /**
     * Given a list of policies, filter out any polices that are not tracked by the table.
     *
     * @param policyList List of policies to filter.
     * @param uid UID of the requesting application.
     * @return Filtered list of policies, containing only the policies that are in the table.
     */
    public List<QosPolicyParams> filterUntrackedPolicies(
            List<QosPolicyParams> policyList, int uid) {
        List<QosPolicyParams> trackedPolicies = new ArrayList<>();
        for (QosPolicyParams policy : policyList) {
            long policyHash = combinePolicyIdAndUid(policy.getPolicyId(), uid);
            if (mPolicyHashToPolicyMap.containsKey(policyHash)) {
                trackedPolicies.add(policy);
            }
        }
        return trackedPolicies;
    }

    /**
     * Translate a list of physical policy IDs to virtual.
     *
     * Method should be considered best-effort. Any policies in the batch
     * that are not found will be silently excluded from the returned list.
     *
     * @param policyIds List of policy IDs to translate.
     * @param uid UID of the requesting application.
     * @return List of virtual policy IDs.
     */
    public List<Integer> translatePolicyIds(List<Integer> policyIds, int uid) {
        List<Integer> virtualPolicyIds = new ArrayList<>();
        for (int policyId : policyIds) {
            long policyHash = combinePolicyIdAndUid(policyId, uid);
            QosPolicyParams policy = mPolicyHashToPolicyMap.get(policyHash);
            if (policy == null) {
                continue;
            }
            virtualPolicyIds.add(policy.getTranslatedPolicyId());
        }
        return virtualPolicyIds;
    }

    /**
     * Retrieve the IDs for all policies owned by this requester.
     *
     * @param uid UID of the requesting application.
     * @return List of policy IDs.
     */
    public List<Integer> getAllPolicyIdsOwnedByUid(int uid) {
        List<Integer> policyIds = new ArrayList<>();
        List<Long> policyHashes = mUidToPolicyHashesMap.get(uid);
        if (policyHashes == null) return policyIds;
        for (long policyHash : policyHashes) {
            int policyId = getPolicyIdFromCombinedLong(policyHash);
            policyIds.add(policyId);
        }
        return policyIds;
    }

    /**
     * Check whether this requester owns any policies in the table.
     *
     * @param uid UID of the requesting application.
     * @return true if the requester owns any policies in the table, false otherwise.
     */
    public boolean tableContainsUid(int uid) {
        return mUidToPolicyHashesMap.containsKey(uid);
    }

    /**
     * Get all policies that are tracked by this table.
     *
     * @param direction Direction of the policies to retrieve.
     * @return List of policies, or empty list if there are no policies in the table.
     */
    public List<QosPolicyParams> getAllPolicies(@QosPolicyParams.Direction int direction) {
        if (mPolicyHashToPolicyMap.isEmpty()) {
            return new ArrayList<>();
        }
        return mPolicyHashToPolicyMap
                .values()
                .stream()
                .filter(p -> p.getDirection() == direction)
                .toList();
    }

    /**
     * Dump information about the internal state.
     *
     * @param pw PrintWriter to write the dump to.
     */
    public void dump(PrintWriter pw) {
        pw.println("Dump of ApplicationQosPolicyTrackingTable");
        int numAvailableVirtualPolicyIds = mAvailableVirtualPolicyIds.size();
        int numTrackedPolicies = mPolicyHashToPolicyMap.size();
        pw.println("Total table size: " + (numAvailableVirtualPolicyIds + numTrackedPolicies));
        pw.println("Num available virtual policy IDs: " + numAvailableVirtualPolicyIds);
        pw.println("Num tracked policies: " + numTrackedPolicies);
        pw.println();

        pw.println("Available virtual policy IDs: " + mAvailableVirtualPolicyIds);
        pw.println("Tracked policies:");
        for (Map.Entry<Long, QosPolicyParams> entry : mPolicyHashToPolicyMap.entrySet()) {
            long policyHash = entry.getKey();
            pw.println("  Policy ID: " + getPolicyIdFromCombinedLong(policyHash));
            pw.println("  Requester UID: " + getUidFromCombinedLong(policyHash));
            pw.println(entry.getValue());
        }
        pw.println();
    }
}
