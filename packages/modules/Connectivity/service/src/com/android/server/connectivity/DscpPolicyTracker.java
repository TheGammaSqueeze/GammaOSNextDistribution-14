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

package com.android.server.connectivity;

import static android.net.NetworkAgent.DSCP_POLICY_STATUS_DELETED;
import static android.net.NetworkAgent.DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES;
import static android.net.NetworkAgent.DSCP_POLICY_STATUS_POLICY_NOT_FOUND;
import static android.net.NetworkAgent.DSCP_POLICY_STATUS_REQUEST_DECLINED;
import static android.net.NetworkAgent.DSCP_POLICY_STATUS_SUCCESS;
import static android.system.OsConstants.ETH_P_ALL;

import android.annotation.NonNull;
import android.net.DscpPolicy;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.net.module.util.BpfMap;
import com.android.net.module.util.Struct;
import com.android.net.module.util.TcUtils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * DscpPolicyTracker has a single entry point from ConnectivityService handler.
 * This guarantees that all code runs on the same thread and no locking is needed.
 */
public class DscpPolicyTracker {
    // After tethering and clat priorities.
    static final short PRIO_DSCP = 5;

    private static final String TAG = DscpPolicyTracker.class.getSimpleName();
    private static final String PROG_PATH =
            "/sys/fs/bpf/net_shared/prog_dscpPolicy_schedcls_set_dscp_ether";
    // Name is "map + *.o + map_name + map". Can probably shorten this
    private static final String IPV4_POLICY_MAP_PATH = makeMapPath(
            "dscpPolicy_ipv4_dscp_policies");
    private static final String IPV6_POLICY_MAP_PATH = makeMapPath(
            "dscpPolicy_ipv6_dscp_policies");
    private static final int MAX_POLICIES = 16;

    private static String makeMapPath(String which) {
        return "/sys/fs/bpf/net_shared/map_" + which + "_map";
    }

    private Set<String> mAttachedIfaces;

    private final BpfMap<Struct.S32, DscpPolicyValue> mBpfDscpIpv4Policies;
    private final BpfMap<Struct.S32, DscpPolicyValue> mBpfDscpIpv6Policies;

    // The actual policy rules used by the BPF code to process packets
    // are in mBpfDscpIpv4Policies and mBpfDscpIpv4Policies. Both of
    // these can contain up to MAX_POLICIES rules.
    //
    // A given policy always consumes one entry in both the IPv4 and
    // IPv6 maps even if if's an IPv4-only or IPv6-only policy.
    //
    // Each interface index has a SparseIntArray of rules which maps a
    // policy ID to the index of the corresponding rule in the maps.
    // mIfaceIndexToPolicyIdBpfMapIndex maps the interface index to
    // the per-interface SparseIntArray.
    private final HashMap<Integer, SparseIntArray> mIfaceIndexToPolicyIdBpfMapIndex;

    public DscpPolicyTracker() throws ErrnoException {
        mAttachedIfaces = new HashSet<String>();
        mIfaceIndexToPolicyIdBpfMapIndex = new HashMap<Integer, SparseIntArray>();
        mBpfDscpIpv4Policies = new BpfMap<>(IPV4_POLICY_MAP_PATH,
                Struct.S32.class, DscpPolicyValue.class);
        mBpfDscpIpv6Policies = new BpfMap<>(IPV6_POLICY_MAP_PATH,
                Struct.S32.class, DscpPolicyValue.class);
    }

    private boolean isUnusedIndex(int index) {
        for (SparseIntArray ifacePolicies : mIfaceIndexToPolicyIdBpfMapIndex.values()) {
            if (ifacePolicies.indexOfValue(index) >= 0) return false;
        }
        return true;
    }

    private int getFirstFreeIndex() {
        if (mIfaceIndexToPolicyIdBpfMapIndex.size() == 0) return 0;
        for (int i = 0; i < MAX_POLICIES; i++) {
            if (isUnusedIndex(i)) {
                return i;
            }
        }
        return MAX_POLICIES;
    }

    private int findIndex(int policyId, int ifIndex) {
        SparseIntArray ifacePolicies = mIfaceIndexToPolicyIdBpfMapIndex.get(ifIndex);
        if (ifacePolicies != null) {
            final int existingIndex = ifacePolicies.get(policyId, -1);
            if (existingIndex != -1) {
                return existingIndex;
            }
        }

        final int firstIndex = getFirstFreeIndex();
        if (firstIndex >= MAX_POLICIES) {
            // New policy is being added, but max policies has already been reached.
            return -1;
        }
        return firstIndex;
    }

