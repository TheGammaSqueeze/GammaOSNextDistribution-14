/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.net.wifi.ITwtCallback;
import android.net.wifi.ITwtCapabilitiesListener;
import android.net.wifi.ITwtStatsListener;
import android.net.wifi.WifiManager;
import android.net.wifi.twt.TwtRequest;
import android.net.wifi.twt.TwtSession;
import android.net.wifi.twt.TwtSessionCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * This class acts as a manager for TWT sessions and callbacks. It establishes a link between unique
 * callback IDs and their corresponding callbacks, ensuring the correct responses are triggered. To
 * manage incoming TWT events, the class registers TWT sessions with the appropriate callbacks.
 * Additionally, it implements a garbage collection task to remove expired callbacks.
 *
 * If a registered callback's process goes away, this class will take care of automatically
 * removing it from the callback list. Twt manager allows simultaneous requests limited by
 * {@link #MAXIMUM_CALLBACKS}.
 *
 * Note: All contexts in TwtManager are in WifiThread. So no locks are used.
 */

class TwtManager {
    public static final String TAG = "TwtManager";
    private static final int TWT_CALLBACK_TIMEOUT_MILLIS = 2000;
    private static final String TWT_MANAGER_ALARM_TAG = "twtManagerAlarm";
    private static final int MAXIMUM_CALLBACKS = 8;

    private class Callback implements IBinder.DeathRecipient {
        public IInterface mCallback;
        public final int mOwner;
        public int mSessionId = -1;
        public final int mId;
        public final CallbackType mType;
        public final long mTimestamp;

        Callback(int id, IInterface callback, CallbackType type, int owner) {
            mId = id;
            mCallback = callback;
            mType = type;
            mOwner = owner;
            mTimestamp = mClock.getElapsedSinceBootMillis();
        }

        @Override
        public void binderDied() {
            mHandler.post(() -> {
                unregisterSession(mSessionId);
                unregisterCallback(mId);
            });
        }
    }

    private enum CallbackType {SETUP, STATS, TEARDOWN}
    private final SparseArray<Callback> mCommandCallbacks = new SparseArray<>();
    private final SparseArray<Callback> mTwtSessionCallbacks = new SparseArray<>();
    private final BitSet mIdBitSet;
    private final int mStartOffset;
    private final int mMaxSessions;
    private String mInterfaceName;
    private final Clock mClock;
    private final AlarmManager mAlarmManager;
    private final Handler mHandler;

    private final WifiNative mWifiNative;
    private final WifiNativeTwtEvents mWifiNativeTwtEvents;
    private final AlarmManager.OnAlarmListener mTimeoutListener = () -> {
        startGarbageCollector();
    };

    /**
     * Whenever primary clientModeManager identified by the interface name gets disconnected, reset
     * the TwtManager.
     */
    private class ClientModeImplListenerInternal implements ClientModeImplListener {
        @Override
        public void onConnectionEnd(@NonNull ConcreteClientModeManager clientModeManager) {
            if (clientModeManager.getInterfaceName() != null
                    && clientModeManager.getInterfaceName().equals(mInterfaceName)) {
                reset();
            }
        }
    }

    TwtManager(@NonNull WifiInjector wifiInjector, @NonNull ClientModeImplMonitor cmiMonitor,
            @NonNull WifiNative wifiNative, @NonNull Handler handler, @NonNull Clock clock,
            int maxSessions, int startOffset) {
        mAlarmManager = wifiInjector.getAlarmManager();
        mHandler = handler;
        mClock = clock;
        mMaxSessions = maxSessions;
        mIdBitSet = new BitSet(MAXIMUM_CALLBACKS);
        mStartOffset = startOffset;
        mWifiNative = wifiNative;
        mWifiNativeTwtEvents = new WifiNativeTwtEvents();
        cmiMonitor.registerListener(new ClientModeImplListenerInternal());
    }

    /**
     * Notify teardown to the registered caller
     */
    private void notifyTeardown(ITwtCallback iTwtCallback,
            @TwtSessionCallback.TwtReasonCode int reasonCode) {
        if (iTwtCallback == null) {
            Log.e(TAG, "notifyTeardown: null interface. Reason code " + reasonCode);
            return;
        }
        try {
            iTwtCallback.onTeardown(reasonCode);
        } catch (RemoteException e) {
            Log.e(TAG, "notifyTeardown: " + e);
        }
    }

    private Bundle getDefaultTwtCapabilities() {
        Bundle twtCapabilities = new Bundle();
        twtCapabilities.putBoolean(WifiManager.TWT_CAPABILITIES_KEY_BOOLEAN_TWT_REQUESTER, false);
        twtCapabilities.putInt(WifiManager.TWT_CAPABILITIES_KEY_INT_MIN_WAKE_DURATION_MICROS, -1);
        twtCapabilities.putInt(WifiManager.TWT_CAPABILITIES_KEY_INT_MAX_WAKE_DURATION_MICROS, -1);
        twtCapabilities.putLong(WifiManager.TWT_CAPABILITIES_KEY_LONG_MIN_WAKE_INTERVAL_MICROS, -1);
        twtCapabilities.putLong(WifiManager.TWT_CAPABILITIES_KEY_LONG_MAX_WAKE_INTERVAL_MICROS, -1);
        return twtCapabilities;
    }

    private static Bundle getDefaultTwtStats() {
        Bundle twtStats = new Bundle();
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_COUNT, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_COUNT, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_TX_PACKET_SIZE, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_RX_PACKET_SIZE, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_AVERAGE_EOSP_DURATION_MICROS, -1);
        twtStats.putInt(TwtSession.TWT_STATS_KEY_INT_EOSP_COUNT, -1);
        return twtStats;
    }

    /**
     * Notify failure to the registered caller
     */
    private void notifyFailure(IInterface iInterface, CallbackType type,
            @TwtSessionCallback.TwtErrorCode int errorCode) {
        if (iInterface == null) {
            Log.e(TAG, "notifyFailure: null interface. Error code " + errorCode);
            return;
        }
        try {
            if (type == CallbackType.STATS) {
                ((ITwtStatsListener) iInterface).onResult(getDefaultTwtStats());
            } else {
                ((ITwtCallback) iInterface).onFailure(errorCode);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "notifyFailure: " + e);
        }
    }

    /**
     * Expire callbacks and fetch next oldest callback's schedule for timeout
     *
     * @param now Current reference time
     * @return Timeout of the oldest callback with respect to current time. A value 0 means no more
     * callbacks to expire.
     */
    private long handleExpirationsAndGetNextTimeout(long now) {
        long oldest = Long.MAX_VALUE;
        List<Integer> expiredIds = new ArrayList<>();
        for (int i = 0; i < mCommandCallbacks.size(); ++i) {
            Callback callback = mCommandCallbacks.valueAt(i);
            if (now - callback.mTimestamp >= TWT_CALLBACK_TIMEOUT_MILLIS) {
                notifyFailure(callback.mCallback, callback.mType,
                        TwtSessionCallback.TWT_ERROR_CODE_TIMEOUT);
                // Unregister session now
                if (callback.mType == CallbackType.TEARDOWN) {
                    unregisterSession(callback.mSessionId);
                }
                expiredIds.add(callback.mId);
            } else {
                oldest = Math.min(callback.mTimestamp, oldest);
            }
        }
        for (int id : expiredIds) {
            unregisterCallback(id);
        }

        if (oldest > now) return 0;
        // Callbacks which has (age >= TWT_COMMAND_TIMEOUT_MILLIS) is cleaned up already
        return TWT_CALLBACK_TIMEOUT_MILLIS - (now - oldest);
    }

    private void startGarbageCollector() {
        long timeout = handleExpirationsAndGetNextTimeout(mClock.getElapsedSinceBootMillis());
        if (timeout <= 0) return;
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
                mClock.getElapsedSinceBootMillis() + timeout, TWT_MANAGER_ALARM_TAG,
                mTimeoutListener, mHandler);
    }

    private void stopGarbageCollector() {
        mAlarmManager.cancel(mTimeoutListener);
    }

    /**
     * Register a callback
     *
     * @param callback A remote interface performing callback
     * @param type     Type of the callback as {@link CallbackType}
     * @param owner    Owner of the callback
     * @return Returns an unique id. -1 if registration fails.
     */
    private int registerCallback(IInterface callback, CallbackType type, int owner) {
        if (callback == null) {
            Log.e(TAG, "registerCallback: Null callback");
            return -1;
        }
        if ((type == CallbackType.SETUP) && (mTwtSessionCallbacks.size() >= mMaxSessions)) {
            Log.e(TAG, "registerCallback: Maximum sessions reached. Setup not allowed.");
            notifyFailure(callback, CallbackType.SETUP,
                    TwtSessionCallback.TWT_ERROR_CODE_MAX_SESSIONS_REACHED);
            return -1;
        }
        int id = mIdBitSet.nextClearBit(0);
        if (id >= MAXIMUM_CALLBACKS) {
            Log.e(TAG, "registerCallback: No more simultaneous requests possible");
            notifyFailure(callback, CallbackType.SETUP,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
            return -1;
        }
        mIdBitSet.set(id);
        id += mStartOffset;
        try {
            Callback cb = new Callback(id, callback, type, owner);
            callback.asBinder().linkToDeath(cb, 0);
            mCommandCallbacks.put(id, cb);
        } catch (RemoteException e) {
            Log.e(TAG, "registerCallback: Error on linkToDeath - " + e);
            notifyFailure(callback, CallbackType.SETUP, TwtSessionCallback.TWT_ERROR_CODE_FAIL);
            return -1;
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "registerCallback: " + e);
            notifyFailure(callback, CallbackType.SETUP, TwtSessionCallback.TWT_ERROR_CODE_FAIL);
            return -1;
        }
        // First register triggers GC
        if (mCommandCallbacks.size() == 1) startGarbageCollector();
        return id;
    }

    /**
     * Unregister a previously registered callback
     *
     * @param id Unique callback id returned by
     *           {@link #registerCallback(IInterface, CallbackType, int)}
     */
    private void unregisterCallback(int id) {
        try {
            if (!mCommandCallbacks.contains(id)) return;
            // Last unregister stops GC
            if (mCommandCallbacks.size() == 1) stopGarbageCollector();
            Callback cb = mCommandCallbacks.get(id);
            if (!mTwtSessionCallbacks.contains(cb.mSessionId)) {
                // Note: unregisterSession() will call Binder#unlinktoDeath()
                cb.mCallback.asBinder().unlinkToDeath(cb, 0);
            }
            mCommandCallbacks.delete(id);
            mIdBitSet.clear(id - mStartOffset);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "unregisterCallback: invalid id " + id + " " + e);
        }
    }

    /**
     * Register a TWT session
     *
     * @param id        Unique callback id returned by
     *                  {@link #registerCallback(IInterface, CallbackType, int)}
     * @param sessionId TWT session id
     * @return true if successful, otherwise false.
     */
    private boolean registerSession(int id, int sessionId) {
        Callback callback = mCommandCallbacks.get(id);
        if (callback == null) {
            Log.e(TAG, "registerSession failed. Invalid id " + id);
            return false;
        }
        if (mTwtSessionCallbacks.contains(sessionId)) {
            Log.e(TAG, "registerSession failed. Session already exists");
            return false;
        }
        callback.mSessionId = sessionId;
        mTwtSessionCallbacks.put(sessionId, callback);
        return true;
    }

    /**
     * Unregister a TWT session
     *
     * @param sessionId TWT session id
     */
    private void unregisterSession(int sessionId) {
        if (!mTwtSessionCallbacks.contains(sessionId)) {
            Log.e(TAG, "unregisterSession failed. Session does not exist");
            return;
        }
        Callback callback = mTwtSessionCallbacks.get(sessionId);
        callback.mCallback.asBinder().unlinkToDeath(callback, 0);
        mTwtSessionCallbacks.delete(sessionId);
    }

    private boolean isSessionRegistered(int sessionId) {
        return mTwtSessionCallbacks.get(sessionId) != null;
    }

    /**
     * Get callback from TWT session id
     *
     * @param sessionId TWT session id
     * @return Callback registered, otherwise null
     */
    private IInterface getCallbackFromSession(int sessionId) {
        if (mTwtSessionCallbacks.get(sessionId) == null) return null;
        return mTwtSessionCallbacks.get(sessionId).mCallback;
    }

    /**
     * Get owner uid
     *
     * @param id unique id returned by {@link #registerCallback(IInterface, CallbackType, int)}
     * @return Owner UID if registered, otherwise -1
     */
    private int getOwnerUid(int id) {
        try {
            return mCommandCallbacks.get(id).mOwner;
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "getOwner: invalid id " + id + " " + e);
            return -1;
        }
    }

    /**
     * Get callback
     *
     * @param id unique id returned by {@link #registerCallback(IInterface, CallbackType, int)}
     * @return Callback if registered, otherwise null
     */
    private IInterface getCallback(int id) {
        try {
            Callback callback = mCommandCallbacks.get(id);
            if (callback == null) return null;
            return callback.mCallback;
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "getCallback: invalid id " + id + " " + e);
            return null;
        }
    }

    /**
     * Implementation of TWT events from WifiNative. see {@link #registerWifiNativeTwtEvents()}
     */
    public class WifiNativeTwtEvents implements WifiNative.WifiTwtEvents {
        @Override
        public void onTwtFailure(int cmdId, int twtErrorCode) {
            ITwtCallback iTwtCallback = (ITwtCallback) getCallback(cmdId);
            if (iTwtCallback == null) {
                Log.e(TAG, "onTwtFailure: Command Id is not registered " + cmdId);
                return;
            }
            try {
                iTwtCallback.onFailure(twtErrorCode);
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            unregisterCallback(cmdId);
        }

        @Override
        public void onTwtSessionCreate(int cmdId, int wakeDurationUs, long wakeIntervalUs,
                int linkId, int sessionId) {
            ITwtCallback iTwtCallback = (ITwtCallback) getCallback(cmdId);
            if (iTwtCallback == null) {
                Log.e(TAG, "onTwtSessionCreate failed. No callback registered for " + cmdId);
                return;
            }
            if (!registerSession(cmdId, sessionId)) {
                Log.e(TAG, "onTwtSessionCreate failed for session " + sessionId);
                return;
            }
            try {
                iTwtCallback.onCreate(wakeDurationUs, wakeIntervalUs, linkId, getOwnerUid(cmdId),
                        sessionId);
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            unregisterCallback(cmdId);
        }

        @Override
        public void onTwtSessionTeardown(int cmdId, int twtSessionId, int twtReasonCode) {
            ITwtCallback iTwtCallback = (ITwtCallback) getCallback(cmdId);
            if (iTwtCallback == null) {
                // Unsolicited teardown. So get callback from session.
                iTwtCallback = (ITwtCallback) getCallbackFromSession(twtSessionId);
                if (iTwtCallback == null) return;
            }
            try {
                iTwtCallback.onTeardown(twtReasonCode);
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            unregisterCallback(cmdId);
            unregisterSession(twtSessionId);
        }

        @Override
        public void onTwtSessionStats(int cmdId, int twtSessionId, Bundle twtStats) {
            ITwtStatsListener iTwtStatsListener = (ITwtStatsListener) getCallback(cmdId);
            if (iTwtStatsListener == null) {
                return;
            }
            try {
                iTwtStatsListener.onResult(twtStats);
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            unregisterCallback(cmdId);
        }
    }

    /**
     * Register for TWT events from WifiNative
     */
    public void registerWifiNativeTwtEvents() {
        mWifiNative.registerTwtCallbacks(mWifiNativeTwtEvents);
    }

    /**
     * Get TWT capabilities for the interface
     *
     * @param interfaceName Interface name
     * @param listener      listener for TWT capabilities
     */
    public void getTwtCapabilities(@Nullable String interfaceName,
            @NonNull ITwtCapabilitiesListener listener) {
        try {
            if (interfaceName == null) {
                listener.onResult(getDefaultTwtCapabilities());
                return;
            }
            Bundle twtCapabilities = mWifiNative.getTwtCapabilities(interfaceName);
            if (twtCapabilities == null) twtCapabilities = getDefaultTwtCapabilities();
            listener.onResult(twtCapabilities);
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    /**
     * Sets up a TWT session for the interface
     *
     * @param interfaceName Interface name
     * @param twtRequest    TWT request parameters
     * @param iTwtCallback  Callback for the TWT setup command
     * @param callingUid    Caller UID
     */
    public void setupTwtSession(@Nullable String interfaceName, @NonNull TwtRequest twtRequest,
            @NonNull ITwtCallback iTwtCallback, int callingUid) {
        if (!registerInterface(interfaceName)) {
            notifyFailure(iTwtCallback, CallbackType.SETUP,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
            return;
        }
        int id = registerCallback(iTwtCallback, TwtManager.CallbackType.SETUP, callingUid);
        if (id < 0) {
            return;
        }
        if (!mWifiNative.setupTwtSession(id, interfaceName, twtRequest)) {
            unregisterCallback(id);
            notifyFailure(iTwtCallback, CallbackType.SETUP,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
        }
    }

    /**
     * Teardown the TWT session
     *
     * @param interfaceName Interface name
     * @param sessionId     TWT session id
     */
    public void tearDownTwtSession(@Nullable String interfaceName, int sessionId) {
        ITwtCallback iTwtCallback = (ITwtCallback) getCallbackFromSession(sessionId);
        if (iTwtCallback == null) {
            return;
        }
        if (!isRegisteredInterface(interfaceName)) {
            notifyFailure(iTwtCallback, CallbackType.TEARDOWN,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
            return;
        }
        int id = registerCallback(iTwtCallback, TwtManager.CallbackType.TEARDOWN,
                Binder.getCallingUid());
        if (id < 0) {
            return;
        }
        if (!mWifiNative.tearDownTwtSession(id, interfaceName, sessionId)) {
            unregisterCallback(id);
            notifyFailure(iTwtCallback, CallbackType.TEARDOWN,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
        }
    }

    /**
     * Gets stats of the TWT session
     *
     * @param interfaceName     Interface name
     * @param iTwtStatsListener Listener for TWT stats
     * @param sessionId         TWT session id
     */
    public void getStatsTwtSession(@Nullable String interfaceName,
            ITwtStatsListener iTwtStatsListener, int sessionId) {
        if (!isRegisteredInterface(interfaceName)) {
            notifyFailure(iTwtStatsListener, CallbackType.STATS,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
            return;
        }

        if (!isSessionRegistered(sessionId)) {
            notifyFailure(iTwtStatsListener, CallbackType.STATS,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
            return;
        }

        int id = registerCallback(iTwtStatsListener, TwtManager.CallbackType.STATS,
                Binder.getCallingUid());
        if (id < 0) {
            notifyFailure(iTwtStatsListener, CallbackType.STATS,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
            return;
        }
        if (!mWifiNative.getStatsTwtSession(id, interfaceName, sessionId)) {
            unregisterCallback(id);
            notifyFailure(iTwtStatsListener, CallbackType.STATS,
                    TwtSessionCallback.TWT_ERROR_CODE_NOT_AVAILABLE);
        }
    }

    private boolean isEmpty() {
        return (mCommandCallbacks.size() == 0 && mTwtSessionCallbacks.size() == 0);
    }

    private void reset() {
        if (isEmpty()) return;
        stopGarbageCollector();
        // Notify failure for all pending callbacks
        for (int i = 0; i < mCommandCallbacks.size(); ++i) {
            Callback callback = mCommandCallbacks.valueAt(i);
            if (!mTwtSessionCallbacks.contains(callback.mSessionId)) {
                // Session cleanup will call Binder#unlinktoDeath()
                callback.mCallback.asBinder().unlinkToDeath(callback, 0);
            }
            notifyFailure(callback.mCallback, callback.mType,
                    TwtSessionCallback.TWT_ERROR_CODE_FAIL);
        }
        // Teardown all active sessions
        for (int i = 0; i < mTwtSessionCallbacks.size(); ++i) {
            Callback callback = mTwtSessionCallbacks.valueAt(i);
            callback.mCallback.asBinder().unlinkToDeath(callback, 0);
            notifyTeardown((ITwtCallback) callback.mCallback,
                    TwtSessionCallback.TWT_REASON_CODE_INTERNALLY_INITIATED);
        }
        mCommandCallbacks.clear();
        mTwtSessionCallbacks.clear();
        mIdBitSet.clear();
        unregisterInterface();
    }

    private void unregisterInterface() {
        mInterfaceName = null;
    }

    private boolean registerInterface(String interfaceName) {
        if (interfaceName == null) return false;
        if (mInterfaceName == null) {
            mInterfaceName = interfaceName;
            return true;
        }
        // Check if already registered to the same interface
        if (interfaceName.equals(mInterfaceName)) {
            return true;
        }
        Log.e(TAG, "Already registered to another interface " + mInterfaceName);
        return false;
    }

    private boolean isRegisteredInterface(String interfaceName) {
        return (interfaceName != null && interfaceName.equals(mInterfaceName));
    }
}
