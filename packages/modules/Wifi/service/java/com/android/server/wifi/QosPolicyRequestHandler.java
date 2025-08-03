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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.net.DscpPolicy;
import android.net.NetworkAgent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequest;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyStatus;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Handler for QoS policy requests.
 */
public class QosPolicyRequestHandler {
    private static final String TAG = "QosPolicyRequestHandler";
    @VisibleForTesting
    public static final int PROCESSING_TIMEOUT_MILLIS = 500;

    private final String mInterfaceName;
    private final WifiNative mWifiNative;
    private final ClientModeImpl mClientModeImpl;
    private WifiNetworkAgent mNetworkAgent;
    private Handler mHandler;
    private boolean mVerboseLoggingEnabled;

    private int mQosRequestDialogToken;
    private int mNumQosPoliciesInRequest;
    private boolean mQosResourcesAvailable;
    private boolean mQosRequestIsProcessing = false;
    private List<QosPolicyStatus> mQosPolicyStatusList = new ArrayList<>();
    private List<Pair<Integer, List<QosPolicyRequest>>> mQosPolicyRequestQueue = new ArrayList<>();

    public QosPolicyRequestHandler(
            @NonNull String ifaceName, @NonNull WifiNative wifiNative,
            @NonNull ClientModeImpl clientModeImpl, @NonNull HandlerThread handlerThread) {
        mInterfaceName = ifaceName;
        mWifiNative = wifiNative;
        mClientModeImpl = clientModeImpl;
        mHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * Enable/disable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * Dump internal state regarding the policy request queue, and the request which is
     * currently being processed.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("mQosRequestDialogToken: " + mQosRequestDialogToken);
        pw.println("mNumQosPoliciesInRequest: " + mNumQosPoliciesInRequest);
        pw.println("mQosResourcesAvailable: " + mQosResourcesAvailable);
        pw.println("mQosPolicyStatusList size: " + mQosPolicyStatusList.size());
        for (QosPolicyStatus status : mQosPolicyStatusList) {
            pw.println("    Policy id: " + status.policyId + ", status: "
                    + status.statusCode);
        }
        pw.println("mQosPolicyRequestQueue size: " + mQosPolicyRequestQueue.size());
        for (Pair<Integer, List<QosPolicyRequest>> request : mQosPolicyRequestQueue) {
            pw.println("    Dialog token: " + request.first
                    + ", Num policies: " + request.second.size());
            for (QosPolicyRequest policy : request.second) {
                pw.println("        " + policy);
            }
        }
    }

    /**
     * Set the network agent.
     */
    public void setNetworkAgent(WifiNetworkAgent wifiNetworkAgent) {
        WifiNetworkAgent oldNetworkAgent = mNetworkAgent;
        mNetworkAgent = wifiNetworkAgent;
        if (mNetworkAgent == null) {
            mQosPolicyStatusList.clear();
            mQosPolicyRequestQueue.clear();
            mQosRequestIsProcessing = false;
        } else if (oldNetworkAgent != null) {
            // Existing network agent was replaced by a new one.
            resetProcessingState();
        }
    }

    /**
     * Queue a QoS policy request to be processed.
     * @param dialogToken Token identifying the request.
     * @param policies List of policies that we are requesting to set.
     */
    public void queueQosPolicyRequest(int dialogToken, List<QosPolicyRequest> policies) {
        if (mNetworkAgent == null) {
            Log.e(TAG, "Attempted to call queueQosPolicyRequest, but mNetworkAgent is null");
            return;
        }
        mQosPolicyRequestQueue.add(new Pair(dialogToken, policies));
        processNextQosPolicyRequestIfPossible();
    }

    /**
     * Set the status for a policy which was processed.
     * @param policyId ID of the policy.
     * @param status code received from the NetworkAgent.
     */
    public void setQosPolicyStatus(int policyId, int status) {
        if (mNetworkAgent == null) {
            Log.e(TAG, "Attempted to call setQosPolicyStatus, but mNetworkAgent is null");
            return;
        }

        mQosPolicyStatusList.add(new QosPolicyStatus(policyId, status));
        if (status == NetworkAgent.DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES) {
            mQosResourcesAvailable = false;
        }
        sendQosPolicyResponseIfReady();
    }

