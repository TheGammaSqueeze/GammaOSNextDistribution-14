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
package com.android.wallpaper.picker.preview.ui.fragment

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.wallpaper.R
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.preview.ui.binder.FullWallpaperPreviewBinder
import com.android.wallpaper.picker.preview.ui.fragment.SmallPreviewFragment.Companion.ARG_EDIT_INTENT
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Shows full preview with an edit activity overlay. */
@AndroidEntryPoint(AppbarFragment::class)
class CreativeNewPreviewFragment : Hilt_CreativeNewPreviewFragment() {

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var displayUtils: DisplayUtils
    @Inject @MainDispatcher lateinit var mainScope: CoroutineScope

    private val wallpaperPreviewViewModel by activityViewModels<WallpaperPreviewViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_full_preview, container, false)
        setUpToolbar(view)

        FullWallpaperPreviewBinder.bind(
            applicationContext = appContext,
            view = view,
            viewModel = wallpaperPreviewViewModel,
            displayUtils = displayUtils,
            lifecycleOwner = viewLifecycleOwner,
            mainScope = mainScope,
        )

        wallpaperPreviewViewModel.setDefaultWallpaperPreviewConfigViewModel(
            displayUtils.getRealSize(requireActivity().display)
        )
        view.requireViewById<Toolbar>(R.id.toolbar).isVisible = false
        view.requireViewById<SurfaceView>(R.id.workspace_surface).isVisible = false
        view.requireViewById<Button>(R.id.crop_wallpaper_button).isVisible = false

        val intent =
            arguments?.getParcelable(ARG_EDIT_INTENT, Intent::class.java)
                ?: throw IllegalArgumentException(
                    "To render the first screen in the create new creative wallpaper flow, the intent for rendering the edit activity overlay can not be null."
                )
        val creativeWallpaperEditActivityResult =
            registerForActivityResult(
                object : ActivityResultContract<Intent, Int>() {
                    override fun createIntent(context: Context, input: Intent): Intent {
                        return input
                    }

                    override fun parseResult(resultCode: Int, intent: Intent?): Int {
                        return resultCode
                    }
                },
            ) {
                // Callback when the overlaying edit activity is finished. Result code of RESULT_OK
                // means the user clicked on the check button; RESULT_CANCELED otherwise.
                if (it == RESULT_OK) {
                    // When clicking on the check button, navigate to the small preview fragment.
                    findNavController()
                        .navigate(R.id.action_creativeNewPreviewFragment_to_smallPreviewFragment)
                } else {
                    activity?.finish()
                }
            }
        creativeWallpaperEditActivityResult.launch(intent)

        return view
    }

    override fun getToolbarColorId(): Int {
        return android.R.color.transparent
    }
}
