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
 *
 */

package com.android.customization.picker.grid.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.customization.model.ResourceConstants
import com.android.customization.picker.grid.domain.interactor.GridInteractor
import com.android.customization.picker.grid.shared.model.GridOptionItemsModel
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GridScreenViewModel(
    context: Context,
    private val interactor: GridInteractor,
) : ViewModel() {

    @SuppressLint("StaticFieldLeak") // We're not leaking this context as it is the app context.
    private val applicationContext = context.applicationContext

    val optionItems: Flow<List<OptionItemViewModel<GridIconViewModel>>> =
        interactor.options.map { model -> toViewModel(model) }

    private fun toViewModel(
        model: GridOptionItemsModel,
    ): List<OptionItemViewModel<GridIconViewModel>> {
        val iconShapePath =
            applicationContext.resources.getString(
                Resources.getSystem()
                    .getIdentifier(
                        ResourceConstants.CONFIG_ICON_MASK,
                        "string",
                        ResourceConstants.ANDROID_PACKAGE,
                    )
            )

        return when (model) {
            is GridOptionItemsModel.Loaded ->
                model.options.map { option ->
                    val text = Text.Loaded(option.name)
                    OptionItemViewModel<GridIconViewModel>(
                        key =
                            MutableStateFlow("${option.cols}x${option.rows}") as StateFlow<String>,
                        payload =
                            GridIconViewModel(
                                columns = option.cols,
                                rows = option.rows,
                                path = iconShapePath,
                            ),
                        text = text,
                        isSelected = option.isSelected,
                        onClicked =
                            option.isSelected.map { isSelected ->
                                if (!isSelected) {
                                    { viewModelScope.launch { option.onSelected() } }
                                } else {
                                    null
                                }
                            },
                    )
                }
            is GridOptionItemsModel.Error -> emptyList()
        }
    }

    class Factory(
        context: Context,
        private val interactor: GridInteractor,
    ) : ViewModelProvider.Factory {

        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GridScreenViewModel(
                context = applicationContext,
                interactor = interactor,
            )
                as T
        }
    }
}
