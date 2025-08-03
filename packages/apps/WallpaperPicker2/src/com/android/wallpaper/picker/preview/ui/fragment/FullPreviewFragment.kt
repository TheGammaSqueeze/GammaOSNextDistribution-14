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
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionInflater
import com.android.wallpaper.R
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.preview.ui.binder.CropWallpaperButtonBinder
import com.android.wallpaper.picker.preview.ui.binder.FullWallpaperPreviewBinder
import com.android.wallpaper.picker.preview.ui.binder.PreviewTooltipBinder
import com.android.wallpaper.picker.preview.ui.binder.WorkspacePreviewBinder
import com.android.wallpaper.picker.preview.ui.fragment.SmallPreviewFragment.Companion.ARG_EDIT_INTENT
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Shows full preview of user selected wallpaper for cropping, zooming and positioning. */
@AndroidEntryPoint(AppbarFragment::class)
class FullPreviewFragment : Hilt_FullPreviewFragment() {

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var displayUtils: DisplayUtils
    @Inject @MainDispatcher lateinit var mainScope: CoroutineScope

    private val wallpaperPreviewViewModel by activityViewModels<WallpaperPreviewViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ENABLE_ANIMATION) {
            sharedElementEnterTransition =
                TransitionInflater.from(appContext).inflateTransition(R.transition.shared_view)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_full_preview, container, false)
        setUpToolbar(view)

        val previewCard: CardView = view.requireViewById(R.id.preview_card)
        ViewCompat.setTransitionName(
            previewCard,
            SmallPreviewFragment.FULL_PREVIEW_SHARED_ELEMENT_ID
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
                findNavController().popBackStack()
            }
        // If edit intent is nonnull, we launch the edit overlay activity, with the wallpaper
        // preview from the Wallpaper Picker app's fragment.
        arguments?.getParcelable(ARG_EDIT_INTENT, Intent::class.java)?.let {
            view.requireViewById<Toolbar>(R.id.toolbar).isVisible = false
            view.requireViewById<SurfaceView>(R.id.workspace_surface).isVisible = false
            view.requireViewById<Button>(R.id.crop_wallpaper_button).isVisible = false
            creativeWallpaperEditActivityResult.launch(it)
            return view
        }

        FullWallpaperPreviewBinder.bind(
            applicationContext = appContext,
            view = view,
            viewModel = wallpaperPreviewViewModel,
            displayUtils = displayUtils,
            lifecycleOwner = viewLifecycleOwner,
            mainScope = mainScope,
        )

        CropWallpaperButtonBinder.bind(
            button = view.requireViewById(R.id.crop_wallpaper_button),
            viewModel = wallpaperPreviewViewModel,
            lifecycleOwner = viewLifecycleOwner,
        ) {
            findNavController().popBackStack()
        }

        WorkspacePreviewBinder.bindFullWorkspacePreview(
            surface = view.requireViewById(R.id.workspace_surface),
            viewModel = wallpaperPreviewViewModel,
            lifecycleOwner = viewLifecycleOwner,
        )

        PreviewTooltipBinder.bind(
            tooltipStub = view.requireViewById(R.id.tooltip_stub),
            enableClickToDismiss = true,
            viewModel = wallpaperPreviewViewModel,
            lifecycleOwner = viewLifecycleOwner,
        )

        return view
    }

    // TODO(b/291761856): Use real string
    override fun getDefaultTitle(): CharSequence {
        return ""
    }

    override fun getToolbarColorId(): Int {
        return android.R.color.transparent
    }

    override fun getToolbarTextColor(): Int {
        return ContextCompat.getColor(requireContext(), R.color.system_on_surface)
    }

    companion object {
        const val ENABLE_ANIMATION = false
    }
}
