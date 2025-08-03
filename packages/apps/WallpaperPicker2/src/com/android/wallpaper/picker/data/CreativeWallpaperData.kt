/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.wallpaper.picker.data

import android.net.Uri

/** Represents data that is specific to only CreativeWallpapers. */
data class CreativeWallpaperData(
    val configPreviewUri: Uri?,
    val cleanPreviewUri: Uri?,
    val deleteUri: Uri?,
    val thumbnailUri: Uri?,
    val shareUri: Uri?,
    val author: String,
    val description: String,
    val contentDescription: String?,
    val isCurrent: Boolean,
)
