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

package com.android.server.uwb.pm;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.UwbServiceCore;
import com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo;
import com.android.server.uwb.data.UwbConfig;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.SessionData;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstract class for session with profiles
 */
public abstract class RangingSessionController extends StateMachine {
    public static String TAG = "RangingSessionController";

    public SessionInfo mSessionInfo;
    public Handler mHandler;
    public UwbInjector mUwbInjector;
    private GenericSpecificationParams mSpecificationParams;
    protected boolean mVerboseLoggingEnabled = false;

    protected State mIdleState = null;
    protected State mDiscoveryState = null;
    protected State mTransportState = null;
    protected State mSecureSessionState = null;
    protected State mRangingState = null;
    protected State mEndSessionState = null;

    public static final int SESSION_INITIALIZED = 1;
    public static final int SESSION_START = 2;
    public static final int SESSION_STOP = 3;

    public static final int DISCOVERY_INIT = 101;
    public static final int DISCOVERY_STARTED = 102;
    public static final int DISCOVERY_SUCCESS = 103;
    public static final int DISCOVERY_ENDED = 104;
    public static final int DISCOVERY_FAILED = 105;

    public static final int TRANSPORT_INIT = 201;
    public static final int TRANSPORT_STARTED = 202;
    public static final int TRANSPORT_COMPLETED = 203;
    public static final int TRANSPORT_FAILURE = 204;

    public static final int SECURE_SESSION_INIT = 301;
    public static final int SECURE_SESSION_ESTABLISHED = 302;
    public static final int SECURE_SESSION_FAILURE = 303;

    public static final int RANGING_INIT = 401;
    public static final int RANGING_OPENED = 402;
    public static final int RANGING_STARTED = 403;
    public static final int RANGING_FAILURE = 404;
    public static final int RANGING_ENDED = 405;

    public RangingSessionController(SessionHandle sessionHandle,
            AttributionSource attributionSource,
            Context context,
            UwbInjector uwbInjector,
            ServiceProfileInfo serviceProfileInfo,
            IUwbRangingCallbacks rangingCallbacks,
            Handler handler,
            String chipId) {
        super("RangingSessionController", handler);

        mSessionInfo = new SessionInfo(attributionSource, sessionHandle,
                serviceProfileInfo, context, rangingCallbacks, chipId);

        mIdleState = getIdleState();
        mDiscoveryState = getDiscoveryState();
        mTransportState = getTransportState();
        mSecureSessionState = getSecureState();
        mRangingState = getRangingState();
        mEndSessionState = getEndingState();

        mUwbInjector = uwbInjector;
        mHandler = handler;

        addState(mIdleState);
        addState(mDiscoveryState);
        {
            addState(mTransportState, mDiscoveryState);
            {
                addState(mSecureSessionState, mTransportState);
                {
                    addState(mRangingState, mSecureSessionState);
                }
            }
        }
        addState(mEndSessionState);

        setInitialState(mIdleState);

        start();

        sendMessage(SESSION_INITIALIZED);
    }

    /** States need to be implemented by profiles */
    public abstract State getIdleState();

    public abstract State getDiscoveryState();

    public abstract State getTransportState();

    public abstract State getSecureState();

    public abstract State getRangingState();

    public abstract State getEndingState();

    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    public void startSession() {
        sendMessage(SESSION_START);
    }

    public void stopSession() {
        sendMessage(SESSION_STOP);
    }

    public void closeSession() {
        sendMessage(RANGING_ENDED);
    }

    public abstract UwbConfig getUwbConfig();

    public void openRangingSession() throws RemoteException {

        FiraOpenSessionParams firaOpenSessionParams =
                UwbConfig.getOpenSessionParams(mSessionInfo, getUwbConfig());

        UwbServiceCore uwbServiceCore = mUwbInjector.getUwbServiceCore();

        uwbServiceCore.openRanging(
                mSessionInfo.mAttributionSource,
                mSessionInfo.mSessionHandle,
                mSessionInfo.mRangingCallbacks,
                firaOpenSessionParams.toBundle(),
                mUwbInjector.getMultichipData().getDefaultChipId()
        );
        sendMessage(RANGING_OPENED);
    }

    protected void startRanging() {
        FiraOpenSessionParams firaOpenSessionParams =
                UwbConfig.getOpenSessionParams(mSessionInfo, getUwbConfig());
        mUwbInjector.getUwbServiceCore().startRanging(mSessionInfo.mSessionHandle,
                firaOpenSessionParams.toBundle());
        sendMessage(RANGING_STARTED);
    }

    protected void stopRanging() {
        mUwbInjector.getUwbServiceCore().stopRanging(mSessionInfo.mSessionHandle);
    }

    protected void closeRanging() {
        mUwbInjector.getUwbServiceCore().closeRanging(mSessionInfo.mSessionHandle);
    }

    protected GenericSpecificationParams getSpecificationInfo() {
        if (mSpecificationParams == null) {
            mSpecificationParams =
                    mUwbInjector.getUwbServiceCore().getCachedSpecificationParams(
                            mSessionInfo.mChipId);
        }
        return mSpecificationParams;
    }

    /**
     * Holds all session related information
     */
    public static class SessionInfo {
        public final AttributionSource mAttributionSource;
        public final SessionHandle mSessionHandle;
        public final Context mContext;
        public final UUID service_instance_id;
        public ServiceProfileInfo mServiceProfileInfo;
        private int mSessionId;
        public IUwbRangingCallbacks mRangingCallbacks;
        private UwbAddress mDeviceAddress;
        public final List<UwbAddress> mDestAddressList;
        public Optional<Integer> subSessionId;
        public final String mChipId;
        public SessionData mSessionData;
        private Optional<byte[]> mSharedSessionKeyInfo = Optional.empty();
        public Optional<byte[]> mSubSessionKey = Optional.empty();
        public Optional<byte[]> mSessionKey = Optional.empty();

        public SessionInfo(AttributionSource attributionSource, SessionHandle sessionHandle,
                ServiceProfileInfo serviceProfileInfo,
                Context context,
                IUwbRangingCallbacks rangingCallbacks,
                String chipId) {
            mAttributionSource = attributionSource;
            mSessionHandle = sessionHandle;
            service_instance_id = serviceProfileInfo.serviceInstanceID;
            mServiceProfileInfo = serviceProfileInfo;
            mContext = context;
            mRangingCallbacks = rangingCallbacks;
            mDestAddressList = new ArrayList<>();
            subSessionId = Optional.empty();
            mChipId = chipId;
        }

        public int getSessionId() {
            return mSessionId;
        }

        public UwbAddress getDeviceAddress() {
            return mDeviceAddress;
        }

        public void setSessionId(int sessionID) {
            mSessionId = sessionID;
        }

        public void setUwbAddress(UwbAddress uwbAddress) {
            mDeviceAddress = uwbAddress;
        }

        public void setSubSessionId(int subSessionId) {
            this.subSessionId = Optional.of(subSessionId);
        }

        public void setSubSessionKey(byte[] subSessionKey) {
            this.mSubSessionKey = Optional.of(subSessionKey);
        }

        public void setSessionKey(byte[] sessionKey) {
            this.mSessionKey = Optional.of(sessionKey);
        }
        /**  Gets the session key info, required for controller of multicast case. */
        @NonNull
        public byte[] getSharedSessionKeyInfo() {
            if (mSharedSessionKeyInfo.isEmpty()) {
                // only set once, as it is shared by all sub sessions.
                mSharedSessionKeyInfo = Optional.of(CsmlUtil.generate256BitRandomKeyInfo());
            }
            return mSharedSessionKeyInfo.get();
        }
    }
}
