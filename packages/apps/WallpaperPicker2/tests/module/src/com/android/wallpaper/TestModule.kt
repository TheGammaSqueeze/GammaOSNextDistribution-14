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
package com.android.wallpaper

import com.android.wallpaper.module.AppModule
import com.android.wallpaper.module.Injector
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.logging.TestUserEventLogger
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.preview.data.util.DefaultLiveWallpaperDownloader
import com.android.wallpaper.picker.preview.data.util.LiveWallpaperDownloader
import com.android.wallpaper.testing.TestInjector
import com.android.wallpaper.testing.TestWallpaperPreferences
import com.android.wallpaper.util.converter.DefaultWallpaperModelFactory
import com.android.wallpaper.util.converter.WallpaperModelFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
abstract class TestModule {
    @Binds @Singleton abstract fun bindInjector(impl: TestInjector): Injector

    @Binds @Singleton abstract fun bindUserEventLogger(impl: TestUserEventLogger): UserEventLogger

    @Binds
    @Singleton
    abstract fun bindWallpaperPreferences(impl: TestWallpaperPreferences): WallpaperPreferences

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
}
