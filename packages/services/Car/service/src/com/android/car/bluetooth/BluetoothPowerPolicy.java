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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.PowerComponent;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * The car power policy associated with Bluetooth. Uses the CarPowerManager power states and
 * changes the state of the Bluetooth adapter.
 */
public final class BluetoothPowerPolicy {
    private static final String TAG = CarLog.tagFor(BluetoothPowerPolicy.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    // These constants come from BluetoothManagerService.java
    private static final int BLUETOOTH_OFF = 0;
    private static final int BLUETOOTH_ON = 1;

    private final int mUserId;
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final UserManager mUserManager;

    private final ICarPowerPolicyListener mPowerPolicyListener =
            new ICarPowerPolicyListener.Stub() {
                @Override
                public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                        CarPowerPolicy accumulatedPolicy) {
                    boolean isOn = accumulatedPolicy.isComponentEnabled(PowerComponent.BLUETOOTH);
                    if (!mUserManager.isUserUnlocked(UserHandle.of(mUserId))) {
                        if (DBG) {
                            Slogf.d(TAG, "User %d is locked, ignoring bluetooth power change %s",
                                    mUserId, (isOn ? "on" : "off"));
                        }
                        return;
                    }
                    if (isOn) {
                        if (isBluetoothPersistedOn()) {
                            enableBluetooth();
                        }
                    } else {
                        // we'll turn off Bluetooth to disconnect devices and better the "off"
                        // illusion
                        if (DBG) {
                            Slogf.d(TAG, "Car power policy turns off bluetooth."
                                    + " Disable bluetooth adapter");
                        }
                        disableBluetooth();
                    }
                }
    };

    @VisibleForTesting
    public ICarPowerPolicyListener getPowerPolicyListener() {
        return mPowerPolicyListener;
    }

    /**
     * Create a new BluetoothPowerPolicy object, responsible for encapsulating the
     * default policy for when to initiate device connections given the list of prioritized devices
     * for each profile.
     *
     * @param context - The context of the creating application
     * @param userId - The user ID we're operating as
     * @return A new instance of a BluetoothPowerPolicy, or null on any error
     */
    public static BluetoothPowerPolicy create(Context context, int userId) {
        try {
            return new BluetoothPowerPolicy(context, userId);
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Create a new BluetoothPowerPolicy object, responsible for encapsulating the default policy
     * for when to enable and disable bluetooth based on the Car Power Management power states and
     * callbacks.
     *
     * @param context - The context of the creating application
     * @param userId - The user ID we're operating as
     * @return A new instance of a BluetoothPowerPolicy
     */
    private BluetoothPowerPolicy(Context context, int userId) {
        mUserId = userId;
        mContext = Objects.requireNonNull(context);
        BluetoothManager bluetoothManager =
                Objects.requireNonNull(mContext.getSystemService(BluetoothManager.class));
        mBluetoothAdapter = Objects.requireNonNull(bluetoothManager.getAdapter());
        mUserManager = mContext.getSystemService(UserManager.class);
    }

    /**
     * Setup the Bluetooth power policy
     */
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "init()");
        }
        CarPowerManagementService cpms = CarLocalServices.getService(
                CarPowerManagementService.class);
        if (cpms != null) {
            CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                    .setComponents(PowerComponent.BLUETOOTH).build();
            cpms.addPowerPolicyListener(filter, mPowerPolicyListener);
        } else {
            Slogf.w(TAG, "Cannot find CarPowerManagementService");
        }
    }

    /**
     * Clean up slate. Close the Bluetooth profile service connections and quit the state machine -
     * {@link BluetoothAutoConnectStateMachine}
     */
    public void release() {
        if (DBG) {
            Slogf.d(TAG, "release()");
        }
        CarPowerManagementService cpms =
                CarLocalServices.getService(CarPowerManagementService.class);
        if (cpms != null) {
            cpms.removePowerPolicyListener(mPowerPolicyListener);
        }
    }

    /**
     * Get the persisted Bluetooth state from Settings
     *
     * @return True if the persisted Bluetooth state is on, false otherwise
     */
    private boolean isBluetoothPersistedOn() {
        // BluetoothManagerService defaults to BLUETOOTH_ON on error as well
        return (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BLUETOOTH_ON, BLUETOOTH_ON) != BLUETOOTH_OFF);
    }

    /**
     * Turn on the Bluetooth Adapter.
     */
    private void enableBluetooth() {
        if (DBG) {
            Slogf.d(TAG, "Enable bluetooth adapter");
        }
        if (mBluetoothAdapter == null) {
            Slogf.e(TAG, "Cannot enable Bluetooth adapter. The object is null.");
            return;
        }
        mBluetoothAdapter.enable();
    }

    /**
     * Turn off the Bluetooth Adapter.
     *
     * Tells BluetoothAdapter to shut down _without_ persisting the off state as the desired state
     * of the Bluetooth adapter for next start up.
     */
    private void disableBluetooth() {
        if (DBG) {
            Slogf.d(TAG, "Disable bluetooth, do not persist state across reboot");
        }
        if (mBluetoothAdapter == null) {
            Slogf.e(TAG, "Cannot disable Bluetooth adapter. The object is null.");
            return;
        }
        mBluetoothAdapter.disable(false);
    }

    /**
     * Print the verbose status of the object
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("%s:\n", TAG);
        writer.increaseIndent();
        writer.printf("UserId: %d\n", mUserId);
        writer.decreaseIndent();
    }
}
