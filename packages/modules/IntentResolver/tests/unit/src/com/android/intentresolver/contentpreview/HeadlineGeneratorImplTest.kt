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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadlineGeneratorImplTest {
    private val generator =
        HeadlineGeneratorImpl(InstrumentationRegistry.getInstrumentation().targetContext)
    private val str = "Some string"
    private val url = "http://www.google.com"

    @Test
    fun testTextHeadline() {
        assertThat(generator.getTextHeadline(str)).isEqualTo("Sharing text")
        assertThat(generator.getTextHeadline(url)).isEqualTo("Sharing link")
    }

    @Test
    fun testImagesWIthTextHeadline() {
        assertThat(generator.getImagesWithTextHeadline(str, 1)).isEqualTo("Sharing image with text")
        assertThat(generator.getImagesWithTextHeadline(url, 1)).isEqualTo("Sharing image with link")
        assertThat(generator.getImagesWithTextHeadline(str, 5))
            .isEqualTo("Sharing 5 images with text")
        assertThat(generator.getImagesWithTextHeadline(url, 5))
            .isEqualTo("Sharing 5 images with link")
    }

    @Test
    fun testVideosWithTextHeadline() {
        assertThat(generator.getVideosWithTextHeadline(str, 1)).isEqualTo("Sharing video with text")
        assertThat(generator.getVideosWithTextHeadline(url, 1)).isEqualTo("Sharing video with link")
        assertThat(generator.getVideosWithTextHeadline(str, 5))
            .isEqualTo("Sharing 5 videos with text")
        assertThat(generator.getVideosWithTextHeadline(url, 5))
            .isEqualTo("Sharing 5 videos with link")
    }

    @Test
    fun testFilesWithTextHeadline() {
        assertThat(generator.getFilesWithTextHeadline(str, 1)).isEqualTo("Sharing file with text")
        assertThat(generator.getFilesWithTextHeadline(url, 1)).isEqualTo("Sharing file with link")
        assertThat(generator.getFilesWithTextHeadline(str, 5))
            .isEqualTo("Sharing 5 files with text")
        assertThat(generator.getFilesWithTextHeadline(url, 5))
            .isEqualTo("Sharing 5 files with link")
    }

    @Test
    fun testImagesHeadline() {
        assertThat(generator.getImagesHeadline(1)).isEqualTo("Sharing image")
        assertThat(generator.getImagesHeadline(4)).isEqualTo("Sharing 4 images")
    }

    @Test
    fun testVideosHeadline() {
        assertThat(generator.getVideosHeadline(1)).isEqualTo("Sharing video")
        assertThat(generator.getVideosHeadline(4)).isEqualTo("Sharing 4 videos")
    }

    @Test
    fun testFilesHeadline() {
        assertThat(generator.getFilesHeadline(1)).isEqualTo("Sharing 1 file")
        assertThat(generator.getFilesHeadline(4)).isEqualTo("Sharing 4 files")
    }

    @Test
    fun testAlbumHeadline() {
        assertThat(generator.getAlbumHeadline()).isEqualTo("Sharing album")
    }
}
