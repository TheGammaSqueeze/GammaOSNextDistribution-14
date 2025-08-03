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

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.android.wallpaper.R
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.preview.ui.binder.DualPreviewSelectorBinder
import com.android.wallpaper.picker.preview.ui.binder.PreviewActionsBinder
import com.android.wallpaper.picker.preview.ui.binder.PreviewSelectorBinder
import com.android.wallpaper.picker.preview.ui.binder.SetWallpaperButtonBinder
import com.android.wallpaper.picker.preview.ui.binder.SetWallpaperProgressDialogBinder
import com.android.wallpaper.picker.preview.ui.fragment.smallpreview.DualPreviewViewPager
import com.android.wallpaper.picker.preview.ui.fragment.smallpreview.views.TabsPagerContainer
import com.android.wallpaper.picker.preview.ui.view.PreviewActionGroup
import com.android.wallpaper.picker.preview.ui.viewmodel.Action
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.util.DisplayUtils
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * This fragment displays the preview of the selected wallpaper on all available workspaces and
 * device displays.
 */
@AndroidEntryPoint(AppbarFragment::class)
class SmallPreviewFragment : Hilt_SmallPreviewFragment() {

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var displayUtils: DisplayUtils
    @Inject @MainDispatcher lateinit var mainScope: CoroutineScope
    @Inject lateinit var logger: UserEventLogger

    private val wallpaperPreviewViewModel by activityViewModels<WallpaperPreviewViewModel>()
    private lateinit var setWallpaperProgressDialog: ProgressDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view =
            inflater.inflate(
                if (displayUtils.hasMultiInternalDisplays())
                    R.layout.fragment_small_preview_foldable
                else R.layout.fragment_small_preview_handheld,
                container,
                false,
            )
        setUpToolbar(view)
        bindScreenPreview(view)
        bindPreviewActions(view)

        SetWallpaperButtonBinder.bind(
            button = view.requireViewById(R.id.button_set_wallpaper),
            viewModel = wallpaperPreviewViewModel,
            lifecycleOwner = viewLifecycleOwner,
        ) {
            findNavController().navigate(R.id.action_smallPreviewFragment_to_setWallpaperDialog)
        }
        setWallpaperProgressDialog =
            ProgressDialog(context, R.style.LightDialogTheme).apply {
                setTitle(null)
                setMessage(context.getString(R.string.set_wallpaper_progress_message))
                isIndeterminate = true
            }
        SetWallpaperProgressDialogBinder.bind(
            dialog = setWallpaperProgressDialog,
            viewModel = wallpaperPreviewViewModel,
            lifecycleOwner = viewLifecycleOwner,
        )

        return view
    }

    override fun getDefaultTitle(): CharSequence {
        return getString(R.string.preview)
    }

    override fun getToolbarColorId(): Int {
        return android.R.color.transparent
    }

    override fun getToolbarTextColor(): Int {
        return ContextCompat.getColor(requireContext(), R.color.system_on_surface)
    }

    private fun bindScreenPreview(view: View) {
        val currentNavDestId = checkNotNull(findNavController().currentDestination?.id)
        if (displayUtils.hasMultiInternalDisplays()) {
            val dualPreviewView: DualPreviewViewPager =
                view.requireViewById(R.id.dual_preview_pager)
            val tabPager: TabsPagerContainer = view.requireViewById(R.id.pager_container)

            DualPreviewSelectorBinder.bind(
                tabPager.getViewPager(),
                dualPreviewView,
                wallpaperPreviewViewModel,
                appContext,
                viewLifecycleOwner,
                mainScope,
                currentNavDestId,
            ) { sharedElement ->
                ViewCompat.setTransitionName(sharedElement, SMALL_PREVIEW_SHARED_ELEMENT_ID)
                val extras =
                    FragmentNavigatorExtras(sharedElement to FULL_PREVIEW_SHARED_ELEMENT_ID)
                findNavController()
                    .navigate(
                        resId = R.id.action_smallPreviewFragment_to_fullPreviewFragment,
                        args = null,
                        navOptions = null,
                        navigatorExtras = extras
                    )
            }
        } else {
            val tabPager: TabsPagerContainer = view.requireViewById(R.id.pager_container)

            PreviewSelectorBinder.bind(
                tabPager.getViewPager(),
                view.requireViewById(R.id.pager_previews),
                displayUtils.getRealSize(displayUtils.getWallpaperDisplay()),
                // TODO: pass correct view models for the view pager
                wallpaperPreviewViewModel,
                appContext,
                viewLifecycleOwner,
                mainScope,
                currentNavDestId,
            ) { sharedElement ->
                ViewCompat.setTransitionName(sharedElement, SMALL_PREVIEW_SHARED_ELEMENT_ID)
                val extras =
                    FragmentNavigatorExtras(sharedElement to FULL_PREVIEW_SHARED_ELEMENT_ID)
                findNavController()
                    .navigate(
                        resId = R.id.action_smallPreviewFragment_to_fullPreviewFragment,
                        args = null,
                        navOptions = null,
                        navigatorExtras = extras
                    )
            }
        }
    }

    private fun bindPreviewActions(view: View) {
        val shareActivityResult =
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
                view
                    .findViewById<PreviewActionGroup>(R.id.action_button_group)
                    ?.setIsChecked(Action.SHARE, false)
            }
        PreviewActionsBinder.bind(
            actionGroup = view.requireViewById(R.id.action_button_group),
            floatingSheet = view.requireViewById(R.id.floating_sheet),
            previewViewModel = wallpaperPreviewViewModel,
            actionsViewModel = wallpaperPreviewViewModel.previewActionsViewModel,
            displaySize = displayUtils.getRealSize(requireActivity().display),
            lifecycleOwner = viewLifecycleOwner,
            logger = logger,
            onStartEditActivity = {
                findNavController()
                    .navigate(
                        resId = R.id.action_smallPreviewFragment_to_fullPreviewFragment,
                        args = Bundle().apply { putParcelable(ARG_EDIT_INTENT, it) },
                        navOptions = null,
                        navigatorExtras = null,
                    )
            },
            onStartShareActivity = { shareActivityResult.launch(it) },
            onShowDeleteConfirmationDialog = { viewModel ->
                val context = context ?: return@bind
                AlertDialog.Builder(context)
                    .setMessage(R.string.delete_wallpaper_confirmation)
                    .setOnDismissListener { viewModel.onDismiss.invoke() }
                    .setPositiveButton(R.string.delete_live_wallpaper) { _, _ ->
                        if (viewModel.creativeWallpaperDeleteUri != null) {
                            appContext.contentResolver.delete(
                                viewModel.creativeWallpaperDeleteUri,
                                null,
                                null
                            )
                        } else if (viewModel.liveWallpaperDeleteIntent != null) {
                            appContext.startService(viewModel.liveWallpaperDeleteIntent)
                        }
                        activity?.finish()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
        )
    }

    companion object {
        const val SMALL_PREVIEW_SHARED_ELEMENT_ID = "small_preview_shared_element"
        const val FULL_PREVIEW_SHARED_ELEMENT_ID = "full_preview_shared_element"
        const val ARG_EDIT_INTENT = "arg_edit_intent"
    }
}
