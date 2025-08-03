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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.IListListener;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wifi.resources.R;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for QoS policy requests initiated by applications.
 */
public class ApplicationQosPolicyRequestHandler {
    private static final String TAG = "ApplicationQosPolicyRequestHandler";

    // QosPolicyParams objects contain an integer policyId in the range [1, 255],
    // while the HAL expects a byte policyId in the range [-128, 127].
    private static final int HAL_POLICY_ID_MIN = Byte.MIN_VALUE;
    private static final int HAL_POLICY_ID_MAX = Byte.MAX_VALUE;
    private static final int MAX_POLICIES_PER_TRANSACTION =
            WifiManager.getMaxNumberOfPoliciesPerQosRequest();
    private static final int DEFAULT_UID = -1;

    // HAL should automatically time out at 1000 ms. Perform a local check at 1500 ms to verify
    // that either the expected callback, or the timeout callback, was received.
    @VisibleForTesting
    protected static final int CALLBACK_TIMEOUT_MILLIS = 1500;

    private final ActiveModeWarden mActiveModeWarden;
    private final WifiNative mWifiNative;
    private final Handler mHandler;
    private final ApCallback mApCallback;
    private final ApplicationQosPolicyTrackingTable mPolicyTrackingTable;
    private final ApplicationDeathRecipient mApplicationDeathRecipient;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final Context mContext;
    private boolean mVerboseLoggingEnabled;

    private Map<String, List<QueuedRequest>> mPerIfaceRequestQueue;
    private Map<String, CallbackParams> mPendingCallbacks;
    private Map<IBinder, Integer> mApplicationBinderToUidMap;
    private Map<Integer, IBinder> mApplicationUidToBinderMap;

    private static final int REQUEST_TYPE_ADD = 0;
    private static final int REQUEST_TYPE_REMOVE = 1;

