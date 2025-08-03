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
package com.android.wallpaper.widget.floatingsheetcontent

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes

/**
 * Object to host content view for floating sheet to display.
 *
 * The view would be created in the constructor.
 *
 * @param <T> the floating sheet content type </T>
 *
 * TODO: refactoring FloatingSheetContent b/258468645
 */
abstract class FloatingSheetContent<T : View>(private val context: Context) {

    lateinit var contentView: T
    private var isVisible = false

    /** Gets the view id to inflate. */
    @get:LayoutRes abstract val viewId: Int

    /** Gets called when the content view is created. */
    abstract fun onViewCreated(view: T)

    /** Gets called when the current content view is going to recreate. */
    open fun onRecreateView(oldView: T) {}

    open fun collapse() {}

    fun initView() {
        contentView = createView()
        setVisibility(true)
    }

    fun recreateView() {
        // Inform that the view is going to recreate.
        onRecreateView(contentView)
        // Create a new view with the given context.
        contentView = createView()
        setVisibility(isVisible)
    }

    private fun createView(): T {
        @Suppress("UNCHECKED_CAST")
        val contentView = LayoutInflater.from(context).inflate(viewId, null) as T
        onViewCreated(contentView)
        contentView.isFocusable = true
        return contentView
    }

    open fun setVisibility(isVisible: Boolean) {
        this.isVisible = isVisible
        contentView.visibility = if (this.isVisible) FrameLayout.VISIBLE else FrameLayout.GONE
    }
}
