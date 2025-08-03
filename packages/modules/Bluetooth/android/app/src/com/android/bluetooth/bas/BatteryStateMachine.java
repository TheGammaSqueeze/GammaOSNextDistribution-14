/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.bas;

import static android.bluetooth.BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
import static android.bluetooth.BluetoothDevice.PHY_LE_1M_MASK;
import static android.bluetooth.BluetoothDevice.PHY_LE_2M_MASK;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.Scanner;
import java.util.UUID;

/**
 * It manages Battery service of a BLE device
 */
public class BatteryStateMachine extends StateMachine {
    private static final boolean DBG = false;
    private static final String TAG = "BatteryStateMachine";

    static final UUID GATT_BATTERY_SERVICE_UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    static final UUID GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;
    static final int CONNECTION_STATE_CHANGED = 3;
    private static final int CONNECT_TIMEOUT = 201;

    // NOTE: the value is not "final" - it is modified in the unit tests
    @VisibleForTesting
    static int sConnectTimeoutMs = 30000;        // 30s

    private Disconnected mDisconnected;
    private Connecting mConnecting;
    private Connected mConnected;
    private Disconnecting mDisconnecting;
    private int mLastConnectionState = BluetoothProfile.STATE_DISCONNECTED;

    WeakReference<BatteryService> mServiceRef;

    BluetoothGatt mBluetoothGatt;
    GattCallback mGattCallback;
    final BluetoothDevice mDevice;

    BatteryStateMachine(BluetoothDevice device, BatteryService service, Looper looper) {
        super(TAG, looper);
        mDevice = device;
        mServiceRef = new WeakReference<>(service);

        mDisconnected = new Disconnected();
        mConnecting = new Connecting();
        mConnected = new Connected();
        mDisconnecting = new Disconnecting();

        addState(mDisconnected);
        addState(mConnecting);
        addState(mDisconnecting);
        addState(mConnected);

        setInitialState(mDisconnected);
    }

    static BatteryStateMachine make(BluetoothDevice device, BatteryService service, Looper looper) {
        Log.i(TAG, "make for device " + device);
        BatteryStateMachine sm = new BatteryStateMachine(device, service, looper);
        sm.start();
        return sm;
    }

    /**
     * Quits the state machine
     */
    public void doQuit() {
        log("doQuit for device " + mDevice);
        quitNow();
    }

