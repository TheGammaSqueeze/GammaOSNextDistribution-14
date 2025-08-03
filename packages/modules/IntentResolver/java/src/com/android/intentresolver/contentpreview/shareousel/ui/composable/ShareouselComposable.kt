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
package com.android.intentresolver.contentpreview.shareousel.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.intentresolver.R
import com.android.intentresolver.contentpreview.shareousel.ui.viewmodel.ShareouselImageViewModel
import com.android.intentresolver.contentpreview.shareousel.ui.viewmodel.ShareouselViewModel

@Composable
fun Shareousel(viewModel: ShareouselViewModel) {
    val centerIdx = viewModel.centerIndex.value
    val carouselState = rememberLazyListState(initialFirstVisibleItemIndex = centerIdx)
    val previewKeys by viewModel.previewKeys.collectAsStateWithLifecycle()
    Column {
        // TODO: item needs to be centered, check out ScalingLazyColumn impl or see if
        //  HorizontalPager works for our use-case
        LazyRow(
            state = carouselState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.chooser_preview_image_height_tall))
        ) {
            items(previewKeys, key = viewModel.previewRowKey) { key ->
                ShareouselCard(viewModel.previewForKey(key))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val actions by viewModel.actions.collectAsStateWithLifecycle(initialValue = emptyList())
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(actions) { actionViewModel ->
                ShareouselAction(
                    label = actionViewModel.label,
                    onClick = actionViewModel.onClick,
                ) {
                    actionViewModel.icon?.let { Image(it) }
                }
            }
        }
    }
}

private const val MIN_ASPECT_RATIO = 0.4f
private const val MAX_ASPECT_RATIO = 2.5f

@Composable
private fun ShareouselCard(viewModel: ShareouselImageViewModel) {
    val bitmap by viewModel.bitmap.collectAsStateWithLifecycle(initialValue = null)
    val selected by viewModel.isSelected.collectAsStateWithLifecycle(initialValue = false)
    val contentDescription by
        viewModel.contentDescription.collectAsStateWithLifecycle(initialValue = null)
    val borderColor = MaterialTheme.colorScheme.primary

    ShareouselCard(
        image = {
            bitmap?.let { bitmap ->
                val aspectRatio =
                    (bitmap.width.toFloat() / bitmap.height.toFloat())
                        // TODO: max ratio is actually equal to the viewport ratio
                        .coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(aspectRatio),
                )
            }
                ?: run {
                    // TODO: look at ScrollableImagePreviewView.setLoading()
                    Box(modifier = Modifier.aspectRatio(2f / 5f))
                }
        },
        selected = selected,
        modifier =
            Modifier.thenIf(selected) {
                    Modifier.border(
                        width = 4.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(size = 12.dp)
                    )
                }
                .clip(RoundedCornerShape(size = 12.dp))
                .clickable { viewModel.setSelected(!selected) },
    )
}

@Composable
private fun ShareouselAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        modifier = modifier
    )
}

inline fun Modifier.thenIf(condition: Boolean, crossinline factory: () -> Modifier): Modifier =
    if (condition) this.then(factory()) else this
