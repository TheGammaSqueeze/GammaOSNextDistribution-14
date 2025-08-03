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

package com.android.intentresolver.shortcuts

import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * A memory leak workaround for b/290971946. Drops the references to the actual [callback] when the
 * [scope] is cancelled allowing it to be garbage-collected (and only leaking this instance).
 */
class ScopedAppTargetListCallback(
    scope: CoroutineScope?,
    callback: (List<AppTarget>) -> Unit,
) {

    @Volatile private var callbackRef: ((List<AppTarget>) -> Unit)? = callback

    constructor(
        context: Context,
        callback: (List<AppTarget>) -> Unit,
    ) : this((context as? LifecycleOwner)?.lifecycle?.coroutineScope, callback)

    init {
        scope?.launch { awaitCancellation() }?.invokeOnCompletion { callbackRef = null }
    }

    private fun notifyCallback(result: List<AppTarget>) {
        callbackRef?.invoke(result)
    }

    fun toConsumer(): Consumer<MutableList<AppTarget>?> =
        Consumer<MutableList<AppTarget>?> { notifyCallback(it ?: emptyList()) }

    fun toAppPredictorCallback(): AppPredictor.Callback =
        AppPredictor.Callback { notifyCallback(it) }
}
