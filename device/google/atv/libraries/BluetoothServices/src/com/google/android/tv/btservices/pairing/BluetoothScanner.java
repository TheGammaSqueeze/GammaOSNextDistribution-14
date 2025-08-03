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

package com.google.android.tv.btservices.pairing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.function.Consumer;

public class BluetoothScanner {

    private static final String TAG = "Atv.BluetoothScanner";

    private static final int FOUND_ON_SCAN = -1;
    private static final int CONSECUTIVE_MISS_THRESHOLD = 4;
    private static final int SCAN_DELAY = 2000;
    private static final int RESTART_DELAY = 4000;

    private static class Device {
        BluetoothDevice btDevice;
        String address;
        String btName;
        int consecutiveMisses;

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("Device(addr=");
            str.append(address);
            str.append(" name=\"");
            str.append(btName);
            str.append(")");
            return str.toString();
        }

        void setNameString(String str) {
            this.btName = (str == null) ? "" : str;
        }
    }

    protected interface Listener {
        void onDeviceAdded(BluetoothDevice device);

        void onDeviceChanged(BluetoothDevice device);

        void onDeviceRemoved(BluetoothDevice device);
    }

    private final Receiver receiver;

    public BluetoothScanner(Context context) {
        receiver = new Receiver(context.getApplicationContext());
    }

    protected void startListening(Listener listener) {
        receiver.startListening(listener);
        Log.v(TAG, "startListening");
    }

    protected void stopListening(Listener listener) {
        Log.v(TAG, "stopListening receiver=" + receiver);
        receiver.stopListening(listener);
    }

    private static class Receiver extends BroadcastReceiver {

        private final Handler handler = new Handler();
        private final ArrayList<Listener> clients = new ArrayList<>();
        private final ArrayList<Device> presentDevices = new ArrayList<>();
        private final Context context;
        private final BluetoothAdapter btAdapter;
        private static boolean keepScanning;
        private boolean registered = false;
        private final Object listenerLock = new Object();

        public Receiver(Context context) {
            this.context = context;
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        public void startListening(Listener listener) {
            synchronized (listenerLock) {
                if (clients.contains(listener)) {
                    throw new RuntimeException("Listener already registered: " + listener);
                }
                clients.add(listener);
            }
            presentDevices.clear();
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothDevice.ACTION_UUID);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            context.registerReceiver(this, filter);
            registered = true;
            keepScanning = true;

            presentDevices.stream().forEach(client -> listener.onDeviceAdded(client.btDevice));

            handler.removeCallbacks(stopTask);
            handler.removeCallbacks(scanTask);
            scanNow();
        }

        public void stopListening(Listener listener) {
            int size = 0;
            synchronized (listenerLock) {
                clients.removeIf(client -> client == listener);
                size = clients.size();
            }
            if (size == 0) {
                handler.post(stopTask);
                presentDevices.clear();
            }
        }

        private void scanNow() {
            if (btAdapter.isDiscovering()) {
                btAdapter.cancelDiscovery();
            }

            btAdapter.startDiscovery();
        }

        private void stopNow() {
            int size;
            synchronized (listenerLock) {
                size = clients.size();
            }
            if (size == 0) {
                handler.removeCallbacks(scanTask);
                handler.removeCallbacks(restartDueToInactivity);
                handler.removeCallbacks(stopTask);

                if (btAdapter != null) {
                    btAdapter.cancelDiscovery();
                }

                keepScanning = false;

                if (BluetoothAdapter.getDefaultAdapter().isEnabled() && registered) {
                    context.unregisterReceiver(Receiver.this);
                    registered = false;
                }
            }
        }

        private final Runnable stopTask =
                () -> {
                    synchronized (listenerLock) {
                        if (clients.size() != 0) {
                            throw new RuntimeException(
                                    "mStopTask running with mListeners.size=" + clients.size());
                        }
                    }
                    stopNow();
                };

        private void removeScanTask() {
            handler.removeCallbacks(scanTask);
            handler.removeCallbacks(restartDueToInactivity);
        }

        private final Runnable scanTask =
                () -> {
                    Log.v(TAG, "scan task running");
                    removeScanTask();
                    scanNow();
                };

        private final Runnable restartDueToInactivity =
                () -> {
                    Log.v(TAG, "restart scanning");
                    scanTask.run();
                };

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                final BluetoothDevice btDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                final String address = btDevice.getAddress();
                final String name = btDevice.getName();

                if (address == null || name == null) {
                    return;
                }

                // We'll keep putting this task into the handler. If no devices appear in
                // RESTART_DELAY ms, we restart the scan instead of waiting for it to complete. This
                // is because all devices typically show up within the first few seconds.
                handler.removeCallbacks(restartDueToInactivity);
                handler.postDelayed(restartDueToInactivity, RESTART_DELAY);

                Device device =
                        presentDevices.stream().filter(d ->
                                address.equals(d.address)).findFirst().orElse(null);

                if (device == null) {
                    Log.v(TAG, "Device is a new device.");
                    device = new Device();
                    device.btDevice = btDevice;
                    device.address = address;
                    device.consecutiveMisses = -1;
                    device.setNameString(name);
                    presentDevices.add(device);
                    signalClients(client -> client.onDeviceAdded(btDevice));
                } else {
                    Log.v(TAG, "Device is an existing device.");
                    // Existing device: update miss count.
                    device.consecutiveMisses = FOUND_ON_SCAN;
                    if (device.btName == null || !device.btName.equals(name)) {
                        device.setNameString(name);
                        signalClients(client -> client.onDeviceChanged(btDevice));
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Clear any devices that have disappeared since the last scan completed
                final int n = presentDevices.size();
                for (int i = n - 1; i >= 0; i--) {
                    final Device device = presentDevices.get(i);
                    if (device.consecutiveMisses < 0) {
                        // -1 means found on this scan, raise to 0 for next time
                        Log.v(TAG, device.address + " -- Found");
                        device.consecutiveMisses = 0;
                    } else if (device.consecutiveMisses >= CONSECUTIVE_MISS_THRESHOLD) {
                        // Too many failures
                        Log.v(TAG, device.address + " -- Removing");
                        presentDevices.remove(i);
                        signalClients(client -> client.onDeviceRemoved(device.btDevice));
                    } else {
                        // Didn't see it this time, but not ready to delete it yet
                        device.consecutiveMisses++;
                        Log.v(TAG, device.address
                                + " -- Missed consecutiveMisses=" + device.consecutiveMisses);
                    }
                }

                if (keepScanning) {
                    handler.postDelayed(scanTask, SCAN_DELAY);
                }
            }
        }

        private void signalClients(Consumer<Listener> doThis) {
            synchronized (listenerLock) {
                clients.stream().forEach(doThis);
            }
        }
    }
}
