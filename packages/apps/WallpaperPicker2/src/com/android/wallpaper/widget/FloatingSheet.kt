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
package com.android.wallpaper.widget

import android.annotation.IntDef
import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import com.android.wallpaper.R
import com.android.wallpaper.util.SizeCalculator
import com.android.wallpaper.widget.floatingsheetcontent.FloatingSheetContent
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import java.util.function.Consumer

/** A `ViewGroup` which provides the specific actions for the user to interact with. */
class FloatingSheet(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    companion object {

        @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
        @IntDef(CUSTOMIZE, INFORMATION, EFFECTS)
        @Retention(AnnotationRetention.SOURCE)
        annotation class FloatingSheetContentType

        const val CUSTOMIZE = 0
        const val INFORMATION = 1
        const val EFFECTS = 2
    }

    private val floatingSheetView: ViewGroup
    private val floatingSheetContainer: ViewGroup
    private val floatingSheetBehavior: BottomSheetBehavior<ViewGroup>
    private val contentViewMap:
        MutableMap<@FloatingSheetContentType Int, FloatingSheetContent<*>?> =
        HashMap()

    // The system "short" animation time duration, in milliseconds. This
    // duration is ideal for subtle animations or animations that occur
    // very frequently.
    private val shortAnimTimeMillis: Long

    init {
        LayoutInflater.from(context).inflate(R.layout.floating_sheet, this, true)
        floatingSheetView = requireViewById(R.id.floating_sheet_content)
        SizeCalculator.adjustBackgroundCornerRadius(floatingSheetView)
        setColor(context)
        floatingSheetContainer = requireViewById(R.id.floating_sheet_container)
        floatingSheetBehavior = BottomSheetBehavior.from(floatingSheetContainer)
        floatingSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        shortAnimTimeMillis = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
    }

    /**
     * Binds the `floatingSheetContent` with an id that can be used identify and switch between
     * floating sheet content
     *
     * @param floatingSheetContent the content object with view being added to the floating sheet
     */
    fun putFloatingSheetContent(
        @FloatingSheetContentType type: Int,
        floatingSheetContent: FloatingSheetContent<*>
    ) {
        floatingSheetContent.initView()
        contentViewMap[type] = floatingSheetContent
        floatingSheetView.addView(floatingSheetContent.contentView)
    }

    /** Dynamic update color with `Context`. */
    fun setColor(context: Context) {
        // Set floating sheet background.
        floatingSheetView.background =
            AppCompatResources.getDrawable(context, R.drawable.floating_sheet_background)
        if (floatingSheetView.childCount > 0) {
            // Update the bottom sheet content view if any.
            floatingSheetView.removeAllViews()
            contentViewMap.values.forEach(
                Consumer { floatingSheetContent: FloatingSheetContent<*>? ->
                    floatingSheetContent?.let {
                        it.recreateView()
                        floatingSheetView.addView(it.contentView)
                    }
                }
            )
        }
    }

    /** Returns `true` if the state of bottom sheet is collapsed. */
    val isFloatingSheetCollapsed: Boolean
        get() = floatingSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN

    /** Expands [FloatingSheet]. */
    fun expand() {
        floatingSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    /** Collapses [FloatingSheet]. */
    fun collapse() {
        floatingSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        endContentViewAnimation()
        contentViewMap.forEach { (i: Int, content: FloatingSheetContent<*>?) ->
            content?.collapse()
        }
    }

    /**
     * Updates content view of [FloatingSheet] with transition animation
     *
     * @param type the integer or enum used to identify the content view
     */
    fun updateContentViewWithAnimation(@FloatingSheetContentType type: Int) {
        val transition = AutoTransition()
        transition.duration = shortAnimTimeMillis
        /**
         * This line records changes you make to its views and applies a transition that animates
         * the changes when the system redraws the user interface
         */
        TransitionManager.beginDelayedTransition(floatingSheetContainer, transition)

        updateContentView(type)
    }

    fun endContentViewAnimation() {
        TransitionManager.endTransitions(floatingSheetContainer)
    }

    /**
     * Updates content view of [FloatingSheet]
     *
     * @param type the integer or enum used to identify the content view
     */
    fun updateContentView(@FloatingSheetContentType type: Int) {
        contentViewMap.forEach { (i: Int, content: FloatingSheetContent<*>?) ->
            content?.setVisibility(i == type)
        }
    }

    /**
     * Adds Floating Sheet Callback to connected [BottomSheetBehavior].
     *
     * @param callback the callback for floating sheet state changes, has to be in the type of
     *   [BottomSheetBehavior.BottomSheetCallback] since the floating sheet behavior is currently
     *   based on [BottomSheetBehavior]
     */
    fun addFloatingSheetCallback(callback: BottomSheetCallback) {
        floatingSheetBehavior.addBottomSheetCallback(callback)
    }

    fun removeFloatingSheetCallback(callback: BottomSheetCallback) {
        floatingSheetBehavior.removeBottomSheetCallback(callback)
    }
}
