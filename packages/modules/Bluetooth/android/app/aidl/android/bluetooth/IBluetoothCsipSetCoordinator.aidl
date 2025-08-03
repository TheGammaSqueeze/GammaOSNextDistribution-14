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
import android.content.AttributionSource;
import android.os.ParcelUuid;
import android.bluetooth.IBluetoothCsipSetCoordinatorLockCallback;

import com.android.modules.utils.SynchronousResultReceiver;

/**
 * APIs for Bluetooth CSIP Set Coordinator
 *
 * @hide
 */
oneway interface IBluetoothCsipSetCoordinator {
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)")
  void connect(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)")
  void disconnect(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void getConnectedDevices(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void getDevicesMatchingConnectionStates(in int[] states, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void getConnectionState(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)")
  void setConnectionPolicy(in BluetoothDevice device, int connectionPolicy, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)")
  void getConnectionPolicy(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

  /**
    * Get the list of group identifiers for the given context {@var uuid}.
    * @return group identifiers as <code>List<Integer></code>
    */
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void getAllGroupIds(in ParcelUuid uuid, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

  /**
    * Get all groups that {@var device} belongs to.
    * @return group identifiers and their context uuids as <code>Map<Integer, ParcelUuid></code>
    */
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void getGroupUuidMapByDevice(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

  /**
   * Get the number of known group members or
   * {@link android.bluetooth.IBluetoothCsipSetCoordinator.CSIS_GROUP_SIZE_UNKNOWN} if unknown.
   * @return group size
   */
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void getDesiredGroupSize(in int group_id, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

  /**
   * Lock group identified with {@var groupId}.
   * @return unique lock identifier required for unlocking
   */
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void lockGroup(int groupId, in IBluetoothCsipSetCoordinatorLockCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

  /**
   * Unlock group using {@var lockUuid} acquired through
   * {@link android.bluetooth.IBluetoothCsipSetCoordinator.lockGroup}.
   */
  @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
  void unlockGroup(in ParcelUuid lockUuid, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

  const int CSIS_GROUP_ID_INVALID = -1;
  const int CSIS_GROUP_SIZE_UNKNOWN = 1;

  const int CSIS_GROUP_LOCK_SUCCESS = 0;
  const int CSIS_GROUP_LOCK_FAILED_INVALID_GROUP = 1;
  const int CSIS_GROUP_LOCK_FAILED_GROUP_EMPTY = 2;
  const int CSIS_GROUP_LOCK_FAILED_GROUP_NOT_CONNECTED = 3;
  const int CSIS_GROUP_LOCK_FAILED_LOCKED_BY_OTHER = 4;
  const int CSIS_GROUP_LOCK_FAILED_OTHER_REASON = 5;
  const int CSIS_LOCKED_GROUP_MEMBER_LOST = 6;
}
