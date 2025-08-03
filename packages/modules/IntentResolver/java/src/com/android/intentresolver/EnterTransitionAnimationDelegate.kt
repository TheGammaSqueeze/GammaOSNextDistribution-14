/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.SharedElementCallback
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback
import com.android.internal.annotations.VisibleForTesting
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.function.Supplier

/**
 * A helper class to track app's readiness for the scene transition animation.
 * The app is ready when both the image is laid out and the drawer offset is calculated.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
class EnterTransitionAnimationDelegate(
    private val activity: ComponentActivity,
    private val transitionTargetSupplier: Supplier<View?>,
) : View.OnLayoutChangeListener, TransitionElementStatusCallback {

    private val transitionElements = HashSet<String>()
    private var previewReady = false
    private var offsetCalculated = false
    private var timeoutJob: Job? = null

    init {
        activity.setEnterSharedElementCallback(
            object : SharedElementCallback() {
                override fun onMapSharedElements(
                    names: MutableList<String>, sharedElements: MutableMap<String, View>
                ) {
                    this@EnterTransitionAnimationDelegate.onMapSharedElements(
                        names, sharedElements
                    )
                }
            })
    }

    fun postponeTransition() {
        activity.postponeEnterTransition()
        timeoutJob = activity.lifecycleScope.launch {
            delay(activity.resources.getInteger(R.integer.config_shortAnimTime).toLong())
            onTimeout()
        }
    }

    private fun onTimeout() {
        // We only mark the preview readiness and not the offset readiness
        // (see [#markOffsetCalculated()]) as this is what legacy logic, effectively, did. We might
        // want to review that aspect separately.
        onAllTransitionElementsReady()
    }

    override fun onTransitionElementReady(name: String) {
        transitionElements.add(name)
    }

    override fun onAllTransitionElementsReady() {
        timeoutJob?.cancel()
        if (!previewReady) {
            previewReady = true
            maybeStartListenForLayout()
        }
    }

    fun markOffsetCalculated() {
        if (!offsetCalculated) {
            offsetCalculated = true
            maybeStartListenForLayout()
        }
    }

    private fun onMapSharedElements(
        names: MutableList<String>,
        sharedElements: MutableMap<String, View>
    ) {
        names.removeAll { !transitionElements.contains(it) }
        sharedElements.entries.removeAll { !transitionElements.contains(it.key) }
    }

    private fun maybeStartListenForLayout() {
        val drawer = transitionTargetSupplier.get()
        if (previewReady && offsetCalculated && drawer != null) {
            if (drawer.isInLayout) {
                startPostponedEnterTransition()
            } else {
                drawer.addOnLayoutChangeListener(this)
                drawer.requestLayout()
            }
        }
    }

    override fun onLayoutChange(
        v: View,
        left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
    ) {
        v.removeOnLayoutChangeListener(this)
        startPostponedEnterTransition()
    }

    private fun startPostponedEnterTransition() {
        if (transitionElements.isNotEmpty() && activity.isActivityTransitionRunning) {
            // Disable the window animations as it interferes with the transition animation.
            activity.window.setWindowAnimations(0)
        }
        activity.startPostponedEnterTransition()
    }
}
