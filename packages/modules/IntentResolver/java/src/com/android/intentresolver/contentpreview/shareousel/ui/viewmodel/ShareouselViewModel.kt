/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.intentresolver.contentpreview.shareousel.ui.viewmodel

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi.ActionFactory
import com.android.intentresolver.contentpreview.ImageLoader
import com.android.intentresolver.contentpreview.MutableActionFactory
import com.android.intentresolver.contentpreview.PayloadToggleInteractor
import com.android.intentresolver.icon.BitmapIcon
import com.android.intentresolver.icon.ComposeIcon
import com.android.intentresolver.widget.ActionRow.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ShareouselViewModel(
    val headline: Flow<String>,
    val previewKeys: StateFlow<List<Any>>,
    val actions: Flow<List<ActionChipViewModel>>,
    val centerIndex: StateFlow<Int>,
    val previewForKey: (key: Any) -> ShareouselImageViewModel,
    val previewRowKey: (Any) -> Any
)

data class ActionChipViewModel(val label: String, val icon: ComposeIcon?, val onClick: () -> Unit)

data class ShareouselImageViewModel(
    val bitmap: Flow<Bitmap?>,
    val contentDescription: Flow<String>,
    val isSelected: Flow<Boolean>,
    val setSelected: (Boolean) -> Unit,
)

suspend fun PayloadToggleInteractor.toShareouselViewModel(
    imageLoader: ImageLoader,
    actionFactory: ActionFactory,
    scope: CoroutineScope,
): ShareouselViewModel {
    return ShareouselViewModel(
        headline = MutableStateFlow("Shareousel"),
        previewKeys = previewKeys.stateIn(scope),
        actions =
            if (actionFactory is MutableActionFactory) {
                actionFactory.customActionsFlow.map { actions ->
                    actions.map { it.toActionChipViewModel() }
                }
            } else {
                flow {
                    emit(actionFactory.createCustomActions().map { it.toActionChipViewModel() })
                }
            },
        centerIndex = targetPosition.stateIn(scope),
        previewForKey = { key ->
            val previewInteractor = previewInteractor(key)
            ShareouselImageViewModel(
                bitmap = previewInteractor.previewUri.map { uri -> uri?.let { imageLoader(uri) } },
                contentDescription = MutableStateFlow(""),
                isSelected = previewInteractor.selected,
                setSelected = { isSelected -> previewInteractor.setSelected(isSelected) },
            )
        },
        previewRowKey = { getKey(it) },
    )
}

private fun Action.toActionChipViewModel() =
    ActionChipViewModel(
        label?.toString() ?: "",
        icon?.let { BitmapIcon(it.toBitmap()) },
        onClick = { onClicked.run() }
    )
