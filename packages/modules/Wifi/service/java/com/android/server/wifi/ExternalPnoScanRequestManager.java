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

import static android.net.wifi.WifiManager.PnoScanResultsCallback.REGISTER_PNO_CALLBACK_RESOURCE_BUSY;
import static android.net.wifi.WifiManager.PnoScanResultsCallback.REMOVE_PNO_CALLBACK_RESULTS_DELIVERED;
import static android.net.wifi.WifiManager.PnoScanResultsCallback.REMOVE_PNO_CALLBACK_UNREGISTERED;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.IPnoScanResultsCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages PNO scan requests from apps.
 * This class is not thread safe and is expected to be run on a single thread.
 */
public class ExternalPnoScanRequestManager implements IBinder.DeathRecipient {
    private static final String TAG = "ExternalPnoScanRequestManager";
    private ExternalPnoScanRequest mCurrentRequest;
    private final Handler mHandler;
    private int mCurrentRequestOnPnoNetworkFoundCount = 0;
    private Context mContext;
    private boolean mVerboseLoggingEnabled = false;

    /**
     * Creates a ExternalPnoScanRequestManager.
     * @param handler to run binder death callback.
     * @param context of the wifi service.
     */
    public ExternalPnoScanRequestManager(Handler handler, Context context) {
        mHandler = handler;
        mContext = context;
    }

    /**
     * Returns a copy of the current SSIDs being requested for PNO scan.
     */
    public Set<String> getExternalPnoScanSsids() {
        return mCurrentRequest == null ? Collections.EMPTY_SET
                : new ArraySet<>(mCurrentRequest.mSsidStrings);
    }

    /**
     * Returns a copy of the current frequencies being requested for PNO scan.
     */
    public Set<Integer> getExternalPnoScanFrequencies() {
        return mCurrentRequest == null ? Collections.EMPTY_SET
                : new ArraySet<>(mCurrentRequest.mFrequencies);
    }

    /**
     * Enables verbose logging.
     */
    public void enableVerboseLogging(boolean enabled) {
        mVerboseLoggingEnabled = enabled;
    }

