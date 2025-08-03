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

package com.android.intentresolver.contentpreview

/**
 * HeadlineGenerator generates the text to show at the top of the sharesheet as a brief description
 * of the content being shared.
 */
interface HeadlineGenerator {
    fun getTextHeadline(text: CharSequence): String

    fun getAlbumHeadline(): String

    fun getImagesWithTextHeadline(text: CharSequence, count: Int): String

    fun getVideosWithTextHeadline(text: CharSequence, count: Int): String

    fun getFilesWithTextHeadline(text: CharSequence, count: Int): String

    fun getImagesHeadline(count: Int): String

    fun getVideosHeadline(count: Int): String

    fun getFilesHeadline(count: Int): String
}
