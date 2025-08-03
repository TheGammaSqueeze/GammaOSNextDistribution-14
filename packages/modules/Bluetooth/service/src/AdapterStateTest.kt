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
package com.android.server.bluetooth.test

import android.bluetooth.BluetoothAdapter.STATE_OFF
import com.android.server.bluetooth.BluetoothAdapterState
import com.android.server.bluetooth.Log
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class BluetoothAdapterStateTest {

    lateinit var mState: BluetoothAdapterState
    @JvmField @Rule val testName = TestName()

    @Before
    fun setUp() {
        Log.i("BluetoothAdapterStateTest", "\t--> setup of " + testName.getMethodName())
        mState = BluetoothAdapterState()
    }

    @Test
    fun init_isStateOff() {
        Log.d("BluetoothAdapterStateTest", "Initial state is " + mState)
        assertThat(mState.get()).isEqualTo(STATE_OFF)
    }

    @Test
    fun get_afterBusy_returnLastValue() {
        val max = 10
        for (i in 0..max) mState.set(i)
        assertThat(mState.get()).isEqualTo(max)
    }

    @Test
    fun immediateReturn_whenStateIsAlreadyCorrect() = runTest {
        val state = 10
        mState.set(state)
        assertThat(runBlocking { mState.waitForState(100.days, state) }).isTrue()
    }

    @Test fun expectTimeout() = runTest { assertThat(mState.waitForState(100.days, -1)).isFalse() }

    @Test
    fun expectTimeout_CalledJavaApi() = runTest {
        assertThat(mState.waitForState(java.time.Duration.ofMillis(10), -1)).isFalse()
    }

    @Test
    fun setState_whileWaiting() = runTest {
        val state = 42
        val waiter = async { mState.waitForState(100.days, state) }
        mState.set(state)
        assertThat(waiter.await()).isTrue()
    }

    @Test
    fun concurrentWaiter_NoStateMissed() = runTest {
        val state0 = 42
        val state1 = 50
        val state2 = 65
        val waiter0 =
            async(start = CoroutineStart.UNDISPATCHED) { mState.waitForState(100.days, state0) }
        val waiter1 =
            async(start = CoroutineStart.UNDISPATCHED) { mState.waitForState(100.days, state1) }
        val waiter2 =
            async(start = CoroutineStart.UNDISPATCHED) { mState.waitForState(100.days, state2) }
        val waiter3 =
            async(start = CoroutineStart.UNDISPATCHED) { mState.waitForState(100.days, -1) }
        mState.set(state0)
        yield()
        mState.set(state1)
        yield()
        mState.set(state2)
        assertThat(waiter0.await()).isTrue()
        assertThat(waiter1.await()).isTrue()
        assertThat(waiter2.await()).isTrue()
        assertThat(waiter3.await()).isFalse()
    }

    @Test
    fun expectTimeout_waitAfterOverride() = runTest {
        val state0 = 42
        val state1 = 50
        mState.set(state0)
        yield()
        mState.set(state1)
        val waiter =
            async(start = CoroutineStart.UNDISPATCHED) { mState.waitForState(100.days, state0) }
        assertThat(waiter.await()).isFalse()
    }

    @Test
    fun oneOf_expectMatch() {
        val state0 = 42
        val state1 = 50
        mState.set(state0)
        assertThat(mState.oneOf(state0, state1)).isTrue()
    }

    @Test
    fun oneOf_expectNotMatch() {
        val state0 = 42
        val state1 = 50
        val state2 = 65
        mState.set(state0)
        assertThat(mState.oneOf(state1, state2)).isFalse()
    }
}
