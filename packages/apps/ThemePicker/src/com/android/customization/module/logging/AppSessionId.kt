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
package com.android.customization.module.logging

import android.util.Log
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSessionId @Inject constructor() {

    private var idSequence: InstanceIdSequence? = null

    private var sessionId: InstanceId? = null

    fun createNewId(): AppSessionId {
        sessionId = newInstanceId()
        return this
    }

    fun getId(): Int {
        val id =
            sessionId
                ?: newInstanceId().also {
                    Log.w(
                        TAG,
                        "Session ID should not be null. We should always call createNewId() before calling getId()."
                    )
                    sessionId = it
                }
        return id.hashCode()
    }

    private fun newInstanceId(): InstanceId =
        (idSequence ?: InstanceIdSequence(INSTANCE_ID_MAX).also { idSequence = it }).newInstanceId()

    companion object {
        private const val TAG = "AppSessionId"
        // At most 20 bits: ~1m possibilities, ~0.5% probability of collision in 100 values
        private const val INSTANCE_ID_MAX = 1 shl 20
    }
}
