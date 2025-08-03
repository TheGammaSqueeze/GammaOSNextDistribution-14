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
package com.android.wallpaper.picker.di.modules

import android.content.Context
import com.android.wallpaper.R
import com.android.wallpaper.util.PreviewUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Qualifier

/*
 * This class provides the preview utils instances required for a specific screen type
 */
@InstallIn(ActivityRetainedComponent::class)
@Module
internal object PreviewUtilsModule {

    @Qualifier @Retention(AnnotationRetention.BINARY) annotation class LockScreenPreviewUtils

    @Qualifier @Retention(AnnotationRetention.BINARY) annotation class HomeScreenPreviewUtils

    @LockScreenPreviewUtils
    @ActivityRetainedScoped
    @Provides
    fun provideLockScreenPreviewUtils(
        @ApplicationContext appContext: Context,
    ): PreviewUtils {
        return PreviewUtils(
            context = appContext,
            authority =
                appContext.getString(
                    R.string.lock_screen_preview_provider_authority,
                ),
        )
    }

    @HomeScreenPreviewUtils
    @ActivityRetainedScoped
    @Provides
    fun provideHomeScreenPreviewUtils(
        @ApplicationContext appContext: Context,
    ): PreviewUtils {
        return PreviewUtils(
            context = appContext,
            authorityMetadataKey =
                appContext.getString(
                    R.string.grid_control_metadata_name,
                ),
        )
    }
}
