/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.aware;

import static com.android.server.wifi.aware.WifiAwareStateManager.INSTANT_MODE_24GHZ;
import static com.android.server.wifi.aware.WifiAwareStateManager.INSTANT_MODE_5GHZ;
import static com.android.server.wifi.aware.WifiAwareStateManager.INSTANT_MODE_DISABLED;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.util.HexEncoding;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.server.wifi.util.WifiPermissionsUtil;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Manages the service-side Aware state of an individual "client". A client
 * corresponds to a single instantiation of the WifiAwareManager - there could be
 * multiple ones per UID/process (each of which is a separate client with its
 * own session namespace). The client state is primarily: (1) callback (a
 * singleton per client) through which Aware-wide events are called, and (2) a set
 * of discovery sessions (publish and/or subscribe) which are created through
 * this client and whose lifetime is tied to the lifetime of the client.
 */
public class WifiAwareClientState {
    private static final String TAG = "WifiAwareClientState";
    private boolean mVdbg = false; // STOPSHIP if true
    private boolean mDbg = false;

    private final Context mContext;
    private final IWifiAwareEventCallback mCallback;
    private final SparseArray<WifiAwareDiscoverySessionState> mSessions = new SparseArray<>();

    private final int mClientId;
    private ConfigRequest mConfigRequest;
    private final int mUid;
    private final int mPid;
    private final String mCallingPackage;
    public final @Nullable String mCallingFeatureId;
    private final boolean mNotifyIdentityChange;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final Object mAttributionSource;

    private final AppOpsManager mAppOps;
    private final long mCreationTime;
    private final boolean mAwareOffload;
    public final int mCallerType;

    private static final byte[] ALL_ZERO_MAC = new byte[] {0, 0, 0, 0, 0, 0};
    private byte[] mLastDiscoveryInterfaceMac = ALL_ZERO_MAC;
    private byte[] mLastClusterId = ALL_ZERO_MAC;

    public WifiAwareClientState(Context context, int clientId, int uid, int pid,
            String callingPackage, @Nullable String callingFeatureId,
            IWifiAwareEventCallback callback, ConfigRequest configRequest,
            boolean notifyIdentityChange, long creationTime,
            WifiPermissionsUtil wifiPermissionsUtil, Object attributionSource,
            boolean awareOffload, int callerType) {
        mContext = context;
        mClientId = clientId;
        mUid = uid;
        mPid = pid;
        mCallingPackage = callingPackage;
        mCallingFeatureId = callingFeatureId;
        mCallback = callback;
        mConfigRequest = configRequest;
        mNotifyIdentityChange = notifyIdentityChange;
        mAwareOffload = awareOffload;

        mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        mCreationTime = creationTime;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mAttributionSource = attributionSource;
        mCallerType = callerType;
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose, boolean vDbg) {
        mDbg = verbose;
        mVdbg = vDbg;
    }

    /**
     * Destroy the current client - corresponds to a disconnect() request from
     * the client. Destroys all discovery sessions belonging to this client.
     */
    public void destroy() {
        if (mDbg) {
            Log.v(TAG, "onAwareSessionTerminated, ClientId:" + mClientId);
        }
        for (int i = 0; i < mSessions.size(); ++i) {
            mSessions.valueAt(i).terminate();
        }
        mSessions.clear();
        mConfigRequest = null;

        try {
            mCallback.onAttachTerminate();
        } catch (RemoteException e1) {
            Log.e(TAG, "Error on onSessionTerminate()");
        }
    }

    public ConfigRequest getConfigRequest() {
        return mConfigRequest;
    }

    public int getClientId() {
        return mClientId;
    }

    public int getUid() {
        return mUid;
    }

    public String getCallingPackage() {
        return mCallingPackage;
    }

    public boolean getNotifyIdentityChange() {
        return mNotifyIdentityChange;
    }

    public long getCreationTime() {
        return mCreationTime;
    }

    public SparseArray<WifiAwareDiscoverySessionState> getSessions() {
        return mSessions;
    }

