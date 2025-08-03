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

package com.android.wallpaper.util.converter.category

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.wallpaper.model.ImageCategory
import com.android.wallpaper.model.PlaceholderCategory
import com.android.wallpaper.picker.data.category.CategoryModel
import com.android.wallpaper.util.converter.WallpaperModelFactory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class CategoryFactoryTest {

    @get:Rule var hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context

    @Inject lateinit var wallpaperModelFactory: WallpaperModelFactory

    private lateinit var mCategoryFactory: CategoryFactory

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        mCategoryFactory = CategoryFactory(wallpaperModelFactory)
    }

    @Test
    fun testGetCategoryModel() {
        val placeholderCategory = PlaceholderCategory(TEST_TITLE, TEST_COLLECTIONID, TEST_PRIORITY)

        val result = mCategoryFactory.getCategoryModel(context, placeholderCategory)

        validateCommonCategoryData(result)
        assertEquals(result.collectionCategoryData, null)
        assertEquals(result.imageCategoryData, null)
        assertEquals(result.thirdPartyCategoryData, null)
    }

    @Test
    fun testGetImageCategoryModel() {
        val imageCategory = ImageCategory(TEST_TITLE, TEST_COLLECTIONID, TEST_PRIORITY)
        val result = mCategoryFactory.getCategoryModel(context, imageCategory)
        validateCommonCategoryData(result)
    }

    private fun validateCommonCategoryData(result: CategoryModel) {
        assertEquals(TEST_TITLE, result.commonCategoryData.title)
        assertEquals(TEST_COLLECTIONID, result.commonCategoryData.collectionId)
        assertEquals(TEST_PRIORITY, result.commonCategoryData.priority)
    }

    companion object {
        const val TEST_TITLE = "Test-Title"
        const val TEST_COLLECTIONID = "Test-Collection-Id"
        const val TEST_PRIORITY = 1
    }
}
