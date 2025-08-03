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

import android.content.Context
import android.util.PluralsMessageFormatter
import androidx.annotation.StringRes
import com.android.intentresolver.R

private const val PLURALS_COUNT = "count"

/**
 * HeadlineGenerator generates the text to show at the top of the sharesheet as a brief description
 * of the content being shared.
 */
class HeadlineGeneratorImpl(private val context: Context) : HeadlineGenerator {
    override fun getTextHeadline(text: CharSequence): String {
        return context.getString(
            getTemplateResource(text, R.string.sharing_link, R.string.sharing_text)
        )
    }

    override fun getAlbumHeadline(): String {
        return context.getString(R.string.sharing_album)
    }

    override fun getImagesWithTextHeadline(text: CharSequence, count: Int): String {
        return getPluralString(
            getTemplateResource(
                text,
                R.string.sharing_images_with_link,
                R.string.sharing_images_with_text
            ),
            count
        )
    }

    override fun getVideosWithTextHeadline(text: CharSequence, count: Int): String {
        return getPluralString(
            getTemplateResource(
                text,
                R.string.sharing_videos_with_link,
                R.string.sharing_videos_with_text
            ),
            count
        )
    }

    override fun getFilesWithTextHeadline(text: CharSequence, count: Int): String {
        return getPluralString(
            getTemplateResource(
                text,
                R.string.sharing_files_with_link,
                R.string.sharing_files_with_text
            ),
            count
        )
    }

    override fun getImagesHeadline(count: Int): String {
        return getPluralString(R.string.sharing_images, count)
    }

    override fun getVideosHeadline(count: Int): String {
        return getPluralString(R.string.sharing_videos, count)
    }

    override fun getFilesHeadline(count: Int): String {
        return getPluralString(R.string.sharing_files, count)
    }

    private fun getPluralString(@StringRes templateResource: Int, count: Int): String {
        return PluralsMessageFormatter.format(
            context.resources,
            mapOf(PLURALS_COUNT to count),
            templateResource
        )
    }

    @StringRes
    private fun getTemplateResource(
        text: CharSequence,
        @StringRes linkResource: Int,
        @StringRes nonLinkResource: Int
    ): Int {
        return if (text.toString().isHttpUri()) linkResource else nonLinkResource
    }
}
