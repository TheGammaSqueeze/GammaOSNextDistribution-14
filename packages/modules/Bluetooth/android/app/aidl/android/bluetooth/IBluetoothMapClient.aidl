/*
 * Copyright 2016 The Android Open Source Project
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

package android.bluetooth;

import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.AttributionSource;
import android.net.Uri;

import com.android.modules.utils.SynchronousResultReceiver;

/**
 * System private API for Bluetooth MAP MCE service
 *
 * {@hide}
 */
oneway interface IBluetoothMapClient {
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void isConnected(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf = { android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_PRIVILEGED })")
    void connect(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf = { android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_PRIVILEGED })")
    void disconnect(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void getConnectedDevices(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void getDevicesMatchingConnectionStates(in int[] states, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void getConnectionState(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf = { android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_PRIVILEGED })")
    void setConnectionPolicy(in BluetoothDevice device,in int connectionPolicy, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf = { android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_PRIVILEGED })")
    void getConnectionPolicy(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf = { android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.SEND_SMS })")
    void sendMessage(in BluetoothDevice device, in Uri[] contacts, in  String message, in PendingIntent sentIntent, in PendingIntent deliveryIntent, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf = { android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.READ_SMS })")
    void getUnreadMessages(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void getSupportedFeatures(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf = { android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.READ_SMS })")
    void setMessageStatus(in BluetoothDevice device, in String handle, in int status, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
}
