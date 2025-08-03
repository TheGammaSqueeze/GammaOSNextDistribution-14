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
package com.android.wallpaper.module

import android.content.Context
import com.android.wallpaper.module.logging.NoOpUserEventLogger
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.preview.data.util.DefaultLiveWallpaperDownloader
import com.android.wallpaper.picker.preview.data.util.LiveWallpaperDownloader
import com.android.wallpaper.util.converter.DefaultWallpaperModelFactory
import com.android.wallpaper.util.converter.WallpaperModelFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds @Singleton abstract fun bindInjector(impl: WallpaperPicker2Injector): Injector

    @Binds
    @Singleton
    abstract fun bindWallpaperModelFactory(
        impl: DefaultWallpaperModelFactory
    ): WallpaperModelFactory

    @Binds
    @Singleton
    abstract fun bindLiveWallpaperDownloader(
        impl: DefaultLiveWallpaperDownloader
    ): LiveWallpaperDownloader

    companion object {
        @Provides
        @Singleton
        fun provideWallpaperPreferences(
            @ApplicationContext context: Context
        ): WallpaperPreferences {
            return DefaultWallpaperPreferences(context)
        }

        @Provides
        @Singleton
        fun provideUserEventLogger(): UserEventLogger {
            return NoOpUserEventLogger()
        }
    }
}
