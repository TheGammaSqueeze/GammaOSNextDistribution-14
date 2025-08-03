/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.tbs;

import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeCall;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothLeCallControl;
import android.bluetooth.IBluetoothLeCallControlCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TbsService extends ProfileService {

    private static final String TAG = "TbsService";
    private static final boolean DBG = true;

    private static TbsService sTbsService;
    private Map<BluetoothDevice, Integer> mDeviceAuthorizations = new HashMap<>();

    private final TbsGeneric mTbsGeneric = new TbsGeneric();

    public TbsService(Context ctx) {
        super(ctx);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileCcpServerEnabled().orElse(false);
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new TbsServerBinder(this);
    }

    @Override
    public void start() {

        if (DBG) {
            Log.d(TAG, "start()");
        }
        if (sTbsService != null) {
            throw new IllegalStateException("start() called twice");
        }

        // Mark service as started
        setTbsService(this);

        mTbsGeneric.init(new TbsGatt(this));
    }

    @Override
    public void stop() {
        if (DBG) {
            Log.d(TAG, "stop()");
        }
        if (sTbsService == null) {
            Log.w(TAG, "stop() called before start()");
            return;
        }

        // Mark service as stopped
        setTbsService(null);

        if (mTbsGeneric != null) {
            mTbsGeneric.cleanup();
        }
    }

    @Override
    public void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }
        mDeviceAuthorizations.clear();
    }

    /**
     * Get the TbsService instance
     *
     * @return TbsService instance
     */
    public static synchronized TbsService getTbsService() {
        if (sTbsService == null) {
            Log.w(TAG, "getTbsService: service is NULL");
            return null;
        }

        if (!sTbsService.isAvailable()) {
            Log.w(TAG, "getTbsService: service is not available");
            return null;
        }

        return sTbsService;
    }

    private static synchronized void setTbsService(TbsService instance) {
        if (DBG) {
            Log.d(TAG, "setTbsService: set to=" + instance);
        }

        sTbsService = instance;
    }

    public void onDeviceUnauthorized(BluetoothDevice device) {
        if (Utils.isPtsTestMode()) {
            Log.d(TAG, "PTS test: setDeviceAuthorized");
            setDeviceAuthorized(device, true);
            return;
        }
        Log.w(TAG, "onDeviceUnauthorized - authorization notification not implemented yet ");
        setDeviceAuthorized(device, false);
    }

    /**
     * Remove authorization information for the device.
     *
     * @param device device to remove from the service information
     * @hide
     */
    public void removeDeviceAuthorizationInfo(BluetoothDevice device) {
        Log.i(TAG, "removeDeviceAuthorizationInfo(): device: " + device);
        mDeviceAuthorizations.remove(device);
    }

    /**
     * Sets device authorization for TBS.
     *
     * @param device device that would be authorized
     * @param isAuthorized boolean value of authorization permission
     * @hide
     */
    public void setDeviceAuthorized(BluetoothDevice device, boolean isAuthorized) {
        Log.i(TAG, "setDeviceAuthorized(): device: " + device + ", isAuthorized: " + isAuthorized);
        int authorization = isAuthorized ? BluetoothDevice.ACCESS_ALLOWED
                : BluetoothDevice.ACCESS_REJECTED;
        mDeviceAuthorizations.put(device, authorization);

        if (mTbsGeneric != null) {
            mTbsGeneric.onDeviceAuthorizationSet(device);
        }
    }

    /**
     * Returns authorization value for given device.
     *
     * @param device device that would be authorized
     * @return authorization value for device
     *
     * Possible authorization values:
     * {@link BluetoothDevice.ACCESS_UNKNOWN},
     * {@link BluetoothDevice.ACCESS_ALLOWED}
     * @hide
     */
    public int getDeviceAuthorization(BluetoothDevice device) {
        /* Telephony Bearer Service is allowed for
         * 1. in PTS mode
         * 2. authorized devices
         * 3. Any LeAudio devices which are allowed to connect
         */
        int authorization = mDeviceAuthorizations.getOrDefault(device, Utils.isPtsTestMode()
                ? BluetoothDevice.ACCESS_ALLOWED : BluetoothDevice.ACCESS_UNKNOWN);
        if (authorization != BluetoothDevice.ACCESS_UNKNOWN) {
            return authorization;
        }

        LeAudioService leAudioService = LeAudioService.getLeAudioService();
        if (leAudioService == null) {
            Log.e(TAG, "TBS access not permited. LeAudioService not available");
            return BluetoothDevice.ACCESS_UNKNOWN;
        }

        if (leAudioService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            if (DBG) {
                Log.d(TAG, "TBS authorization allowed based on supported LeAudio service");
            }
            setDeviceAuthorized(device, true);
            return BluetoothDevice.ACCESS_ALLOWED;
        }

        Log.e(TAG, "TBS access not permited");
        return BluetoothDevice.ACCESS_UNKNOWN;
    }

    /**
     * Set inband ringtone for the device.
     * When set, notification will be sent to given device.
     *
     * @param device    device for which inband ringtone has been set
     */
    public void setInbandRingtoneSupport(BluetoothDevice device) {
        if (mTbsGeneric == null) {
            Log.i(TAG, "setInbandRingtoneSupport, mTbsGeneric not available");
            return;
        }
        mTbsGeneric.setInbandRingtoneSupport(device);
    }

    /**
     * Clear inband ringtone for the device.
     * When set, notification will be sent to given device.
     *
     * @param device    device for which inband ringtone has been clear
     */
    public void clearInbandRingtoneSupport(BluetoothDevice device) {
        if (mTbsGeneric == null) {
            Log.i(TAG, "clearInbandRingtoneSupport, mTbsGeneric not available");
            return;
        }
        mTbsGeneric.clearInbandRingtoneSupport(device);
    }


    /** Binder object: must be a static class or memory leak may occur */
    @VisibleForTesting
    static class TbsServerBinder extends IBluetoothLeCallControl.Stub implements IProfileServiceBinder {
        private TbsService mService;

        private TbsService getService(AttributionSource source) {
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                Log.w(TAG, "TbsService call not allowed for non-active user");
                return null;
            }

            if (mService != null) {
                if (DBG) {
                    Log.d(TAG, "Service available");
                }

                enforceBluetoothPrivilegedPermission(mService);
                return mService;
            }

            return null;
        }

        TbsServerBinder(TbsService service) {
            mService = service;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public void registerBearer(String token, IBluetoothLeCallControlCallback callback, String uci,
                List<String> uriSchemes, int capabilities, String providerName, int technology,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.registerBearer(token, callback, uci, uriSchemes, capabilities, providerName,
                        technology);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void unregisterBearer(String token,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.unregisterBearer(token);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void requestResult(int ccid, int requestId, int result,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.requestResult(ccid, requestId, result);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void callAdded(int ccid, BluetoothLeCall call,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.callAdded(ccid, call);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void callRemoved(int ccid, ParcelUuid callId, int reason,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.callRemoved(ccid, callId.getUuid(), reason);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void callStateChanged(int ccid, ParcelUuid callId, int state,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.callStateChanged(ccid, callId.getUuid(), state);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void currentCallsList(int ccid, List<BluetoothLeCall> calls,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.currentCallsList(ccid, calls);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void networkStateChanged(int ccid, String providerName, int technology,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.networkStateChanged(ccid, providerName, technology);
            } else {
                Log.w(TAG, "Service not active");
            }
        }
    }

    @VisibleForTesting
    void registerBearer(String token, IBluetoothLeCallControlCallback callback, String uci,
            List<String> uriSchemes, int capabilities, String providerName, int technology) {
        if (DBG) {
            Log.d(TAG, "registerBearer: token=" + token);
        }

        boolean success = mTbsGeneric.addBearer(token, callback, uci, uriSchemes, capabilities,
                providerName, technology);
        if (success) {
            try {
                callback.asBinder().linkToDeath(() -> {
                    Log.e(TAG, token + " application died, removing...");
                    unregisterBearer(token);
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        if (DBG) {
            Log.d(TAG, "registerBearer: token=" + token + " success=" + success);
        }
    }

    @VisibleForTesting
    void unregisterBearer(String token) {
        if (DBG) {
            Log.d(TAG, "unregisterBearer: token=" + token);
        }

        mTbsGeneric.removeBearer(token);
    }

    @VisibleForTesting
    public void requestResult(int ccid, int requestId, int result) {
        if (DBG) {
            Log.d(TAG, "requestResult: ccid=" + ccid + " requestId=" + requestId + " result="
                    + result);
        }

        mTbsGeneric.requestResult(ccid, requestId, result);
    }

    @VisibleForTesting
    void callAdded(int ccid, BluetoothLeCall call) {
        if (DBG) {
            Log.d(TAG, "callAdded: ccid=" + ccid + " call=" + call);
        }

        mTbsGeneric.callAdded(ccid, call);
    }

    @VisibleForTesting
    void callRemoved(int ccid, UUID callId, int reason) {
        if (DBG) {
            Log.d(TAG, "callRemoved: ccid=" + ccid + " callId=" + callId + " reason=" + reason);
        }

        mTbsGeneric.callRemoved(ccid, callId, reason);
    }

    @VisibleForTesting
    void callStateChanged(int ccid, UUID callId, int state) {
        if (DBG) {
            Log.d(TAG, "callStateChanged: ccid=" + ccid + " callId=" + callId + " state=" + state);
        }

        mTbsGeneric.callStateChanged(ccid, callId, state);
    }

    @VisibleForTesting
    void currentCallsList(int ccid, List<BluetoothLeCall> calls) {
        if (DBG) {
            Log.d(TAG, "currentCallsList: ccid=" + ccid + " calls=" + calls);
        }

        mTbsGeneric.currentCallsList(ccid, calls);
    }

    @VisibleForTesting
    void networkStateChanged(int ccid, String providerName, int technology) {
        if (DBG) {
            Log.d(TAG, "networkStateChanged: ccid=" + ccid + " providerName=" + providerName
                    + " technology=" + technology);
        }

        mTbsGeneric.networkStateChanged(ccid, providerName, technology);
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        sb.append("TbsService instance:\n");

        mTbsGeneric.dump(sb);

        for (Map.Entry<BluetoothDevice, Integer> entry : mDeviceAuthorizations.entrySet()) {
            String accessString;
            if (entry.getValue() == BluetoothDevice.ACCESS_REJECTED) {
                accessString = "ACCESS_REJECTED";
            } else if (entry.getValue() == BluetoothDevice.ACCESS_ALLOWED) {
                accessString = "ACCESS_ALLOWED";
            } else {
                accessString = "ACCESS_UNKNOWN";
            }
            sb.append("\n\tDevice: " + entry.getKey() + ", access: " + accessString);
        }
    }
}