    private void rejectQosPolicy(int policyId) {
        mQosPolicyStatusList.add(new QosPolicyStatus(
                policyId, NetworkAgent.DSCP_POLICY_STATUS_REQUEST_DECLINED));
        sendQosPolicyResponseIfReady();
    }

    private void sendQosPolicyResponseIfReady() {
        if (mQosRequestIsProcessing && mQosPolicyStatusList.size() == mNumQosPoliciesInRequest) {
            mWifiNative.sendQosPolicyResponse(mInterfaceName, mQosRequestDialogToken,
                    mQosResourcesAvailable, mQosPolicyStatusList);
            mQosRequestIsProcessing = false;
            mHandler.post(() -> processNextQosPolicyRequestIfPossible());
        }
    }

    private void processNextQosPolicyRequestIfPossible() {
        if (!mQosRequestIsProcessing && mQosPolicyRequestQueue.size() != 0) {
            Pair<Integer, List<QosPolicyRequest>> nextRequest = mQosPolicyRequestQueue.get(0);
            mQosPolicyRequestQueue.remove(0);
            mQosRequestIsProcessing = true;
            processQosPolicyRequest(nextRequest.first, nextRequest.second);
        }
    }

    private void checkForProcessingStall(int dialogToken) {
        if (mQosRequestIsProcessing && dialogToken == mQosRequestDialogToken) {
            Log.e(TAG, "Stop processing stalled QoS request " + dialogToken);
            resetProcessingState();
        }
    }

    private void resetProcessingState() {
        mQosRequestIsProcessing = false;
        mQosPolicyRequestQueue.clear();
        mClientModeImpl.clearQueuedQosMessages();
        mWifiNative.removeAllQosPolicies(mInterfaceName);
        if (mNetworkAgent != null) {
            mNetworkAgent.sendRemoveAllDscpPolicies();
        }
    }

    private void processQosPolicyRequest(int dialogToken, List<QosPolicyRequest> policies) {
        if (mNetworkAgent == null) {
            Log.e(TAG, "Attempted to call processQosPolicyRequest, but mNetworkAgent is null");
            return;
        }

        mQosRequestDialogToken = dialogToken;
        mQosResourcesAvailable = true;
        mNumQosPoliciesInRequest = policies.size();
        mQosPolicyStatusList.clear();

        if (policies.size() == 0) {
            sendQosPolicyResponseIfReady();
            return;
        }

        // Reject entire batch if any duplicate policy id's exist.
        Set<Byte> uniquePolicyIds = new HashSet<>();
        for (QosPolicyRequest policy : policies) {
            uniquePolicyIds.add(policy.policyId);
        }
        if (policies.size() != uniquePolicyIds.size()) {
            for (QosPolicyRequest policy : policies) {
                rejectQosPolicy(policy.policyId);
            }
            return;
        }

        if (SdkLevel.isAtLeastT()) {
            for (QosPolicyRequest policy : policies) {
                if (policy.isRemoveRequest()) {
                    mNetworkAgent.sendRemoveDscpPolicy(policy.policyId);
                } else if (policy.isAddRequest()) {
                    if (!policy.classifierParams.isValid) {
                        rejectQosPolicy(policy.policyId);
                        continue;
                    }
                    DscpPolicy.Builder builder = new DscpPolicy.Builder(
                            policy.policyId, policy.dscp)
                            .setSourcePort(policy.classifierParams.srcPort)
                            .setProtocol(policy.classifierParams.protocol)
                            .setDestinationPortRange(policy.classifierParams.dstPortRange);

                    // Only set src and dest IP if a value exists in classifierParams.
                    if (policy.classifierParams.hasSrcIp) {
                        builder.setSourceAddress(policy.classifierParams.srcIp);
                    }
                    if (policy.classifierParams.hasDstIp) {
                        builder.setDestinationAddress(policy.classifierParams.dstIp);
                    }

                    try {
                        mNetworkAgent.sendAddDscpPolicy(builder.build());
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Unable to send DSCP policy ", e);
                        rejectQosPolicy(policy.policyId);
                        continue;
                    }
                } else {
                    Log.e(TAG, "Unknown request type received");
                    rejectQosPolicy(policy.policyId);
                    continue;
                }
            }
            mHandler.postDelayed(() -> checkForProcessingStall(dialogToken),
                    PROCESSING_TIMEOUT_MILLIS);
        }
    }
}
