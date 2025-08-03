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

package com.android.intentresolver.widget

import android.view.View

interface ImagePreviewView {
    fun setTransitionElementStatusCallback(callback: TransitionElementStatusCallback?)
    fun getTransitionView(): View?

    /**
     * [ImagePreviewView] progressively prepares views for shared element transition and reports
     * each successful preparation with [onTransitionElementReady] call followed by
     * closing [onAllTransitionElementsReady] invocation. Thus the overall invocation pattern is
     * zero or more [onTransitionElementReady] calls followed by the final
     * [onAllTransitionElementsReady] call.
     */
    interface TransitionElementStatusCallback {
        /**
         * Invoked when a view for a shared transition animation element is ready i.e. the image
         * is loaded and the view is laid out.
         * @param name shared element name.
         */
        fun onTransitionElementReady(name: String)

        /**
         * Indicates that all supported transition elements have been reported with
         * [onTransitionElementReady].
         */
        fun onAllTransitionElementsReady()
    }
}
