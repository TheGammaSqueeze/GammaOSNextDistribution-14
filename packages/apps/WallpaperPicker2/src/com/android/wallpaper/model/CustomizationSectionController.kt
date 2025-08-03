/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.wallpaper.model

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.android.wallpaper.picker.SectionView

/**
 * The interface for the behavior of section in the customization picker.
 *
 * @param <T> the [SectionView] to create for the section </T>
 */
interface CustomizationSectionController<T : SectionView> {
    /** Interface for customization section navigation. */
    interface CustomizationSectionNavigationController {
        /** Navigates to the given `fragment`. */
        fun navigateTo(fragment: Fragment?)

        /** Navigates to a `fragment` that maps to the given destination ID. */
        fun navigateTo(destinationId: String?)

        /** Navigates like [navigateTo] but without adding picker to back stack. */
        fun standaloneNavigateTo(destinationId: String?)
    }

    data class ViewCreationParams(
        /** Whether the view is being created in the context of the lock screen tab of the UI. */
        val isOnLockScreen: Boolean = false,
        /**
         * Whether the view is being created in the context of a bunch of "connected" sections that
         * are laid out side-by-side in a horizontal layout.
         */
        val isConnectedHorizontallyToOtherSections: Boolean = false,
        /**
         * Whether the Wallpaper's engine visibility is determined by which tab is currently
         * selected
         */
        val isWallpaperVisibilityControlledByTab: Boolean = false,
    )

    /**
     * It means that the creation of the controller can be expensive and we should avoid recreation
     * in conditions like the user switching between the home and lock screen.
     */
    fun shouldRetainInstanceWhenSwitchingTabs(): Boolean {
        return false
    }

    /** Returns `true` if the customization section is available. */
    fun isAvailable(context: Context): Boolean

    /**
     * Returns a newly created [SectionView] for the section.
     *
     * @param context The [Context] to inflate view.
     * @param params Parameters for the creation of the view.
     */
    fun createView(context: Context, params: ViewCreationParams): T {
        return createView(context)
    }

    /**
     * Returns a newly created [SectionView] for the section.
     *
     * @param context the [Context] to inflate view
     */
    fun createView(context: Context): T

    /** Saves the view state for configuration changes. */
    fun onSaveInstanceState(savedInstanceState: Bundle) = Unit

    /** Releases the controller. */
    fun release() = Unit

    /** Gets called when the section gets transitioned out. */
    fun onTransitionOut() = Unit
}
