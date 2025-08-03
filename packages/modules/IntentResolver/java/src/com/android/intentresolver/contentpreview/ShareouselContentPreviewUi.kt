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
package com.android.intentresolver.contentpreview

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.intentresolver.R
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi.ActionFactory
import com.android.intentresolver.contentpreview.shareousel.ui.composable.Shareousel
import com.android.intentresolver.contentpreview.shareousel.ui.viewmodel.ShareouselViewModel
import com.android.intentresolver.contentpreview.shareousel.ui.viewmodel.toShareouselViewModel

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class ShareouselContentPreviewUi(
    private val actionFactory: ActionFactory,
) : ContentPreviewUi() {

    override fun getType(): Int = ContentPreviewType.CONTENT_PREVIEW_IMAGE

    override fun display(
        resources: Resources,
        layoutInflater: LayoutInflater,
        parent: ViewGroup,
        headlineViewParent: View?,
    ): ViewGroup {
        return displayInternal(parent, headlineViewParent).also { layout ->
            displayModifyShareAction(headlineViewParent ?: layout, actionFactory)
        }
    }

    private fun displayInternal(
        parent: ViewGroup,
        headlineViewParent: View?,
    ): ViewGroup {
        if (headlineViewParent != null) {
            inflateHeadline(headlineViewParent)
        }
        val composeView =
            ComposeView(parent.context).apply {
                setContent {
                    val vm: BasePreviewViewModel = viewModel()
                    val interactor =
                        requireNotNull(vm.payloadToggleInteractor) { "Should not be null" }

                    var viewModel by remember { mutableStateOf<ShareouselViewModel?>(null) }
                    LaunchedEffect(Unit) {
                        viewModel =
                            interactor.toShareouselViewModel(
                                vm.imageLoader,
                                actionFactory,
                                vm.viewModelScope
                            )
                    }

                    headlineViewParent?.let {
                        viewModel?.let { viewModel ->
                            LaunchedEffect(viewModel) {
                                viewModel.headline.collect { headline ->
                                    headlineViewParent
                                        .findViewById<TextView>(R.id.headline)
                                        ?.apply {
                                            if (headline.isNotBlank()) {
                                                text = headline
                                                visibility = View.VISIBLE
                                            } else {
                                                visibility = View.GONE
                                            }
                                        }
                                }
                            }
                        }
                    }

                    viewModel?.let { viewModel ->
                        MaterialTheme(
                            colorScheme =
                                if (isSystemInDarkTheme()) {
                                    dynamicDarkColorScheme(LocalContext.current)
                                } else {
                                    dynamicLightColorScheme(LocalContext.current)
                                },
                        ) {
                            Shareousel(viewModel = viewModel)
                        }
                    }
                        ?: run {
                            Spacer(
                                Modifier.height(
                                    dimensionResource(R.dimen.chooser_preview_image_height_tall)
                                )
                            )
                        }
                }
            }
        return composeView
    }
}