    /**
     * Sets the request. This will fail if there's already a request set.
     */
    public boolean setRequest(int uid, @NonNull String packageName, @NonNull IBinder binder,
            @NonNull IPnoScanResultsCallback callback,
            @NonNull List<WifiSsid> ssids, @NonNull int[] frequencies) {
        if (mCurrentRequest != null && uid != mCurrentRequest.mUid) {
            try {
                callback.onRegisterFailed(REGISTER_PNO_CALLBACK_RESOURCE_BUSY);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException failed to trigger onRegisterFailed for callback="
                        + callback);
            }
            return false;
        }
        ExternalPnoScanRequest request = new ExternalPnoScanRequestManager.ExternalPnoScanRequest(
                uid, packageName, binder, callback, ssids, frequencies);
        try {
            request.mBinder.linkToDeath(this, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "mBinder.linkToDeath failed: " + e.getMessage());
            return false;
        }
        try {
            request.mCallback.onRegisterSuccess();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register request due to remote exception:" + e.getMessage());
            return false;
        }
        removeCurrentRequest();
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Successfully set external PNO scan request:" + request);
        }
        mCurrentRequest = request;
        return true;
    }

    private void removeCurrentRequest() {
        if (mCurrentRequest != null) {
            try {
                mCurrentRequest.mBinder.unlinkToDeath(this, 0);
                if (mVerboseLoggingEnabled) {
                    Log.i(TAG, "mBinder.unlinkToDeath on request:" + mCurrentRequest);
                }
            } catch (NoSuchElementException e) {
                Log.e(TAG, "Encountered remote exception in unlinkToDeath=" + e.getMessage());
            }
        }
        mCurrentRequest = null;
        mCurrentRequestOnPnoNetworkFoundCount = 0;
    }

    /**
     * Removes the requests. Will fail if the remover's uid and packageName does not match with the
     * creator's uid and packageName.
     */
    public boolean removeRequest(int uid) {
        if (mCurrentRequest == null || uid != mCurrentRequest.mUid) {
            return false;
        }

        try {
            mCurrentRequest.mCallback.onRemoved(REMOVE_PNO_CALLBACK_UNREGISTERED);
        } catch (RemoteException e) {
            Log.e(TAG, "Encountered remote exception in onRemoved=" + e.getMessage());
        }
        removeCurrentRequest();
        return true;
    }

    /**
     * Triggered when networks are found. Any results matching the external request will be
     * sent to the callback.
     * @param scanDetails the list of scanDetails received by the framework
     */
    public void onScanResultsAvailable(List<ScanDetail> scanDetails) {
        if (mCurrentRequest == null) {
            return;
        }
        mCurrentRequestOnPnoNetworkFoundCount++;
        List<ScanResult> requestedResults = new ArrayList<>();
        for (ScanDetail scanDetail : scanDetails) {
            if (mCurrentRequest.mSsidStrings.contains(
                    scanDetail.getScanResult().getWifiSsid().toString())) {
                requestedResults.add(scanDetail.getScanResult());
            }
        }
        if (requestedResults.isEmpty()) {
            return;
        }

        // requested PNO SSIDs found. Send results and then remove request.
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "On network found for request:" + mCurrentRequest);
        }
        sendScanResultAvailableBroadcastToPackage(mCurrentRequest.mPackageName);
        try {
            mCurrentRequest.mCallback.onScanResultsAvailable(requestedResults);
            mCurrentRequest.mCallback.onRemoved(REMOVE_PNO_CALLBACK_RESULTS_DELIVERED);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send PNO results via callback due to remote exception="
                    + e.getMessage());
        }
        removeCurrentRequest();
    }

    private void sendScanResultAvailableBroadcastToPackage(String packageName) {
        Intent intent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intent.putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true);
        intent.setPackage(packageName);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Successfully sent out targeted broadcast for:" + mCurrentRequest);
        }
    }

    /**
     * Tracks a request for PNO scan made by an app.
     */
    public static class ExternalPnoScanRequest {
        private int mUid;
        private String mPackageName;
        private Set<String> mSsidStrings;
        private Set<Integer> mFrequencies;
        private IPnoScanResultsCallback mCallback;
        private IBinder mBinder;

        /**
         * @param uid identifies the caller
         * @param binder obtained from the caller
         * @param callback used to send results back to the caller
         * @param ssids requested SSIDs for PNO scan
         */
        public ExternalPnoScanRequest(int uid, String packageName, IBinder binder,
                IPnoScanResultsCallback callback, List<WifiSsid> ssids, int[] frequencies) {
            mUid = uid;
            mPackageName = packageName;
            mBinder = binder;
            mCallback = callback;
            mSsidStrings = new ArraySet<>();
            for (WifiSsid wifiSsid : ssids) {
                mSsidStrings.add(wifiSsid.toString());
            }
            mFrequencies = Arrays.stream(frequencies).boxed().collect(Collectors.toSet());
        }

        @Override
        public String toString() {
            StringBuilder sbuf = new StringBuilder();
            sbuf.append("uid=").append(mUid)
                    .append(", packageName=").append(mPackageName)
                    .append(", binder=").append(mBinder)
                    .append(", callback=").append(mCallback)
                    .append(", mSsidStrings=");
            for (String s : mSsidStrings) {
                sbuf.append(s).append(", ");
            }
            sbuf.append(" frequencies=");
            for (int f : mFrequencies) {
                sbuf.append(f).append(", ");
            }
            return sbuf.toString();
        }
    }

    /**
     * Binder has died. Perform cleanup.
     */
    @Override
    public void binderDied() {
        // Log binder died, but keep the request since result will still be delivered with directed
        // broadcast
        Log.w(TAG, "Binder died.");
    }

    /**
     * Dump the local logs.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of ExternalPnoScanRequestManager");
        pw.println("ExternalPnoScanRequestManager - Log Begin ----");
        if (mCurrentRequest != null) {
            pw.println("Current external PNO scan request:");
            pw.println(mCurrentRequest.toString());
        } else {
            pw.println("No external PNO scan request set.");
        }
        pw.println("mCurrentRequestOnPnoNetworkFoundCount: "
                + mCurrentRequestOnPnoNetworkFoundCount);
        pw.println("ExternalPnoScanRequestManager - Log End ----");
    }
}
