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
package com.android.wallpaper.picker.preview.ui.binder

import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.preview.ui.view.PreviewActionFloatingSheet
import com.android.wallpaper.picker.preview.ui.view.PreviewActionGroup
import com.android.wallpaper.picker.preview.ui.viewmodel.Action.CUSTOMIZE
import com.android.wallpaper.picker.preview.ui.viewmodel.Action.DELETE
import com.android.wallpaper.picker.preview.ui.viewmodel.Action.DOWNLOAD
import com.android.wallpaper.picker.preview.ui.viewmodel.Action.EDIT
import com.android.wallpaper.picker.preview.ui.viewmodel.Action.EFFECTS
import com.android.wallpaper.picker.preview.ui.viewmodel.Action.INFORMATION
import com.android.wallpaper.picker.preview.ui.viewmodel.Action.SHARE
import com.android.wallpaper.picker.preview.ui.viewmodel.DeleteConfirmationDialogViewModel
import com.android.wallpaper.picker.preview.ui.viewmodel.PreviewActionsViewModel
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import kotlinx.coroutines.launch

/** Binds the action buttons and bottom sheet to [PreviewActionsViewModel] */
object PreviewActionsBinder {
    fun bind(
        actionGroup: PreviewActionGroup,
        floatingSheet: PreviewActionFloatingSheet,
        previewViewModel: WallpaperPreviewViewModel,
        actionsViewModel: PreviewActionsViewModel,
        displaySize: Point,
        lifecycleOwner: LifecycleOwner,
        logger: UserEventLogger,
        onStartEditActivity: (intent: Intent) -> Unit,
        onStartShareActivity: (intent: Intent) -> Unit,
        onShowDeleteConfirmationDialog: (videModel: DeleteConfirmationDialogViewModel) -> Unit,
    ) {
        val floatingSheetCallback =
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(view: View, newState: Int) {
                    if (newState == STATE_HIDDEN) {
                        actionsViewModel.onFloatingSheetCollapsed()
                    }
                }

                override fun onSlide(p0: View, p1: Float) {}
            }
        floatingSheet.addFloatingSheetCallback(floatingSheetCallback)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                floatingSheet.addFloatingSheetCallback(floatingSheetCallback)
            }
            floatingSheet.removeFloatingSheetCallback(floatingSheetCallback)
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                /** [INFORMATION] */
                launch {
                    actionsViewModel.isInformationVisible.collect {
                        actionGroup.setIsVisible(INFORMATION, it)
                    }
                }

                launch {
                    actionsViewModel.isInformationChecked.collect {
                        actionGroup.setIsChecked(INFORMATION, it)
                    }
                }

                launch {
                    actionsViewModel.onInformationClicked.collect {
                        actionGroup.setClickListener(INFORMATION, it)
                    }
                }

                launch {
                    actionsViewModel.informationFloatingSheetViewModel.collect { viewModel ->
                        if (viewModel == null) {
                            floatingSheet.collapse()
                        } else {
                            val onExploreButtonClicked =
                                viewModel.exploreActionUrl?.let { url ->
                                    {
                                        logger.logWallpaperExploreButtonClicked()
                                        val appContext = floatingSheet.context.applicationContext
                                        appContext.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        )
                                    }
                                }
                            floatingSheet.setInformationContent(
                                viewModel.attributions,
                                onExploreButtonClicked
                            )
                            floatingSheet.expand()
                        }
                    }
                }

                /** [DOWNLOAD] */
                launch {
                    actionsViewModel.isDownloadVisible.collect {
                        actionGroup.setIsVisible(DOWNLOAD, it)
                    }
                }

                launch {
                    actionsViewModel.isDownloading.collect { actionGroup.setIsDownloading(it) }
                }

                launch {
                    actionsViewModel.isDownloadButtonEnabled.collect {
                        actionGroup.setClickListener(
                            DOWNLOAD,
                            if (it) {
                                {
                                    lifecycleOwner.lifecycleScope.launch {
                                        actionsViewModel.downloadWallpaper()
                                    }
                                }
                            } else null,
                        )
                    }
                }

                /** [DELETE] */
                launch {
                    actionsViewModel.isDeleteVisible.collect {
                        actionGroup.setIsVisible(DELETE, it)
                    }
                }

                launch {
                    actionsViewModel.isDeleteChecked.collect {
                        actionGroup.setIsChecked(DELETE, it)
                    }
                }

                launch {
                    actionsViewModel.onDeleteClicked.collect {
                        actionGroup.setClickListener(DELETE, it)
                    }
                }

                launch {
                    actionsViewModel.deleteConfirmationDialogViewModel.collect { viewModel ->
                        if (viewModel != null) {
                            onShowDeleteConfirmationDialog.invoke(viewModel)
                        }
                    }
                }

                /** [EDIT] */
                launch {
                    actionsViewModel.isEditVisible.collect { actionGroup.setIsVisible(EDIT, it) }
                }

                launch {
                    actionsViewModel.isEditChecked.collect { actionGroup.setIsChecked(EDIT, it) }
                }

                launch {
                    actionsViewModel.editIntent.collect {
                        actionGroup.setClickListener(
                            EDIT,
                            if (it != null) {
                                {
                                    // We need to set default wallpaper preview config view model
                                    // before entering full screen with edit activity overlay.
                                    previewViewModel.setDefaultWallpaperPreviewConfigViewModel(
                                        displaySize
                                    )
                                    onStartEditActivity.invoke(it)
                                }
                            } else null
                        )
                    }
                }

                /** [CUSTOMIZE] */
                launch {
                    actionsViewModel.isCustomizeVisible.collect {
                        actionGroup.setIsVisible(CUSTOMIZE, it)
                    }
                }

                launch {
                    actionsViewModel.isCustomizeChecked.collect {
                        actionGroup.setIsChecked(CUSTOMIZE, it)
                    }
                }

                launch {
                    actionsViewModel.onCustomizeClicked.collect {
                        actionGroup.setClickListener(CUSTOMIZE, it)
                    }
                }

                /** [EFFECTS] */
                launch {
                    actionsViewModel.isEffectsVisible.collect {
                        actionGroup.setIsVisible(EFFECTS, it)
                    }
                }

                launch {
                    actionsViewModel.isEffectsChecked.collect {
                        actionGroup.setIsChecked(EFFECTS, it)
                    }
                }

                launch {
                    actionsViewModel.onEffectsClicked.collect {
                        actionGroup.setClickListener(EFFECTS, it)
                    }
                }

                launch {
                    actionsViewModel.effectFloatingSheetViewModel.collect { viewModel ->
                        if (viewModel == null) {
                            floatingSheet.collapse()
                        } else {
                            floatingSheet.setEffectContent(
                                viewModel.effectType,
                                viewModel.myPhotosClickListener,
                                viewModel.collapseFloatingSheetListener,
                                viewModel.effectSwitchListener,
                                viewModel.effectDownloadClickListener,
                                viewModel.status,
                                viewModel.resultCode,
                                viewModel.errorMessage,
                                viewModel.title,
                                viewModel.effectTextRes,
                            )
                            floatingSheet.expand()
                        }
                    }
                }

                /** [SHARE] */
                launch {
                    actionsViewModel.isShareVisible.collect { actionGroup.setIsVisible(SHARE, it) }
                }

                launch {
                    actionsViewModel.shareIntent.collect {
                        actionGroup.setClickListener(
                            SHARE,
                            if (it != null) {
                                { onStartShareActivity.invoke(it) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
