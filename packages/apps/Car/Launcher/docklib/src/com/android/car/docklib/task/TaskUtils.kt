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

package com.android.car.docklib.task

import android.app.ActivityManager
import android.content.ComponentName
import com.android.car.docklib.media.MediaUtils.Companion.getMediaComponentName
import com.android.car.docklib.media.MediaUtils.Companion.isMediaComponent

class TaskUtils {
    companion object {
        fun getComponentName(taskInfo: ActivityManager.RunningTaskInfo): ComponentName? {
            if (taskInfo.baseActivity == null && taskInfo.baseIntent.component == null) {
                return null
            }
            val component = if (taskInfo.baseActivity != null) {
                taskInfo.baseActivity
            } else {
                taskInfo.baseIntent.component
            }

            return if (isMediaComponent(component)) getMediaComponentName(taskInfo) else component
        }
    }
}
