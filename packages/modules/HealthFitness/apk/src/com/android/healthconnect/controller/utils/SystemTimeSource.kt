/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.utils

import android.content.Context
import android.text.format.DateFormat.is24HourFormat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Singleton

/** Time source that uses the system time. */
object SystemTimeSource : TimeSource {
    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    override fun deviceZoneOffset(): ZoneId {
        return ZoneId.systemDefault()
    }

    override fun currentLocalDateTime(): LocalDateTime {
        return Instant.ofEpochMilli(currentTimeMillis())
            .atZone(deviceZoneOffset())
            .toLocalDateTime()
    }

    override fun is24Hour(context: Context): Boolean {
        return is24HourFormat(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
class SystemTimeSourceModule {
    @Provides
    @Singleton
    fun providesSystemTimeSourceModule(): TimeSource {
        return SystemTimeSource
    }
}