    public Object getAttributionSource() {
        return mAttributionSource;
    }

    public boolean isAwareOffload() {
        return mAwareOffload;
    }
    /**
     * Searches the discovery sessions of this client and returns the one
     * corresponding to the publish/subscribe ID. Used on callbacks from HAL to
     * map callbacks to the correct discovery session.
     *
     * @param pubSubId The publish/subscribe match session ID.
     * @return Aware session corresponding to the requested ID.
     */
    public WifiAwareDiscoverySessionState getAwareSessionStateForPubSubId(int pubSubId) {
        for (int i = 0; i < mSessions.size(); ++i) {
            WifiAwareDiscoverySessionState session = mSessions.valueAt(i);
            if (session.isPubSubIdSession(pubSubId)) {
                return session;
            }
        }

        return null;
    }

    /**
     * Add the session to the client database.
     *
     * @param session Session to be added.
     */
    public void addSession(WifiAwareDiscoverySessionState session) {
        int sessionId = session.getSessionId();
        if (mSessions.get(sessionId) != null) {
            Log.w(TAG, "createSession: sessionId already exists (replaced) - " + sessionId);
        }

        mSessions.put(sessionId, session);
    }

    /**
     * Remove the specified session from the client database - without doing a
     * terminate on the session. The assumption is that it is already
     * terminated.
     *
     * @param sessionId The session ID of the session to be removed.
     */
    public void removeSession(int sessionId) {
        if (mSessions.get(sessionId) == null) {
            Log.e(TAG, "removeSession: sessionId doesn't exist - " + sessionId);
            return;
        }

        mSessions.delete(sessionId);
    }

    /**
     * Destroy the discovery session: terminates discovery and frees up
     * resources.
     *
     * @param sessionId The session ID of the session to be destroyed.
     */
    public WifiAwareDiscoverySessionState terminateSession(int sessionId) {
        WifiAwareDiscoverySessionState session = mSessions.get(sessionId);
        if (session == null) {
            Log.e(TAG, "terminateSession: sessionId doesn't exist - " + sessionId);
            return null;
        }

        session.terminate();
        mSessions.delete(sessionId);

        return session;
    }

    /**
     * Retrieve a session.
     *
     * @param sessionId Session ID of the session to be retrieved.
     * @return Session or null if there's no session corresponding to the
     *         sessionId.
     */
    public WifiAwareDiscoverySessionState getSession(int sessionId) {
        return mSessions.get(sessionId);
    }

    /**
     * Called to dispatch the Aware interface address change to the client - as an
     * identity change (interface address information not propagated to client -
     * privacy concerns).
     *
     * @param mac The new MAC address of the discovery interface - optionally propagated to the
     *            client.
     */
    public void onInterfaceAddressChange(byte[] mac) {
        if (mDbg) {
            Log.v(TAG, "onInterfaceAddressChange: mClientId=" + mClientId
                    + ", mNotifyIdentityChange=" + mNotifyIdentityChange
                    + ", mac=" + String.valueOf(HexEncoding.encode(mac))
                    + ", mLastDiscoveryInterfaceMac="
                    + String.valueOf(HexEncoding.encode(mLastDiscoveryInterfaceMac)));
        }
        if (mNotifyIdentityChange && !Arrays.equals(mac, mLastDiscoveryInterfaceMac)) {
            boolean hasPermission = mWifiPermissionsUtil.checkCallersLocationPermission(
                    mCallingPackage, mCallingFeatureId, mUid,
                    /* coarseForTargetSdkLessThanQ */ true, null);
            try {
                if (mVdbg) Log.v(TAG, "hasPermission=" + hasPermission);
                mCallback.onIdentityChanged(hasPermission ? mac : ALL_ZERO_MAC);
            } catch (RemoteException e) {
                Log.w(TAG, "onIdentityChanged: RemoteException - ignored: " + e);
            }
        }

        mLastDiscoveryInterfaceMac = mac;
    }

