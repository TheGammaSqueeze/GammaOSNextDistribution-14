/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.bluetooth.telephony;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothLeCall;
import android.bluetooth.BluetoothLeCallControl;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.BluetoothCallQualityReport;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.InCallService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.bluetooth.hfp.BluetoothHeadsetProxy;
import com.android.bluetooth.tbs.BluetoothLeCallControlProxy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Used to receive updates about calls from the Telecom component. This service is bound to Telecom
 * while there exist calls which potentially require UI. This includes ringing (incoming), dialing
 * (outgoing), and active calls. When the last BluetoothCall is disconnected, Telecom will unbind
 * to the service triggering InCallActivity (via CallList) to finish soon after.
 */
public class BluetoothInCallService extends InCallService {

    private static final String TAG = "BluetoothInCallService";
    // match up with bthf_call_state_t of bt_hf.h
    private static final int CALL_STATE_ACTIVE = 0;
    private static final int CALL_STATE_HELD = 1;
    private static final int CALL_STATE_DIALING = 2;
    private static final int CALL_STATE_ALERTING = 3;
    private static final int CALL_STATE_INCOMING = 4;
    private static final int CALL_STATE_WAITING = 5;
    private static final int CALL_STATE_IDLE = 6;
    private static final int CALL_STATE_DISCONNECTED = 7;

    // match up with bthf_call_state_t of bt_hf.h
    // Terminate all held or set UDUB("busy") to a waiting call
    private static final int CHLD_TYPE_RELEASEHELD = 0;
    // Terminate all active calls and accepts a waiting/held call
    private static final int CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD = 1;
    // Hold all active calls and accepts a waiting/held call
    private static final int CHLD_TYPE_HOLDACTIVE_ACCEPTHELD = 2;
    // Add all held calls to a conference
    private static final int CHLD_TYPE_ADDHELDTOCONF = 3;

    // Indicates that no BluetoothCall is ringing
    private static final int DEFAULT_RINGING_ADDRESS_TYPE = 128;

    private int mNumActiveCalls = 0;
    private int mNumHeldCalls = 0;
    private int mNumChildrenOfActiveCall = 0;
    private int mBluetoothCallState = CALL_STATE_IDLE;
    private String mRingingAddress = "";
    private int mRingingAddressType = DEFAULT_RINGING_ADDRESS_TYPE;
    private BluetoothCall mOldHeldCall = null;
    private boolean mHeadsetUpdatedRecently = false;
    private boolean mIsDisconnectedTonePlaying = false;

    @VisibleForTesting
    boolean mIsTerminatedByClient = false;

    private static final Object LOCK = new Object();

    @VisibleForTesting
    BluetoothHeadsetProxy mBluetoothHeadset;

    @VisibleForTesting
    BluetoothLeCallControlProxy mBluetoothLeCallControl;
    private ExecutorService mExecutor;

    @VisibleForTesting
    public TelephonyManager mTelephonyManager;

    @VisibleForTesting
    public TelecomManager mTelecomManager;

    @VisibleForTesting
    public final HashMap<Integer, CallStateCallback> mCallbacks = new HashMap<>();

    @VisibleForTesting
    public final HashMap<Integer, BluetoothCall> mBluetoothCallHashMap = new HashMap<>();

    private final HashMap<Integer, BluetoothCall> mBluetoothConferenceCallInference =
            new HashMap<>();

    // A queue record the removal order of bluetooth calls
    private final Queue<Integer> mBluetoothCallQueue = new ArrayDeque<>();

    private static BluetoothInCallService sInstance = null;

    public CallInfo mCallInfo = new CallInfo();

    protected boolean mOnCreateCalled = false;

    private int mMaxNumberOfCalls = 0;

    /**
     * Listens to connections and disconnections of bluetooth headsets. We need to save the current
     * bluetooth headset so that we know where to send BluetoothCall updates.
     */
    @VisibleForTesting
    public BluetoothProfile.ServiceListener mProfileListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    synchronized (LOCK) {
                        if (profile == BluetoothProfile.HEADSET) {
                            setBluetoothHeadset(
                                    new BluetoothHeadsetProxy((BluetoothHeadset) proxy));
                            updateHeadsetWithCallState(true /* force */);
                        } else {
                            setBluetoothLeCallControl(
                                    new BluetoothLeCallControlProxy(
                                            (BluetoothLeCallControl) proxy));
                            sendTbsCurrentCallsList();
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    synchronized (LOCK) {
                        if (profile == BluetoothProfile.HEADSET) {
                            setBluetoothHeadset(null);
                        } else {
                            setBluetoothLeCallControl(null);
                        }
                    }
                }
            };

