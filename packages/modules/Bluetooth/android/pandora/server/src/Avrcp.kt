/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.pandora

import android.bluetooth.BluetoothManager
import android.content.Context
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import pandora.AVRCPGrpc.AVRCPImplBase
import pandora.AvrcpProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Avrcp(val context: Context) : AVRCPImplBase(), Closeable {
    private val TAG = "PandoraAvrcp"

    private val scope: CoroutineScope

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    init {
        // Init the CoroutineScope
        scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    }

    override fun close() {
        // Deinit the CoroutineScope
        scope.cancel()
    }
}