    @IntDef(prefix = { "REQUEST_TYPE_" }, value = {
            REQUEST_TYPE_ADD,
            REQUEST_TYPE_REMOVE,
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface RequestType {}

    private static class QueuedRequest {
        // Initial state.
        public final @RequestType int requestType;
        public final @Nullable List<QosPolicyParams> policiesToAdd;
        public final @Nullable List<Integer> policyIdsToRemove;
        public final @NonNull ApplicationCallback callback;
        public final @Nullable IBinder binder;
        public final int requesterUid;

        // Set during processing.
        public boolean processedOnAnyIface;
        public @Nullable List<Integer> initialStatusList;
        public @Nullable List<Byte> virtualPolicyIdsToRemove;

        QueuedRequest(@RequestType int inRequestType,
                @Nullable List<QosPolicyParams> inPoliciesToAdd,
                @Nullable List<Integer> inPolicyIdsToRemove,
                @Nullable IListListener inListener, @Nullable IBinder inBinder,
                int inRequesterUid) {
            requestType = inRequestType;
            policiesToAdd = inPoliciesToAdd;
            policyIdsToRemove = inPolicyIdsToRemove;
            callback = new ApplicationCallback(inListener);
            binder = inBinder;
            requesterUid = inRequesterUid;
            processedOnAnyIface = false;
        }

        @Override
        public String toString() {
            return "{requestType: " + requestType + ", "
                    + "policiesToAdd: " + policiesToAdd + ", "
                    + "policyIdsToRemove: " + policyIdsToRemove + ", "
                    + "callback: " + callback + ", "
                    + "binder: " + binder + ", "
                    + "requesterUid: " + requesterUid + ", "
                    + "processedOnAnyIface: " + processedOnAnyIface + ", "
                    + "initialStatusList: " + initialStatusList + ", "
                    + "virtualPolicyIdsToRemove: " + virtualPolicyIdsToRemove + "}";
        }
    }

    /**
     * Wrapper around the calling application's IListListener.
     * Ensures that the listener is only called once.
     */
    private static class ApplicationCallback {
        private @Nullable IListListener mListener;

        ApplicationCallback(@Nullable IListListener inListener) {
            mListener = inListener;
        }

        public void sendResult(List<Integer> statusList) {
            if (mListener == null) return;
            try {
                mListener.onResult(statusList);
            } catch (RemoteException e) {
                Log.e(TAG, "Listener received remote exception " + e);
            }

            // Set mListener to null to avoid calling again.
            // The application should only be notified once.
            mListener = null;
        }

        /**
         * Use when all policies should be assigned the same status code.
         * Ex. If all policies are rejected with the same error code.
         */
        public void sendResult(int size, @WifiManager.QosRequestStatus int statusCode) {
            List<Integer> statusList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                statusList.add(statusCode);
            }
            sendResult(statusList);
        }

        @Override
        public String toString() {
            return mListener != null ? mListener.toString() : "null";
        }
    }

    /**
     * Represents a request that has been sent to the HAL and is awaiting the AP callback.
     */
    private static class CallbackParams {
        public final @NonNull List<Byte> policyIds;

        CallbackParams(@NonNull List<Byte> inPolicyIds) {
            Collections.sort(inPolicyIds);
            policyIds = inPolicyIds;
        }

        public boolean matchesResults(List<SupplicantStaIfaceHal.QosPolicyStatus> resultList) {
            List<Byte> resultPolicyIds = new ArrayList<>();
            for (SupplicantStaIfaceHal.QosPolicyStatus status : resultList) {
                resultPolicyIds.add((byte) status.policyId);
            }
            Collections.sort(resultPolicyIds);
            return policyIds.equals(resultPolicyIds);
        }

        @Override
        public String toString() {
            return "{policyIds: " + policyIds + "}";
        }
    }

    private class ApCallback implements SupplicantStaIfaceHal.QosScsResponseCallback {
        @Override
        public void onApResponse(String ifaceName,
                List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList) {
            mHandler.post(() -> {
                logApCallbackMockable(ifaceName, halStatusList);
                CallbackParams expectedParams = mPendingCallbacks.get(ifaceName);
                if (expectedParams == null) {
                    Log.i(TAG, "Callback was not expected on this interface");
                    return;
                }

                if (!expectedParams.matchesResults(halStatusList)) {
                    // Silently ignore this callback if it does not match the expected parameters.
                    Log.i(TAG, "Callback was unsolicited. statusList: " + halStatusList);
                    return;
                }

                Log.i(TAG, "Expected callback was received");
                mPendingCallbacks.remove(ifaceName);
                processNextRequestIfPossible(ifaceName);
            });
        }
    }

    private class ApplicationDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
        }