    public class BluetoothAdapterReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (LOCK) {
                if (intent.getAction() != BluetoothAdapter.ACTION_STATE_CHANGED) {
                    Log.w(TAG, "BluetoothAdapterReceiver: Intent action " + intent.getAction());
                    return;
                }
                int state = intent
                        .getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                Log.d(TAG, "Bluetooth Adapter state: " + state);
                if (state == BluetoothAdapter.STATE_ON) {
                    queryPhoneState();
                } else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    clear();
                }
            }
        }
    };

    /**
     * Receives events for global state changes of the bluetooth adapter.
     */
    // TODO: The code is moved from Telecom stack. Since we're running in the BT process itself,
    // we may be able to simplify this in a future patch.
    @VisibleForTesting
    public BluetoothAdapterReceiver mBluetoothAdapterReceiver;

    @VisibleForTesting
    public class CallStateCallback extends Call.Callback {
        public int mLastState;

        public CallStateCallback(int initialState) {
            mLastState = initialState;
        }

        public int getLastState() {
            return mLastState;
        }

        public void onStateChanged(BluetoothCall call, int state) {
            if (mCallInfo.isNullCall(call)) {
                return;
            }
            if (call.isExternalCall()) {
                return;
            }
            if (state == Call.STATE_DISCONNECTING) {
                mLastState = state;
                return;
            }

            Integer tbsCallState = getTbsCallState(call);
            if (mBluetoothLeCallControl != null && tbsCallState != null) {
                mBluetoothLeCallControl.onCallStateChanged(call.getTbsCallId(), tbsCallState);
            }

            // If a BluetoothCall is being put on hold because of a new connecting call, ignore the
            // CONNECTING since the BT state update needs to send out the numHeld = 1 + dialing
            // state atomically.
            // When the BluetoothCall later transitions to DIALING/DISCONNECTED we will then
            // send out the aggregated update.
            if (getLastState() == Call.STATE_ACTIVE && state == Call.STATE_HOLDING) {
                for (BluetoothCall otherCall : mCallInfo.getBluetoothCalls()) {
                    if (otherCall.getState() == Call.STATE_CONNECTING) {
                        mLastState = state;
                        return;
                    }
                }
            }

            // To have an active BluetoothCall and another dialing at the same time is an invalid BT
            // state. We can assume that the active BluetoothCall will be automatically held
            // which will send another update at which point we will be in the right state.
            BluetoothCall activeCall = mCallInfo.getActiveCall();
            if (!mCallInfo.isNullCall(activeCall)
                    && getLastState() == Call.STATE_CONNECTING
                    && (state == Call.STATE_DIALING || state == Call.STATE_PULLING_CALL)) {
                mLastState = state;
                return;
            }
            mLastState = state;
            updateHeadsetWithCallState(false /* force */);
        }

        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            onStateChanged(getBluetoothCallById(System.identityHashCode(call)), state);
        }

        public void onDetailsChanged(BluetoothCall call, Call.Details details) {
            if (mCallInfo.isNullCall(call)) {
                return;
            }
            if (call.isExternalCall()) {
                onCallRemoved(call, false /* forceRemoveCallback */);
            } else {
                onCallAdded(call);
            }
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            super.onDetailsChanged(call, details);
            onDetailsChanged(getBluetoothCallById(System.identityHashCode(call)), details);
        }

        public void onParentChanged(BluetoothCall call) {
            if (mCallInfo.isNullCall(call) || call.isExternalCall()) {
                Log.w(TAG, "null call or external call");
                return;
            }
            if (call.getParentId() != null) {
                // If this BluetoothCall is newly conferenced, ignore the callback.
                // We only care about the one sent for the parent conference call.
                Log.d(TAG,
                        "Ignoring onIsConferenceChanged from child BluetoothCall with new parent");
                return;
            }
            updateHeadsetWithCallState(false /* force */);
        }

        @Override
        public void onParentChanged(Call call, Call parent) {
            super.onParentChanged(call, parent);
            onParentChanged(
                    getBluetoothCallById(System.identityHashCode(call)));
        }

        public void onChildrenChanged(BluetoothCall call, List<BluetoothCall> children) {
            if (mCallInfo.isNullCall(call) || call.isExternalCall()) {
                Log.w(TAG, "null call or external call");
                return;
            }
            if (call.getChildrenIds().size() == 1) {
                // If this is a parent BluetoothCall with only one child,
                // ignore the callback as well since the minimum number of child calls to
                // start a conference BluetoothCall is 2. We expect this to be called again
                // when the parent BluetoothCall has another child BluetoothCall added.
                Log.d(TAG,
                        "Ignoring onIsConferenceChanged from parent with only one child call");
                return;
            }
            updateHeadsetWithCallState(false /* force */);
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            super.onChildrenChanged(call, children);
            onChildrenChanged(
                    getBluetoothCallById(System.identityHashCode(call)),
                    getBluetoothCallsByIds(BluetoothCall.getIds(children)));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind. Intent: " + intent);
        IBinder binder = super.onBind(intent);
        mTelephonyManager = getSystemService(TelephonyManager.class);
        mTelecomManager = getSystemService(TelecomManager.class);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind. Intent: " + intent);
        return super.onUnbind(intent);
    }

    public BluetoothInCallService() {
        Log.i(TAG, "BluetoothInCallService is created");
        sInstance = this;
        mExecutor = Executors.newSingleThreadExecutor();
    }

    public static BluetoothInCallService getInstance() {
        return sInstance;
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    protected void enforceModifyPermission() {
        enforceCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE, null);
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean answerCall() {
        synchronized (LOCK) {
            enforceModifyPermission();
            Log.i(TAG, "BT - answering call");
            BluetoothCall call = mCallInfo.getRingingOrSimulatedRingingCall();
            if (mCallInfo.isNullCall(call)) {
                return false;
            }
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean hangupCall() {
        synchronized (LOCK) {
            enforceModifyPermission();
            Log.i(TAG, "BT - hanging up call");
            BluetoothCall call = mCallInfo.getForegroundCall();
            if (mCallInfo.isNullCall(call)) {
                return false;
            }
            // release the parent if there is a conference call
            BluetoothCall conferenceCall = getBluetoothCallById(call.getParentId());
            if (!mCallInfo.isNullCall(conferenceCall)
                    && conferenceCall.getState() == Call.STATE_ACTIVE) {
                Log.i(TAG, "BT - hanging up conference call");
                call = conferenceCall;
            }
            if (call.getState() == Call.STATE_RINGING) {
                call.reject(false, "");
            } else {
                call.disconnect();
            }
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean sendDtmf(int dtmf) {
        synchronized (LOCK) {
            enforceModifyPermission();
            Log.i(TAG, "BT - sendDtmf " + dtmf);
            BluetoothCall call = mCallInfo.getForegroundCall();
            if (mCallInfo.isNullCall(call)) {
                return false;
            }
            // TODO: Consider making this a queue instead of starting/stopping
            // in quick succession.
            call.playDtmfTone((char) dtmf);
            call.stopDtmfTone();
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public String getNetworkOperator()  {
        synchronized (LOCK) {
            enforceModifyPermission();
            Log.i(TAG, "getNetworkOperator");
            PhoneAccount account = mCallInfo.getBestPhoneAccount();
            if (account != null && account.getLabel() != null) {
                return account.getLabel().toString();
            }
            // Finally, just get the network name from telephony.
            return mTelephonyManager.getNetworkOperatorName();
        }
    }

    /**
     * Gets the brearer technology.
     *
     * @return bearer technology as defined in Bluetooth Assigned Numbers
     */
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public int getBearerTechnology()  {
        synchronized (LOCK) {
            enforceModifyPermission();
            Log.i(TAG, "getBearerTechnology");
            // Get the network name from telephony.
            int dataNetworkType = mTelephonyManager.getDataNetworkType();
            switch (dataNetworkType) {
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                case TelephonyManager.NETWORK_TYPE_GSM:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_GSM;

                case TelephonyManager.NETWORK_TYPE_GPRS:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_2G;

                case TelephonyManager.NETWORK_TYPE_EDGE :
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_3G;

                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_WCDMA;

                case TelephonyManager.NETWORK_TYPE_LTE:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_LTE;

                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_CDMA;

                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_4G;

                case TelephonyManager.NETWORK_TYPE_IWLAN:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_WIFI;

                case TelephonyManager.NETWORK_TYPE_NR:
                    return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_5G;
            }

            return BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_GSM;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public String getSubscriberNumber() {
        synchronized (LOCK) {
            enforceModifyPermission();
            Log.i(TAG, "getSubscriberNumber");
            String address = null;
            PhoneAccount account = mCallInfo.getBestPhoneAccount();
            if (account != null) {
                Uri addressUri = account.getAddress();
                if (addressUri != null) {
                    address = addressUri.getSchemeSpecificPart();
                }
            }
            if (TextUtils.isEmpty(address)) {
                if (mTelephonyManager == null) {
                    address = null;
                } else {
                    address = mTelephonyManager.getLine1Number();
                }
                if (address == null) address = "";
            }
            return address;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean listCurrentCalls() {
        synchronized (LOCK) {
            if (!mOnCreateCalled) {
                Log.w(TAG, "listcurrentCalls() is called before onCreate()");
                return false;
            }
            enforceModifyPermission();
            // only log if it is after we recently updated the headset state or else it can
            // clog the android log since this can be queried every second.
            boolean logQuery = mHeadsetUpdatedRecently;
            mHeadsetUpdatedRecently = false;

            if (logQuery) {
                Log.i(TAG, "listcurrentCalls");
            }

            sendListOfCalls(logQuery);
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean queryPhoneState() {
        synchronized (LOCK) {
            enforceModifyPermission();
            Log.i(TAG, "queryPhoneState");
            updateHeadsetWithCallState(true);
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public boolean processChld(int chld) {
        synchronized (LOCK) {
            enforceModifyPermission();
            final long token = Binder.clearCallingIdentity();
            try {
                Log.i(TAG, "processChld " + chld);
                return _processChld(chld);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    public void onCallAdded(BluetoothCall call) {
        if (call.isExternalCall()) {
            return;
        }
        if (!mBluetoothCallHashMap.containsKey(call.getId())) {
            Log.d(TAG, "onCallAdded");
            CallStateCallback callback = new CallStateCallback(call.getState());
            mCallbacks.put(call.getId(), callback);
            call.registerCallback(callback);

            mBluetoothCallHashMap.put(call.getId(), call);
            if (!call.isConference()) {
                mMaxNumberOfCalls = Integer.max(mMaxNumberOfCalls, mBluetoothCallHashMap.size());
            }
            updateHeadsetWithCallState(false /* force */);

            BluetoothLeCall tbsCall = createTbsCall(call);
            if (mBluetoothLeCallControl != null && tbsCall != null) {
                mBluetoothLeCallControl.onCallAdded(tbsCall);
            }
        }
    }

    public void sendBluetoothCallQualityReport(
            long timestamp,
            int rssi,
            int snr,
            int retransmissionCount,
            int packetsNotReceiveCount,
            int negativeAcknowledgementCount) {
        BluetoothCall call = mCallInfo.getForegroundCall();
        if (mCallInfo.isNullCall(call)) {
            Log.w(TAG, "No foreground call while trying to send BQR");
            return;
        }
        Bundle b = new Bundle();
        b.putParcelable(
                BluetoothCallQualityReport.EXTRA_BLUETOOTH_CALL_QUALITY_REPORT,
                new BluetoothCallQualityReport.Builder()
                        .setSentTimestampMillis(timestamp)
                        .setChoppyVoice(true)
                        .setRssiDbm(rssi)
                        .setSnrDb(snr)
                        .setRetransmittedPacketsCount(retransmissionCount)
                        .setPacketsNotReceivedCount(packetsNotReceiveCount)
                        .setNegativeAcknowledgementCount(negativeAcknowledgementCount)
                        .build());
        call.sendCallEvent(
                BluetoothCallQualityReport.EVENT_BLUETOOTH_CALL_QUALITY_REPORT, b);
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        onCallAdded(new BluetoothCall(call));
    }

    /**
     * Called when a {@code BluetoothCall} has been removed from this in-call session.
     *
     * @param call the {@code BluetoothCall} to remove
     * @param forceRemoveCallback if true, this will always unregister this {@code InCallService} as
     *                            a callback for the given {@code BluetoothCall}, when false, this
     *                            will not remove the callback when the {@code BluetoothCall} is
     *                            external so that the call can be added back if no longer external.
     */
    public void onCallRemoved(BluetoothCall call, boolean forceRemoveCallback) {
        Log.d(TAG, "onCallRemoved");
        CallStateCallback callback = getCallback(call);
        if (callback != null && (forceRemoveCallback || !call.isExternalCall())) {
            call.unregisterCallback(callback);
        }

        if (mBluetoothCallHashMap.containsKey(call.getId())) {
            mBluetoothCallHashMap.remove(call.getId());

            DisconnectCause cause = call.getDisconnectCause();
            if (cause != null && cause.getCode() == DisconnectCause.OTHER) {
                Log.d(TAG, "add inference call with reason: " + cause.getReason());
                mBluetoothCallQueue.add(call.getId());
                mBluetoothConferenceCallInference.put(call.getId(), call);
                // queue size limited to 2 because merge operation only happens on 2 calls
                // we are only interested in last 2 calls merged
                if (mBluetoothCallQueue.size() > 2) {
                    Integer callId = mBluetoothCallQueue.peek();
                    mBluetoothCallQueue.remove();
                    mBluetoothConferenceCallInference.remove(callId);
                }
            }
            // As there is at most 1 conference call, so clear inference when parent call ends
            if (call.isConference()) {
                Log.d(TAG, "conference call ends, clear inference");
                mBluetoothConferenceCallInference.clear();
                mBluetoothCallQueue.clear();
            }
        }

        updateHeadsetWithCallState(false /* force */);

        if (mBluetoothLeCallControl != null) {
            mBluetoothLeCallControl.onCallRemoved(call.getTbsCallId(), getTbsTerminationReason(call));
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        BluetoothCall bluetoothCall = getBluetoothCallById(System.identityHashCode(call));
        if (bluetoothCall == null) {
            Log.w(TAG, "onCallRemoved, BluetoothCall is removed before registered");
            return;
        }
        onCallRemoved(bluetoothCall, true /* forceRemoveCallback */);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        super.onCallAudioStateChanged(audioState);
        Log.d(TAG, "onCallAudioStateChanged, audioState == " + audioState);
    }


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        BluetoothAdapter.getDefaultAdapter()
                .getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);
        BluetoothAdapter.getDefaultAdapter()
                .getProfileProxy(this, mProfileListener, BluetoothProfile.LE_CALL_CONTROL);
        mBluetoothAdapterReceiver = new BluetoothAdapterReceiver();
        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mBluetoothAdapterReceiver, intentFilter);
        mOnCreateCalled = true;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        clear();
        mOnCreateCalled = false;
        super.onDestroy();
    }

    @Override
    @VisibleForTesting
    public void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @VisibleForTesting
    void clear() {
        Log.d(TAG, "clear");
        if (mBluetoothAdapterReceiver != null) {
            unregisterReceiver(mBluetoothAdapterReceiver);
            mBluetoothAdapterReceiver = null;
        }
        if (mBluetoothHeadset != null) {
            mBluetoothHeadset.closeBluetoothHeadsetProxy(this);
            mBluetoothHeadset = null;
        }
        if (mBluetoothLeCallControl != null) {
            mBluetoothLeCallControl.unregisterBearer();
            mBluetoothLeCallControl.closeBluetoothLeCallControlProxy(this);
        }
        mProfileListener = null;
        sInstance = null;
        mCallbacks.clear();
        mBluetoothCallHashMap.clear();
        mBluetoothConferenceCallInference.clear();
        mBluetoothCallQueue.clear();
        mMaxNumberOfCalls = 0;
    }

    private static boolean isConferenceWithNoChildren(BluetoothCall call) {
        return call.isConference()
            && (call.can(Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN)
                    || call.getChildrenIds().isEmpty());
    }

    private void sendListOfCalls(boolean shouldLog) {
        Collection<BluetoothCall> calls = mCallInfo.getBluetoothCalls();

        // either do conference call CLCC index inference or normal conference call
        BluetoothCall conferenceCallChildrenNotReady = null;
        for (BluetoothCall call : calls) {
            // find the conference call parent among calls
            if (call.isConference() && !mBluetoothConferenceCallInference.isEmpty()) {
                Log.d(
                        TAG,
                        "conference call inferred size: "
                                + mBluetoothConferenceCallInference.size()
                                + " current size: "
                                + mBluetoothCallHashMap.size());
                // Do conference call inference until at least 2 children arrive
                // If carrier sends children info, then inference will end when info arrives.
                // If carrier doesn't send children info, then inference won't impact actual value.
                if (call.getChildrenIds().size() >= 2) {
                    mBluetoothConferenceCallInference.clear();
                    break;
                }
                conferenceCallChildrenNotReady = call;
            }
        }
        if (conferenceCallChildrenNotReady != null) {
            SortedMap<Integer, Object[]> clccResponseMap = new TreeMap<>();
            for (BluetoothCall inferredCall : mBluetoothConferenceCallInference.values()) {
                if (inferredCall.isCallNull() || inferredCall.getHandle() == null) {
                    Log.w(TAG, "inferredCall does not have handle");
                    continue;
                }
                // save the index so later on when real children arrive, index is the same
                int index = inferredCall.mClccIndex;
                if (index == -1) {
                    Log.w(TAG, "inferred index is not valid");
                    continue;
                }

                // associate existing bluetoothCall with inferredCall based on call handle
                for (BluetoothCall bluetoothCall : mBluetoothCallHashMap.values()) {
                    if (bluetoothCall.getHandle() == null) {
                        Log.w(TAG, "call id: " + bluetoothCall.getId() + " handle is null");
                        continue;
                    }
                    if (mTelephonyManager == null) {
                        Log.w(TAG, "mTelephonyManager is null");
                        continue;
                    }
                    boolean isSame =
                            PhoneNumberUtils.areSamePhoneNumber(
                                    bluetoothCall.getHandle().toString(),
                                    inferredCall.getHandle().toString(),
                                    mTelephonyManager.getNetworkCountryIso());
                    if (isSame) {
                        Log.d(
                                TAG,
                                "found conference call children that has same call handle, "
                                        + "call id: "
                                        + bluetoothCall.getId());
                        bluetoothCall.mClccIndex = inferredCall.mClccIndex;
                        break;
                    }
                }

                int direction = inferredCall.isIncoming() ? 1 : 0;
                int state = CALL_STATE_ACTIVE;
                boolean isPartOfConference = true;
                final Uri addressUri;
                if (inferredCall.getGatewayInfo() != null) {
                    addressUri = inferredCall.getGatewayInfo().getOriginalAddress();
                } else {
                    addressUri = inferredCall.getHandle();
                }
                String address = addressUri == null ? null : addressUri.getSchemeSpecificPart();
                if (address != null) {
                    address = PhoneNumberUtils.stripSeparators(address);
                }
                int addressType = address == null ? -1 : PhoneNumberUtils.toaFromString(address);
                clccResponseMap.put(
                        index,
                        new Object[] {
                            index, direction, state, 0, isPartOfConference, address, addressType
                        });
            }
            // sort CLCC response based on index
            for (Object[] response : clccResponseMap.values()) {
                if (response.length < 7) {
                    Log.e(TAG, "clccResponseMap entry too short");
                    continue;
                }
                Log.i(
                        TAG,
                        String.format(
                                "sending inferred clcc for BluetoothCall: index %d, direction"
                                        + " %d, state %d, isPartOfConference %b, addressType %d",
                                (int) response[0],
                                (int) response[1],
                                (int) response[2],
                                (boolean) response[4],
                                (int) response[6]));
                mBluetoothHeadset.clccResponse(
                        (int) response[0],
                        (int) response[1],
                        (int) response[2],
                        (int) response[3],
                        (boolean) response[4],
                        (String) response[5],
                        (int) response[6]);
            }
            sendClccEndMarker();
            return;
        }

        for (BluetoothCall call : calls) {
            // We don't send the parent conference BluetoothCall to the bluetooth device.
            // We do, however want to send conferences that have no children to the bluetooth
            // device (e.g. IMS Conference).
            boolean isConferenceWithNoChildren = isConferenceWithNoChildren(call);
            Log.i(
                    TAG,
                    "sendListOfCalls isConferenceWithNoChildren "
                            + isConferenceWithNoChildren
                            + ", call.getChildrenIds() size "
                            + call.getChildrenIds().size());
            if (!call.isConference() || isConferenceWithNoChildren) {
                sendClccForCall(call, shouldLog);
            }
        }
        sendClccEndMarker();
    }

    private void sendClccEndMarker() {
        // End marker is recognized with an index value of 0. All other parameters are ignored.
        if (mBluetoothHeadset != null) {
            mBluetoothHeadset.clccResponse(0 /* index */, 0, 0, 0, false, null, 0);
        }
    }

    /**
     * Sends a single clcc (C* List Current Calls) event for the specified call.
     */
    private void sendClccForCall(BluetoothCall call, boolean shouldLog) {
        boolean isForeground = mCallInfo.getForegroundCall() == call;
        int state = getBtCallState(call, isForeground);
        boolean isPartOfConference = false;
        boolean isConferenceWithNoChildren = isConferenceWithNoChildren(call);

        if (state == CALL_STATE_IDLE) {
            return;
        }

        BluetoothCall conferenceCall = getBluetoothCallById(call.getParentId());
        if (!mCallInfo.isNullCall(conferenceCall)) {
            isPartOfConference = true;

            if (conferenceCall.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)) {
                // Run some alternative states for CDMA Conference-level merge/swap support.
                // Basically, if BluetoothCall supports swapping or merging at the conference-level,
                // then we need to expose the calls as having distinct states
                // (ACTIVE vs CAPABILITY_HOLD) or
                // the functionality won't show up on the bluetooth device.

                // Before doing any special logic, ensure that we are dealing with an
                // ACTIVE BluetoothCall and that the conference itself has a notion of
                // the current "active" child call.
                BluetoothCall activeChild =
                        getBluetoothCallById(
                                conferenceCall.getGenericConferenceActiveChildCallId());
                if (state == CALL_STATE_ACTIVE && !mCallInfo.isNullCall(activeChild)) {
                    // Reevaluate state if we can MERGE or if we can SWAP without previously having
                    // MERGED.
                    boolean shouldReevaluateState =
                            conferenceCall.can(Connection.CAPABILITY_MERGE_CONFERENCE)
                                    || (conferenceCall.can(Connection.CAPABILITY_SWAP_CONFERENCE)
                                            && !conferenceCall.wasConferencePreviouslyMerged());

                    if (shouldReevaluateState) {
                        isPartOfConference = false;
                        if (call == activeChild) {
                            state = CALL_STATE_ACTIVE;
                        } else {
                            // At this point we know there is an "active" child and we know that it
                            // is not this call, so set it to HELD instead.
                            state = CALL_STATE_HELD;
                        }
                    }
                }
            }
            if (conferenceCall.getState() == Call.STATE_HOLDING
                    && conferenceCall.can(Connection.CAPABILITY_MANAGE_CONFERENCE)) {
                // If the parent IMS CEP conference BluetoothCall is on hold, we should mark
                // this BluetoothCall as being on hold regardless of what the other
                // children are doing.
                state = CALL_STATE_HELD;
            }
        } else if (isConferenceWithNoChildren) {
            // Handle the special case of an IMS conference BluetoothCall without conference
            // event package support.
            // The BluetoothCall will be marked as a conference, but the conference will not have
            // child calls where conference event packages are not used by the carrier.
            isPartOfConference = true;
        }

        int index = getIndexForCall(call);
        int direction = call.isIncoming() ? 1 : 0;
        final Uri addressUri;
        if (call.getGatewayInfo() != null) {
            addressUri = call.getGatewayInfo().getOriginalAddress();
        } else {
            addressUri = call.getHandle();
        }

        String address = addressUri == null ? null : addressUri.getSchemeSpecificPart();
        if (address != null) {
            address = PhoneNumberUtils.stripSeparators(address);
        }

        int addressType = address == null ? -1 : PhoneNumberUtils.toaFromString(address);

        if (shouldLog) {
            Log.i(TAG, "sending clcc for BluetoothCall "
                            + index + ", "
                            + direction + ", "
                            + state + ", "
                            + isPartOfConference + ", "
                            + addressType);
        }

        if (mBluetoothHeadset == null) {
            Log.w(TAG, "mBluetoothHeasdset is null when sending clcc for BluetoothCall "
                    + index + ", "
                    + direction + ", "
                    + state + ", "
                    + isPartOfConference + ", "
                    + addressType);
        } else {
            mBluetoothHeadset.clccResponse(
                    index, direction, state, 0, isPartOfConference, address, addressType);
        }
    }

    private String getClccMapKey(BluetoothCall call) {
        if (mCallInfo.isNullCall(call) || call.getHandle() == null) {
            return "";
        }
        Uri handle = call.getHandle();
        String key;
        if (call.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)) {
            key = handle.toString() + " self managed " + call.getId();
        } else {
            key = handle.toString();
        }
        return key;
    }

    int getNextAvailableClccIndex(int index) {
        // find the next available smallest index
        SortedSet<Integer> availableIndex = new TreeSet<>();
        for (int i = index; i <= mMaxNumberOfCalls + 1; i++) {
            availableIndex.add(i);
        }
        for (BluetoothCall bluetoothCall : mBluetoothCallHashMap.values()) {
            int callCLCCIndex = bluetoothCall.mClccIndex;
            if (availableIndex.contains(callCLCCIndex)) {
                availableIndex.remove(callCLCCIndex);
            }
        }
        Log.d(TAG, "availableIndex first: " + availableIndex.first());
        return availableIndex.first();
    }

    /**
     * Returns the caches index for the specified call. If no such index exists, then an index is
     * given (the smallest number starting from 1 that isn't already taken).
     */
    private int getIndexForCall(BluetoothCall call) {
        if (mCallInfo.isNullCall(call)) {
            Log.w(TAG, "empty or null call");
            return -1;
        }
        if (call.mClccIndex >= 1) {
            return call.mClccIndex;
        }

        int index = 1; // Indexes for bluetooth clcc are 1-based.
        if (call.isConference()) {
            index = mMaxNumberOfCalls + 1; // The conference call should have a higher index
            Log.i(TAG, "getIndexForCall for conference call starting from " + mMaxNumberOfCalls);
        }

        // NOTE: Indexes are removed in {@link #onCallRemoved}.
        call.mClccIndex = getNextAvailableClccIndex(index);
        Log.d(TAG, "call " + call.getId() + " CLCC index is " + call.mClccIndex);
        return call.mClccIndex;
    }


    private boolean _processChld(int chld) {
        BluetoothCall activeCall = mCallInfo.getActiveCall();
        BluetoothCall ringingCall = mCallInfo.getRingingOrSimulatedRingingCall();
        if (ringingCall == null) {
            Log.i(TAG, "ringingCall null at processChld");
        } else {
            Log.i(TAG, "ringingCall hashcode: " + ringingCall.hashCode());
        }

        BluetoothCall heldCall = mCallInfo.getHeldCall();

        Log.i(TAG, "Active: " + activeCall
                + " Ringing: " + ringingCall
                + " Held: " + heldCall
                + " chld: " + chld);

        if (chld == CHLD_TYPE_RELEASEHELD) {
            Log.i(TAG, "chld is CHLD_TYPE_RELEASEHELD");
            if (!mCallInfo.isNullCall(ringingCall)) {
                Log.i(TAG, "reject ringing call " + ringingCall.hashCode());
                ringingCall.reject(false, null);
                return true;
            } else if (!mCallInfo.isNullCall(heldCall)) {
                heldCall.disconnect();
                return true;
            }
        } else if (chld == CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD) {
            if (mCallInfo.isNullCall(activeCall)
                    && mCallInfo.isNullCall(ringingCall)
                    && mCallInfo.isNullCall(heldCall)) {
                return false;
            }
            if (!mCallInfo.isNullCall(activeCall)) {
                BluetoothCall conferenceCall = getBluetoothCallById(activeCall.getParentId());
                if (!mCallInfo.isNullCall(conferenceCall)
                        && conferenceCall.getState() == Call.STATE_ACTIVE) {
                    Log.i(TAG, "CHLD: disconnect conference call");
                    conferenceCall.disconnect();
                } else {
                    activeCall.disconnect();
                }
            }
            if (!mCallInfo.isNullCall(ringingCall)) {
                ringingCall.answer(ringingCall.getVideoState());
            } else if (!mCallInfo.isNullCall(heldCall)) {
                heldCall.unhold();
            }
            return true;
        } else if (chld == CHLD_TYPE_HOLDACTIVE_ACCEPTHELD) {
            if (!mCallInfo.isNullCall(activeCall)
                    && activeCall.can(Connection.CAPABILITY_SWAP_CONFERENCE)) {
                activeCall.swapConference();
                Log.i(TAG, "CDMA calls in conference swapped, updating headset");
                updateHeadsetWithCallState(true /* force */);
                return true;
            } else if (!mCallInfo.isNullCall(ringingCall)) {
                ringingCall.answer(VideoProfile.STATE_AUDIO_ONLY);
                return true;
            } else if (!mCallInfo.isNullCall(heldCall)) {
                // CallsManager will hold any active calls when unhold() is called on a
                // currently-held call.
                heldCall.unhold();
                return true;
            } else if (!mCallInfo.isNullCall(activeCall)
                    && activeCall.can(Connection.CAPABILITY_HOLD)) {
                activeCall.hold();
                return true;
            }
        } else if (chld == CHLD_TYPE_ADDHELDTOCONF) {
            if (!mCallInfo.isNullCall(activeCall)) {
                if (activeCall.can(Connection.CAPABILITY_MERGE_CONFERENCE)) {
                    activeCall.mergeConference();
                    return true;
                } else {
                    List<BluetoothCall> conferenceable = getBluetoothCallsByIds(
                            activeCall.getConferenceableCalls());
                    if (!conferenceable.isEmpty()) {
                        activeCall.conference(conferenceable.get(0));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Sends an update of the current BluetoothCall state to the current Headset.
     *
     * @param force {@code true} if the headset state should be sent regardless if no changes to
     * the state have occurred, {@code false} if the state should only be sent if the state
     * has changed.
     */
    private void updateHeadsetWithCallState(boolean force) {
        BluetoothCall activeCall = mCallInfo.getActiveCall();
        BluetoothCall ringingCall = mCallInfo.getRingingOrSimulatedRingingCall();
        BluetoothCall heldCall = mCallInfo.getHeldCall();

        int bluetoothCallState = getBluetoothCallStateForUpdate();

        String ringingAddress = null;
        int ringingAddressType = DEFAULT_RINGING_ADDRESS_TYPE;
        String ringingName = null;
        if (!mCallInfo.isNullCall(ringingCall) && ringingCall.getHandle() != null
                && !ringingCall.isSilentRingingRequested()) {
            ringingAddress = ringingCall.getHandle().getSchemeSpecificPart();
            if (ringingAddress != null) {
                ringingAddressType = PhoneNumberUtils.toaFromString(ringingAddress);
            }
            ringingName = ringingCall.getCallerDisplayName();
            if (TextUtils.isEmpty(ringingName)) {
                ringingName = ringingCall.getContactDisplayName();
            }
        }
        if (ringingAddress == null) {
            ringingAddress = "";
        }

        int numActiveCalls = mCallInfo.isNullCall(activeCall) ? 0 : 1;
        int numHeldCalls = mCallInfo.getNumHeldCalls();
        int numChildrenOfActiveCall =
                mCallInfo.isNullCall(activeCall) ? 0 : activeCall.getChildrenIds().size();

        // Intermediate state for GSM calls which are in the process of being swapped.
        // TODO: Should we be hardcoding this value to 2 or should we check if all top level calls
        //       are held?
        boolean callsPendingSwitch = (numHeldCalls == 2);

        // For conference calls which support swapping the active BluetoothCall within the
        // conference (namely CDMA calls) we need to expose that as a held BluetoothCall
        // in order for the BT device to show "swap" and "merge" functionality.
        boolean ignoreHeldCallChange = false;
        if (!mCallInfo.isNullCall(activeCall) && activeCall.isConference()
                && !activeCall.can(Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN)) {
            if (activeCall.can(Connection.CAPABILITY_SWAP_CONFERENCE)) {
                // Indicate that BT device should show SWAP command by indicating that there is a
                // BluetoothCall on hold, but only if the conference wasn't previously merged.
                numHeldCalls = activeCall.wasConferencePreviouslyMerged() ? 0 : 1;
            } else if (activeCall.can(Connection.CAPABILITY_MERGE_CONFERENCE)) {
                numHeldCalls = 1;  // Merge is available, so expose via numHeldCalls.
            }

            for (Integer id : activeCall.getChildrenIds()) {
                // Held BluetoothCall has changed due to it being combined into a CDMA conference.
                // Keep track of this and ignore any future update since it doesn't really count
                // as a BluetoothCall change.
                if (mOldHeldCall != null && Objects.equals(mOldHeldCall.getId(), id)) {
                    ignoreHeldCallChange = true;
                    break;
                }
            }
        }

        if (mBluetoothHeadset != null
                && (force
                    || (!callsPendingSwitch
                        && (numActiveCalls != mNumActiveCalls
                            || numChildrenOfActiveCall != mNumChildrenOfActiveCall
                            || numHeldCalls != mNumHeldCalls
                            || bluetoothCallState != mBluetoothCallState
                            || !TextUtils.equals(ringingAddress, mRingingAddress)
                            || ringingAddressType != mRingingAddressType
                            || (heldCall != mOldHeldCall && !ignoreHeldCallChange))))) {

            // If the BluetoothCall is transitioning into the alerting state, send DIALING first.
            // Some devices expect to see a DIALING state prior to seeing an ALERTING state
            // so we need to send it first.
            boolean sendDialingFirst = mBluetoothCallState != bluetoothCallState
                    && bluetoothCallState == CALL_STATE_ALERTING;

            mOldHeldCall = heldCall;
            mNumActiveCalls = numActiveCalls;
            mNumChildrenOfActiveCall = numChildrenOfActiveCall;
            mNumHeldCalls = numHeldCalls;
            mBluetoothCallState = bluetoothCallState;
            mRingingAddress = ringingAddress;
            mRingingAddressType = ringingAddressType;

            if (sendDialingFirst) {
                // Log in full to make logs easier to debug.
                Log.i(TAG, "updateHeadsetWithCallState "
                                + "numActive " + mNumActiveCalls + ", "
                                + "numHeld " + mNumHeldCalls + ", "
                                + "callState " + CALL_STATE_DIALING + ", "
                                + "ringing type " + mRingingAddressType);
                mBluetoothHeadset.phoneStateChanged(
                        mNumActiveCalls,
                        mNumHeldCalls,
                        CALL_STATE_DIALING,
                        mRingingAddress,
                        mRingingAddressType,
                        ringingName);
            }

            Log.i(TAG, "updateHeadsetWithCallState "
                    + "numActive " + mNumActiveCalls + ", "
                    + "numHeld " + mNumHeldCalls + ", "
                    + "callState " + mBluetoothCallState + ", "
                    + "ringing type " + mRingingAddressType);

            mBluetoothHeadset.phoneStateChanged(
                    mNumActiveCalls,
                    mNumHeldCalls,
                    mBluetoothCallState,
                    mRingingAddress,
                    mRingingAddressType,
                    ringingName);

            mHeadsetUpdatedRecently = true;
        }
    }

    private int getBluetoothCallStateForUpdate() {
        BluetoothCall ringingCall = mCallInfo.getRingingOrSimulatedRingingCall();
        BluetoothCall dialingCall = mCallInfo.getOutgoingCall();
        boolean hasOnlyDisconnectedCalls = mCallInfo.hasOnlyDisconnectedCalls();

        //
        // !! WARNING !!
        // You will note that CALL_STATE_WAITING, CALL_STATE_HELD, and CALL_STATE_ACTIVE are not
        // used in this version of the BluetoothCall state mappings.  This is on purpose.
        // phone_state_change() in btif_hf.c is not written to handle these states. Only with the
        // listCalls*() method are WAITING and ACTIVE used.
        // Using the unsupported states here caused problems with inconsistent state in some
        // bluetooth devices (like not getting out of ringing state after answering a call).
        //
        int bluetoothCallState = CALL_STATE_IDLE;
        if (!mCallInfo.isNullCall(ringingCall) && !ringingCall.isSilentRingingRequested()) {
            bluetoothCallState = CALL_STATE_INCOMING;
        } else if (!mCallInfo.isNullCall(dialingCall)) {
            bluetoothCallState = CALL_STATE_ALERTING;
        } else if (hasOnlyDisconnectedCalls || mIsDisconnectedTonePlaying) {
            // Keep the DISCONNECTED state until the disconnect tone's playback is done
            bluetoothCallState = CALL_STATE_DISCONNECTED;
        }
        return bluetoothCallState;
    }

    private int getBtCallState(BluetoothCall call, boolean isForeground) {
        switch (call.getState()) {
            case Call.STATE_NEW:
            case Call.STATE_DISCONNECTED:
            case Call.STATE_AUDIO_PROCESSING:
                return CALL_STATE_IDLE;

            case Call.STATE_ACTIVE:
                return CALL_STATE_ACTIVE;

            case Call.STATE_CONNECTING:
            case Call.STATE_SELECT_PHONE_ACCOUNT:
            case Call.STATE_DIALING:
            case Call.STATE_PULLING_CALL:
                // Yes, this is correctly returning ALERTING.
                // "Dialing" for BT means that we have sent information to the service provider
                // to place the BluetoothCall but there is no confirmation that the BluetoothCall
                // is going through. When there finally is confirmation, the ringback is
                // played which is referred to as an "alert" tone, thus, ALERTING.
                // TODO: We should consider using the ALERTING terms in Telecom because that
                // seems to be more industry-standard.
                return CALL_STATE_ALERTING;

            case Call.STATE_HOLDING:
                return CALL_STATE_HELD;

            case Call.STATE_RINGING:
            case Call.STATE_SIMULATED_RINGING:
                if (call.isSilentRingingRequested()) {
                    return CALL_STATE_IDLE;
                } else if (isForeground) {
                    return CALL_STATE_INCOMING;
                } else {
                    return CALL_STATE_WAITING;
                }
        }
        return CALL_STATE_IDLE;
    }

    @VisibleForTesting
    public CallStateCallback getCallback(BluetoothCall call) {
        return mCallbacks.get(call.getId());
    }

    @VisibleForTesting
    public void setBluetoothHeadset(BluetoothHeadsetProxy bluetoothHeadset) {
        mBluetoothHeadset = bluetoothHeadset;
    }

    @VisibleForTesting
    public BluetoothCall getBluetoothCallById(Integer id) {
        if (mBluetoothCallHashMap.containsKey(id)) {
            return mBluetoothCallHashMap.get(id);
        }
        return null;
    }

    @VisibleForTesting
    public List<BluetoothCall> getBluetoothCallsByIds(List<Integer> ids) {
        List<BluetoothCall> calls = new ArrayList<>();
        for (Integer id : ids) {
            BluetoothCall call = getBluetoothCallById(id);
            if (!mCallInfo.isNullCall(call)) {
                calls.add(call);
            }
        }
        return calls;
    }

    // extract call information functions out into this part, so we can mock it in testing
    @VisibleForTesting
    public class CallInfo {

        public BluetoothCall getForegroundCall() {
            LinkedHashSet<Integer> states = new LinkedHashSet<Integer>();
            BluetoothCall foregroundCall;

            states.add(Call.STATE_CONNECTING);
            foregroundCall = getCallByStates(states);
            if (!mCallInfo.isNullCall(foregroundCall)) {
                return foregroundCall;
            }

            states.clear();
            states.add(Call.STATE_ACTIVE);
            states.add(Call.STATE_DIALING);
            states.add(Call.STATE_PULLING_CALL);
            foregroundCall = getCallByStates(states);
            if (!mCallInfo.isNullCall(foregroundCall)) {
                return foregroundCall;
            }

            states.clear();
            states.add(Call.STATE_RINGING);
            foregroundCall = getCallByStates(states);
            if (!mCallInfo.isNullCall(foregroundCall)) {
                return foregroundCall;
            }

            return null;
        }

        public BluetoothCall getCallByStates(LinkedHashSet<Integer> states) {
            List<BluetoothCall> calls = getBluetoothCalls();
            for (BluetoothCall call : calls) {
                if (states.contains(call.getState())) {
                    return call;
                }
            }
            return null;
        }

        public BluetoothCall getCallByState(int state) {
            List<BluetoothCall> calls = getBluetoothCalls();
            for (BluetoothCall call : calls) {
                if (state == call.getState()) {
                    return call;
                }
            }
            return null;
        }

        public int getNumHeldCalls() {
            int number = 0;
            List<BluetoothCall> calls = getBluetoothCalls();
            for (BluetoothCall call : calls) {
                if (call.getState() == Call.STATE_HOLDING) {
                    number++;
                }
            }
            return number;
        }

        public boolean hasOnlyDisconnectedCalls() {
            List<BluetoothCall> calls = getBluetoothCalls();
            if (calls.size() == 0) {
                return false;
            }
            for (BluetoothCall call : calls) {
                if (call.getState() != Call.STATE_DISCONNECTED) {
                    return false;
                }
            }
            return true;
        }

        public List<BluetoothCall> getBluetoothCalls() {
            return getBluetoothCallsByIds(BluetoothCall.getIds(getCalls()));
        }

        public BluetoothCall getOutgoingCall() {
            LinkedHashSet<Integer> states = new LinkedHashSet<Integer>();
            states.add(Call.STATE_CONNECTING);
            states.add(Call.STATE_DIALING);
            states.add(Call.STATE_PULLING_CALL);
            return getCallByStates(states);
        }

        public BluetoothCall getRingingOrSimulatedRingingCall() {
            LinkedHashSet<Integer> states = new LinkedHashSet<Integer>();
            states.add(Call.STATE_RINGING);
            states.add(Call.STATE_SIMULATED_RINGING);
            return getCallByStates(states);
        }

        public BluetoothCall getActiveCall() {
            return getCallByState(Call.STATE_ACTIVE);
        }

        public BluetoothCall getHeldCall() {
            return getCallByState(Call.STATE_HOLDING);
        }

        /**
         * Returns the best phone account to use for the given state of all calls.
         * First, tries to return the phone account for the foreground call, second the default
         * phone account for PhoneAccount.SCHEME_TEL.
         */
        public PhoneAccount getBestPhoneAccount() {
            BluetoothCall call = getForegroundCall();

            PhoneAccount account = null;
            if (!mCallInfo.isNullCall(call)) {
                PhoneAccountHandle handle = call.getAccountHandle();
                if (handle != null) {
                    // First try to get the network name of the foreground call.
                    account = mTelecomManager.getPhoneAccount(handle);
                }
            }

            if (account == null) {
                // Second, Try to get the label for the default Phone Account.
                if (mTelecomManager == null) {
                    Log.w(TAG, "mTelecomManager is null");
                    return null;
                }
                List<PhoneAccountHandle> handles =
                        mTelecomManager.getPhoneAccountsSupportingScheme(PhoneAccount.SCHEME_TEL);
                while (handles.iterator().hasNext()) {
                    account = mTelecomManager.getPhoneAccount(handles.iterator().next());
                    if (account != null) {
                        return account;
                    }
                }
            }
            return null;
        }

        public boolean isNullCall(BluetoothCall call) {
            return call == null || call.isCallNull();
        }

        public BluetoothCall getCallByCallId(UUID callId) {
            List<BluetoothCall> calls = getBluetoothCalls();
            for (BluetoothCall call : calls) {
                Log.i(TAG, "getCallByCallId lookingFor=" + callId + " has=" + call.getTbsCallId());
                if (callId.equals(call.getTbsCallId())) {
                    return call;
                }
            }
            return null;
        }
    };

    @VisibleForTesting
    public void setBluetoothLeCallControl(BluetoothLeCallControlProxy bluetoothTbs) {
        mBluetoothLeCallControl = bluetoothTbs;

        if ((mBluetoothLeCallControl) != null && (mTelecomManager != null)) {
            mBluetoothLeCallControl.registerBearer(TAG,
                    new ArrayList<String>(Arrays.asList("tel")),
                    BluetoothLeCallControl.CAPABILITY_HOLD_CALL, getNetworkOperator(),
                    getBearerTechnology(), mExecutor, mBluetoothLeCallControlCallback);
        }
    }

    private Integer getTbsCallState(BluetoothCall call) {
        switch (call.getState()) {
        case Call.STATE_ACTIVE:
            return BluetoothLeCall.STATE_ACTIVE;

        case Call.STATE_CONNECTING:
        case Call.STATE_SELECT_PHONE_ACCOUNT:
            return BluetoothLeCall.STATE_DIALING;

        case Call.STATE_DIALING:
        case Call.STATE_PULLING_CALL:
            return BluetoothLeCall.STATE_ALERTING;

        case Call.STATE_HOLDING:
            return BluetoothLeCall.STATE_LOCALLY_HELD;

        case Call.STATE_RINGING:
        case Call.STATE_SIMULATED_RINGING:
            if (call.isSilentRingingRequested()) {
                return null;
            } else {
                return BluetoothLeCall.STATE_INCOMING;
            }
        }
        return null;
    }

    @VisibleForTesting
    int getTbsTerminationReason(BluetoothCall call) {
        DisconnectCause cause = call.getDisconnectCause();
        if (cause == null) {
            Log.w(TAG, " termination cause is null");
            return BluetoothLeCallControl.TERMINATION_REASON_FAIL;
        }

        switch (cause.getCode()) {
        case DisconnectCause.BUSY:
            return BluetoothLeCallControl.TERMINATION_REASON_LINE_BUSY;
        case DisconnectCause.REMOTE:
        case DisconnectCause.REJECTED:
            return BluetoothLeCallControl.TERMINATION_REASON_REMOTE_HANGUP;
        case DisconnectCause.LOCAL:
            if (mIsTerminatedByClient) {
                mIsTerminatedByClient = false;
                return BluetoothLeCallControl.TERMINATION_REASON_CLIENT_HANGUP;
            }
            return BluetoothLeCallControl.TERMINATION_REASON_SERVER_HANGUP;
        case DisconnectCause.ERROR:
            return BluetoothLeCallControl.TERMINATION_REASON_NETWORK_CONGESTION;
        case DisconnectCause.CONNECTION_MANAGER_NOT_SUPPORTED:
            return BluetoothLeCallControl.TERMINATION_REASON_INVALID_URI;
        default:
            return BluetoothLeCallControl.TERMINATION_REASON_FAIL;
        }
    }

    private BluetoothLeCall createTbsCall(BluetoothCall call) {
        Integer state = getTbsCallState(call);
        boolean isPartOfConference = false;
        boolean isConferenceWithNoChildren = isConferenceWithNoChildren(call);

        if (state == null) {
            return null;
        }

        BluetoothCall conferenceCall = getBluetoothCallById(call.getParentId());
        if (!mCallInfo.isNullCall(conferenceCall)) {
            isPartOfConference = true;

            // Run some alternative states for Conference-level merge/swap support.
            // Basically, if BluetoothCall supports swapping or merging at the
            // conference-level,
            // then we need to expose the calls as having distinct states
            // (ACTIVE vs CAPABILITY_HOLD) or
            // the functionality won't show up on the bluetooth device.

            // Before doing any special logic, ensure that we are dealing with an
            // ACTIVE BluetoothCall and that the conference itself has a notion of
            // the current "active" child call.
            BluetoothCall activeChild =
                getBluetoothCallById(conferenceCall.getGenericConferenceActiveChildCallId());
            if (state == BluetoothLeCall.STATE_ACTIVE && !mCallInfo.isNullCall(activeChild)) {
                // Reevaluate state if we can MERGE or if we can SWAP without previously having
                // MERGED.
                boolean shouldReevaluateState =
                        conferenceCall.can(Connection.CAPABILITY_MERGE_CONFERENCE)
                                    || (conferenceCall.can(Connection.CAPABILITY_SWAP_CONFERENCE)
                                    && !conferenceCall.wasConferencePreviouslyMerged());

                if (shouldReevaluateState) {
                    isPartOfConference = false;
                    if (call == activeChild) {
                        state = BluetoothLeCall.STATE_ACTIVE;
                    } else {
                        // At this point we know there is an "active" child and we know that it is
                        // not this call, so set it to HELD instead.
                        state = BluetoothLeCall.STATE_LOCALLY_HELD;
                    }
                }
            }
            if (conferenceCall.getState() == Call.STATE_HOLDING
                    && conferenceCall.can(Connection.CAPABILITY_MANAGE_CONFERENCE)) {
                // If the parent IMS CEP conference BluetoothCall is on hold, we should mark
                // this BluetoothCall as being on hold regardless of what the other
                // children are doing.
                state = BluetoothLeCall.STATE_LOCALLY_HELD;
            }
        } else if (isConferenceWithNoChildren) {
            // Handle the special case of an IMS conference BluetoothCall without conference
            // event package support.
            // The BluetoothCall will be marked as a conference, but the conference will not
            // have
            // child calls where conference event packages are not used by the carrier.
            isPartOfConference = true;
        }

        final Uri addressUri;
        if (call.getGatewayInfo() != null) {
            addressUri = call.getGatewayInfo().getOriginalAddress();
        } else {
            addressUri = call.getHandle();
        }

        String uri = addressUri == null ? null : addressUri.toString();
        int callFlags = call.isIncoming() ? 0 : BluetoothLeCall.FLAG_OUTGOING_CALL;

        String friendlyName = call.getCallerDisplayName();
        if (TextUtils.isEmpty(friendlyName)) {
            friendlyName = call.getContactDisplayName();
        }

        return new BluetoothLeCall(call.getTbsCallId(), uri, friendlyName, state, callFlags);
    }

    private void sendTbsCurrentCallsList() {
        List<BluetoothLeCall> tbsCalls = new ArrayList<>();

        for (BluetoothCall call : mBluetoothCallHashMap.values()) {
            BluetoothLeCall tbsCall = createTbsCall(call);
            if (tbsCall != null) {
                tbsCalls.add(tbsCall);
            }
        }

        mBluetoothLeCallControl.currentCallsList(tbsCalls);
    }

    @VisibleForTesting
    final BluetoothLeCallControl.Callback mBluetoothLeCallControlCallback =
            new BluetoothLeCallControl.Callback() {

        @Override
        public void onAcceptCall(int requestId, UUID callId) {
            synchronized (LOCK) {
                enforceModifyPermission();
                Log.i(TAG, "TBS - accept call=" + callId);
                int result = BluetoothLeCallControl.RESULT_SUCCESS;
                BluetoothCall call = mCallInfo.getCallByCallId(callId);
                if (mCallInfo.isNullCall(call)) {
                    result = BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID;
                } else {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY);
                }
                mBluetoothLeCallControl.requestResult(requestId, result);
            }
        }

        @Override
        public void onTerminateCall(int requestId, UUID callId) {
            synchronized (LOCK) {
                enforceModifyPermission();
                Log.i(TAG, "TBS - terminate call=" + callId);
                int result = BluetoothLeCallControl.RESULT_SUCCESS;
                BluetoothCall call = mCallInfo.getCallByCallId(callId);
                if (mCallInfo.isNullCall(call)) {
                    result = BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID;
                } else {
                    mIsTerminatedByClient = true;
                    call.disconnect();
                }
                mBluetoothLeCallControl.requestResult(requestId, result);
            }
        }

        @Override
        public void onHoldCall(int requestId, UUID callId) {
            synchronized (LOCK) {
                enforceModifyPermission();
                Log.i(TAG, "TBS - hold call=" + callId);
                int result = BluetoothLeCallControl.RESULT_SUCCESS;
                BluetoothCall call = mCallInfo.getCallByCallId(callId);
                if (mCallInfo.isNullCall(call)) {
                    result = BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID;
                } else {
                    call.hold();
                }
                mBluetoothLeCallControl.requestResult(requestId, result);
            }
        }

        @Override
        public void onUnholdCall(int requestId, UUID callId) {
            synchronized (LOCK) {
                enforceModifyPermission();
                Log.i(TAG, "TBS - unhold call=" + callId);
                int result = BluetoothLeCallControl.RESULT_SUCCESS;
                BluetoothCall call = mCallInfo.getCallByCallId(callId);
                if (mCallInfo.isNullCall(call)) {
                    result = BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID;
                } else {
                    call.unhold();
                }
                mBluetoothLeCallControl.requestResult(requestId, result);
            }
        }

        @Override
        public void onPlaceCall(int requestId, UUID callId, String uri) {
            mBluetoothLeCallControl.requestResult(requestId, BluetoothLeCallControl.RESULT_ERROR_APPLICATION);
        }

        @Override
        public void onJoinCalls(int requestId, @NonNull List<UUID> callIds) {
            synchronized (LOCK) {
                Log.i(TAG, "TBS - onJoinCalls");
                int result = BluetoothLeCallControl.RESULT_SUCCESS;
                List<UUID> alreadyJoinedCalls = new ArrayList<>();
                BluetoothCall baseCallInstance = null;

                if (callIds.size() < 2) {
                    Log.e(TAG, "TBS - onJoinCalls, join call number is invalid: " + callIds.size());
                    result = BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID;
                    mBluetoothLeCallControl.requestResult(requestId, result);
                    return;
                }

                for (UUID callToJoinUuid : callIds) {
                    BluetoothCall callToJoinInstance = mCallInfo.getCallByCallId(callToJoinUuid);

                    /* Skip invalid and already add device */
                    if ((callToJoinInstance == null)
                            || (alreadyJoinedCalls.contains(callToJoinUuid))) {
                        continue;
                    }

                    /* Lets make first valid call the base call */
                    if (baseCallInstance == null) {
                        baseCallInstance = callToJoinInstance;
                        alreadyJoinedCalls.add(callToJoinUuid);
                        continue;
                    }

                    baseCallInstance.conference(callToJoinInstance);
                    alreadyJoinedCalls.add(callToJoinUuid);
                }

                if ((baseCallInstance == null) || (alreadyJoinedCalls.size() < 2)) {
                    result = BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID;
                }

                mBluetoothLeCallControl.requestResult(requestId, result);
            }
        }
    };
}
