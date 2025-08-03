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
 *
 *
 */

package com.android.healthconnect.controller.shared.app

import android.content.Context
import android.content.res.Resources
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Helper class to determine whether a package name is the default Health Connect app on this
 * device.
 */
interface AppUtils {
    fun isDefaultApp(context: Context, packageName: String): Boolean
}

class AppUtilsImpl @Inject constructor() : AppUtils {
    private val DEFAULT_APP_CONFIG_STRING = "android:string/config_defaultHealthConnectApp"

    override fun isDefaultApp(context: Context, packageName: String): Boolean {
        val defaultApp =
            context.resources.getString(
                Resources.getSystem().getIdentifier(DEFAULT_APP_CONFIG_STRING, null, null))

        return packageName == defaultApp
    }
}

@Module
@InstallIn(SingletonComponent::class)
class AppUtilsModule {
    @Provides
    fun providesAppUtils(): AppUtils {
        return AppUtilsImpl()
    }
}