    /**
     * Cleans up the resources the state machine held.
     */
    public void cleanup() {
        log("cleanup for device " + mDevice);
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mGattCallback = null;
        }
    }

    BluetoothDevice getDevice() {
        return mDevice;
    }

    synchronized boolean isConnected() {
        return getCurrentState() == mConnected;
    }

    private static String messageWhatToString(int what) {
        switch (what) {
            case CONNECT:
                return "CONNECT";
            case DISCONNECT:
                return "DISCONNECT";
            case CONNECTION_STATE_CHANGED:
                return "CONNECTION_STATE_CHANGED";
            case CONNECT_TIMEOUT:
                return "CONNECT_TIMEOUT";
            default:
                break;
        }
        return Integer.toString(what);
    }

    private static String profileStateToString(int state) {
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return "DISCONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothProfile.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "DISCONNECTING";
            default:
                break;
        }
        return Integer.toString(state);
    }

    /**
     * Dumps battery state machine state.
     */
    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mDevice: " + mDevice);
        ProfileService.println(sb, "  StateMachine: " + this);
        ProfileService.println(sb, "  BluetoothGatt: " + mBluetoothGatt);
        // Dump the state machine logs
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        super.dump(new FileDescriptor(), printWriter, new String[]{});
        printWriter.flush();
        stringWriter.flush();
        ProfileService.println(sb, "  StateMachineLog:");
        Scanner scanner = new Scanner(stringWriter.toString());
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            ProfileService.println(sb, "    " + line);
        }
        scanner.close();
    }

    @BluetoothProfile.BtProfileState
    int getConnectionState() {
        String currentState = getCurrentState().getName();
        switch (currentState) {
            case "Disconnected":
                return BluetoothProfile.STATE_DISCONNECTED;
            case "Connecting":
                return BluetoothProfile.STATE_CONNECTING;
            case "Connected":
                return BluetoothProfile.STATE_CONNECTED;
            case "Disconnecting":
                return BluetoothProfile.STATE_DISCONNECTING;
            default:
                Log.e(TAG, "Bad currentState: " + currentState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    void dispatchConnectionStateChanged(int fromState, int toState) {
        log("Connection state " + mDevice + ": " + profileStateToString(fromState)
                + "->" + profileStateToString(toState));

        BatteryService service = mServiceRef.get();
        if (service != null) {
            service.handleConnectionStateChanged(this, fromState, toState);
        }
    }

    /**
     * Connects to the GATT server of the device.
     *
     * @return {@code true} if it successfully connects to the GATT server.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean connectGatt() {
        BatteryService service = mServiceRef.get();
        if (service == null) {
            return false;
        }

        if (mGattCallback == null) {
            mGattCallback = new GattCallback();
        }
        if (mBluetoothGatt != null) {
            Log.w(TAG, "Trying connectGatt with existing BluetoothGatt instance.");
            mBluetoothGatt.close();
        }
        mBluetoothGatt = mDevice.connectGatt(service, /*autoConnect=*/false,
                mGattCallback, TRANSPORT_LE, /*opportunistic=*/true,
                PHY_LE_1M_MASK | PHY_LE_2M_MASK, getHandler());
        return mBluetoothGatt != null;
    }

    @VisibleForTesting
    void updateBatteryLevel(byte[] value) {
        if (value.length == 0) {
            return;
        }
        int batteryLevel = value[0] & 0xFF;

        BatteryService service = mServiceRef.get();
        if (service != null) {
            service.handleBatteryChanged(mDevice, batteryLevel);
        }
    }

    @VisibleForTesting
    void resetBatteryLevel() {
        BatteryService service = mServiceRef.get();
        if (service != null) {
            service.handleBatteryChanged(mDevice, BATTERY_LEVEL_UNKNOWN);
        }
    }

    @Override
    protected void log(String msg) {
        if (DBG) {
            super.log(msg);
        }
    }

    static void log(String tag, String msg) {
        if (DBG) {
            Log.d(tag, msg);
        }
    }

    @VisibleForTesting
    class Disconnected extends State {
        private static final String TAG = "BASM_Disconnected";

        @Override
        public void enter() {
            log(TAG, "Enter (" + mDevice + "): " + messageWhatToString(
                        getCurrentMessage().what));

            if (mBluetoothGatt != null) {
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }

            if (mLastConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
                // Don't broadcast during startup
                dispatchConnectionStateChanged(mLastConnectionState,
                        BluetoothProfile.STATE_DISCONNECTED);
            }
        }

        @Override
        public void exit() {
            log(TAG, "Exit (" + mDevice + "): " + messageWhatToString(
                    getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_DISCONNECTED;
        }

        @Override
        public boolean processMessage(Message message) {
            log(TAG, "Process message(" + mDevice + "): " + messageWhatToString(
                    message.what));

            BatteryService service = mServiceRef.get();
            switch (message.what) {
                case CONNECT:
                    log(TAG, "Connecting to " + mDevice);
                    if (service != null && service.canConnect(mDevice)) {
                        if (connectGatt()) {
                            transitionTo(mConnecting);
                        } else {
                            Log.w(TAG, "Battery connecting request rejected due to "
                                    + "GATT connection rejection: " + mDevice);
                        }
                    } else {
                        // Reject the request and stay in Disconnected state
                        Log.w(TAG, "Battery connecting request rejected: "
                                + mDevice);
                    }
                    break;
                case DISCONNECT:
                    Log.w(TAG, "DISCONNECT ignored: " + mDevice);
                    break;
                case CONNECTION_STATE_CHANGED:
                    processConnectionEvent(message.arg1);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in Disconnected state
        private void processConnectionEvent(int state) {
            switch (state) {
                case BluetoothGatt.STATE_DISCONNECTED:
                    Log.w(TAG, "Ignore Battery DISCONNECTED event: " + mDevice);
                    break;
                default:
                    Log.e(TAG, "Incorrect state: " + state + " device: " + mDevice);
                    break;
            }
        }
    }

    @VisibleForTesting
    class Connecting extends State {
        private static final String TAG = "BASM_Connecting";
        @Override
        public void enter() {
            log(TAG, "Enter (" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            dispatchConnectionStateChanged(mLastConnectionState, BluetoothProfile.STATE_CONNECTING);
        }

        @Override
        public void exit() {
            log(TAG, "Exit (" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_CONNECTING;
        }

        @Override
        public boolean processMessage(Message message) {
            log(TAG, "process message(" + mDevice + "): "
                    + messageWhatToString(message.what));

            switch (message.what) {
                case CONNECT:
                    Log.w(TAG, "CONNECT ignored: " + mDevice);
                    break;
                case CONNECT_TIMEOUT:
                    Log.e(TAG, "Connection timeout unexpected: " + mDevice);
                    break;
                case DISCONNECT:
                    log(TAG, "Connection canceled to " + mDevice);
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                    }
                    // As we're not yet connected we don't need to wait for callbacks.
                    transitionTo(mDisconnected);
                    break;
                case CONNECTION_STATE_CHANGED:
                    processConnectionEvent(message.arg1);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in Connecting state
        private void processConnectionEvent(int state) {
            switch (state) {
                case BluetoothGatt.STATE_DISCONNECTED:
                    Log.w(TAG, "Device disconnected: " + mDevice);
                    transitionTo(mDisconnected);
                    break;
                case BluetoothGatt.STATE_CONNECTED:
                    transitionTo(mConnected);
                    break;
                default:
                    Log.e(TAG, "Incorrect state: " + state);
                    break;
            }
        }
    }

    @VisibleForTesting
    class Disconnecting extends State {
        private static final String TAG = "BASM_Disconnecting";
        @Override
        public void enter() {
            log(TAG, "Enter (" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            sendMessageDelayed(CONNECT_TIMEOUT, sConnectTimeoutMs);
            dispatchConnectionStateChanged(mLastConnectionState,
                    BluetoothProfile.STATE_DISCONNECTING);
        }

        @Override
        public void exit() {
            log(TAG, "Exit (" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            mLastConnectionState = BluetoothProfile.STATE_DISCONNECTING;
            removeMessages(CONNECT_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message message) {
            log(TAG, "Process message(" + mDevice + "): "
                    + messageWhatToString(message.what));

            switch (message.what) {
                //TODO: Check if connect while disconnecting is okay.
                // It is related to CONNECT_TIMEOUT as well.
                case CONNECT:
                    Log.w(TAG, "CONNECT ignored: " + mDevice);
                    break;
                case DISCONNECT:
                    Log.w(TAG, "DISCONNECT ignored: " + mDevice);
                    break;
                case CONNECT_TIMEOUT:
                    Log.w(TAG, "Connection timeout: " + mDevice);
                    transitionTo(mDisconnected);
                    break;
                case CONNECTION_STATE_CHANGED:
                    processConnectionEvent(message.arg1);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in Disconnecting state
        private void processConnectionEvent(int state) {
            switch (state) {
                case BluetoothGatt.STATE_DISCONNECTED:
                    Log.i(TAG, "Disconnected: " + mDevice);
                    transitionTo(mDisconnected);
                    break;
                case BluetoothGatt.STATE_CONNECTED: {
                    // Reject the connection and stay in Disconnecting state
                    Log.w(TAG, "Incoming Battery connected request rejected: "
                            + mDevice);
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                    } else {
                        transitionTo(mDisconnected);
                    }
                    break;
                }
                default:
                    Log.e(TAG, "Incorrect state: " + state);
                    break;
            }
        }
    }

    @VisibleForTesting
    class Connected extends State {
        private static final String TAG = "BASM_Connected";
        @Override
        public void enter() {
            log(TAG, "Enter (" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            dispatchConnectionStateChanged(mLastConnectionState, BluetoothProfile.STATE_CONNECTED);

            if (mBluetoothGatt != null) {
                mBluetoothGatt.discoverServices();
            }
        }

        @Override
        public void exit() {
            log(TAG, "Exit (" + mDevice + "): "
                    + messageWhatToString(getCurrentMessage().what));
            // Reset the battery level only after connected
            resetBatteryLevel();
            mLastConnectionState = BluetoothProfile.STATE_CONNECTED;
        }

        @Override
        public boolean processMessage(Message message) {
            log(TAG, "Process message(" + mDevice + "): "
                    + messageWhatToString(message.what));

            switch (message.what) {
                case CONNECT:
                    Log.w(TAG, "CONNECT ignored: " + mDevice);
                    break;
                case DISCONNECT:
                    log(TAG, "Disconnecting from " + mDevice);
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                        transitionTo(mDisconnecting);
                    } else {
                        transitionTo(mDisconnected);
                    }
                    break;
                case CONNECTION_STATE_CHANGED:
                    processConnectionEvent(message.arg1);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        // in Connected state
        private void processConnectionEvent(int state) {
            switch (state) {
                case BluetoothGatt.STATE_DISCONNECTED:
                    Log.i(TAG, "Disconnected from " + mDevice);
                    transitionTo(mDisconnected);
                    break;
                case BluetoothGatt.STATE_CONNECTED:
                    Log.w(TAG, "Ignore CONNECTED event: " + mDevice);
                    break;
                default:
                    Log.e(TAG, "Connection State Device: " + mDevice + " bad state: " + state);
                    break;
            }
        }
    }

    final class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            sendMessage(CONNECTION_STATE_CHANGED, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "No gatt service");
                return;
            }

            final BluetoothGattService batteryService = gatt.getService(GATT_BATTERY_SERVICE_UUID);
            if (batteryService == null) {
                Log.e(TAG, "No battery service");
                return;
            }

            final BluetoothGattCharacteristic batteryLevel =
                    batteryService.getCharacteristic(GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID);
            if (batteryLevel == null) {
                Log.e(TAG, "No battery level characteristic");
                return;
            }

            // This may not trigger onCharacteristicRead if CCCD is already set but then
            // onCharacteristicChanged will be triggered soon.
            gatt.readCharacteristic(batteryLevel);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, byte[] value) {
            if (GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                updateBatteryLevel(value);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Read characteristic failure on " + gatt + " " + characteristic);
                return;
            }

            if (GATT_BATTERY_LEVEL_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                updateBatteryLevel(value);
                BluetoothGattDescriptor cccd =
                        characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
                if (cccd != null) {
                    gatt.setCharacteristicNotification(characteristic, /*enable=*/true);
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    Log.w(TAG, "No CCCD for battery level characteristic, "
                            + "it won't be notified");
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Failed to write descriptor " + descriptor.getUuid());
            }
        }
    }
}
