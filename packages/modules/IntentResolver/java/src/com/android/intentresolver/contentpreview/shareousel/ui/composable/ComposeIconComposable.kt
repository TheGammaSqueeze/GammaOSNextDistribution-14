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

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.android.intentresolver.icon.AdaptiveIcon
import com.android.intentresolver.icon.BitmapIcon
import com.android.intentresolver.icon.ComposeIcon
import com.android.intentresolver.icon.ResourceIcon

@Composable
fun Image(icon: ComposeIcon) {
    when (icon) {
        is AdaptiveIcon -> Image(icon.wrapped)
        is BitmapIcon -> Image(icon.bitmap.asImageBitmap(), contentDescription = null)
        is ResourceIcon -> {
            val localContext = LocalContext.current
            val wrappedContext: Context =
                object : ContextWrapper(localContext) {
                    override fun getResources(): Resources = icon.res
                }
            CompositionLocalProvider(LocalContext provides wrappedContext) {
                Image(painterResource(icon.resId), contentDescription = null)
            }
        }
    }
}
