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

import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_PROFILES_INHIBITED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.car.ICarBluetoothUserService;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.util.SetMultimap;
import com.android.internal.annotations.GuardedBy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the inhibiting of Bluetooth profile connections to and from specific devices.
 */
public class BluetoothProfileInhibitManager {
    private static final String TAG = CarLog.tagFor(BluetoothProfileInhibitManager.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String SETTINGS_DELIMITER = ",";
    private static final Binder RESTORED_PROFILE_INHIBIT_TOKEN = new Binder();
    private static final long RESTORE_BACKOFF_MILLIS = 1000L;

    private final Context mUserContext;
    private final BluetoothAdapter mBluetoothAdapter;

    // Per-User information
    private final int mUserId;
    private final ICarBluetoothUserService mBluetoothUserProxies;
    private final String mLogHeader;

    private final Object mProfileInhibitsLock = new Object();

    @GuardedBy("mProfileInhibitsLock")
    private final SetMultimap<BluetoothConnection, InhibitRecord> mProfileInhibits =
            new SetMultimap<>();

    @GuardedBy("mProfileInhibitsLock")
    private final HashSet<InhibitRecord> mRestoredInhibits = new HashSet<>();

    @GuardedBy("mProfileInhibitsLock")
    private final HashSet<BluetoothConnection> mAlreadyDisabledProfiles = new HashSet<>();

    private final Handler mHandler = new Handler(
            CarServiceUtils.getHandlerThread(CarBluetoothService.THREAD_NAME).getLooper());
    /**
     * BluetoothConnection - encapsulates the information representing a connection to a device on a
     * given profile. This object is hashable, encodable and decodable.
     *
     * Encodes to the following structure:
     * <device>/<profile>
     *
     * Where,
     *    device - the device we're connecting to, can be null
     *    profile - the profile we're connecting on, can be null
     */
    public class BluetoothConnection {
        // Examples:
        // 01:23:45:67:89:AB/9
        // null/0
        // null/null
        private static final String FLATTENED_PATTERN =
                "^(([0-9A-F]{2}:){5}[0-9A-F]{2}|null)/([0-9]+|null)$";

        private final BluetoothDevice mBluetoothDevice;
        private final Integer mBluetoothProfile;

        /**
         * Creates a {@link BluetoothConnection} from a previous output of {@link #encode()}.
         *
         * @param flattenedParams A flattened string representation of a {@link BluetoothConnection}
         */
        public BluetoothConnection(String flattenedParams) {
            if (!flattenedParams.matches(FLATTENED_PATTERN)) {
                throw new IllegalArgumentException("Bad format for flattened BluetoothConnection");
            }

            BluetoothDevice device = null;
            Integer profile = null;

            if (mBluetoothAdapter != null) {
                String[] parts = flattenedParams.split("/");
                if (!"null".equals(parts[0])) {
                    device = mBluetoothAdapter.getRemoteDevice(parts[0]);
                }
                if (!"null".equals(parts[1])) {
                    profile = Integer.valueOf(parts[1]);
                }
            }

            mBluetoothDevice = device;
            mBluetoothProfile = profile;
        }

        public BluetoothConnection(Integer profile, BluetoothDevice device) {
            mBluetoothProfile = profile;
            mBluetoothDevice = device;
        }

        public BluetoothDevice getDevice() {
            return mBluetoothDevice;
        }

        public Integer getProfile() {
            return mBluetoothProfile;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BluetoothConnection)) {
                return false;
            }
            BluetoothConnection otherParams = (BluetoothConnection) other;
            return Objects.equals(mBluetoothDevice, otherParams.mBluetoothDevice)
                && Objects.equals(mBluetoothProfile, otherParams.mBluetoothProfile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mBluetoothDevice, mBluetoothProfile);
        }

        @Override
        public String toString() {
            return encode();
        }

