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

package com.android.bluetooth.mcp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothMcpServiceManager;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides Media Control Profile, as a service in the Bluetooth application.
 * @hide
 */
public class McpService extends ProfileService {
    private static final boolean DBG = true;
    private static final boolean VDBG = false;
    private static final String TAG = "BluetoothMcpService";

    private static McpService sMcpService;
    private static MediaControlProfile sGmcsForTesting;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private MediaControlProfile mGmcs;
    private Map<BluetoothDevice, Integer> mDeviceAuthorizations = new HashMap<>();
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public McpService(Context ctx) {
        super(ctx);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileMcpServerEnabled().orElse(false);
    }

    private static synchronized void setMcpService(McpService instance) {
        if (VDBG) {
            Log.d(TAG, "setMcpService(): set to: " + instance);
        }
        sMcpService = instance;
    }

    public static synchronized McpService getMcpService() {
        if (sMcpService == null) {
            Log.w(TAG, "getMcpService(): service is NULL");
            return null;
        }

        if (!sMcpService.isAvailable()) {
            Log.w(TAG, "getMcpService(): service is not available");
            return null;
        }
        return sMcpService;
    }

    @VisibleForTesting
    public static MediaControlProfile getMediaControlProfile() {
        return sGmcsForTesting;
    }

    @VisibleForTesting
    public static void setMediaControlProfileForTesting(MediaControlProfile mediaControlProfile) {
        sGmcsForTesting = mediaControlProfile;
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothMcpServiceBinder(this);
    }

    @Override
    public void start() {
        if (DBG) {
            Log.d(TAG, "start()");
        }

        if (sMcpService != null) {
            throw new IllegalStateException("start() called twice");
        }

        // Mark service as started
        setMcpService(this);

        synchronized (mLock) {
            if (getGmcsLocked() == null) {
                // Initialize the Media Control Service Server
                if (mGmcs != null) {
                    mGmcs.cleanup();
                }
                mGmcs = new MediaControlProfile(this);
                // Requires this service to be already started thus we have to make it an async call
                mHandler.post(() -> {
                    synchronized (mLock) {
                        if (mGmcs != null) {
                            mGmcs.init();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void stop() {
        if (DBG) {
            Log.d(TAG, "stop()");
        }

        if (sMcpService == null) {
            Log.w(TAG, "stop() called before start()");
            return;
        }

        synchronized (mLock) {
            // A runnable for calling mGmcs.init() could be pending on mHandler
            mHandler.removeCallbacksAndMessages(null);
            if (mGmcs != null) {
                mGmcs.cleanup();
                mGmcs = null;
            }
            if (sGmcsForTesting != null) {
                sGmcsForTesting.cleanup();
                sGmcsForTesting = null;
            }
        }

        // Mark service as stopped
        setMcpService(null);
    }

    @Override
    public void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        synchronized (mLock) {
            MediaControlProfile gmcs = getGmcsLocked();
            if (gmcs != null) {
                gmcs.dump(sb);
            }
        }

        for (Map.Entry<BluetoothDevice, Integer> entry : mDeviceAuthorizations.entrySet()) {
            String accessString;
            if (entry.getValue() == BluetoothDevice.ACCESS_REJECTED) {
                accessString = "ACCESS_REJECTED";
            } else if (entry.getValue() == BluetoothDevice.ACCESS_ALLOWED) {
                accessString = "ACCESS_ALLOWED";
            } else {
                accessString = "ACCESS_UNKNOWN";
            }
            sb.append("\n\t\tDevice: " + entry.getKey() + ", access: " + accessString);
        }
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

    public void setDeviceAuthorized(BluetoothDevice device, boolean isAuthorized) {
        Log.i(TAG, "\tsetDeviceAuthorized(): device: " + device + ", isAuthorized: "
                + isAuthorized);
        int authorization = isAuthorized ? BluetoothDevice.ACCESS_ALLOWED
                : BluetoothDevice.ACCESS_REJECTED;
        mDeviceAuthorizations.put(device, authorization);

        synchronized (mLock) {
            MediaControlProfile gmcs = getGmcsLocked();
            if (gmcs != null) {
                gmcs.onDeviceAuthorizationSet(device);
            }
        }
    }

    public int getDeviceAuthorization(BluetoothDevice device) {
        /* Media control is allowed for
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
            Log.e(TAG, "MCS access not permited. LeAudioService not available");
            return BluetoothDevice.ACCESS_UNKNOWN;
        }

        if (leAudioService.getConnectionPolicy(device)
                > BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            if (DBG) {
                Log.d(TAG, "MCS authorization allowed based on supported LeAudio service");
            }
            setDeviceAuthorized(device, true);
            return BluetoothDevice.ACCESS_ALLOWED;
        }

        Log.e(TAG, "MCS access not permited");
        return BluetoothDevice.ACCESS_UNKNOWN;
    }

    List<ParcelUuid> getNotificationSubscriptions(int ccid, BluetoothDevice device) {
        synchronized (mLock) {
            MediaControlProfile gmcs = getGmcsLocked();
            if (gmcs != null) {
                return gmcs.getNotificationSubscriptions(ccid, device);
            }
        }
        return Collections.emptyList();
    }

    void setNotificationSubscription(
            int ccid, BluetoothDevice device, ParcelUuid charUuid, boolean doNotify) {
        synchronized (mLock) {
            MediaControlProfile gmcs = getGmcsLocked();
            if (gmcs != null) {
                gmcs.setNotificationSubscription(ccid, device, charUuid, doNotify);
            }
        }
    }

    @GuardedBy("mLock")
    private MediaControlProfile getGmcsLocked() {
        if (sGmcsForTesting != null) {
            return sGmcsForTesting;
        } else {
            return mGmcs;
        }
    }

    /**
     * Binder object: must be a static class or memory leak may occur
     */
    static class BluetoothMcpServiceBinder
            extends IBluetoothMcpServiceManager.Stub implements IProfileServiceBinder {
        private McpService mService;

        BluetoothMcpServiceBinder(McpService svc) {
            mService = svc;
        }

        private McpService getService(AttributionSource source) {
            if (mService != null && mService.isAvailable()) {
                return mService;
            }
            Log.e(TAG, "getService() - Service requested, but not available!");
            return null;
        }

        @Override
        public void setDeviceAuthorized(BluetoothDevice device, boolean isAuthorized,
                AttributionSource source) {
            McpService service = getService(source);
            if (service == null) {
                return;
            }
            Utils.enforceBluetoothPrivilegedPermission(service);
            service.setDeviceAuthorized(device, isAuthorized);
        }

        @Override
        public void cleanup() {
            if (mService != null) {
                mService.cleanup();
            }
            mService = null;
        }
    }
}
