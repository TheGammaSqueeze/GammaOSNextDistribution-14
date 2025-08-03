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

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BluetoothConnectionRetryManager: manages retry attempts for failed connections.
 * <p>
 * {@link FirstConnectionTracker} tracks the first auto-connect immediately following bonding,
 * distinguished from other connection attempts. It automatically retries failed "first connects"
 * every {@link sRetryFirstConnectTimeoutMs} milliseconds for a maximum of {@link
 * MAX_RETRY_ATTEMPTS} attempts. It stops tracking a device if all expected profiles successfully
 * connect for the first time, or if the device unbonds, or if the Bluetooth stack is torn down.
 */
public final class BluetoothConnectionRetryManager {
    private static final String TAG = CarLog.tagFor(BluetoothConnectionRetryManager.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final int MAX_RETRY_ATTEMPTS = 3;
    // NOTE: the value is not "final" - it is modified in the unit tests
    @VisibleForTesting
    static int sRetryFirstConnectTimeoutMs = 8000;

    private final Context mContext;
    @Nullable
    private Context mUserContext;
    private BluetoothAdapter mBluetoothAdapter;
    private final BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;
    private final Handler mHandler = new Handler(
            CarServiceUtils.getHandlerThread(CarBluetoothService.THREAD_NAME).getLooper());

    private static final int[] MANAGED_PROFILES = BluetoothUtils.getManagedProfilesIds();

    private final FirstConnectionTracker mFirstConnectionTracker;

    /**
     * A BroadcastReceiver for the device we are managing.
     */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = null;

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR);

