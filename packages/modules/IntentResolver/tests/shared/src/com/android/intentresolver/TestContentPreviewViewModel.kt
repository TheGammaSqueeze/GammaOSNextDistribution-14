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

package com.android.intentresolver

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.intentresolver.contentpreview.BasePreviewViewModel
import com.android.intentresolver.contentpreview.ImageLoader
import com.android.intentresolver.contentpreview.PayloadToggleInteractor

/** A test content preview model that supports image loader override. */
class TestContentPreviewViewModel(
    private val viewModel: BasePreviewViewModel,
    override val imageLoader: ImageLoader,
) : BasePreviewViewModel() {

    override val previewDataProvider
        get() = viewModel.previewDataProvider

    override val payloadToggleInteractor: PayloadToggleInteractor?
        get() = viewModel.payloadToggleInteractor

    override fun init(
        targetIntent: Intent,
        chooserIntent: Intent,
        additionalContentUri: Uri?,
        focusedItemIdx: Int,
        isPayloadTogglingEnabled: Boolean,
    ) {
        viewModel.init(
            targetIntent,
            chooserIntent,
            additionalContentUri,
            focusedItemIdx,
            isPayloadTogglingEnabled
        )
    }

    companion object {
        fun wrap(
            factory: ViewModelProvider.Factory,
            imageLoader: ImageLoader?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val wrapped = factory.create(modelClass, extras) as BasePreviewViewModel
                    return TestContentPreviewViewModel(
                        wrapped,
                        imageLoader ?: wrapped.imageLoader,
                    )
                        as T
                }
            }
    }
}
