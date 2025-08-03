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

package com.android.customization.picker.quickaffordance.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.transition.Transition
import androidx.transition.doOnStart
import com.android.customization.module.ThemePickerInjector
import com.android.customization.picker.quickaffordance.ui.binder.KeyguardQuickAffordancePickerBinder
import com.android.customization.picker.quickaffordance.ui.binder.KeyguardQuickAffordancePreviewBinder
import com.android.customization.picker.quickaffordance.ui.viewmodel.KeyguardQuickAffordancePickerViewModel
import com.android.themepicker.R
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.AppbarFragment

class KeyguardQuickAffordancePickerFragment : AppbarFragment() {
    companion object {
        const val DESTINATION_ID = "quick_affordances"
        @JvmStatic
        fun newInstance(): KeyguardQuickAffordancePickerFragment {
            return KeyguardQuickAffordancePickerFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view =
            inflater.inflate(
                R.layout.fragment_lock_screen_quick_affordances,
                container,
                false,
            )
        setUpToolbar(view)

        // For nav bar edge-to-edge effect.
        view.setOnApplyWindowInsetsListener { v: View, windowInsets: WindowInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets
        }

        val injector = InjectorProvider.getInjector() as ThemePickerInjector
        val viewModel: KeyguardQuickAffordancePickerViewModel =
            ViewModelProvider(
                    requireActivity(),
                    injector.getKeyguardQuickAffordancePickerViewModelFactory(requireContext()),
                )
                .get()

        KeyguardQuickAffordancePreviewBinder.bind(
            activity = requireActivity(),
            previewView = view.requireViewById(R.id.preview),
            viewModel = viewModel,
            lifecycleOwner = this,
            offsetToStart =
                requireActivity().let {
                    injector.getDisplayUtils(it).isSingleDisplayOrUnfoldedHorizontalHinge(it)
                }
        )
        KeyguardQuickAffordancePickerBinder.bind(
            view = view,
            viewModel = viewModel,
            lifecycleOwner = this,
        )
        postponeEnterTransition()
        view.post { startPostponedEnterTransition() }
        (returnTransition as? Transition)?.doOnStart {
            // Hide preview during exit transition animation
            view?.findViewById<View>(R.id.preview)?.isVisible = false
        }
        return view
    }

    override fun getDefaultTitle(): CharSequence {
        return requireContext().getString(R.string.keyguard_quick_affordance_title)
    }

    override fun getToolbarColorId(): Int {
        return android.R.color.transparent
    }

    override fun getToolbarTextColor(): Int {
        return ContextCompat.getColor(
            requireContext(),
            com.android.wallpaper.R.color.system_on_surface
        )
    }
}
