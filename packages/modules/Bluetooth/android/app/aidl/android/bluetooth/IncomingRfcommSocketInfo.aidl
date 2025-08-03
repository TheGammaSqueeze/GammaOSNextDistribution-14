/*
 * Copyright 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelFileDescriptor;

/**
 * Output parameters for IBluetooth.retrievePendingSocketForServiceRecord
 *
 * @hide
 */
parcelable IncomingRfcommSocketInfo {
    /** The underlying file descriptor for the socket. */
    ParcelFileDescriptor pfd;
    /** The bluetooth device info for the remote device that this socket is connected to. */
    BluetoothDevice bluetoothDevice;
    /**
     * Status info on whether or not the socket was retrieved successfully. See
     * BluetoothAdapter.RfcommListenerResult for possible values.
     */
    int status;
}

