/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wallpaper.picker.broadcast

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.wallpaper.picker.di.modules.ConcurrencyModule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(RobolectricTestRunner::class)
class BroadcastDispatcherTest {

    private lateinit var mContext: Context

    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter
    private lateinit var broadcastDispatcher: BroadcastDispatcher

    private lateinit var mainExecutor: Executor

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        val backgroundRunningLooper = provideBroadcastRunningLooper()
        mainExecutor = provideBroadcastRunningExecutor(backgroundRunningLooper)
        broadcastReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {}
            }
        broadcastDispatcher =
            BroadcastDispatcher(mContext, mainExecutor, backgroundRunningLooper, mainExecutor)
    }

    @Test
    fun testBroadcastFlow_emitsValues() = runTest {
        intentFilter = IntentFilter("ACTION_TEST")
        val testIntent = Intent("ACTION_TEST")

        val flow =
            broadcastDispatcher.broadcastFlow(intentFilter) { intent, receiver ->
                intent to receiver
            }
        val collectedValues = mutableListOf<Pair<Intent, BroadcastReceiver>>()
        val job = launch { flow.collect { collectedValues.add(it) } }

        // Waits for the flow collection coroutine to start and collect any immediate emissions
        advanceUntilIdle()

        val shadowApplication =
            Shadows.shadowOf(ApplicationProvider.getApplicationContext() as Application)
        val receivers = shadowApplication.registeredReceivers
        val capturedReceiver = receivers.find { it.broadcastReceiver is BroadcastReceiver }
        assertThat(capturedReceiver).isNotNull()
        capturedReceiver?.let { collectedValues.add(testIntent to it.broadcastReceiver) }

        // Processes any additional tasks that may have been scheduled as a result of
        // adding to collectedValues
        advanceUntilIdle()

        val expectedValues = listOf(testIntent to capturedReceiver?.broadcastReceiver)
        assertThat(expectedValues).isEqualTo(collectedValues)
        job.cancel()
    }

    @Test
    fun testRegisterReceiver() {
        intentFilter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)

        broadcastDispatcher.registerReceiver(broadcastReceiver, intentFilter)

        val shadowApplication =
            Shadows.shadowOf(ApplicationProvider.getApplicationContext() as Application)
        val receivers = shadowApplication.registeredReceivers
        val isRegistered = receivers.any { it.broadcastReceiver == broadcastReceiver }
        assertThat(isRegistered).isEqualTo(true)
    }

    @Test
    fun testUnregisterReceiver() {
        intentFilter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)

        broadcastDispatcher.registerReceiver(broadcastReceiver, intentFilter)
        broadcastDispatcher.unregisterReceiver(broadcastReceiver)

        val shadowApplication =
            Shadows.shadowOf(ApplicationProvider.getApplicationContext() as Application)
        val receivers = shadowApplication.registeredReceivers
        val isUnregistered = receivers.none { it.broadcastReceiver == broadcastReceiver }
        assertThat(isUnregistered).isEqualTo(true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFilterMustNotContainDataType() {
        val testFilter = IntentFilter(TEST_ACTION).apply { addDataType(TEST_TYPE) }

        broadcastDispatcher.registerReceiver(broadcastReceiver, testFilter)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFilterMustNotSetPriority() {
        val testFilter =
            IntentFilter(TEST_ACTION).apply { priority = IntentFilter.SYSTEM_HIGH_PRIORITY }

        broadcastDispatcher.registerReceiver(broadcastReceiver, testFilter)
    }

    private fun provideBroadcastRunningLooper(): Looper {
        return HandlerThread(
                "BroadcastRunning",
                Process.THREAD_PRIORITY_BACKGROUND,
            )
            .apply {
                start()
                looper.setSlowLogThresholdMs(
                    BROADCAST_SLOW_DISPATCH_THRESHOLD,
                    BROADCAST_SLOW_DELIVERY_THRESHOLD,
                )
            }
            .looper
    }

    private fun provideBroadcastRunningExecutor(
        @ConcurrencyModule.BroadcastRunning looper: Looper?
    ): Executor {
        val handler = Handler(looper ?: Looper.getMainLooper())
        return Executor { command -> handler.post(command) }
    }

    companion object {
        private val BROADCAST_SLOW_DISPATCH_THRESHOLD = 1000L
        private val BROADCAST_SLOW_DELIVERY_THRESHOLD = 1000L
        const val TEST_ACTION = "TEST_ACTION"
        const val TEST_TYPE = "test/type"
    }
}
