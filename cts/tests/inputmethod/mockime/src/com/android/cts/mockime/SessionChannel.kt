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
package com.android.cts.mockime

import android.os.Bundle
import android.os.RemoteCallback
import java.util.function.Consumer
import kotlinx.atomicfu.atomic

class SessionChannel(private var mEstablishedCallback: Runnable) : AutoCloseable {
    private val listeners = mutableListOf<Consumer<Bundle>>()
    private val remote = atomic<RemoteCallback?>(null)
    private var local: RemoteCallback? = RemoteCallback { bundle ->
        checkNotNull(bundle)
        if (bundle.containsKey(ESTABLISH_KEY)) {
            val remote = bundle.getParcelable(ESTABLISH_KEY, RemoteCallback::class.java)!!
            check(this.remote.getAndSet(remote) == null) {
                "already initialized"
            }
            mEstablishedCallback.run()
            mEstablishedCallback = Runnable {}
        } else {
            listeners.forEach { listener ->
                listener.accept(bundle)
            }
        }
    }

    constructor(transport: RemoteCallback) : this({}) {
        this.remote.value = transport
        send(Bundle().apply {
            putParcelable(ESTABLISH_KEY, takeTransport())
        })
    }

    fun takeTransport(): RemoteCallback {
        val taken = checkNotNull(local) { "Can only take transport once" }
        local = null
        return taken
    }

    fun send(bundle: Bundle): Boolean {
        val remote = remote.value ?: run {
            return false
        }
        remote.sendResult(bundle)
        return true
    }

    fun registerListener(listener: Consumer<Bundle>) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: Consumer<Bundle>) {
        listeners.remove(listener)
    }

    override fun close() {
        remote.value = null
        listeners.clear()
    }

    companion object {
        val ESTABLISH_KEY = SessionChannel::class.qualifiedName
    }
}
