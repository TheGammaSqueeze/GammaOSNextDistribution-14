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

import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_DEVICES;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

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
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * BluetoothDeviceManager - Manages a list of devices, sorted by connection attempt priority.
 * Provides a means to request connection attempts and adjust device connection priorities.
 *
 * This class maintains two core sets of variables.
 *   (1) Prioritized Devices - Determines the order in which the *next* auto-connect will be made.
 *       This can edited through public functions from outside the class. A snap shot of this is
 *       taken immediately prior to the auto-connection process beginning. Access to this set is
 *       guarded internally by {@code  mPrioritizedDevicesLock}.
 *   (2) Auto-connecting State Variables - Captures the state of an ongoing auto connection process.
 *       Once these are locked in by {@code beginAutoConnecting()}, they can't be changed until the
 *       process completes or is cancelled. They can only be read by public functions for those that
 *       are interested in the state of auto-connection. All these state variables are guarded
 *       internally by {@code mAutoConnectLock}.
 *
 * Note that, due to the nature of having two locks and they way they're currently used, you MUST be
 * sure to only grab them {@code mAutoConnectLock} first and {@code mPrioritizedDevicesLock} second.
 * If you do not then you run the risk for deadlock. As it stands, public functions will not let
 * consumers of this class get into a deadlock state.
 */