        @Override
        public void binderDied(@NonNull IBinder who) {
            mHandler.post(() -> {
                Integer uid = mApplicationBinderToUidMap.get(who);
                Log.i(TAG, "Application binder died. who=" + who + ", uid=" + uid);
                if (uid == null) {
                    // Application is not registered with us.
                    return;
                }

                // Remove this application from the tracking maps
                // and clear out any policies that they own.
                mApplicationBinderToUidMap.remove(who);
                mApplicationUidToBinderMap.remove(uid);
                queueRemoveAllRequest(uid);
            });
        }
    }

    public ApplicationQosPolicyRequestHandler(@NonNull ActiveModeWarden activeModeWarden,
            @NonNull WifiNative wifiNative, @NonNull HandlerThread handlerThread,
            @NonNull DeviceConfigFacade deviceConfigFacade, @NonNull Context context) {
        mActiveModeWarden = activeModeWarden;
        mWifiNative = wifiNative;
        mHandler = new Handler(handlerThread.getLooper());
        mPerIfaceRequestQueue = new HashMap<>();
        mPendingCallbacks = new HashMap<>();
        mApplicationBinderToUidMap = new HashMap<>();
        mApplicationUidToBinderMap = new HashMap<>();
        mApCallback = new ApCallback();
        mApplicationDeathRecipient = new ApplicationDeathRecipient();
        mDeviceConfigFacade = deviceConfigFacade;
        mContext = context;
        mVerboseLoggingEnabled = false;
        mPolicyTrackingTable =
                new ApplicationQosPolicyTrackingTable(HAL_POLICY_ID_MIN, HAL_POLICY_ID_MAX);
        mWifiNative.registerQosScsResponseCallback(mApCallback);
    }

    /**
     * Enable or disable verbose logging.
     */
    public void enableVerboseLogging(boolean enable) {
        mVerboseLoggingEnabled = enable;
    }

    @VisibleForTesting
    protected void logApCallbackMockable(String ifaceName,
            List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList) {
        Log.i(TAG, "Received AP callback on " + ifaceName + ", size=" + halStatusList.size());
        if (mVerboseLoggingEnabled) {
            long numPoliciesAccepted = halStatusList.stream()
                    .filter(status -> status.statusCode
                            == SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS)
                    .count();
            Log.d(TAG, "AP accepted " + numPoliciesAccepted + " policies");
        }
    }

    /**
     * Check whether the Application QoS policy feature is enabled.
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean isFeatureEnabled() {
        // Both the experiment flag and overlay value must be enabled,
        // and the HAL must support this feature.
        return mDeviceConfigFacade.isApplicationQosPolicyApiEnabled()
                && mContext.getResources().getBoolean(
                R.bool.config_wifiApplicationCentricQosPolicyFeatureEnabled)
                && mWifiNative.isSupplicantAidlServiceVersionAtLeast(2);
    }

    /**
     * Request to add a list of new QoS policies.
     *
     * @param policies List of {@link QosPolicyParams} objects representing the policies.
     * @param listener Listener to call when the operation is complete.
     * @param uid UID of the requesting application.
     */
    public void queueAddRequest(@NonNull List<QosPolicyParams> policies,
            @NonNull IListListener listener, @NonNull IBinder binder, int uid) {
        Log.i(TAG, "Queueing add request. size=" + policies.size());
        QueuedRequest request = new QueuedRequest(
                REQUEST_TYPE_ADD, policies, null, listener, binder, uid);
        queueRequestOnAllIfaces(request);
        processNextRequestOnAllIfacesIfPossible();
    }

    /**
     * Request to remove a list of existing QoS policies.
     *
     * @param policyIds List of integer policy IDs.
     * @param uid UID of the requesting application.
     */
    public void queueRemoveRequest(@NonNull List<Integer> policyIds, int uid) {
        Log.i(TAG, "Queueing remove request. size=" + policyIds.size());
        QueuedRequest request = new QueuedRequest(
                REQUEST_TYPE_REMOVE, null, policyIds, null, null, uid);
        queueRequestOnAllIfaces(request);
        processNextRequestOnAllIfacesIfPossible();
    }

    /**
     * Request to remove all policies owned by this requester.
     *
     * @param uid UID of the requesting application.
     */
    public void queueRemoveAllRequest(int uid) {
        List<Integer> ownedPolicies = mPolicyTrackingTable.getAllPolicyIdsOwnedByUid(uid);
        Log.i(TAG, "Queueing removeAll request. numOwnedPolicies=" + ownedPolicies.size());
        if (ownedPolicies.isEmpty()) return;

        // Divide ownedPolicies into batches of size MAX_POLICIES_PER_TRANSACTION,
        // and queue each batch on all interfaces.
        List<List<Integer>> batches = divideRequestIntoBatches(ownedPolicies);
        for (List<Integer> batch : batches) {
            QueuedRequest request = new QueuedRequest(
                    REQUEST_TYPE_REMOVE, null, batch, null, null, uid);
            queueRequestOnAllIfaces(request);
        }
        processNextRequestOnAllIfacesIfPossible();
    }

    /**
     * Request to send all tracked policies to the specified interface.
     *
     * @param ifaceName Interface name to send the policies to.
     * @param includeUplink Whether stored uplink policies should be queued on this interface.
     */
    public void queueAllPoliciesOnIface(String ifaceName, boolean includeUplink) {
        List<QosPolicyParams> policyList = new ArrayList<>(mPolicyTrackingTable
                .getAllPolicies(QosPolicyParams.DIRECTION_DOWNLINK));
        if (includeUplink) {
            Log.i(TAG, "Including uplink policies");
            policyList.addAll(
                    mPolicyTrackingTable.getAllPolicies(QosPolicyParams.DIRECTION_UPLINK));
        }
        Log.i(TAG, "Queueing all policies on iface=" + ifaceName + ". numPolicies="
                + policyList.size());
        if (policyList.isEmpty()) return;

        // Divide policyList into batches of size MAX_POLICIES_PER_TRANSACTION,
        // and queue each batch on the specified interface.
        List<List<QosPolicyParams>> batches = divideRequestIntoBatches(policyList);
        for (List<QosPolicyParams> batch : batches) {
            QueuedRequest request = new QueuedRequest(
                    REQUEST_TYPE_ADD, batch, null, null, null, DEFAULT_UID);

            // Indicate that all policies have already been processed and are in the table.
            request.processedOnAnyIface = true;
            request.initialStatusList = generateStatusList(
                    batch.size(), WifiManager.QOS_REQUEST_STATUS_TRACKING);
            queueRequestOnIface(ifaceName, request);
        }
        processNextRequestIfPossible(ifaceName);
    }

    private void queueRequestOnAllIfaces(QueuedRequest request) {
        List<ClientModeManager> clientModeManagers =
                mActiveModeWarden.getInternetConnectivityClientModeManagers();
        if (clientModeManagers.size() == 0) {
            // Reject request if no ClientModeManagers are available.
            request.callback.sendResult(request.policiesToAdd.size(),
                    WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
            return;
        }

        // Pre-process each request before queueing.
        if (request.requestType == REQUEST_TYPE_ADD) {
            List<Integer> statusList = mPolicyTrackingTable.addPolicies(
                    request.policiesToAdd, request.requesterUid);
            List<QosPolicyParams> acceptedPolicies =
                    filterPoliciesByStatusList(request.policiesToAdd, statusList);
            if (acceptedPolicies.isEmpty()) {
                // Tracking table rejected all policies in the request. Table may be full,
                // or all policies are already being tracked.
                request.callback.sendResult(statusList);
                return;
            }
            request.initialStatusList = statusList;
        } else if (request.requestType == REQUEST_TYPE_REMOVE) {
            List<Integer> virtualPolicyIds = mPolicyTrackingTable.translatePolicyIds(
                    request.policyIdsToRemove, request.requesterUid);
            if (virtualPolicyIds.isEmpty()) {
                // None of these policies are being tracked by the table.
                return;
            }
            mPolicyTrackingTable.removePolicies(request.policyIdsToRemove, request.requesterUid);

            List<Byte> virtualPolicyIdBytes = new ArrayList<>();
            for (int policyId : virtualPolicyIds) {
                virtualPolicyIdBytes.add((byte) policyId);
            }
            request.virtualPolicyIdsToRemove = virtualPolicyIdBytes;

            // Unregister death handler if this application no longer owns any policies.
            unregisterDeathHandlerIfNeeded(request.requesterUid);
        }

        for (ClientModeManager cmm : clientModeManagers) {
            queueRequestOnIface(cmm.getInterfaceName(), request);
        }
    }

    private void queueRequestOnIface(String ifaceName, QueuedRequest request) {
        if (!mPerIfaceRequestQueue.containsKey(ifaceName)) {
            mPerIfaceRequestQueue.put(ifaceName, new ArrayList<>());
        }
        mPerIfaceRequestQueue.get(ifaceName).add(request);
    }

    private void processNextRequestOnAllIfacesIfPossible() {
        for (String ifaceName : mPerIfaceRequestQueue.keySet()) {
            processNextRequestIfPossible(ifaceName);
        }
    }

    private void processNextRequestIfPossible(String ifaceName) {
        if (mPendingCallbacks.containsKey(ifaceName)) {
            // Supplicant is still processing a request on this interface.
            return;
        } else if (mPerIfaceRequestQueue.get(ifaceName).isEmpty()) {
            // No requests in this queue.
            return;
        }

        QueuedRequest request = mPerIfaceRequestQueue.get(ifaceName).get(0);
        mPerIfaceRequestQueue.get(ifaceName).remove(0);
        if (request.requestType == REQUEST_TYPE_ADD) {
            processAddRequest(ifaceName, request);
        } else if (request.requestType == REQUEST_TYPE_REMOVE) {
            processRemoveRequest(ifaceName, request);
        }
    }

    private void checkForStalledCallback(String ifaceName, CallbackParams processedParams) {
        CallbackParams pendingParams = mPendingCallbacks.get(ifaceName);
        if (pendingParams == processedParams) {
            Log.e(TAG, "Callback timed out. Expected params " + pendingParams);
            mPendingCallbacks.remove(ifaceName);
            processNextRequestIfPossible(ifaceName);
        }
    }

    /**
     * Divide a large request into batches of max size {@link #MAX_POLICIES_PER_TRANSACTION}.
     */
    @VisibleForTesting
    protected <T> List<List<T>> divideRequestIntoBatches(List<T> request) {
        List<List<T>> batches = new ArrayList<>();
        int startIndex = 0;
        int endIndex = Math.min(request.size(), MAX_POLICIES_PER_TRANSACTION);
        while (startIndex < endIndex) {
            batches.add(request.subList(startIndex, endIndex));
            startIndex += MAX_POLICIES_PER_TRANSACTION;
            endIndex = Math.min(request.size(), endIndex + MAX_POLICIES_PER_TRANSACTION);
        }
        return batches;
    }

    private List<Integer> generateStatusList(int size, @WifiManager.QosRequestStatus int status) {
        List<Integer> statusList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            statusList.add(status);
        }
        return statusList;
    }

    /**
     * Filter out policies that do not have status code
     * {@link WifiManager#QOS_REQUEST_STATUS_TRACKING}.
     */
    private List<QosPolicyParams> filterPoliciesByStatusList(List<QosPolicyParams> policyList,
            List<Integer> statusList) {
        List<QosPolicyParams> filteredPolicies = new ArrayList<>();
        for (int i = 0; i < statusList.size(); i++) {
            if (statusList.get(i) == WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                filteredPolicies.add(policyList.get(i));
            }
        }
        return filteredPolicies;
    }

    private void processAddRequest(String ifaceName, QueuedRequest request) {
        boolean previouslyProcessed = request.processedOnAnyIface;
        request.processedOnAnyIface = true;
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "Processing add request on iface=" + ifaceName + ", size="
                    + request.policiesToAdd.size());
        }

        // Verify that the requesting application is still alive.
        if (request.binder != null && !request.binder.pingBinder()) {
            Log.e(TAG, "Requesting application died before processing. request=" + request);
            processNextRequestIfPossible(ifaceName);
            return;
        }

        // Filter out policies that were already in the table during pre-processing.
        List<Integer> statusList = new ArrayList(request.initialStatusList);
        List<QosPolicyParams> policyList = filterPoliciesByStatusList(
                request.policiesToAdd, request.initialStatusList);

        // Filter out policies that were removed from the table in processSynchronousHalResponse().
        // Only applies to new policy requests that are queued on multiple interfaces.
        if (previouslyProcessed && request.requesterUid != DEFAULT_UID) {
            policyList = mPolicyTrackingTable.filterUntrackedPolicies(policyList,
                    request.requesterUid);
        }

        if (policyList.isEmpty()) {
            Log.e(TAG, "All policies were removed during filtering");
            processNextRequestIfPossible(ifaceName);
            return;
        }

        List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList =
                mWifiNative.addQosPolicyRequestForScs(ifaceName, policyList);
        if (halStatusList == null) {
            if (!previouslyProcessed) {
                statusList = handleHalPolicyAddError(
                        statusList, request.policiesToAdd, request.requesterUid);
                request.callback.sendResult(statusList);
            }
            processNextRequestIfPossible(ifaceName);
            return;
        }

        if (!previouslyProcessed) {
            // Send the status list to the requesting application.
            // Should only be done the first time that a request is processed.
            statusList = processSynchronousHalResponse(
                    statusList, halStatusList, request.policiesToAdd, request.requesterUid);
            request.callback.sendResult(statusList);

            // Register death handler if this application owns any policies in the table.
            registerDeathHandlerIfNeeded(request.requesterUid, request.binder);
        }

        // Policies that were sent to the AP expect a response from the callback.
        List<Byte> policiesAwaitingCallback = getPoliciesAwaitingCallback(halStatusList);
        if (policiesAwaitingCallback.isEmpty()) {
            processNextRequestIfPossible(ifaceName);
        } else {
            CallbackParams cbParams = new CallbackParams(policiesAwaitingCallback);
            mPendingCallbacks.put(ifaceName, cbParams);
            mHandler.postDelayed(() -> checkForStalledCallback(ifaceName, cbParams),
                    CALLBACK_TIMEOUT_MILLIS);
        }
    }

    private void processRemoveRequest(String ifaceName, QueuedRequest request) {
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Processing remove request on iface=" + ifaceName + ", size="
                    + request.policyIdsToRemove.size());
        }
        List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList =
                mWifiNative.removeQosPolicyForScs(ifaceName, request.virtualPolicyIdsToRemove);
        if (halStatusList == null) {
            processNextRequestIfPossible(ifaceName);
            return;
        }

        // Policies that were sent to the AP expect a response from the callback.
        List<Byte> policiesAwaitingCallback = getPoliciesAwaitingCallback(halStatusList);
        if (policiesAwaitingCallback.isEmpty()) {
            processNextRequestIfPossible(ifaceName);
        } else {
            CallbackParams cbParams = new CallbackParams(policiesAwaitingCallback);
            mPendingCallbacks.put(ifaceName, cbParams);
            mHandler.postDelayed(() -> checkForStalledCallback(ifaceName, cbParams),
                    CALLBACK_TIMEOUT_MILLIS);
        }
    }

    /**
     * Get the list of policy IDs that are expected in the AP callback.
     *
     * Any policies that were sent to the AP will appear in the list.
     */
    private List<Byte> getPoliciesAwaitingCallback(
            List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList) {
        List<Byte> policiesAwaitingCallback = new ArrayList<>();
        for (SupplicantStaIfaceHal.QosPolicyStatus status : halStatusList) {
            if (status.statusCode == SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_SENT) {
                policiesAwaitingCallback.add((byte) status.policyId);
            }
        }

        if (mVerboseLoggingEnabled) {
            Log.d(TAG, policiesAwaitingCallback.size()
                    + " policies were sent to the AP and are awaiting callback");
        }
        return policiesAwaitingCallback;
    }

    private static @WifiManager.QosRequestStatus int halToWifiManagerSyncStatus(
            @SupplicantStaIfaceHal.QosPolicyScsRequestStatusCode int halStatus) {
        switch (halStatus) {
            case SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_SENT:
                return WifiManager.QOS_REQUEST_STATUS_TRACKING;
            case SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_ALREADY_ACTIVE:
                return WifiManager.QOS_REQUEST_STATUS_ALREADY_ACTIVE;
            case SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_INVALID:
                return WifiManager.QOS_REQUEST_STATUS_INVALID_PARAMETERS;
            default:
                return WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN;
        }
    }

    /**
     * Handle the case where {@link WifiNative#addQosPolicyRequestForScs(String, List)} fails.
     *
     * For any policy that was sent to the HAL, assign the proper error code and
     * remove that policy from the tracking table.
     */
    private List<Integer> handleHalPolicyAddError(List<Integer> statusList,
            List<QosPolicyParams> policyList, int uid) {
        List<Integer> rejectedPolicies = new ArrayList<>();
        for (int i = 0; i < statusList.size(); i++) {
            if (statusList.get(i) != WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                // Policy was assigned an error code by the tracking table
                // and was not sent to the HAL.
                continue;
            }
            statusList.set(i, WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN);
            rejectedPolicies.add(policyList.get(i).getPolicyId());
        }

        // Remove policies that were sent to the HAL from the tracking table.
        mPolicyTrackingTable.removePolicies(rejectedPolicies, uid);
        return statusList;
    }

    /**
     * Process the status list from {@link WifiNative#addQosPolicyRequestForScs(String, List)}.
     *
     * For each policy that was sent to the HAL, merge the HAL status into the main status list.
     * If any policies were rejected by the HAL, remove them from the policy tracking table.
     */
    @VisibleForTesting
    protected List<Integer> processSynchronousHalResponse(List<Integer> statusList,
            List<SupplicantStaIfaceHal.QosPolicyStatus> halResults,
            List<QosPolicyParams> policyList, int uid) {
        int halIndex = 0;
        List<Integer> rejectedPolicies = new ArrayList<>();
        for (int i = 0; i < statusList.size(); i++) {
            if (statusList.get(i) != WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                // Policy was assigned an error code by the tracking table
                // and was not sent to the HAL.
                continue;
            }
            int statusCode = halToWifiManagerSyncStatus(halResults.get(halIndex).statusCode);
            if (statusCode != WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                rejectedPolicies.add(policyList.get(i).getPolicyId());
            }
            statusList.set(i, statusCode);
            halIndex++;
        }

        if (!rejectedPolicies.isEmpty()) {
            // Remove policies rejected by the HAL from the tracking table.
            mPolicyTrackingTable.removePolicies(rejectedPolicies, uid);
        }
        return statusList;
    }

    /**
     * Register death handler for this application if it owns policies in the tracking table,
     * and no death handlers have been registered before.
     */
    private void registerDeathHandlerIfNeeded(int uid, @NonNull IBinder binder) {
        if (mApplicationUidToBinderMap.containsKey(uid)) {
            // Application has already been linked to the death recipient.
            return;
        } else if (!mPolicyTrackingTable.tableContainsUid(uid)) {
            // Application does not own any policies in the tracking table.
            return;
        }

        try {
            binder.linkToDeath(mApplicationDeathRecipient, /* flags */ 0);
            mApplicationBinderToUidMap.put(binder, uid);
            mApplicationUidToBinderMap.put(uid, binder);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Exception occurred while linking to death: " + e);
        }
    }

    /**
     * Unregister the death handler for this application if it
     * no longer owns any policies in the tracking table.
     */
    private void unregisterDeathHandlerIfNeeded(int uid) {
        if (!mApplicationUidToBinderMap.containsKey(uid)) {
            // Application has already been unlinked from the death recipient.
            return;
        } else if (mPolicyTrackingTable.tableContainsUid(uid)) {
            // Application still owns policies in the tracking table.
            return;
        }

        IBinder binder = mApplicationUidToBinderMap.get(uid);
        binder.unlinkToDeath(mApplicationDeathRecipient, /* flags */ 0);
        mApplicationBinderToUidMap.remove(binder);
        mApplicationUidToBinderMap.remove(uid);
    }

    /**
     * Dump information about the internal state.
     *
     * @param pw PrintWriter to write the dump to.
     */
    public void dump(PrintWriter pw) {
        pw.println("Dump of ApplicationQosPolicyRequestHandler");
        pw.println("mPerIfaceRequestQueue: " + mPerIfaceRequestQueue);
        pw.println("mPendingCallbacks: " + mPendingCallbacks);
        pw.println();
        mPolicyTrackingTable.dump(pw);
    }
}
