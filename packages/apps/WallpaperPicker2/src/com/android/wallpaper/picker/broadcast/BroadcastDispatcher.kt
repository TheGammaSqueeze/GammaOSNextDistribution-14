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
 * limitations under the License
 */

package com.android.wallpaper.picker.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.android.systemui.dagger.qualifiers.Main
import com.android.wallpaper.picker.di.modules.ConcurrencyModule.*
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * WallpaperPicker master Broadcast Dispatcher.
 *
 * This class allows [BroadcastReceiver] to register and centralizes registrations to [Context] from
 * WallpaperPicker. That way the number of calls to [BroadcastReceiver.onReceive] can be reduced for
 * a given broadcast.
 *
 * Use only for IntentFilters with actions and optionally categories. It does not support schemes,
 * data types, data authorities or priority different than 0.
 *
 * Cannot be used for getting sticky broadcasts (either as return of registering or as re-delivery).
 */
@Singleton
open class BroadcastDispatcher
@Inject
constructor(
    private val context: Context,
    @Main private val mainExecutor: Executor,
    @BroadcastRunning private val broadcastLooper: Looper,
    @BroadcastRunning private val broadcastExecutor: Executor,
) {
    /**
     * Register a receiver for broadcast with the dispatcher
     *
     * @param receiver A receiver to dispatch the [Intent]
     * @param filter A filter to determine what broadcasts should be dispatched to this receiver. It
     *   will only take into account actions and categories for filtering. It must have at least one
     *   action.
     * @param executor An executor to dispatch [BroadcastReceiver.onReceive].
     * @param flags Flags to use when registering the receiver. [Context.RECEIVER_EXPORTED] by
     *   default.
     * @param permission to enforce security on who can send broadcasts to the receiver.
     * @throws IllegalArgumentException if the filter has other constraints that are not actions or
     *   categories or the filter has no actions.
     */
    @JvmOverloads
    open fun registerReceiver(
        receiver: BroadcastReceiver,
        filter: IntentFilter,
        executor: Executor = mainExecutor,
        @Context.RegisterReceiverFlags flags: Int = Context.RECEIVER_EXPORTED,
        permission: String? = null
    ) {
        checkFilter(filter)
        context.registerReceiver(receiver, filter, permission, Handler(broadcastLooper), flags)
    }

    /**
     * Returns a [Flow] that, when collected, emits a new value whenever a broadcast matching
     * [filter] is received. The value will be computed from the intent and the registered receiver
     * using [map].
     *
     * @see registerReceiver
     */
    @JvmOverloads
    fun <T> broadcastFlow(
        filter: IntentFilter,
        @Context.RegisterReceiverFlags flags: Int = Context.RECEIVER_EXPORTED,
        permission: String? = null,
        map: (Intent, BroadcastReceiver) -> T,
    ): Flow<T> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(map(intent, this))
                }
            }

        registerReceiver(
            receiver,
            filter,
            broadcastExecutor,
            flags,
            permission,
        )

        awaitClose { unregisterReceiver(receiver) }
    }

    /**
     * Returns a [Flow] that, when collected, emits `Unit` whenever a broadcast matching [filter] is
     * received.
     *
     * @see registerReceiver
     */
    @JvmOverloads
    fun broadcastFlow(
        filter: IntentFilter,
        @Context.RegisterReceiverFlags flags: Int = Context.RECEIVER_EXPORTED,
        permission: String? = null,
    ): Flow<Unit> = broadcastFlow(filter, flags, permission) { _, _ -> Unit }

    private fun checkFilter(filter: IntentFilter) {
        buildString {
                if (filter.countActions() == 0) {
                    append("Filter must contain at least one action. ")
                }
                if (filter.countDataAuthorities() != 0) {
                    append("Filter cannot contain DataAuthorities. ")
                }
                if (filter.countDataPaths() != 0) {
                    append("Filter cannot contain DataPaths. ")
                }
                if (filter.countDataSchemes() != 0) {
                    append("Filter cannot contain DataSchemes. ")
                }
                if (filter.countDataTypes() != 0) {
                    append("Filter cannot contain DataTypes. ")
                }
                if (filter.priority != 0) {
                    append("Filter cannot modify priority. ")
                }
            }
            .let {
                if (it.isNotEmpty()) {
                    throw IllegalArgumentException(it)
                }
            }
    }

    /**
     * Unregister receiver for the current user.
     *
     * @param receiver The receiver to unregister.
     */
    open fun unregisterReceiver(receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }
}
