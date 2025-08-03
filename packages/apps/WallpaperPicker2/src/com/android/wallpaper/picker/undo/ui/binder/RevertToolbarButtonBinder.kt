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

package com.android.wallpaper.picker.undo.ui.binder

import android.app.Dialog
import android.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.picker.common.dialog.ui.viewbinder.DialogViewBinder
import com.android.wallpaper.picker.common.dialog.ui.viewmodel.DialogViewModel
import com.android.wallpaper.picker.undo.ui.viewmodel.UndoViewModel
import kotlinx.coroutines.launch

object RevertToolbarButtonBinder {
    /** Binds the given view to the given view-model. */
    fun bind(
        view: Toolbar,
        viewModel: UndoViewModel,
        lifecycleOwner: LifecycleOwner,
    ) {
        val menuItem = view.menu.findItem(R.id.revert)
        menuItem.setOnMenuItemClickListener {
            viewModel.onRevertButtonClicked()
            true
        }

        var dialog: Dialog? = null

        fun showDialog(viewModel: DialogViewModel) {
            dialog =
                DialogViewBinder.show(
                    context = view.context,
                    viewModel = viewModel,
                    dialogStyleResourceId = R.style.ResetDialogTheme,
                )
            dialog?.show()
        }

        fun dismissDialog() {
            dialog?.setOnDismissListener(null)
            dialog?.dismiss()
            dialog = null
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isRevertButtonVisible.collect { menuItem.isVisible = it } }

                launch {
                    viewModel.dialog.collect { dialogViewModel ->
                        if (dialogViewModel != null) {
                            dismissDialog()
                            showDialog(dialogViewModel)
                        } else {
                            dismissDialog()
                        }
                    }
                }
            }
        }
    }
}
