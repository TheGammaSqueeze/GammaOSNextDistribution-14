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

package android.tools

const val FLICKER_TAG = "FLICKER"
const val MILLISECOND_AS_NANOSECONDS: Long = 1000000
const val SECOND_AS_NANOSECONDS: Long = 1000000000
const val MINUTE_AS_NANOSECONDS: Long = 60000000000
const val HOUR_AS_NANOSECONDS: Long = 3600000000000
const val DAY_AS_NANOSECONDS: Long = 86400000000000

var Logger: ILogger = ConsoleLogger()
    internal set

var Timestamps: TimestampFactory = TimestampFactory()
    internal set

var Cache: ICache = CacheImpl()
    internal set
