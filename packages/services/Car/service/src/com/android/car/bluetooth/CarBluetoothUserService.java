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
package com.android.car.bluetooth;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.car.ICarBluetoothUserService;
import android.car.builtin.bluetooth.BluetoothHeadsetClientHelper;
import android.car.builtin.util.Slogf;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.android.car.CarLog;
import com.android.car.CarPerUserServiceImpl;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages Bluetooth for user
 */
public class CarBluetoothUserService extends ICarBluetoothUserService.Stub {

    private static final String TAG = CarLog.tagFor(CarBluetoothUserService.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final int PROXY_OPERATION_TIMEOUT_MS = 8_000;

    // Profiles we support
    private static final List<Integer> sProfilesToConnect = Arrays.asList(
            BluetoothProfile.HEADSET_CLIENT,
            BluetoothProfile.A2DP_SINK
    );

    private final CarPerUserServiceImpl mService;
    private final BluetoothAdapter mBluetoothAdapter;
    private final TelecomManager mTelecomManager;

    // Profile Proxies Objects to pair with above list. Access to these proxy objects will all be
    // guarded by the below mBluetoothProxyLock
    private BluetoothA2dpSink mBluetoothA2dpSink;
    private BluetoothHeadsetClient mBluetoothHeadsetClient;

    // Concurrency variables for waitForProxies. Used so we can best effort block with a timeout
    // while waiting for services to be bound to the proxy objects.
    private final ReentrantLock mBluetoothProxyLock;
    private final Condition mConditionAllProxiesConnected;
    private final FastPairProvider mFastPairProvider;
    private SparseBooleanArray mBluetoothProfileStatus;
    private int mConnectedProfiles;

    /**
     * Create a CarBluetoothUserService instance.
     *
     * @param service - A reference to a CarPerUserService, so we can use its context to receive
     *                 updates as a particular user.
     */
    public CarBluetoothUserService(CarPerUserServiceImpl service) {
        mService = service;
        mConnectedProfiles = 0;
        mBluetoothProfileStatus = new SparseBooleanArray();
        for (int profile : sProfilesToConnect) {
            mBluetoothProfileStatus.put(profile, false);
        }
        mBluetoothProxyLock = new ReentrantLock();
        mConditionAllProxiesConnected = mBluetoothProxyLock.newCondition();
        mBluetoothAdapter = mService.getApplicationContext()
                .getSystemService(BluetoothManager.class).getAdapter();
        Objects.requireNonNull(mBluetoothAdapter, "Bluetooth adapter cannot be null");
        mTelecomManager = mService.getApplicationContext().getSystemService(TelecomManager.class);
        mFastPairProvider = new FastPairProvider(service);
    }

    /**
     * Setup connections to the profile proxy objects that talk to the Bluetooth profile services.
     *
     * Proxy references are held by the Bluetooth Framework on our behalf. We will be notified each
     * time the underlying service connects for each proxy we create. Notifications stop when we
     * close the proxy. As such, each time this is called we clean up any existing proxies before
     * creating new ones.
     */
    @Override
    public void setupBluetoothConnectionProxies() {
        if (DBG) {
            Slogf.d(TAG, "Initiate connections to profile proxies");
        }

        // Clear existing proxy objects
        closeBluetoothConnectionProxies();

        // Create proxy for each supported profile. Objects arrive later in the profile listener.
        // Operations on the proxies expect them to be connected. Functions below should call
        // waitForProxies() to best effort wait for them to be up if Bluetooth is enabled.
        for (int profile : sProfilesToConnect) {
            if (DBG) {
                Slogf.d(TAG, "Creating proxy for %s", BluetoothUtils.getProfileName(profile));
            }
            mBluetoothAdapter.getProfileProxy(mService.getApplicationContext(),
                    mProfileListener, profile);
        }
        mFastPairProvider.start();
    }

    /**
     * Close connections to the profile proxy objects
     */
    @Override
    public void closeBluetoothConnectionProxies() {
        if (DBG) {
            Slogf.d(TAG, "Clean up profile proxy objects");
        }
        mBluetoothProxyLock.lock();
        try {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, mBluetoothA2dpSink);
            mBluetoothA2dpSink = null;
            mBluetoothProfileStatus.put(BluetoothProfile.A2DP_SINK, false);

            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT,
                    mBluetoothHeadsetClient);
            mBluetoothHeadsetClient = null;
            mBluetoothProfileStatus.put(BluetoothProfile.HEADSET_CLIENT, false);

            mConnectedProfiles = 0;
        } finally {
            mBluetoothProxyLock.unlock();
        }
        mFastPairProvider.stop();
    }

    /**
     * Listen for and collect Bluetooth profile proxy connections and disconnections.
     */
    private BluetoothProfile.ServiceListener mProfileListener =
            new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DBG) {
                Slogf.d(TAG, "onServiceConnected profile: %s",
                        BluetoothUtils.getProfileName(profile));
            }

            // Grab the profile proxy object and update the status book keeping in one step so the
            // book keeping and proxy objects never disagree
            mBluetoothProxyLock.lock();
            try {
                switch (profile) {
                    case BluetoothProfile.A2DP_SINK:
                        mBluetoothA2dpSink = (BluetoothA2dpSink) proxy;
                        break;
                    case BluetoothProfile.HEADSET_CLIENT:
                        mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
                        break;
                    default:
                        if (DBG) {
                            Slogf.d(TAG, "Unsupported profile connected: %s",
                                    BluetoothUtils.getProfileName(profile));
                        }
                        break;
                }

                if (!mBluetoothProfileStatus.get(profile, false)) {
                    mBluetoothProfileStatus.put(profile, true);
                    mConnectedProfiles++;
                    if (mConnectedProfiles == sProfilesToConnect.size()) {
                        if (DBG) {
                            Slogf.d(TAG, "All profiles have connected");
                        }
                        mConditionAllProxiesConnected.signal();
                    }
                } else {
                    Slogf.w(TAG, "Received duplicate service connection event for: %s",
                            BluetoothUtils.getProfileName(profile));
                }
            } finally {
                mBluetoothProxyLock.unlock();
            }
        }

        public void onServiceDisconnected(int profile) {
            if (DBG) {
                Slogf.d(TAG, "onServiceDisconnected profile: %s",
                        BluetoothUtils.getProfileName(profile));
            }
            mBluetoothProxyLock.lock();
            try {
                if (mBluetoothProfileStatus.get(profile, false)) {
                    mBluetoothProfileStatus.put(profile, false);
                    mConnectedProfiles--;
                } else {
                    Slogf.w(TAG, "Received duplicate service disconnection event for: %s",
                            BluetoothUtils.getProfileName(profile));
                }
            } finally {
                mBluetoothProxyLock.unlock();
            }
        }
    };

    /**
     * Check if a proxy is available for the given profile to talk to the Profile's bluetooth
     * service.
     *
     * @param profile - Bluetooth profile to check for
     * @return - true if proxy available, false if not.
     */
    @Override
    public boolean isBluetoothConnectionProxyAvailable(int profile) {
        if (!mBluetoothAdapter.isEnabled()) return false;
        boolean proxyConnected = false;
        mBluetoothProxyLock.lock();
        try {
            proxyConnected = mBluetoothProfileStatus.get(profile, false);
        } finally {
            mBluetoothProxyLock.unlock();
        }
        return proxyConnected;
    }

    /**
     * Wait for the proxy objects to be up for all profiles, with a timeout.
     *
     * @param timeout Amount of time in milliseconds to wait for giving up on the wait operation
     * @return True if the condition was satisfied within the timeout, False otherwise
     */
    private boolean waitForProxies(int timeout /* ms */) {
        if (DBG) {
            Slogf.d(TAG, "waitForProxies()");
        }
        // If bluetooth isn't on then the operation waiting on proxies was never meant to actually
        // work regardless if Bluetooth comes on within the timeout period or not. Return false.
        if (!mBluetoothAdapter.isEnabled()) return false;
        try {
            while (mConnectedProfiles != sProfilesToConnect.size()) {
                if (!mConditionAllProxiesConnected.await(
                        timeout, TimeUnit.MILLISECONDS)) {
                    Slogf.e(TAG, "Timeout while waiting for proxies, Connected: %d/%d",
                            mConnectedProfiles, sProfilesToConnect.size());
                    return false;
                }
            }
        } catch (InterruptedException e) {
            Slogf.w(TAG, "waitForProxies: interrupted", e);
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    /**
     * Get the connection policy of the given Bluetooth profile for the given remote device
     *
     * @param profile - Bluetooth profile
     * @param device - remote Bluetooth device
     */
    @Override
    public int getConnectionPolicy(int profile, BluetoothDevice device) {
        if (device == null) {
            Slogf.e(TAG, "Cannot get %s profile connection policy on null device",
                    BluetoothUtils.getProfileName(profile));
            return BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
        }
        int policy;
        mBluetoothProxyLock.lock();
        try {
            if (!isBluetoothConnectionProxyAvailable(profile)) {
                if (!waitForProxies(PROXY_OPERATION_TIMEOUT_MS)
                        && !isBluetoothConnectionProxyAvailable(profile)) {
                    Slogf.e(TAG, "Cannot get %s profile connection policy. Proxy Unavailable",
                            BluetoothUtils.getProfileName(profile));
                    return BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                }
            }
            switch (profile) {
                case BluetoothProfile.A2DP_SINK:
                    policy = mBluetoothA2dpSink.getConnectionPolicy(device);
                    break;
                default:
                    Slogf.w(TAG, "Unsupported Profile: %s", BluetoothUtils.getProfileName(profile));
                    policy = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                    break;
            }
        } finally {
            mBluetoothProxyLock.unlock();
        }
        if (DBG) {
            Slogf.d(TAG, "%s connection policy for %s (%s) = %d",
                    BluetoothUtils.getProfileName(profile),
                    device.getName(), device.getAddress(), policy);
        }
        return policy;
    }

    /**
     * Set the connection policy of the given Bluetooth profile for the given remote device
     *
     * @param profile - Bluetooth profile
     * @param device - remote Bluetooth device
     * @param policy - connection policy to set
     */
    @Override
    public void setConnectionPolicy(int profile, BluetoothDevice device, int policy) {
        if (device == null) {
            Slogf.e(TAG, "Cannot set %s profile connection policy on null device",
                    BluetoothUtils.getProfileName(profile));
            return;
        }
        if (DBG) {
            Slogf.d(TAG, "Setting %s connection policy for %s (%s) to %d",
                    BluetoothUtils.getProfileName(profile), device.getName(), device.getAddress(),
                    policy);
        }
        mBluetoothProxyLock.lock();
        try {
            if (!isBluetoothConnectionProxyAvailable(profile)) {
                if (!waitForProxies(PROXY_OPERATION_TIMEOUT_MS)
                        && !isBluetoothConnectionProxyAvailable(profile)) {
                    Slogf.e(TAG, "Cannot set %s profile connection policy. Proxy Unavailable",
                            BluetoothUtils.getProfileName(profile));
                    return;
                }
            }
            switch (profile) {
                case BluetoothProfile.A2DP_SINK:
                    mBluetoothA2dpSink.setConnectionPolicy(device, policy);
                    break;
                default:
                    Slogf.w(TAG, "Unsupported Profile: %s", BluetoothUtils.getProfileName(profile));
                    break;
            }
        } finally {
            mBluetoothProxyLock.unlock();
        }
    }

    /**
     * Triggers Bluetooth to start a BVRA session.
     */
    public boolean startBluetoothVoiceRecognition() {
        mBluetoothProxyLock.lock();
        try {
            if (mBluetoothHeadsetClient == null) {
                Slogf.e(TAG, "HFP BVRA, no headsetclient proxy found.");
                return false;
            }
            List<BluetoothDevice> devices = BluetoothHeadsetClientHelper.getConnectedBvraDevices(
                    mBluetoothHeadsetClient);
            if (devices != null && !devices.isEmpty()) {
                // Until a UI has been agreed upon that allows a user to select from multiple
                // devices, a BVRA device will be chosen as follows:
                //   1. Use the device corresponding to the default phone account.
                //   2. If that device doesn't support BVRA or if there is no default account, use
                //      the first device that supports BVRA.
                BluetoothDevice bvraDevice = devices.get(0);

                // {@link TelecomManager#getUserSelectedOutgoingPhoneAccount} returns the
                // user-chosen default for making outgoing phone calls. This default is set when
                // {@link HfpClientConnectionService} creates a phone account for a device, via
                // {@link HfpClientDeviceBlock}.
                PhoneAccountHandle defaultPhone =
                        mTelecomManager.getUserSelectedOutgoingPhoneAccount();
                if (defaultPhone != null) {
                    // When {@link HfpClientConnectionService#createAccount} creates a {@link
                    // PhoneAccountHandle}, it sets the ID to the device's {@code BD_ADDR}.
                    String defaultPhoneBdAddr = defaultPhone.getId();
                    if (defaultPhoneBdAddr != null) {
                        for (int i = 0; i < devices.size(); i++) {
                            BluetoothDevice d = devices.get(i);
                            if (defaultPhoneBdAddr.equals(d.getAddress())) {
                                bvraDevice = d;
                                break;
                            }
                        }
                    }
                }

                if (BluetoothHeadsetClientHelper.startVoiceRecognition(
                        mBluetoothHeadsetClient, bvraDevice)) {
                    if (DBG) {
                        Slogf.d(TAG, "HFP BVRA started for %s", bvraDevice.getAddress());
                    }
                    return true;
                } else {
                    Slogf.w(TAG, "Unable to start HFP BVRA for %s", bvraDevice.getAddress());
                }
            } else {
                Slogf.w(TAG, "No devices supporting BVRA found.");
            }
        } finally {
            mBluetoothProxyLock.unlock();
        }
        return false;
    }

    /** Dump for debugging */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter pw) {
        pw.printf("Supported profiles: %s\n", sProfilesToConnect);
        pw.printf("Number of connected profiles: %d\n", mConnectedProfiles);
        pw.printf("Profiles status: %s\n", mBluetoothProfileStatus);
        pw.printf("Proxy operation timeout: %d ms\n", PROXY_OPERATION_TIMEOUT_MS);
        pw.printf("BluetoothAdapter: %s\n", mBluetoothAdapter);
        pw.printf("BluetoothA2dpSink: %s\n", mBluetoothA2dpSink);
        pw.printf("BluetoothHeadsetClient: %s\n", mBluetoothHeadsetClient);
        mFastPairProvider.dump(pw);
    }
}
