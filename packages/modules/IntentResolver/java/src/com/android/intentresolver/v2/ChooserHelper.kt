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

package com.android.intentresolver.v2

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

/**
 * __Purpose__
 *
 * Cleanup aid. Provides a pathway to cleaner code.
 *
 * __Incoming References__
 *
 * For use by ChooserActivity only; must not be accessed by any code outside of ChooserActivity.
 * This prevents circular dependencies and coupling, and maintains unidirectional flow. This is
 * important for maintaining a migration path towards healthier architecture.
 *
 * __Outgoing References__
 *
 * _ChooserActivity_
 *
 * This class must only reference it's host as Activity/ComponentActivity; no down-cast to
 * [ChooserActivity]. Other components should be passed in and not pulled from other places. This
 * prevents circular dependencies from forming.
 *
 * _Elsewhere_
 *
 * Where possible, Singleton and ActivityScoped dependencies should be injected here instead of
 * referenced from an existing location. If not available for injection, the value should be
 * constructed here, then provided to where it is needed. If existing objects from ChooserActivity
 * are required, supply a factory interface which satisfies the necessary dependencies and use it
 * during construction.
 */

@ActivityScoped
class ChooserHelper @Inject constructor(
    hostActivity: Activity,
) : DefaultLifecycleObserver {
    // This is guaranteed by Hilt, since only a ComponentActivity is injectable.
    private val activity: ComponentActivity = hostActivity as ComponentActivity

    private var activityPostCreate: Runnable? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    /**
     * Provides a optional callback to setup state which is not yet possible to do without circular
     * dependencies or by moving more code.
     */
    fun setPostCreateCallback(onPostCreate: Runnable) {
        activityPostCreate = onPostCreate
    }

    /**
     * Invoked by Lifecycle, after Activity.onCreate() _returns_.
     */
    override fun onCreate(owner: LifecycleOwner) {
        activityPostCreate?.run()
    }
}