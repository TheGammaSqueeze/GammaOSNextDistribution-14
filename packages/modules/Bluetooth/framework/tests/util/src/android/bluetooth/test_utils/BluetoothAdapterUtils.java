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

package android.bluetooth.test_utils;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.time.Duration;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Utility for controlling the Bluetooth adapter from CTS test. */
public class BluetoothAdapterUtils {
    private static final String TAG = BluetoothAdapterUtils.class.getSimpleName();

    /**
     * ADAPTER_ENABLE_TIMEOUT_MS = AdapterState.BLE_START_TIMEOUT_DELAY +
     * AdapterState.BREDR_START_TIMEOUT_DELAY + (10 seconds of additional delay)
     */
    private static final Duration ADAPTER_ENABLE_TIMEOUT = Duration.ofSeconds(18);

    /**
     * ADAPTER_DISABLE_TIMEOUT_MS = AdapterState.BLE_STOP_TIMEOUT_DELAY +
     * AdapterState.BREDR_STOP_TIMEOUT_DELAY
     */
    private static final Duration ADAPTER_DISABLE_TIMEOUT = Duration.ofSeconds(5);

    /** Redefined from {@link BluetoothAdapter} because of hidden APIs */
    public static final int STATE_BLE_TURNING_ON = 14;

    public static final int STATE_BLE_TURNING_OFF = 16;

    private static final HashMap<Integer, Duration> sStateTimeouts = new HashMap<>();

    static {
        sStateTimeouts.put(BluetoothAdapter.STATE_OFF, ADAPTER_DISABLE_TIMEOUT);
        sStateTimeouts.put(BluetoothAdapter.STATE_TURNING_ON, ADAPTER_ENABLE_TIMEOUT);
        sStateTimeouts.put(BluetoothAdapter.STATE_ON, ADAPTER_ENABLE_TIMEOUT);
        sStateTimeouts.put(BluetoothAdapter.STATE_TURNING_OFF, ADAPTER_DISABLE_TIMEOUT);
        sStateTimeouts.put(STATE_BLE_TURNING_ON, ADAPTER_ENABLE_TIMEOUT);
        sStateTimeouts.put(BluetoothAdapter.STATE_BLE_ON, ADAPTER_ENABLE_TIMEOUT);
        sStateTimeouts.put(STATE_BLE_TURNING_OFF, ADAPTER_DISABLE_TIMEOUT);
    }

    private static boolean sAdapterVarsInitialized;
    private static ReentrantLock sBluetoothAdapterLock;
    private static Condition sConditionAdapterStateReached;
    private static int sDesiredState;
    private static int sAdapterState;