    private void sendStatus(NetworkAgentInfo nai, int policyId, int status) {
        try {
            nai.networkAgent.onDscpPolicyStatusUpdated(policyId, status);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed update policy status: ", e);
        }
    }

    private boolean matchesIpv4(DscpPolicy policy) {
        return ((policy.getDestinationAddress() == null
                       || policy.getDestinationAddress() instanceof Inet4Address)
            && (policy.getSourceAddress() == null
                        || policy.getSourceAddress() instanceof Inet4Address));
    }

    private boolean matchesIpv6(DscpPolicy policy) {
        return ((policy.getDestinationAddress() == null
                       || policy.getDestinationAddress() instanceof Inet6Address)
            && (policy.getSourceAddress() == null
                        || policy.getSourceAddress() instanceof Inet6Address));
    }

    private int getIfaceIndex(NetworkAgentInfo nai) {
        String iface = nai.linkProperties.getInterfaceName();
        NetworkInterface netIface;
        try {
            netIface = NetworkInterface.getByName(iface);
        } catch (IOException e) {
            Log.e(TAG, "Unable to get iface index for " + iface + ": " + e);
            netIface = null;
        }
        return (netIface != null) ? netIface.getIndex() : 0;
    }

    private int addDscpPolicyInternal(DscpPolicy policy, int ifIndex) {
        // If there is no existing policy with a matching ID, and we are already at
        // the maximum number of policies then return INSUFFICIENT_PROCESSING_RESOURCES.
        SparseIntArray ifacePolicies = mIfaceIndexToPolicyIdBpfMapIndex.get(ifIndex);
        if (ifacePolicies == null) {
            ifacePolicies = new SparseIntArray(MAX_POLICIES);
        }

        // Currently all classifiers are supported, if any are removed return
        // DSCP_POLICY_STATUS_REQUESTED_CLASSIFIER_NOT_SUPPORTED,
        // and for any other generic error DSCP_POLICY_STATUS_REQUEST_DECLINED

        final int addIndex = findIndex(policy.getPolicyId(), ifIndex);
        if (addIndex == -1) {
            return DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES;
        }

        try {
            // Add v4 policy to mBpfDscpIpv4Policies if source and destination address
            // are both null or if they are both instances of Inet4Address.
            if (matchesIpv4(policy)) {
                mBpfDscpIpv4Policies.insertOrReplaceEntry(
                        new Struct.S32(addIndex),
                        new DscpPolicyValue(policy.getSourceAddress(),
                            policy.getDestinationAddress(), ifIndex,
                            policy.getSourcePort(), policy.getDestinationPortRange(),
                            (short) policy.getProtocol(), (byte) policy.getDscpValue()));
            }

            // Add v6 policy to mBpfDscpIpv6Policies if source and destination address
            // are both null or if they are both instances of Inet6Address.
            if (matchesIpv6(policy)) {
                mBpfDscpIpv6Policies.insertOrReplaceEntry(
                        new Struct.S32(addIndex),
                        new DscpPolicyValue(policy.getSourceAddress(),
                                policy.getDestinationAddress(), ifIndex,
                                policy.getSourcePort(), policy.getDestinationPortRange(),
                                (short) policy.getProtocol(), (byte) policy.getDscpValue()));
            }

            ifacePolicies.put(policy.getPolicyId(), addIndex);
            // Only add the policy to the per interface map if the policy was successfully
            // added to both bpf maps above. It is safe to assume that if insert fails for
            // one map then it fails for both.
            mIfaceIndexToPolicyIdBpfMapIndex.put(ifIndex, ifacePolicies);
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to insert policy into map: ", e);
            return DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES;
        }

        return DSCP_POLICY_STATUS_SUCCESS;
    }

    private boolean isEthernet(String iface) {
        try {
            return TcUtils.isEthernet(iface);
        } catch (IOException e) {
            Log.e(TAG, "Failed to check ether type", e);
        }
        return false;
    }

    /**
     * Add the provided DSCP policy to the bpf map. Attach bpf program dscpPolicy to iface
     * if not already attached. Response will be sent back to nai with status.
     *
     * DSCP_POLICY_STATUS_SUCCESS - if policy was added successfully
     * DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES - if max policies were already set
     * DSCP_POLICY_STATUS_REQUEST_DECLINED - Interface index was invalid
     */
    public void addDscpPolicy(NetworkAgentInfo nai, DscpPolicy policy) {
        String iface = nai.linkProperties.getInterfaceName();
        if (!isEthernet(iface)) {
            Log.e(TAG, "DSCP policies are not supported on raw IP interfaces.");
            sendStatus(nai, policy.getPolicyId(), DSCP_POLICY_STATUS_REQUEST_DECLINED);
            return;
        }
        if (!mAttachedIfaces.contains(iface) && !attachProgram(iface)) {
            Log.e(TAG, "Unable to attach program");
            sendStatus(nai, policy.getPolicyId(),
                    DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES);
            return;
        }

        final int ifIndex = getIfaceIndex(nai);
        if (ifIndex == 0) {
            Log.e(TAG, "Iface index is invalid");
            sendStatus(nai, policy.getPolicyId(), DSCP_POLICY_STATUS_REQUEST_DECLINED);
            return;
        }

        int status = addDscpPolicyInternal(policy, ifIndex);
        sendStatus(nai, policy.getPolicyId(), status);
    }

