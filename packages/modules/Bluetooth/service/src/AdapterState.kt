/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.STATE_OFF
import kotlin.time.Duration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Thread safe class that allow waiting on a specific state change */
class BluetoothAdapterState {
    // MutableStateFlow cannot be used because it is conflated (See official doc)
    private val _uiState = MutableSharedFlow<Int>(1 /* replay only most recent value*/)

    init {
        set(STATE_OFF)
    }

    fun set(s: Int) = runBlocking { _uiState.emit(s) }

    fun get(): Int = _uiState.replayCache.get(0)

    fun oneOf(vararg states: Int): Boolean = states.contains(get())

    override fun toString() = BluetoothAdapter.nameForState(get())

    fun waitForState(timeout: java.time.Duration, vararg states: Int) = runBlocking {
        waitForState(timeout.toKotlinDuration(), *states)
    }

    suspend fun waitForState(timeout: Duration, vararg states: Int): Boolean =
        withTimeoutOrNull(timeout) { _uiState.filter { states.contains(it) }.first() } != null
}