    /**
     * Called to dispatch the Aware cluster change (due to joining of a new
     * cluster or starting a cluster) to the client - as an identity change
     * (interface address information not propagated to client - privacy
     * concerns). Dispatched if the client registered for the identity changed
     * event.
     *
     * @param clusterEventType The type of the cluster event that triggered the callback.
     * @param clusterId The cluster ID of the cluster started or joined.
     * @param currentDiscoveryInterfaceMac The MAC address of the discovery interface.
     */
    public void onClusterChange(@IdentityChangedListener.ClusterChangeEvent int clusterEventType,
            byte[] clusterId, byte[] currentDiscoveryInterfaceMac) {
        if (mDbg) {
            Log.v(TAG,
                    "onClusterChange: mClientId=" + mClientId + ", mNotifyIdentityChange="
                            + mNotifyIdentityChange + ", clusterId=" + String.valueOf(
                            HexEncoding.encode(clusterId)) + ", currentDiscoveryInterfaceMac="
                            + String.valueOf(HexEncoding.encode(currentDiscoveryInterfaceMac))
                            + ", mLastDiscoveryInterfaceMac=" + String.valueOf(
                            HexEncoding.encode(mLastDiscoveryInterfaceMac))
                            + ", mLastClusterId="
                            + String.valueOf(HexEncoding.encode(mLastClusterId)));
        }
        if (!mNotifyIdentityChange) {
            mLastDiscoveryInterfaceMac = currentDiscoveryInterfaceMac;
            mLastClusterId = clusterId;
            return;
        }
        boolean hasPermission = mWifiPermissionsUtil.checkCallersLocationPermission(
                mCallingPackage, mCallingFeatureId, mUid,
                /* coarseForTargetSdkLessThanQ */ true, null);
        if (mVdbg) Log.v(TAG, "hasPermission=" + hasPermission);
        if (!Arrays.equals(currentDiscoveryInterfaceMac, mLastDiscoveryInterfaceMac)) {
            try {
                mCallback.onIdentityChanged(
                        hasPermission ? currentDiscoveryInterfaceMac : ALL_ZERO_MAC);
            } catch (RemoteException e) {
                Log.w(TAG, "onIdentityChanged: RemoteException - ignored: " + e);
            }
        }

        mLastDiscoveryInterfaceMac = currentDiscoveryInterfaceMac;

        if (!Arrays.equals(clusterId, mLastClusterId)) {
            try {
                mCallback.onClusterIdChanged(clusterEventType,
                        hasPermission ? clusterId : ALL_ZERO_MAC);
            } catch (RemoteException e) {
                Log.w(TAG, "onClusterIdChanged: RemoteException - ignored: " + e);
            }
        }

        mLastClusterId = clusterId;
    }

    /**
     * Check if client needs ranging enabled.
     * @return True if one of the discovery session has ranging enabled, false otherwise.
     */
    public boolean isRangingEnabled() {
        for (int i = 0; i < mSessions.size(); ++i) {
            if (mSessions.valueAt(i).isRangingEnabled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check the highest instant communication mode of the client.
     * @param timeout Specify an interval when instant mode config timeout
     * @return current instant mode one of the {@code INSTANT_MODE_*}
     */
    public int getInstantMode(long timeout) {
        int instantMode = INSTANT_MODE_DISABLED;
        for (int i = 0; i < mSessions.size(); ++i) {
            int currentSession = mSessions.valueAt(i).getInstantMode(timeout);
            if (currentSession == INSTANT_MODE_5GHZ) {
                return INSTANT_MODE_5GHZ;
            }
            if (currentSession == INSTANT_MODE_24GHZ) {
                instantMode = currentSession;
            }
        }
        return instantMode;
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("AwareClientState:");
        pw.println("  mClientId: " + mClientId);
        pw.println("  mConfigRequest: " + mConfigRequest);
        pw.println("  mNotifyIdentityChange: " + mNotifyIdentityChange);
        pw.println("  mCallback: " + mCallback);
        pw.println("  mSessions: [" + mSessions + "]");
        for (int i = 0; i < mSessions.size(); ++i) {
            mSessions.valueAt(i).dump(fd, pw, args);
        }
    }
}
