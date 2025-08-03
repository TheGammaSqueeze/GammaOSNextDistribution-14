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
package com.android.systemui.tv.dagger

import android.app.Service
import com.android.systemui.SystemUIService
import com.android.systemui.dump.SystemUIAuxiliaryDumpService
import com.android.systemui.wallpapers.ImageWallpaper
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Services that are injectable should go here. */
@Module
abstract class TvServiceBinder {
  @Binds
  @IntoMap
  @ClassKey(SystemUIService::class)
  abstract fun bindSystemUIService(service: SystemUIService): Service

  @Binds
  @IntoMap
  @ClassKey(SystemUIAuxiliaryDumpService::class)
  abstract fun bindSystemUIAuxiliaryDumpService(service: SystemUIAuxiliaryDumpService): Service

  @Binds
  @IntoMap
  @ClassKey(ImageWallpaper::class)
  abstract fun bindImageWallpaper(service: ImageWallpaper): Service
}