        /**
         * Converts these {@link BluetoothConnection} to a parseable string representation.
         *
         * @return A parseable string representation of this BluetoothConnection object.
         */
        public String encode() {
            return mBluetoothDevice + "/" + mBluetoothProfile;
        }
    }

    private class InhibitRecord implements IBinder.DeathRecipient {
        private final BluetoothConnection mParams;
        private final IBinder mToken;

        private boolean mRemoved = false;

        InhibitRecord(BluetoothConnection params, IBinder token) {
            this.mParams = params;
            this.mToken = token;
        }

        public BluetoothConnection getParams() {
            return mParams;
        }

        public IBinder getToken() {
            return mToken;
        }

        public boolean removeSelf() {
            synchronized (mProfileInhibitsLock) {
                if (mRemoved) {
                    return true;
                }

                if (removeInhibitRecord(this)) {
                    mRemoved = true;
                    return true;
                } else {
                    return false;
                }
            }
        }

        @Override
        public void binderDied() {
            if (DBG) {
                Slogf.d(TAG, "%s Releasing inhibit request on profile %s for device %s"
                                + ": requesting process died",
                        mLogHeader, BluetoothUtils.getProfileName(mParams.getProfile()),
                        mParams.getDevice());
            }
            removeSelf();
        }
    }

    /**
     * Creates a new instance of a BluetoothProfileInhibitManager
     *
     * @param context - context of calling code
     * @param userId - ID of user we want to manage inhibits for
     * @param bluetoothUserProxies - Set of per-user bluetooth proxies for calling into the
     *                               bluetooth stack as the current user.
     * @return A new instance of a BluetoothProfileInhibitManager
     */
    public BluetoothProfileInhibitManager(Context context, int userId,
            ICarBluetoothUserService bluetoothUserProxies) {
        mUserContext = context.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        mUserId = userId;
        mLogHeader = "[User: " + mUserId + "]";
        mBluetoothUserProxies = bluetoothUserProxies;
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    /**
     * Create {@link InhibitRecord}s for all profile inhibits written to {@link Settings.Secure}.
     */
    private void load() {
        String savedBluetoothConnection = Settings.Secure.getString(
                mUserContext.getContentResolver(), KEY_BLUETOOTH_PROFILES_INHIBITED);

        if (TextUtils.isEmpty(savedBluetoothConnection)) {
            return;
        }

        if (DBG) {
            Slogf.d(TAG, "%s Restoring profile inhibits: %s", mLogHeader, savedBluetoothConnection);
        }

        synchronized (mProfileInhibitsLock) {
            for (String paramsStr : savedBluetoothConnection.split(SETTINGS_DELIMITER)) {
                try {
                    BluetoothConnection params = new BluetoothConnection(paramsStr);
                    InhibitRecord record = new InhibitRecord(
                            params, RESTORED_PROFILE_INHIBIT_TOKEN);
                    mProfileInhibits.put(params, record);
                    mRestoredInhibits.add(record);
                    if (DBG) {
                        Slogf.d(TAG, "%s Restored profile inhibits for %s", mLogHeader, params);
                    }
                } catch (IllegalArgumentException e) {
                    // We won't ever be able to fix a bad parse, so skip it and move on.
                    Slogf.e(TAG, "%s Bad format for saved profile inhibit: %s, %s",
                            mLogHeader, paramsStr, e);
                }
            }
        }
    }

    /**
     * Dump all currently-active profile inhibits to {@link Settings.Secure}.
     */
    @GuardedBy("mProfileInhibitsLock")
    private void commitLocked() {
        Set<BluetoothConnection> inhibitedProfiles = new HashSet<>(mProfileInhibits.keySet());
        // Don't write out profiles that were disabled before a request was made, since
        // restoring those profiles is a no-op.
        inhibitedProfiles.removeAll(mAlreadyDisabledProfiles);
        String savedDisconnects =
                inhibitedProfiles
                        .stream()
                        .map(BluetoothConnection::encode)
                        .collect(Collectors.joining(SETTINGS_DELIMITER));

        Settings.Secure.putString(mUserContext.getContentResolver(),
                KEY_BLUETOOTH_PROFILES_INHIBITED, savedDisconnects);

        if (DBG) {
            Slogf.d(TAG, "%s Committed key: %s, value: '%s'",
                    mLogHeader, KEY_BLUETOOTH_PROFILES_INHIBITED, savedDisconnects);
        }
    }

    /**
     *
     */
    public void start() {
        load();
        removeRestoredProfileInhibits();
    }

    /**
     *
     */
    public void stop() {
        releaseAllInhibitsBeforeUnbind();
    }

    /**
     * Request to disconnect the given profile on the given device, and prevent it from reconnecting
     * until either the request is released, or the process owning the given token dies.
     *
     * @param device  The device on which to inhibit a profile.
     * @param profile The {@link android.bluetooth.BluetoothProfile} to inhibit.
     * @param token   A {@link IBinder} to be used as an identity for the request. If the process
     *                owning the token dies, the request will automatically be released
     * @return {@code true} if the profile was successfully inhibited, {@code false} if an error
     *         occurred.
     */
    boolean requestProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Slogf.d(TAG, "%s Request profile inhibit: profile %s, device %s",
                    mLogHeader, BluetoothUtils.getProfileName(profile), device.getAddress());
        }
        BluetoothConnection params = new BluetoothConnection(profile, device);
        InhibitRecord record = new InhibitRecord(params, token);
        return addInhibitRecord(record);
    }

    /**
     * Undo a previous call to {@link #requestProfileInhibit} with the same parameters,
     * and reconnect the profile if no other requests are active.
     *
     * @param device  The device on which to release the inhibit request.
     * @param profile The profile on which to release the inhibit request.
     * @param token   The token provided in the original call to
     *                {@link #requestBluetoothProfileInhibit}.
     * @return {@code true} if the request was released, {@code false} if an error occurred.
     */
    boolean releaseProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Slogf.d(TAG, "%s Release profile inhibit: profile %s, device %s",
                    mLogHeader, BluetoothUtils.getProfileName(profile), device.getAddress());
        }

        BluetoothConnection params = new BluetoothConnection(profile, device);
        InhibitRecord record;
        record = findInhibitRecord(params, token);

        if (record == null) {
            Slogf.e(TAG, "Record not found");
            return false;
        }

        return record.removeSelf();
    }

    /**
     * Checks whether a request to disconnect the given profile on the given device has been made
     * and if the inhibit request is still active.
     *
     * @param device  The device on which to verify the inhibit request.
     * @param profile The profile on which to verify the inhibit request.
     * @param token   The token provided in the original call to
     *                {@link #requestBluetoothProfileInhibit}.
     * @return {@code true} if inhibit was requested and is still active, {@code false} if an error
     *         occurred or inactive.
     */
    boolean isProfileInhibited(BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Slogf.d(TAG, "%s Check profile inhibit: profile %s, device %s",
                    mLogHeader, BluetoothUtils.getProfileName(profile), device.getAddress());
        }

        if (findInhibitRecord(new BluetoothConnection(profile, device), token) == null) {
            Slogf.e(TAG, "Record not found");
            return false;
        }

        if (!isProxyAvailable(profile)) {
            return false;
        }

        try {
            int policy = mBluetoothUserProxies.getConnectionPolicy(profile, device);
            return policy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
        } catch (RemoteException e) {
            Slogf.e(TAG, "Could not retrieve policy for profile", e);
            return false;
        }
    }

    /**
     * Add a profile inhibit record, disabling the profile if necessary.
     */
    private boolean addInhibitRecord(InhibitRecord record) {
        synchronized (mProfileInhibitsLock) {
            BluetoothConnection params = record.getParams();
            if (!isProxyAvailable(params.getProfile())) {
                return false;
            }

            Set<InhibitRecord> previousRecords = mProfileInhibits.get(params);
            if (findInhibitRecord(params, record.getToken()) != null) {
                Slogf.e(TAG, "Inhibit request already registered - skipping duplicate");
                return false;
            }

            try {
                record.getToken().linkToDeath(record, 0);
            } catch (RemoteException e) {
                Slogf.e(TAG, "Could not link to death on inhibit token (already dead?)", e);
                return false;
            }

            boolean isNewlyAdded = previousRecords.isEmpty();
            mProfileInhibits.put(params, record);

            if (isNewlyAdded) {
                try {
                    int policy =
                            mBluetoothUserProxies.getConnectionPolicy(
                                    params.getProfile(),
                                    params.getDevice());
                    if (policy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
                        // This profile was already disabled (and not as the result of an inhibit).
                        // Add it to the already-disabled list, and do nothing else.
                        mAlreadyDisabledProfiles.add(params);

                        if (DBG) {
                            Slogf.d(TAG, "%s Profile %s already disabled for device %s"
                                            + " - suppressing re-enable",
                                    mLogHeader, BluetoothUtils.getProfileName(params.getProfile()),
                                    params.getDevice());
                        }
                    } else {
                        mBluetoothUserProxies.setConnectionPolicy(
                                params.getProfile(),
                                params.getDevice(),
                                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
                        if (DBG) {
                            Slogf.d(TAG, "%s Disabled profile %s for device %s",
                                    mLogHeader, BluetoothUtils.getProfileName(params.getProfile()),
                                    params.getDevice());
                        }
                    }
                } catch (RemoteException e) {
                    Slogf.e(TAG, "Could not disable profile", e);
                    record.getToken().unlinkToDeath(record, 0);
                    mProfileInhibits.remove(params, record);
                    return false;
                }
            }

            commitLocked();
            return true;
        }
    }

    /**
     * Find the inhibit record, if any, corresponding to the given parameters and token.
     *
     * @param params  BluetoothConnection parameter pair that could have an inhibit on it
     * @param token   The token provided in the call to {@link #requestBluetoothProfileInhibit}.
     * @return InhibitRecord for the connection parameters and token if exists, {@code null}
     *         otherwise.
     */
    private InhibitRecord findInhibitRecord(BluetoothConnection params, IBinder token) {
        synchronized (mProfileInhibitsLock) {
            return mProfileInhibits.get(params)
                .stream()
                .filter(r -> r.getToken() == token)
                .findAny()
                .orElse(null);
        }
    }

    /**
     * Remove a given profile inhibit record, reconnecting if necessary.
     */
    private boolean removeInhibitRecord(InhibitRecord record) {
        synchronized (mProfileInhibitsLock) {
            BluetoothConnection params = record.getParams();
            if (!isProxyAvailable(params.getProfile())) {
                return false;
            }
            if (!mProfileInhibits.containsEntry(params, record)) {
                Slogf.e(TAG, "Record already removed");
                // Removing something a second time vacuously succeeds.
                return true;
            }

            // Re-enable profile before unlinking and removing the record, in case of error.
            // The profile should be re-enabled if this record is the only one left for that
            // device and profile combination.
            if (mProfileInhibits.get(params).size() == 1) {
                if (!restoreConnectionPolicy(params)) {
                    return false;
                }
            }

            record.getToken().unlinkToDeath(record, 0);
            mProfileInhibits.remove(params, record);

            commitLocked();
            return true;
        }
    }

    /**
     * Re-enable and reconnect a given profile for a device.
     */
    @GuardedBy("mProfileInhibitsLock")
    private boolean restoreConnectionPolicy(BluetoothConnection params) {
        if (!isProxyAvailable(params.getProfile())) {
            return false;
        }

        if (mAlreadyDisabledProfiles.remove(params)) {
            // The profile does not need any state changes, since it was disabled
            // before it was inhibited. Leave it disabled.
            if (DBG) {
                Slogf.d(TAG, "%s Not restoring profile %s for device %s - was manually disabled",
                        mLogHeader, BluetoothUtils.getProfileName(params.getProfile()),
                        params.getDevice());
            }
            return true;
        }

        try {
            mBluetoothUserProxies.setConnectionPolicy(
                    params.getProfile(),
                    params.getDevice(),
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            if (DBG) {
                Slogf.d(TAG, "%s Restored profile %s for device %s",
                        mLogHeader, BluetoothUtils.getProfileName(params.getProfile()),
                        params.getDevice());
            }
            return true;
        } catch (RemoteException e) {
            Slogf.e(TAG, "%s Could not enable profile: %s", mLogHeader, e);
            return false;
        }
    }

    /**
     * Try once to remove all restored profile inhibits.
     *
     * If the CarBluetoothUserService is not yet available, or it hasn't yet bound its profile
     * proxies, the removal will fail, and will need to be retried later.
     */
    @GuardedBy("mProfileInhibitsLock")
    private void tryRemoveRestoredProfileInhibitsLocked() {
        HashSet<InhibitRecord> successfullyRemoved = new HashSet<>();

        for (InhibitRecord record : mRestoredInhibits) {
            if (removeInhibitRecord(record)) {
                successfullyRemoved.add(record);
            }
        }

        mRestoredInhibits.removeAll(successfullyRemoved);
    }

    /**
     * Keep trying to remove all profile inhibits that were restored from settings
     * until all such inhibits have been removed.
     */
    private void removeRestoredProfileInhibits() {
        synchronized (mProfileInhibitsLock) {
            tryRemoveRestoredProfileInhibitsLocked();

            if (!mRestoredInhibits.isEmpty()) {
                if (DBG) {
                    Slogf.d(TAG, "%s Could not remove all restored profile inhibits - "
                            + "trying again in %dms",
                            mLogHeader, RESTORE_BACKOFF_MILLIS);
                }
                mHandler.postDelayed(
                        this::removeRestoredProfileInhibits,
                        RESTORED_PROFILE_INHIBIT_TOKEN,
                        RESTORE_BACKOFF_MILLIS);
            }
        }
    }

    /**
     * Release all active inhibit records prior to user switch or shutdown
     */
    private  void releaseAllInhibitsBeforeUnbind() {
        if (DBG) {
            Slogf.d(TAG, "%s Unbinding CarBluetoothUserService - releasing all profile inhibits",
                    mLogHeader);
        }

        synchronized (mProfileInhibitsLock) {
            for (BluetoothConnection params : mProfileInhibits.keySet()) {
                for (InhibitRecord record : mProfileInhibits.get(params)) {
                    record.removeSelf();
                }
            }

            // Some inhibits might be hanging around because they couldn't be cleaned up.
            // Make sure they get persisted...
            commitLocked();

            // ...then clear them from the map.
            mProfileInhibits.clear();

            // We don't need to maintain previously-disabled profiles any more - they were already
            // skipped in saveProfileInhibitsToSettings() above, and they don't need any
            // further handling when the user resumes.
            mAlreadyDisabledProfiles.clear();

            // Clean up bookkeeping for restored inhibits. (If any are still around, they'll be
            // restored again when this user restarts.)
            mHandler.removeCallbacksAndMessages(RESTORED_PROFILE_INHIBIT_TOKEN);
            mRestoredInhibits.clear();
        }
    }

    /**
     * Determines if the per-user bluetooth proxy for a given profile is active and usable.
     *
     * @return {@code true} if proxy is available, {@code false} otherwise
     */
    private boolean isProxyAvailable(int profile) {
        try {
            return mBluetoothUserProxies.isBluetoothConnectionProxyAvailable(profile);
        } catch (RemoteException e) {
            Slogf.e(TAG, "%s Car BT Service Remote Exception. Proxy for %s not available.",
                    mLogHeader, BluetoothUtils.getProfileName(profile));
        }
        return false;
    }

    /**
     * Print the verbose status of the object
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("%s:\n", TAG);
        writer.increaseIndent();

        // User metadata
        writer.printf("User: %d\n", mUserId);

        // Current inhibits
        String inhibits;
        synchronized (mProfileInhibitsLock) {
            inhibits = mProfileInhibits.keySet().toString();
        }
        writer.printf("Inhibited profiles: %s\n", inhibits);

        writer.decreaseIndent();
    }
}
