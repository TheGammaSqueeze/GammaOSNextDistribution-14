package com.android.intentresolver.v2.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "BroadcastFlow"

/**
 * Returns a [callbackFlow] that, when collected, registers a broadcast receiver and emits a new
 * value whenever broadcast matching _filter_ is received. The result value will be computed using
 * [transform] and emitted if non-null.
 */
internal fun <T> broadcastFlow(
    context: Context,
    filter: IntentFilter,
    user: UserHandle,
    transform: (Intent) -> T?
): Flow<T> = callbackFlow {
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                transform(intent)?.also { result ->
                    trySend(result).onFailure { Log.e(TAG, "Failed to send $result", it) }
                }
                    ?: Log.w(TAG, "Ignored broadcast $intent")
            }
        }

    context.registerReceiverAsUser(
        receiver,
        user,
        IntentFilter(filter),
        null,
        null,
        Context.RECEIVER_NOT_EXPORTED
    )
    awaitClose { context.unregisterReceiver(receiver) }
}
