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

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.VehicleSeatOccupancyState;
import android.car.builtin.util.Slogf;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.CarDrivingStateService;
import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarPropertyService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.util.List;
import java.util.Objects;

/**
 * A Bluetooth Device Connection policy that is specific to the use cases of a Car. Contains policy
 * for deciding when to trigger connection and disconnection events.
 */
public final class BluetoothDeviceConnectionPolicy {
    private static final String TAG = CarLog.tagFor(BluetoothDeviceConnectionPolicy.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final int mUserId;
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final CarBluetoothService mCarBluetoothService;
    private final CarServicesHelper mCarHelper;

    @Nullable
    private Context mUserContext;

    /**
     * A BroadcastReceiver that listens specifically for actions related to the profile we're
     * tracking and uses them to update the status.
     *
     * On BluetoothAdapter.ACTION_STATE_CHANGED:
     * If the adapter is going into the ON state, then commit trigger auto connection.
     */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (DBG) {
                    Slogf.d(TAG, "Bluetooth Adapter state changed: %s",
                            BluetoothUtils.getAdapterStateName(state));
                }
                if (state == BluetoothAdapter.STATE_ON) {
                    connectDevices();
                }
            }
        }
    }

    private final BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;

    /**
     * A helper class to interact with the VHAL and the rest of the car.
     */
    final class CarServicesHelper {
        private final CarPropertyService mCarPropertyService;
        private final CarDrivingStateService mCarDrivingStateService;

        // Location of the driver's seat, e.g., left or right side.
        private final int mDriverSeat;

        CarServicesHelper() {
            mCarPropertyService = CarLocalServices.getService(CarPropertyService.class);
            if (mCarPropertyService == null) {
                Slogf.w(TAG, "Cannot find CarPropertyService");
            }
            mDriverSeat = getDriverSeatLocationFromVhal();
            mCarDrivingStateService = CarLocalServices.getService(CarDrivingStateService.class);
            if (mCarDrivingStateService == null) {
                Slogf.w(TAG, "Cannot find mCarDrivingStateService");
            }
        }

        /**
         * Set up vehicle event listeners. Remember to call {@link release()} when done.
         */
        public void init() {
            if (mCarPropertyService != null) {
                mCarPropertyService.registerListenerSafe(VehiclePropertyIds.SEAT_OCCUPANCY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE, mSeatOnOccupiedListener);
            }
        }

        public void release() {
            if (mCarPropertyService != null) {
                mCarPropertyService.unregisterListenerSafe(VehiclePropertyIds.SEAT_OCCUPANCY,
                        mSeatOnOccupiedListener);
            }
        }

        /**
         * A {@code ICarPropertyEventListener} that triggers the auto-connection process when
         * {@code SEAT_OCCUPANCY} is {@code OCCUPIED}.
         */
        private final ICarPropertyEventListener mSeatOnOccupiedListener =
                new ICarPropertyEventListener.Stub() {
                    @Override
                    public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                        for (CarPropertyEvent event : events) {
                            onSeatOccupancyCarPropertyEvent(event);
                        }
                    }
                };

        /**
         * Acts on {@link CarPropertyEvent} events marked with
         * {@link CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE} and marked with {@link
         * VehiclePropertyIds.SEAT_OCCUPANCY} by calling {@link connectDevices}.
         * <p>
         * Default implementation filters on driver's seat only, but can change to trigger on
         * any front row seat, or any seat in the car.
         * <p>
         * Default implementation also restricts this trigger to when the car is in the
         * parked state, to discourage drivers from exploiting to connect while driving, and to
         * also filter out spurious seat sensor signals while driving.
         * <p>
         * This method does nothing if the event parameter is {@code null}.
         *
         * @param event - The {@link CarPropertyEvent} to be handled.
         */
        private void onSeatOccupancyCarPropertyEvent(CarPropertyEvent event) {
            if ((event == null)
                    || (event.getEventType() != CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE)) {
                return;
            }
            CarPropertyValue value = event.getCarPropertyValue();
            if (DBG) {
                Slogf.d(TAG, "Car property changed: %s", value);
            }
            if (mBluetoothAdapter.isEnabled()
                    && (value.getPropertyId() == VehiclePropertyIds.SEAT_OCCUPANCY)
                    && ((int) value.getValue() == VehicleSeatOccupancyState.OCCUPIED)
                    && (value.getAreaId() == mDriverSeat)
                    && isParked()) {
                connectDevices();
            }
        }

        /**
         * Gets the location of the driver's seat (e.g., front-left, front-right) from the VHAL.
         * <p>
         * Default implementation sets the driver's seat to front-left if mCarPropertyService is
         * not found.
         * <p>
         * Note, comments for {@link CarPropertyManager#getIntProperty(int, int)} indicate it may
         * take a couple of seconds to complete, whereas there are no such comments for
         * {@link CarPropertyService#getPropertySafe(int, int)}, but we assume there is also similar
         * latency in querying VHAL properties.
         *
         * @return An {@code int} representing driver's seat location.
         */
        private int getDriverSeatLocationFromVhal() {
            int defaultLocation = VehicleAreaSeat.SEAT_ROW_1_LEFT;

            if (mCarPropertyService == null) {
                return defaultLocation;
            }
            CarPropertyValue value = mCarPropertyService.getPropertySafe(
                    VehiclePropertyIds.INFO_DRIVER_SEAT, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
            if (value == null) {
                // Distinguish between two possible causes for null, based on
                // {@code mConfigs.get(prop)} in {@link CarPropertyService#getProperty} and
                // {@link CarPropertyService#getPropertyConfigList}
                List<CarPropertyConfig> availableProp = mCarPropertyService.getPropertyConfigList(
                        new int[]{VehiclePropertyIds.INFO_DRIVER_SEAT})
                        .carPropertyConfigList.getConfigs();
                if (availableProp.isEmpty() || availableProp.get(0) == null) {
                    if (DBG) {
                        Slogf.d(TAG, "Driver seat location property is not in config list.");
                    }
                } else {
                    if (DBG) {
                        Slogf.d(TAG, "Driver seat location property is not ready yet.");
                    }
                }
                return defaultLocation;
            }
            return (int) value.getValue();
        }

        public int getDriverSeatLocation() {
            return mDriverSeat;
        }

        /**
         * Returns {@code true} if the car is in parked gear.
         * <p>
         * We are being conservative and only want to trigger when car is in parked state. Extending
         * this conservative approach, we default return false if {@code mCarDrivingStateService}
         * is not found, or if we otherwise can't get the value.
         */
        public boolean isParked() {
            if (mCarDrivingStateService == null) {
                return false;
            }
            CarDrivingStateEvent event = mCarDrivingStateService.getCurrentDrivingState();
            if (event == null) {
                return false;
            }
            return event.eventValue == CarDrivingStateEvent.DRIVING_STATE_PARKED;
        }
    }

    /**
     * Create a new BluetoothDeviceConnectionPolicy object, responsible for encapsulating the
     * default policy for when to initiate device connections given the list of prioritized devices
     * for each profile.
     *
     * @param context - The context of the creating application
     * @param userId - The user ID we're operating as
     * @param bluetoothService - A reference to CarBluetoothService so we can connect devices
     * @return A new instance of a BluetoothProfileDeviceManager, or null on any error
     */
    public static BluetoothDeviceConnectionPolicy create(Context context, int userId,
            CarBluetoothService bluetoothService) {
        try {
            return new BluetoothDeviceConnectionPolicy(context, userId, bluetoothService);
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Create a new BluetoothDeviceConnectionPolicy object, responsible for encapsulating the
     * default policy for when to initiate device connections given the list of prioritized devices
     * for each profile.
     *
     * @param context - The context of the creating application
     * @param userId - The user ID we're operating as
     * @param bluetoothService - A reference to CarBluetoothService so we can connect devices
     * @return A new instance of a BluetoothProfileDeviceManager
     */
    private BluetoothDeviceConnectionPolicy(Context context, int userId,
            CarBluetoothService bluetoothService) {
        mUserId = userId;
        mContext = Objects.requireNonNull(context);
        mCarBluetoothService = bluetoothService;
        mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
        BluetoothManager bluetoothManager =
                Objects.requireNonNull(mContext.getSystemService(BluetoothManager.class));
        mBluetoothAdapter = Objects.requireNonNull(bluetoothManager.getAdapter());
        mCarHelper = new CarServicesHelper();
    }

    /**
     * Setup the Bluetooth profile service connections and Vehicle Event listeners.
     * and start the state machine -{@link BluetoothAutoConnectStateMachine}
     */
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "init()");
        }
        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        UserHandle currentUser = UserHandle.of(ActivityManager.getCurrentUser());
        mUserContext = mContext.createContextAsUser(currentUser, /* flags= */ 0);
        mUserContext.registerReceiver(mBluetoothBroadcastReceiver, profileFilter);
        mCarHelper.init();

        // Since we do this only on start up and on user switch, it's safe to kick off a connect on
        // init. If we have a connect in progress, this won't hurt anything. If we already have
        // devices connected, this will add on top of it. We _could_ enter this from a crash
        // recovery, but that would at worst cause more devices to connect and wouldn't change the
        // existing devices.
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            // CarPowerManager doesn't provide a getState() or that would go here too.
            connectDevices();
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
        if (mBluetoothBroadcastReceiver != null && mUserContext != null) {
            mUserContext.unregisterReceiver(mBluetoothBroadcastReceiver);
            mUserContext = null;
        }
        mCarHelper.release();
    }

    /**
     * Tell each Profile device manager that its time to begin auto connecting devices
     */
    public void connectDevices() {
        if (DBG) {
            Slogf.d(TAG, "Connect devices for each profile");
        }
        mCarBluetoothService.connectDevices();
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
