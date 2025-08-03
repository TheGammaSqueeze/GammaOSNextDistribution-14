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
import android.util.Log
import com.android.wallpaper.model.Category
import com.android.wallpaper.model.ImageCategory
import com.android.wallpaper.model.ThirdPartyAppCategory
import com.android.wallpaper.model.WallpaperCategory
import com.android.wallpaper.picker.data.category.CategoryModel
import com.android.wallpaper.picker.data.category.CollectionCategoryData
import com.android.wallpaper.picker.data.category.CommonCategoryData
import com.android.wallpaper.picker.data.category.ImageCategoryData
import com.android.wallpaper.picker.data.category.ThirdPartyCategoryData
import com.android.wallpaper.util.converter.WallpaperModelFactory
import javax.inject.Inject
import javax.inject.Singleton

/** This class creates an instance of [CategoryModel] from an instance of [Category] object. */
@Singleton
class CategoryFactory
@Inject
constructor(private val wallpaperModelFactory: WallpaperModelFactory) {

    fun getCategoryModel(context: Context, category: Category): CategoryModel {
        return CategoryModel(
            commonCategoryData = getCommonCategoryData(category),
            collectionCategoryData =
                (category as? WallpaperCategory)?.getCollectionsCategoryData(context),
            imageCategoryData = getImageCategoryData(category, context),
            thirdPartyCategoryData = getThirdPartyCategoryData(category)
        )
    }

    private fun getCommonCategoryData(category: Category): CommonCategoryData {
        return CommonCategoryData(
            title = category.title,
            collectionId = category.collectionId,
            priority = category.priority
        )
    }

    private fun WallpaperCategory.getCollectionsCategoryData(
        context: Context
    ): CollectionCategoryData? {
        val wallpaperModelList =
            wallpapers.map { wallpaperInfo ->
                wallpaperModelFactory.getWallpaperModel(context, wallpaperInfo)
            }
        return CollectionCategoryData(
            wallpaperModels = wallpaperModelList,
            thumbAsset = thumbAsset,
            featuredThumbnailIndex = featuredThumbnailIndex,
            isSingleWallpaperCategory = isSingleWallpaperCategory,
        )
    }

    private fun getImageCategoryData(category: Category, context: Context): ImageCategoryData? {
        return if (category is ImageCategory) {
            ImageCategoryData(overlayIconDrawable = category.getOverlayIcon(context))
        } else {
            Log.w(TAG, "Passed category is not of type ImageCategory")
            null
        }
    }

    private fun getThirdPartyCategoryData(category: Category): ThirdPartyCategoryData? {
        return if (category is ThirdPartyAppCategory) {
            ThirdPartyCategoryData(resolveInfo = category.resolveInfo)
        } else {
            Log.w(TAG, "Passed category is not of type ThirdPartyAppCategory")
            null
        }
    }

    companion object {
        private const val TAG = "DefaultCategoryFactory"
    }
}
