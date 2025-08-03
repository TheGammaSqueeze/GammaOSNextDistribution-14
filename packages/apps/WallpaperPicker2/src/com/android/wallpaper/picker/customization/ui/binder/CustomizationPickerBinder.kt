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
 *
 */

package com.android.wallpaper.picker.customization.ui.binder

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.SectionView
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel
import com.android.wallpaper.picker.undo.ui.binder.RevertToolbarButtonBinder
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch

typealias SectionController = CustomizationSectionController<*>

/** Binds view to view-model for the customization picker. */
object CustomizationPickerBinder {

    /**
     * Binds the given view and view-model, keeping the UI up-to-date and listening to user input.
     *
     * @param view The root of the UI to keep up-to-date and observe for user input.
     * @param toolbarViewId The view ID of the toolbar view.
     * @param viewModel The view-model to observe UI state from and report user input to.
     * @param lifecycleOwner An owner of the lifecycle, so we can stop doing work when the lifecycle
     *   cleans up.
     * @param sectionControllerProvider A function that can provide the list of [SectionController]
     *   instances to show, based on the given passed-in value of "isOnLockScreen".
     * @return A [DisposableHandle] to use to dispose of the binding before another binding is about
     *   to be created by a subsequent call to this function.
     */
    @JvmStatic
    fun bind(
        view: View,
        @IdRes toolbarViewId: Int,
        viewModel: CustomizationPickerViewModel,
        lifecycleOwner: LifecycleOwner,
        sectionControllerProvider: (isOnLockScreen: Boolean) -> List<SectionController>,
    ): DisposableHandle {
        RevertToolbarButtonBinder.bind(
            view = view.requireViewById(toolbarViewId),
            viewModel = viewModel.undo,
            lifecycleOwner = lifecycleOwner,
        )

        CustomizationPickerTabsBinder.bind(
            view = view,
            viewModel = viewModel,
            lifecycleOwner = lifecycleOwner,
        )

        val lockScrollContainer = view.requireViewById<NestedScrollView>(R.id.lock_scroll_container)
        val homeScrollContainer = view.requireViewById<NestedScrollView>(R.id.home_scroll_container)

        val lockSectionContainer = view.requireViewById<ViewGroup>(R.id.lock_section_container)
        lockSectionContainer.setOnApplyWindowInsetsListener { v: View, windowInsets: WindowInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets.consumeSystemWindowInsets()
        }
        lockSectionContainer.updateLayoutParams<FrameLayout.LayoutParams> {
            // We don't want the top margin from the XML because our tabs have that as padding so
            // they can be collapsed into the toolbar with spacing from the actual title text.
            topMargin = 0
        }

        val homeSectionContainer = view.requireViewById<ViewGroup>(R.id.home_section_container)
        homeSectionContainer.setOnApplyWindowInsetsListener { v: View, windowInsets: WindowInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets.consumeSystemWindowInsets()
        }
        homeSectionContainer.updateLayoutParams<FrameLayout.LayoutParams> {
            // We don't want the top margin from the XML because our tabs have that as padding so
            // they can be collapsed into the toolbar with spacing from the actual title text.
            topMargin = 0
        }

        // create and add sections to both the lock and home screen tabs ahead of time, since
        // the lock and home screen preview sections are both needed to load initial wallpaper
        // colors for the correct functioning of the color picker
        createAndAddSections(
            view.context,
            homeSectionContainer,
            isOnLockScreen = false,
            sectionControllerProvider
        )
        createAndAddSections(
            view.context,
            lockSectionContainer,
            isOnLockScreen = true,
            sectionControllerProvider
        )

        val job =
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.isOnLockScreen.collect { isOnLockScreen ->
                            InjectorProvider.getInjector()
                                .getPreviewActivityIntentFactory()
                                .setViewAsHome(!isOnLockScreen)
                            InjectorProvider.getInjector()
                                .getViewOnlyPreviewActivityIntentFactory()
                                .setViewAsHome(!isOnLockScreen)
                            // Offset the scroll position of both tabs
                            lockScrollContainer.scrollTo(0, 0)
                            homeScrollContainer.scrollTo(0, 0)

                            lockScrollContainer.isInvisible = !isOnLockScreen
                            homeScrollContainer.isInvisible = isOnLockScreen
                        }
                    }
                }

                // This happens when the lifecycle is stopped.
                lockSectionContainer.children
                    .map { Pair(it, it.tag as? CustomizationSectionController<out SectionView>) }
                    .forEach {
                        it.second?.release()
                        it.first?.tag = null
                    }
                lockSectionContainer.removeAllViews()
                homeSectionContainer.children
                    .map { Pair(it, it.tag as? CustomizationSectionController<out SectionView>) }
                    .forEach {
                        it.second?.release()
                        it.first?.tag = null
                    }
                homeSectionContainer.removeAllViews()
            }
        return DisposableHandle { job.cancel() }
    }

    private fun createAndAddSections(
        context: Context,
        container: ViewGroup,
        isOnLockScreen: Boolean,
        sectionControllerProvider: (isOnLockScreen: Boolean) -> List<SectionController>,
    ) {
        sectionControllerProvider
            .invoke(isOnLockScreen)
            .filter { it.isAvailable(context) }
            .forEach { controller ->
                val viewToAdd =
                    controller.createView(
                        context,
                        CustomizationSectionController.ViewCreationParams(
                            isOnLockScreen = isOnLockScreen,
                            isWallpaperVisibilityControlledByTab = true,
                        )
                    )
                viewToAdd.tag = controller
                container.addView(viewToAdd)
            }
    }
}
