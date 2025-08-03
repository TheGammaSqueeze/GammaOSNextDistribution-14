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

package com.android.wallpaper.picker.di.modules

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executor
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ConcurrencyModule {

    private val BROADCAST_SLOW_DISPATCH_THRESHOLD = 1000L
    private val BROADCAST_SLOW_DELIVERY_THRESHOLD = 1000L

    @Qualifier
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    annotation class BroadcastRunning

    @Provides
    @Singleton
    @BroadcastRunning
    fun provideBroadcastRunningLooper(): Looper {
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

    /** Provide a BroadcastRunning Executor (for sending and receiving broadcasts). */
    @Provides
    @Singleton
    @BroadcastRunning
    fun provideBroadcastRunningExecutor(@BroadcastRunning looper: Looper?): Executor {
        val handler = Handler(looper ?: Looper.getMainLooper())
        return Executor { command -> handler.post(command) }
    }
}
