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

package com.android.intentresolver.inject

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object ConcurrencyModule {

    @Provides @Main fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    /** Injectable alternative to [MainScope()][kotlinx.coroutines.MainScope] */
    @Provides
    @Singleton
    @Main
    fun mainCoroutineScope(@Main mainDispatcher: CoroutineDispatcher) =
        CoroutineScope(SupervisorJob() + mainDispatcher)

    @Provides @Background fun backgroundDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
