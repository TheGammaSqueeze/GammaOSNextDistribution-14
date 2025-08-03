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

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Qualifier for main thread [CoroutineDispatcher] bound to app lifecycle. */
@Qualifier annotation class MainDispatcher

/** Qualifier for background thread [CoroutineDispatcher] for long running and blocking tasks. */
@Qualifier annotation class BackgroundDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object DispatchersModule {

    @Provides
    @MainDispatcher
    fun provideMainScope(): CoroutineScope = CoroutineScope(Dispatchers.Main)

    @Provides @MainDispatcher fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @BackgroundDispatcher
    fun provideBackgroundScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Provides
    @BackgroundDispatcher
    fun provideBackgroundDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