    private void removePolicyFromMap(NetworkAgentInfo nai, int policyId, int index,
            boolean sendCallback) {
        int status = DSCP_POLICY_STATUS_POLICY_NOT_FOUND;
        try {
            mBpfDscpIpv4Policies.replaceEntry(new Struct.S32(index), DscpPolicyValue.NONE);
            mBpfDscpIpv6Policies.replaceEntry(new Struct.S32(index), DscpPolicyValue.NONE);
            status = DSCP_POLICY_STATUS_DELETED;
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to delete policy from map: ", e);
        }

        if (sendCallback) {
            sendStatus(nai, policyId, status);
        }
    }

    /**
     * Remove specified DSCP policy and detach program if no other policies are active.
     */
    public void removeDscpPolicy(NetworkAgentInfo nai, int policyId) {
        if (!mAttachedIfaces.contains(nai.linkProperties.getInterfaceName())) {
            // Nothing to remove since program is not attached. Send update back for policy id.
            sendStatus(nai, policyId, DSCP_POLICY_STATUS_POLICY_NOT_FOUND);
            return;
        }

        SparseIntArray ifacePolicies = mIfaceIndexToPolicyIdBpfMapIndex.get(getIfaceIndex(nai));
        if (ifacePolicies == null) return;

        final int existingIndex = ifacePolicies.get(policyId, -1);
        if (existingIndex == -1) {
            Log.e(TAG, "Policy " + policyId + " does not exist in map.");
            sendStatus(nai, policyId, DSCP_POLICY_STATUS_POLICY_NOT_FOUND);
            return;
        }

        removePolicyFromMap(nai, policyId, existingIndex, true);
        ifacePolicies.delete(policyId);

        if (ifacePolicies.size() == 0) {
            detachProgram(nai.linkProperties.getInterfaceName());
        }
    }

    /**
     * Remove all DSCP policies and detach program. Send callback if requested.
     */
    public void removeAllDscpPolicies(NetworkAgentInfo nai, boolean sendCallback) {
        if (!mAttachedIfaces.contains(nai.linkProperties.getInterfaceName())) {
            // Nothing to remove since program is not attached. Send update for policy
            // id 0. The status update must contain a policy ID, and 0 is an invalid id.
            if (sendCallback) {
                sendStatus(nai, 0, DSCP_POLICY_STATUS_SUCCESS);
            }
            return;
        }

        SparseIntArray ifacePolicies = mIfaceIndexToPolicyIdBpfMapIndex.get(getIfaceIndex(nai));
        if (ifacePolicies == null) return;
        for (int i = 0; i < ifacePolicies.size(); i++) {
            removePolicyFromMap(nai, ifacePolicies.keyAt(i), ifacePolicies.valueAt(i),
                    sendCallback);
        }
        ifacePolicies.clear();
        detachProgram(nai.linkProperties.getInterfaceName());
    }

    /**
     * Attach BPF program
     */
    private boolean attachProgram(@NonNull String iface) {
        try {
            NetworkInterface netIface = NetworkInterface.getByName(iface);
            TcUtils.tcFilterAddDevBpf(netIface.getIndex(), false, PRIO_DSCP, (short) ETH_P_ALL,
                    PROG_PATH);
        } catch (IOException e) {
            Log.e(TAG, "Unable to attach to TC on " + iface + ": " + e);
            return false;
        }
        mAttachedIfaces.add(iface);
        return true;
    }

    /**
     * Detach BPF program
     */
    public void detachProgram(@NonNull String iface) {
        try {
            NetworkInterface netIface = NetworkInterface.getByName(iface);
            if (netIface != null) {
                TcUtils.tcFilterDelDev(netIface.getIndex(), false, PRIO_DSCP, (short) ETH_P_ALL);
            }
            mAttachedIfaces.remove(iface);
        } catch (IOException e) {
            Log.e(TAG, "Unable to detach to TC on " + iface + ": " + e);
        }
    }
}
