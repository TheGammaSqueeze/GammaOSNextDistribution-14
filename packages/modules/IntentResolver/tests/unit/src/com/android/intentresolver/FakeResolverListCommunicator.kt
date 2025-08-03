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

package com.android.intentresolver

import android.content.Intent
import android.content.pm.ActivityInfo
import java.util.concurrent.atomic.AtomicInteger

class FakeResolverListCommunicator(private val layoutWithDefaults: Boolean = true) :
    ResolverListAdapter.ResolverListCommunicator {
    private val sendVoiceCounter = AtomicInteger()
    private val updateProfileViewButtonCounter = AtomicInteger()

    val sendVoiceCommandCount
        get() = sendVoiceCounter.get()

    override fun getReplacementIntent(activityInfo: ActivityInfo?, defIntent: Intent): Intent {
        return defIntent
    }

    override fun onPostListReady(
        listAdapter: ResolverListAdapter?,
        updateUi: Boolean,
        rebuildCompleted: Boolean,
    ) = Unit

    override fun sendVoiceChoicesIfNeeded() {
        sendVoiceCounter.incrementAndGet()
    }

    override fun useLayoutWithDefault(): Boolean = layoutWithDefaults

    override fun shouldGetActivityMetadata(): Boolean = true

    override fun onHandlePackagesChanged(listAdapter: ResolverListAdapter?) {}
}