public final class BluetoothDeviceManager {
    private static final String TAG = CarLog.tagFor(BluetoothDeviceManager.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final String SETTINGS_KEY = KEY_BLUETOOTH_DEVICES;
    private static final String SETTINGS_DELIMITER = ",";

    private static final int AUTO_CONNECT_TIMEOUT_MS = 8000;
    private static final Object AUTO_CONNECT_TOKEN = new Object();

    private final Context mContext;

    // Central priority list of devices
    private final Object mPrioritizedDevicesLock = new Object();
    @GuardedBy("mPrioritizedDevicesLock")
    private ArrayList<BluetoothDevice> mPrioritizedDevices;

    // Auto connection process state
    private final Object mAutoConnectLock = new Object();
    @GuardedBy("mAutoConnectLock")
    private boolean mConnecting;
    @GuardedBy("mAutoConnectLock")
    private int mAutoConnectPriority;
    @GuardedBy("mAutoConnectLock")
    private final SparseIntArray mAutoConnectingDeviceProfiles = new SparseIntArray();
    @GuardedBy("mAutoConnectLock")
    private List<BluetoothDevice> mAutoConnectingDevices;

    @Nullable
    private Context mUserContext;

    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;
    private final Handler mHandler = new Handler(
            CarServiceUtils.getHandlerThread(CarBluetoothService.THREAD_NAME).getLooper());

    /**
     * A BroadcastReceiver that listens specifically for actions related to the device we're
     * tracking and uses them to update the status.
     *
     * <p>ON {@code BluetoothDevice.ACTION_BOND_STATE_CHANGED}:
     *   - Remove a device from the list if the bond has been removed
     *   - Otherwise ignore other state changes and do nothing
     *
     * <p>ON {@code BluetoothAdapter.ACTION_STATE_CHANGED}:
     *   If the new adapter state is not ON, cancel any auto connection processes
     *
     * <p>ON Any of the various <Profile>.ACTION_CONNECTION_STATE_CHANGED, where <Profile> can be
     * any of BluetoothA2dpSink, BluetoothHeadsetClient, BluetoothMapClient, BluetoothPan, or
     * BluetoothPbapClient:
     *   - If the profile state change belongs to the auto-connecting device then indicate to the
     *     auto connection process that a profile has connected for the current device.
     *   - If the device is not the auto connecting device, then trigger outgoing connections with a
     *     BluetoothDevice.connect() call
     */
    private final class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = null;
            switch (action) {
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.ERROR);
                    handleDeviceBondStateChange(device, bondState);
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    handleAdapterStateChange(adapterState);
                    break;
                default:
                    // Treat all other actions as if we think they're a profile action. If the
                    // action doesn't resolve to a profile action then we get -1 here and return
                    int profile = BluetoothUtils.getProfileFromConnectionAction(action);
                    if (profile < 0) {
                        return;
                    }
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                            BluetoothProfile.STATE_DISCONNECTED);
                    handleProfileConnectionStateChanged(device, profile, state);
                    break;
            }
        }
    }

    /**
     * Handles an incoming Profile-Device connection state change event.
     *
     * <p>On <BluetoothProfile>.ACTION_CONNECTION_STATE_CHANGED coming from the BroadcastReceiver:
     *    On connected, if we're auto connecting and this is the current device we're managing, then
     *    see if we can move on to the next device in the list. Otherwise, If the device connected
     *    then add it to our priority list if it's not on their already.
     *
     * @param device - The Bluetooth device the state change is for
     * @param state - The new profile connection state of the device
     */
    private void handleProfileConnectionStateChanged(BluetoothDevice device, int profile,
            int state) {
        if (DBG) {
            Slogf.d(TAG, "Received profile state change, device: %s, profile: %s, state: %s",
                    BluetoothUtils.getDeviceDebugInfo(device),
                    BluetoothUtils.getProfileName(profile),
                    BluetoothUtils.getConnectionStateName(state));
        }

        if (handleAutoConnectingDeviceStateChange(device, profile, state)) {
            return;
        }

        if (state == BluetoothProfile.STATE_CONNECTED) {
            // If a device was allowed to connected then its assumed it has a connection policy
            // value of ALLOWED.
            addDevice(device);

            // Do not trigger connections based on the connection status of PAN. PAN is known to
            // allow connections, just to tear it down immediately. A race condition can cause the
            // connection to continually be brought up and down as a result, unless timing is
            // perfect
            if (profile != BluetoothProfile.PAN) {
                triggerConnections(device);
            }
        }
    }

    /**
     * Handles an incoming device bond status event.
     *
     * <p>On BluetoothDevice.ACTION_BOND_STATE_CHANGED:
     *    - If a device becomes unbonded, remove it from our list if it's there.
     *    - If it's bonded, then add it to our list if the UUID set says it supports us.
     *
     * @param device - The Bluetooth device the state change is for
     * @param state - The new bond state of the device
     */
    private void handleDeviceBondStateChange(BluetoothDevice device, int state) {
        if (DBG) {
            Slogf.d(TAG, "Bond state has changed [device: %s, state: %s]", device,
                    BluetoothUtils.getBondStateName(state));
        }
        if (state == BluetoothDevice.BOND_NONE) {
            // Note: We have seen cases of unbonding events being sent without actually
            // unbonding the device.
            removeDevice(device);
        }
    }

    /**
     * Handles an adapter state change event.
     *
     * <p>On BluetoothAdapter.ACTION_STATE_CHANGED:
     *    If the adapter is going into the OFF state, then cancel any auto connecting, commit our
     *    priority list and go idle.
     *
     * @param state - The new state of the Bluetooth adapter
     */
    private void handleAdapterStateChange(int state) {
        if (DBG) {
            Slogf.d(TAG, "Bluetooth Adapter state changed: %s",
                    BluetoothUtils.getAdapterStateName(state));
        }
        // Crashes of the BT stack mean we're not promised to see all the state changes we
        // might want to see. In order to be a bit more robust to crashes, we'll treat any
        // non-ON state as a time to cancel auto-connect. This gives us a better chance of
        // seeing a cancel state before a crash, as well as makes sure we're "cancelled"
        // before we see an ON.
        if (state != BluetoothAdapter.STATE_ON) {
            cancelAutoConnecting();
        }
        // To reduce how many times we're committing the list, we'll only write back on off
        if (state == BluetoothAdapter.STATE_OFF) {
            commit();
        }
    }

    /**
     * Creates an instance of BluetoothDeviceManager that will manage devices
     * for the given profile ID.
     *
     * @param context - context of calling code
     * @return A new instance of a BluetoothDeviceManager, or null on any error
     */
    public static @Nullable BluetoothDeviceManager create(Context context) {
        try {
            return new BluetoothDeviceManager(context);
        } catch (NullPointerException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Creates an instance of BluetoothDeviceManager that will manage devices
     * for the given profile ID.
     *
     * @param context - context of calling code
     * @return A new instance of a BluetoothDeviceManager
     */
    private BluetoothDeviceManager(Context context) {
        mContext = Objects.requireNonNull(context);
        BluetoothManager bluetoothManager =
                Objects.requireNonNull(mContext.getSystemService(BluetoothManager.class));
        mBluetoothAdapter = Objects.requireNonNull(bluetoothManager.getAdapter());
        mPrioritizedDevices = new ArrayList<>();
        mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
    }

    /**
     * Begins managing devices. Sets the start state from persistent memory.
     */
    public void start() {
        if (DBG) {
            Slogf.d(TAG, "Starting device management");
        }

        synchronized (mAutoConnectLock) {
            mConnecting = false;
            mAutoConnectPriority = -1;
            mAutoConnectingDevices = null;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        // TODO (201800664): Profile State Change actions are hidden. This is a work around for now
        filter.addAction(BluetoothUtils.A2DP_SOURCE_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.A2DP_SINK_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.HFP_CLIENT_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.MAP_CLIENT_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.PAN_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothUtils.PBAP_CLIENT_CONNECTION_STATE_CHANGED);

        UserHandle currentUser = UserHandle.of(ActivityManager.getCurrentUser());
        mUserContext = mContext.createContextAsUser(currentUser, /* flags= */ 0);

        load();

        mUserContext.registerReceiver(mBluetoothBroadcastReceiver, filter);
    }

    /**
     * Stops managing devices. Commits the final priority list to persistent memory and cleans up
     * local resources.
     */
    public void stop() {
        if (DBG) {
            Slogf.d(TAG, "Stopping device management");
        }
        cancelAutoConnecting();
        if (mUserContext != null) {
            commit();
            if (mBluetoothBroadcastReceiver != null) {
                mUserContext.unregisterReceiver(mBluetoothBroadcastReceiver);
            } else {
                Slogf.wtf(TAG, "mBluetoothBroadcastReceiver null during stop()");
            }
            mUserContext = null;
        }
    }

    /**
     * Loads the current device priority list from persistent memory in {@link Settings.Secure}.
     *
     * <p>This will overwrite the contents of the local priority list. It does not attempt to take
     * the union of the file and existing set. As such, you likely do not want to load after
     * starting. Failed attempts to load leave the prioritized device list unchanged.
     *
     * @return true on success, false otherwise
     */
    private boolean load() {
        if (DBG) {
            Slogf.d(TAG, "Loading device priority list snapshot using key '%s'", SETTINGS_KEY);
        }
        // Read from Settings.Secure for our profile, as the current user.
        String devicesStr = Settings.Secure.getString(mUserContext.getContentResolver(),
                SETTINGS_KEY);
        if (DBG) {
            Slogf.d(TAG, "Found Device String: '%s'", devicesStr);
        }
        if (TextUtils.isEmpty(devicesStr)) {
            return false;
        }

        // Split string into list of device MAC addresses
        List<String> deviceList = Arrays.asList(devicesStr.split(SETTINGS_DELIMITER));
        if (deviceList == null) {
            return false;
        }

        // Turn the strings into full blown Bluetooth devices
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        for (int i = 0; i < deviceList.size(); i++) {
            String address = deviceList.get(i);
            try {
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                devices.add(device);
            } catch (IllegalArgumentException e) {
                Slogf.w(TAG, "Unable to parse address '%s' to a device", address);
                continue;
            }
        }

        synchronized (mPrioritizedDevicesLock) {
            mPrioritizedDevices = devices;
        }

        if (DBG) {
            Slogf.d(TAG, "Loaded Priority list: %s", devices);
        }
        return true;
    }

    /**
     * Commits the current device priority list to persistent memory in {@link Settings.Secure}.
     *
     * @return true on success, false otherwise
     */
    private boolean commit() {
        StringBuilder sb = new StringBuilder();
        String delimiter = "";
        synchronized (mPrioritizedDevicesLock) {
            for (BluetoothDevice device : mPrioritizedDevices) {
                sb.append(delimiter);
                sb.append(device.getAddress());
                delimiter = SETTINGS_DELIMITER;
            }
        }

        String devicesStr = sb.toString();
        Settings.Secure.putString(mUserContext.getContentResolver(), SETTINGS_KEY, devicesStr);
        if (DBG) {
            Slogf.d(TAG, "Committed key: %s, value: '%s'", SETTINGS_KEY, devicesStr);
        }
        return true;
    }

    /**
     * Makes a clone of the current prioritized device list in a synchronized fashion
     *
     * @return A clone of the most up to date prioritized device list
     */
    public List<BluetoothDevice> getDeviceListSnapshot() {
        synchronized (mPrioritizedDevicesLock) {
            return (ArrayList) mPrioritizedDevices.clone();
        }
    }

    /**
     * Adds a device to the end of the priority list.
     */
    public void addDevice(@Nullable BluetoothDevice device) {
        if (device == null) {
            return;
        }
        synchronized (mPrioritizedDevicesLock) {
            if (mPrioritizedDevices.contains(device)) {
                return;
            }
            if (DBG) {
                Slogf.d(TAG, "Add device %s", device);
            }
            mPrioritizedDevices.add(device);
            commit();
        }
    }

    /**
     * Removes a device from the priority list.
     */
    public void removeDevice(@Nullable BluetoothDevice device) {
        if (device == null) {
            return;
        }
        synchronized (mPrioritizedDevicesLock) {
            if (!mPrioritizedDevices.contains(device)) {
                return;
            }
            if (DBG) {
                Slogf.d(TAG, "Remove device %s", device);
            }
            mPrioritizedDevices.remove(device);
            commit();
        }
    }

    /**
     * Gets the connection priority of a device.
     *
     * @param device - The device you want the priority of
     * @return The priority of the device, or -1 if the device is not in the list
     */
    public int getDeviceConnectionPriority(@Nullable BluetoothDevice device) {
        if (device == null) {
            return -1;
        }
        if (DBG) {
            Slogf.d(TAG, "Get connection priority of %s", device);
        }
        synchronized (mPrioritizedDevicesLock) {
            return mPrioritizedDevices.indexOf(device);
        }
    }

    /**
     * Sets the connection priority of a device.
     *
     * <p>If the device does not exist, it will be added. If the priority is less than zero,
     * no priority will be set. If the priority exceeds the bounds of the list, no priority will be
     * set.
     *
     * @param device - The device you want to set the priority of
     * @param priority - The priority you want to the device to have
     */
    public void setDeviceConnectionPriority(@Nullable BluetoothDevice device, int priority) {
        synchronized (mPrioritizedDevicesLock) {
            if (device == null || priority < 0 || priority > mPrioritizedDevices.size()
                    || getDeviceConnectionPriority(device) == priority) {
                return;
            }
            int priorityToSet = priority;
            if (mPrioritizedDevices.contains(device)) {
                mPrioritizedDevices.remove(device);
                if (priorityToSet > mPrioritizedDevices.size()) {
                    priorityToSet = mPrioritizedDevices.size();
                }
            }
            if (DBG) {
                Slogf.d(TAG, "Set connection priority of %s to %d", device, priorityToSet);
            }
            mPrioritizedDevices.add(priorityToSet, device);
            commit();
        }
    }

    /**
     * Connects a specific device on all enabled profiles.
     *
     * @param device - The device to connect
     * @return A {@code BluetoothStatusCodes.SUCCESS} code on success, or various errors otherwise
     */
    private int connect(BluetoothDevice device) {
        if (DBG) {
            Slogf.d(TAG, "Connecting %s", device);
        }
        if (device == null) {
            return -1;
        }
        return device.connect();
    }

    /**
     * Begins the process of connecting to devices, one by one, in the order that the priority
     * list currently specifies. If we are already connecting, or no devices are present, then no
     * work is done.
     */
    public void beginAutoConnecting() {
        if (DBG) {
            Slogf.d(TAG, "Request to begin auto connection process");
        }
        synchronized (mAutoConnectLock) {
            if (isAutoConnecting()) {
                if (DBG) {
                    Slogf.d(TAG, "Auto connect requested while we are already auto connecting");
                }
                return;
            }
            if (mBluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
                if (DBG) {
                    Slogf.d(TAG, "Bluetooth Adapter is not on, cannot connect devices");
                }
                return;
            }

            mAutoConnectingDevices = getDeviceListSnapshot();
            if (mAutoConnectingDevices.isEmpty()) {
                if (DBG) {
                    Slogf.d(TAG, "No saved devices to auto-connect to.");
                }
                cancelAutoConnecting();
                return;
            }
            mConnecting = true;
            mAutoConnectPriority = 0;
        }
        autoConnectWithTimeout();
    }

    /**
     * Connects the current priority device and sets a timeout timer to indicate when to give up and
     * move on to the next one.
     */
    private void autoConnectWithTimeout() {
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) {
                if (DBG) {
                    Slogf.d(TAG,
                            "Autoconnect process was cancelled, skipping connecting next device");
                }
                return;
            }
            if (mAutoConnectPriority < 0 || mAutoConnectPriority >= mAutoConnectingDevices.size()) {
                return;
            }

            BluetoothDevice device = mAutoConnectingDevices.get(mAutoConnectPriority);
            if (DBG) {
                Slogf.d(TAG, "Auto connecting (%d) device: %s", mAutoConnectPriority, device);
            }

            mHandler.post(() -> {
                initializeAutoConnectingDeviceProfiles();
                int connectStatus = connect(device);
                if (connectStatus != BluetoothStatusCodes.SUCCESS) {
                    Slogf.w(TAG,
                            "Connection attempt immediately failed, moving to the next device");
                    continueAutoConnecting();
                }
            });
            mHandler.postDelayed(() -> {
                Slogf.w(TAG, "Auto connect process has timed out connecting to %s", device);
                continueAutoConnecting();
            }, AUTO_CONNECT_TOKEN, AUTO_CONNECT_TIMEOUT_MS);
        }
    }

    /**
     * Initializes the expected connected profiles for the current auto-connect device
     */
    private void initializeAutoConnectingDeviceProfiles() {
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) {
                return;
            }

            mAutoConnectingDeviceProfiles.clear();

            addWatchedProfileIfSupported(BluetoothProfile.A2DP);
            addWatchedProfileIfSupported(BluetoothProfile.A2DP_SINK);
            addWatchedProfileIfSupported(BluetoothProfile.HEADSET_CLIENT);
            addWatchedProfileIfSupported(BluetoothProfile.MAP_CLIENT);
            addWatchedProfileIfSupported(BluetoothProfile.PAN);
            addWatchedProfileIfSupported(BluetoothProfile.PBAP_CLIENT);
        }
    }

    /**
     * Utility method that adds a profile to our watched profiles list for the currently active auto
     * connecting device. Assumes you hold {@code mAutoConnectLock}.
     */
    private void addWatchedProfileIfSupported(int profile) {
        List<ParcelUuid> ourUuids = mBluetoothAdapter.getUuidsList();
        synchronized (mAutoConnectLock) {
            BluetoothDevice device = mAutoConnectingDevices.get(mAutoConnectPriority);
            if (device == null) {
                return;
            }
            if (BluetoothUtils.isProfileSupported(ourUuids, device, profile)) {
                mAutoConnectingDeviceProfiles.put(profile, -1);
                if (DBG) {
                    Slogf.d(TAG, "Device %s supports %s. Expect a connection", device,
                            BluetoothUtils.getProfileName(profile));
                }
            }
        }
    }

    /**
     * Determines if the auto-connected device has completed connecting yet or not
     *
     * @return {@code true} if all profiles are connected for the current auto connect device, True
     *         if the auto-connect process is not running, false otherwise.
     */
    private boolean isAutoConnectDeviceConnected() {
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) {
                return true;
            }

            for (int i = 0; i < mAutoConnectingDeviceProfiles.size(); i++) {
                int profileState = mAutoConnectingDeviceProfiles.valueAt(i);
                if (profileState != BluetoothProfile.STATE_CONNECTED
                        && profileState != BluetoothProfile.STATE_DISCONNECTED) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Handles a profile connection event that belongs to the currently auto-connecting device
     * and determines if the device is fully connected. If the device is fully connected, then
     * we can move on to the next device in the priority list.
     *
     * @param device The BluetoothDevice this event belonged to
     * @param profile The profile this event is associated with
     * @param state The new state the device is in on this profile
     * @return {@code true} if the event belonged to the auto-connect process and was handled, false
     *         otherwise.
     */
    private boolean handleAutoConnectingDeviceStateChange(BluetoothDevice device, int profile,
            int state) {
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting() || !isAutoConnectingDevice(device)) {
                return false;
            }

            mAutoConnectingDeviceProfiles.put(profile, state);
            if (isAutoConnectDeviceConnected()) {
                continueAutoConnecting();
            }

            return true;
        }
    }

    /**
     * Forcibly moves the auto connect process to the next device, or finishes it if no more
     * devices are available.
     */
    private void continueAutoConnecting() {
        if (DBG) {
            Slogf.d(TAG, "Continue auto-connect process on next device");
        }
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) {
                if (DBG) {
                    Slogf.d(TAG, "Autoconnect process was cancelled, no need to continue.");
                }
                return;
            }
            mHandler.removeCallbacksAndMessages(AUTO_CONNECT_TOKEN);
            mAutoConnectPriority++;
            if (mAutoConnectPriority >= mAutoConnectingDevices.size()) {
                if (DBG) {
                    Slogf.d(TAG, "No more devices to connect to");
                }
                cancelAutoConnecting();
                return;
            }
        }
        autoConnectWithTimeout();
    }

    /**
     * Cancels the auto-connection process. Any in-flight connection attempts will still be tried.
     *
     * <p>Cancelling is defined as deleting the snapshot of devices, resetting the device to connect
     * index, setting the connecting boolean to null, and removing any pending timeouts if they
     * exist. If there are no auto-connects in process this will do nothing.
     */
    private void cancelAutoConnecting() {
        if (DBG) {
            Slogf.d(TAG, "Cleaning up any auto-connect process");
        }
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) return;
            mHandler.removeCallbacksAndMessages(AUTO_CONNECT_TOKEN);
            mConnecting = false;
            mAutoConnectPriority = -1;
            mAutoConnectingDevices = null;
            mAutoConnectingDeviceProfiles.clear();
        }
    }

    /**
     * Gets the auto-connect status of thie profile device manager
     */
    public boolean isAutoConnecting() {
        synchronized (mAutoConnectLock) {
            return mConnecting;
        }
    }

    /**
     * Determines if a device is the currently auto-connecting device
     *
     * @param device - A BluetoothDevice object to compare against any know auto connecting device
     * @return {@code true} if the input device is the device we're currently connecting,
     *         {@code false} otherwise
     */
    private boolean isAutoConnectingDevice(BluetoothDevice device) {
        synchronized (mAutoConnectLock) {
            if (mAutoConnectingDevices == null) {
                return false;
            }
            return mAutoConnectingDevices.get(mAutoConnectPriority).equals(device);
        }
    }

    /**
     * Triggers connections of related Bluetooth profiles on a device
     *
     * @param device - The Bluetooth device you would like to connect to
     */
    private void triggerConnections(BluetoothDevice device) {
        if (DBG) {
            Slogf.d(TAG, "Trigger connection to %s", device);
        }
        connect(device);
    }

    /**
     * Writes the verbose current state of the object to the PrintWriter
     *
     * @param writer PrintWriter object to write lines to
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("%s\n", TAG);
        writer.increaseIndent();
        writer.printf("Auto-Connecting: %b\n", isAutoConnecting());
        writer.printf("Priority List:\n");

        writer.increaseIndent();
        List<BluetoothDevice> devices = getDeviceListSnapshot();
        for (BluetoothDevice device : devices) {
            writer.printf("%s - %s\n", device.getAddress(), device.getName());
        }
        writer.decreaseIndent();

        writer.decreaseIndent();
    }
}