                mFirstConnectionTracker.handleDeviceBondStateChange(device, bondState);
            } else if (BluetoothUtils.isAProfileAction(action)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int profile = BluetoothUtils.getProfileFromConnectionAction(action);
                int toState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED);
                int fromState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                        BluetoothProfile.STATE_DISCONNECTED);

                mFirstConnectionTracker.handleProfileConnectionStateChange(device, profile,
                        toState, fromState);
            }
        }
    }

    /**
     * A helper class to track the first auto-connect immediately following bonding, distinguished
     * from other connection attempts.
     * <p>
     * This is done by inferring whether a device failed a connection attempt, and retrying the
     * connection if the device hasn't connected before since bonding. This does not persist across
     * Bluetooth sessions, e.g., if a device bonds and Bluetooth is restarted before the device
     * successfully connects on all supported profiles, {@code FirstConnectionTracker} will
     * *no longer* track the device as one that yet to connect.
     */
    private final class FirstConnectionTracker {
        /**
         * A simple counter to track the number of retry attempts a profile has made on a device.
         * The counter instance also serves as a token to associate enqueued retry attempts to the
         * (device, profile).
         * <p>
         * It does not get decremented or reset to {@code 0}. Connection attempts not initated by
         * {@code FirstConnectionTracker} do not count, e.g., manual connections by the user, or
         * possibly other connection retry attempts that are not first-connection-after-pairing.
         */
        private final class RetryTokenAndCounter {
            private int mRetryAttempts = 0;
            int getCount() {
                return mRetryAttempts;
            }
            int increment() {
                return ++mRetryAttempts;
            }
        }

        /**
         * Tracks devices that have bonded but whose profiles have not successfully connected
         * at least once during the current Bluetooth session. Tracked profiles are the only
         * ones that may attempt retries if they fail to connect. Note: does not persist across
         * restarts of Bluetooth.
         * <p>
         * Key: A remote device's BD_ADDR. A BD_ADDR is present if and only if it is being
         * tracked. A device starts getting tracked if it bonds during the current Bluetooth
         * session. A device is untracked when (1) all supported profiles on the device
         * successfully connect for the first time; or (2) the device unbonds beforehand.
         * <p>
         * Value: A {@link SparseArray} of {@link RetryTokenAndCounter}, a token for each supported
         * profile. A token is present in the array if and only if its profile is being tracked. A
         * profile starts getting tracked when the device starts getting tracked (i.e., the device
         * bonds). A profile is untracked when (1) the profile successfully connects for the first
         * time; or (2) the device is untracked beforehand (e.g., the device unbonds).
         */
        private Map<String, SparseArray<RetryTokenAndCounter>> mBondedYetToConnect =
                new HashMap<>();

        // Only purpose is to help {@link isRetryPosted}.
        private static final int RETRY_MSG_WHAT = 0;

        void handleDeviceBondStateChange(BluetoothDevice device, int state) {
            if (DBG) {
                Slogf.d(TAG, "Bond state has changed [device: %s, state: %s]", device,
                        BluetoothUtils.getBondStateName(state));
            }
            if ((state == BluetoothDevice.BOND_BONDED) && !isDeviceBeingTracked(device)) {
                // an untracked device has bonded
                trackDevice(device);
            } else if ((state == BluetoothDevice.BOND_NONE) && isDeviceBeingTracked(device)) {
                // a tracked device has unbonded
                untrackDevice(device);
            }
        }

        void handleProfileConnectionStateChange(BluetoothDevice device, int profile, int toState,
                int fromState) {
            // We are interested in connection state for *tracked* (device, profile)'s only
            if (!isProfileBeingTracked(device, profile)) {
                return;
            }
            if (toState == BluetoothProfile.STATE_CONNECTED) {
                // tracked (device, profile) has successfully connected
                if (DBG) {
                    Slogf.d(TAG, "%s has connected for the first time on %s.", device,
                            BluetoothUtils.getProfileName(profile));
                }
                untrackProfile(device, profile);
            } else if ((fromState == BluetoothProfile.STATE_CONNECTING)
                    && ((toState == BluetoothProfile.STATE_DISCONNECTING) || (toState
                    == BluetoothProfile.STATE_DISCONNECTED))) {
                // Proxy for detecting a failed connection, until we get callbacks with
                // status codes. Caveats:
                // * False positives, e.g., a disconnect attempt during connecting.
                // * PAN doesn't seem to follow these state transitions.
                if (DBG) {
                    Slogf.d(TAG, "%s has failed to connect on %s.", device,
                            BluetoothUtils.getProfileName(profile));
                }
                RetryTokenAndCounter counter = mBondedYetToConnect.get(device.getAddress())
                        .get(profile);
                if ((counter.getCount() < MAX_RETRY_ATTEMPTS)
                        && !isRetryPosted(device, profile)) {
                    // Retry connection attempt.
                    // Do not post a retry if there is already an outstanding retry posted.
                    // This ensures retries are posted at least {@link
                    // sRetryFirstConnectTimeoutMs} apart.
                    int countForLogs = counter.increment();
                    mHandler.postDelayed(() -> {
                        if (DBG) {
                            Slogf.d(TAG, "[%s, %s] retry attempt (%s/%s)", device,
                                    BluetoothUtils.getProfileName(profile),
                                    countForLogs, MAX_RETRY_ATTEMPTS);
                        }
                        connect(device);
                    }, /* token */ counter, sRetryFirstConnectTimeoutMs);
                    // Only purpose is to help {@link isRetryPosted}.
                    mHandler.sendMessage(mHandler.obtainMessage(RETRY_MSG_WHAT,
                            /* token */ counter));
                }
            }
        }

        /**
         * Adds {@code device} to {@link mBondedYetToConnect}.
         * <p>
         * Assumes device exists (i.e., {@code device != null}) and is not already tracked (i.e.,
         * {@code mBondedYetToConnect.containsKey(device.getAddress()) == false}).
         */
        private void trackDevice(BluetoothDevice device) {
            if (DBG) {
                Slogf.d(TAG, "Tracking %s, supported profiles:", device);
                // additional debug messages continued in for-loop below
            }
            List<ParcelUuid> ourUuids = mBluetoothAdapter.getUuidsList();
            SparseArray<RetryTokenAndCounter> profileCounters =
                    new SparseArray<RetryTokenAndCounter>(MANAGED_PROFILES.length);
            for (int i = 0; i < MANAGED_PROFILES.length; i++) {
                int profileId = MANAGED_PROFILES[i];
                if (BluetoothUtils.isProfileSupported(ourUuids, device, profileId)) {
                    if (DBG) {
                        // debug messaging continued from above
                        Slogf.d(TAG, "    %s", BluetoothUtils.getProfileName(profileId));
                    }
                    profileCounters.put(profileId, new RetryTokenAndCounter());
                }
            }
            mBondedYetToConnect.put(device.getAddress(), profileCounters);
        }

        /**
         * Removes {@code device} from {@link mBondedYetToConnect}. Also removes any pending retry
         * attempts.
         * <p>
         * Assumes device exists (i.e., {@code device != null}) and is being tracked
         * (i.e., {@code mBondedYetToConnect.containsKey(device.getAddress()) == true}).
         */
        private void untrackDevice(BluetoothDevice device) {
            SparseArray<RetryTokenAndCounter> profileTokens =
                    mBondedYetToConnect.get(device.getAddress());
            for (int i = 0; i < profileTokens.size(); i++) {
                mHandler.removeCallbacksAndMessages(profileTokens.valueAt(i));
            }
            mBondedYetToConnect.remove(device.getAddress());
        }

        private void untrackProfile(BluetoothDevice device, int profile) {
            RetryTokenAndCounter token = mBondedYetToConnect.get(device.getAddress()).get(profile);
            if (token == null) {
                Slogf.w(TAG, "Untracking profile, no token found for %s on device: %s",
                        BluetoothUtils.getProfileName(profile), device);
                return;
            }
            mHandler.removeCallbacksAndMessages(token);
            mBondedYetToConnect.get(device.getAddress()).delete(profile);
            if (mBondedYetToConnect.get(device.getAddress()).size() == 0) {
                untrackDevice(device);
            }
        }

        /**
         * Returns {@code true} if {@code device} is being tracked, and {@code false} otherwise.
         * <p>
         * Assumes {@code device != null}.
         */
        private boolean isDeviceBeingTracked(BluetoothDevice device) {
            return mBondedYetToConnect.containsKey(device.getAddress());
        }

        private boolean isProfileBeingTracked(BluetoothDevice device, int profile) {
            SparseArray<RetryTokenAndCounter> profileTokens =
                    mBondedYetToConnect.get(device.getAddress());
            if (profileTokens == null) {
                return false;
            }
            return profileTokens.contains(profile);
        }

        /**
         * Determine if retry attempts have been posted.
         */
        boolean isRetryPosted(BluetoothDevice device, int profile) {
            if (!isProfileBeingTracked(device, profile)) {
                // An untracked (device, profile) should have had any pending callbacks and
                // messages removed
                if (DBG) {
                    Slogf.d(TAG, "%s is no longer being tracked on device %s by the time"
                            + " isRetryPosted was called.",
                            BluetoothUtils.getProfileName(profile), device);
                }
                return false;
            }
            RetryTokenAndCounter token = mBondedYetToConnect.get(device.getAddress()).get(profile);
            return mHandler.hasMessages(RETRY_MSG_WHAT, token);
        }
    }

    /**
     * For unit testing purposes, to aid in verifying retry attempts were posted.
     */
    @VisibleForTesting
    boolean isRetryPosted(BluetoothDevice device, int profile) {
        return mFirstConnectionTracker.isRetryPosted(device, profile);
    }

    /**
     * For unit testing purposes.
     */
    @VisibleForTesting
    int getMaxRetriesFirstConnection() {
        return MAX_RETRY_ATTEMPTS;
    }

    /**
     * Creates an instance of {@link BluetoothConnectionRetryManager} that will manage
     * connection retries.
     *
     * @param context - {@link Context} of calling code.
     * @return A new instance of a {@link BluetoothConnectionRetryManager}, or {@code null}
     * on error.
     */
    public static BluetoothConnectionRetryManager create(Context context) {
        try {
            return new BluetoothConnectionRetryManager(context);
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Creates an instance of {@link BluetoothConnectionRetryManager} that will manage
     * connection retries.
     *
     * @param context - {@link Context} of calling code.
     * @return A new instance of a {@link BluetoothConnectionRetryManager}, or {@code null}
     * on error.
     */
    private BluetoothConnectionRetryManager(Context context) {
        mContext = Objects.requireNonNull(context);
        BluetoothManager bluetoothManager =
                Objects.requireNonNull(mContext.getSystemService(BluetoothManager.class));
        mBluetoothAdapter = Objects.requireNonNull(bluetoothManager.getAdapter());
        mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
        mFirstConnectionTracker = new FirstConnectionTracker();
    }

    /**
     * Begin managing connection retries.
     */
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "Starting connection retry management, managed profiles:");
            for (int i = 0; i < MANAGED_PROFILES.length; i++) {
                Slogf.d(TAG, "    %s",
                        BluetoothUtils.getProfileName(MANAGED_PROFILES[i]));
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        // TODO (201800664): Profile State Change actions are hidden. This is a work around for now
        filter.addAction(BluetoothUtils.A2DP_SINK_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.HFP_CLIENT_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.MAP_CLIENT_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.PAN_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.PBAP_CLIENT_CONNECTION_STATE_CHANGED);

        UserHandle currentUser = UserHandle.of(ActivityManager.getCurrentUser());
        mUserContext = mContext.createContextAsUser(currentUser, /* flags= */ 0);

        mUserContext.registerReceiver(mBluetoothBroadcastReceiver, filter);
    }

    /**
     * Stop managing connection retries. Clean up local resources.
     */
    public void release() {
        if (DBG) {
            Slogf.d(TAG, "Stopping connection retry management");
        }

        if (mUserContext != null) {
            if (mBluetoothBroadcastReceiver != null) {
                mUserContext.unregisterReceiver(mBluetoothBroadcastReceiver);
            } else {
                Slogf.wtf(TAG, "mBluetoothBroadcastReceiver null during release()");
            }
            mUserContext = null;
        }
    }

    /**
     * Connect a device.
     *
     * @param device - The device to connect
     * @return
     */
    private int connect(BluetoothDevice device) {
        if (DBG) {
            Slogf.d(TAG, "Connecting %s", device);
        }
        if (device == null) {
            return BluetoothStatusCodes.ERROR_UNKNOWN;
        }
        return device.connect();
    }
}
