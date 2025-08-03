/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothVolumeControlCallback;
import android.content.AttributionSource;

import com.android.modules.utils.SynchronousResultReceiver;

/**
 * APIs for Bluetooth Volume Control service
 *
 * @hide
 */
oneway interface IBluetoothVolumeControl {

    const int VOLUME_CONTROL_UNKNOWN_VOLUME = -1;

    /* Public API */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void connect(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void disconnect(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void getConnectedDevices(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void getDevicesMatchingConnectionStates(in int[] states, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void getConnectionState(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void setConnectionPolicy(in BluetoothDevice device, int connectionPolicy, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void getConnectionPolicy(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void isVolumeOffsetAvailable(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void getNumberOfVolumeOffsetInstances(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void setVolumeOffset(in BluetoothDevice device, int instanceId, int volumeOffset, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void setGroupVolume(int group_id, int volume, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void getGroupVolume(int group_id, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void setGroupActive(int group_id, boolean active, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void setDeviceVolume(in BluetoothDevice device, int volume, boolean isGroupOp, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void mute(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void muteGroup(int group_id, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void unmute(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void unmuteGroup(int group_id, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void registerCallback(in IBluetoothVolumeControlCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void unregisterCallback(in IBluetoothVolumeControlCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    void notifyNewRegisteredCallback(in IBluetoothVolumeControlCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
}