    /** Handles BluetoothAdapter state changes and signals when we have reached a desired state */
    private static class BluetoothAdapterReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_BLE_STATE_CHANGED.equals(action)) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                Log.d(TAG, "Bluetooth adapter state changed: " + newState);

                // Signal if the state is set to the one we are waiting on
                sBluetoothAdapterLock.lock();
                try {
                    sAdapterState = newState;
                    if (sDesiredState == newState) {
                        Log.d(TAG, "Adapter has reached desired state: " + sDesiredState);
                        sConditionAdapterStateReached.signal();
                    }
                } finally {
                    sBluetoothAdapterLock.unlock();
                }
            }
        }
    }

    /** Initialize all static state variables */
    private static void initAdapterStateVariables(Context context) {
        Log.d(TAG, "Initializing adapter state variables");
        BluetoothAdapterReceiver sAdapterReceiver = new BluetoothAdapterReceiver();
        sBluetoothAdapterLock = new ReentrantLock();
        sConditionAdapterStateReached = sBluetoothAdapterLock.newCondition();
        sDesiredState = -1;
        sAdapterState = -1;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        context.registerReceiver(sAdapterReceiver, filter);
        sAdapterVarsInitialized = true;
    }

    /**
     * Helper method to wait for the bluetooth adapter to be in a given state
     *
     * <p>Assumes all state variables are initialized. Assumes it's being run with
     * sBluetoothAdapterLock in the locked state.
     */
    private static boolean waitForAdapterStateLocked(int desiredState, BluetoothAdapter adapter)
            throws InterruptedException {
        Duration timeout = sStateTimeouts.getOrDefault(desiredState, ADAPTER_ENABLE_TIMEOUT);

        Log.d(TAG, "Waiting for adapter state " + desiredState);
        sDesiredState = desiredState;

        // Wait until we have reached the desired state
        // Handle spurious wakeup
        while (desiredState != sAdapterState) {
            if (sConditionAdapterStateReached.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                // Handle spurious wakeup
                continue;
            }
            // Handle timeout cases
            // Case 1: situation where state change occurs, but we don't receive the broadcast
            if (desiredState >= BluetoothAdapter.STATE_OFF
                    && desiredState <= BluetoothAdapter.STATE_TURNING_OFF) {
                int currentState = adapter.getState();
                Log.d(TAG, "desiredState: " + desiredState + ", currentState: " + currentState);
                return desiredState == currentState;
            } else if (desiredState == BluetoothAdapter.STATE_BLE_ON) {
                Log.d(TAG, "adapter isLeEnabled: " + adapter.isLeEnabled());
                return adapter.isLeEnabled();
            }
            // Case 2: Actual timeout
            Log.e(
                    TAG,
                    "Timeout while waiting for Bluetooth adapter state "
                            + desiredState
                            + " while current state is "
                            + sAdapterState);
            break;
        }

        Log.d(TAG, "Final state while waiting: " + sAdapterState);
        return sAdapterState == desiredState;
    }

    /** Utility method to wait on any specific adapter state */
    public static boolean waitForAdapterState(int desiredState, BluetoothAdapter adapter) {
        sBluetoothAdapterLock.lock();
        try {
            return waitForAdapterStateLocked(desiredState, adapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "waitForAdapterState(): interrupted", e);
        } finally {
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /** Enables Bluetooth to a Low Energy only mode */
    public static boolean enableBLE(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.isLeEnabled()) {
            return true;
        }

        sBluetoothAdapterLock.lock();
        try {
            Log.d(TAG, "Enabling Bluetooth low energy only mode");
            if (!bluetoothAdapter.enableBLE()) {
                Log.e(TAG, "Unable to enable Bluetooth low energy only mode");
                return false;
            }
            return waitForAdapterStateLocked(BluetoothAdapter.STATE_BLE_ON, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "enableBLE(): interrupted", e);
        } finally {
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /** Disable Bluetooth Low Energy mode */
    public static boolean disableBLE(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            return true;
        }

        sBluetoothAdapterLock.lock();
        try {
            Log.d(TAG, "Disabling Bluetooth low energy");
            bluetoothAdapter.disableBLE();
            return waitForAdapterStateLocked(BluetoothAdapter.STATE_OFF, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "disableBLE(): interrupted", e);
        } finally {
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /** Enables the Bluetooth Adapter. Return true if it is already enabled or is enabled. */
    public static boolean enableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.isEnabled()) {
            return true;
        }

        Set<String> permissionsAdopted = TestUtils.getAdoptedShellPermissions();
        String[] permissionArray = permissionsAdopted.toArray(String[]::new);
        if (UiAutomation.ALL_PERMISSIONS.equals(permissionsAdopted)) {
            permissionArray = null;
        }

        sBluetoothAdapterLock.lock();
        try {
            Log.d(TAG, "Enabling Bluetooth adapter");
            TestUtils.dropPermissionAsShellUid();
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
            bluetoothAdapter.enable();
            return waitForAdapterStateLocked(BluetoothAdapter.STATE_ON, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "enableAdapter(): interrupted", e);
        } finally {
            TestUtils.dropPermissionAsShellUid();
            TestUtils.adoptPermissionAsShellUid(permissionArray);
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }

    /** Disable the Bluetooth Adapter. Return true if it is already disabled or is disabled. */
    public static boolean disableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        return disableAdapter(bluetoothAdapter, true, context);
    }

    /**
     * Disable the Bluetooth Adapter with then option to persist the off state or not.
     *
     * <p>Returns true if the adapter is already disabled or was disabled.
     */
    public static boolean disableAdapter(
            BluetoothAdapter bluetoothAdapter, boolean persist, Context context) {
        if (!sAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            return true;
        }

        Set<String> permissionsAdopted = TestUtils.getAdoptedShellPermissions();
        String[] permissionArray = permissionsAdopted.toArray(String[]::new);
        if (UiAutomation.ALL_PERMISSIONS.equals(permissionsAdopted)) {
            permissionArray = null;
        }

        sBluetoothAdapterLock.lock();
        try {
            Log.d(TAG, "Disabling Bluetooth adapter, persist=" + persist);
            TestUtils.dropPermissionAsShellUid();
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
            bluetoothAdapter.disable(persist);
            return waitForAdapterStateLocked(BluetoothAdapter.STATE_OFF, bluetoothAdapter);
        } catch (InterruptedException e) {
            Log.w(TAG, "disableAdapter(persist=" + persist + "): interrupted", e);
        } finally {
            TestUtils.dropPermissionAsShellUid();
            TestUtils.adoptPermissionAsShellUid(permissionArray);
            sBluetoothAdapterLock.unlock();
        }
        return false;
    }
}
