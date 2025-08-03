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

package com.android.systemui.car.distantdisplay.common;

import android.content.Intent;

import java.util.LinkedHashMap;

/**
 * Custom implementation for LinkedHashMap such that when a duplicate is added the duplicated
 * element stays towards the end of the list. The elements are mapped with key taskId and value
 * that contain information about a particular task.
 */
class DistantDisplayForegroundTaskMap {

    private final LinkedHashMap<Integer, TaskData> mData;

    DistantDisplayForegroundTaskMap() {
        mData = new LinkedHashMap<>();
    }

    void put(int taskId, int displayId, Intent baseIntent) {
        mData.remove(taskId);
        mData.put(taskId, new TaskData(taskId, displayId, baseIntent));
    }

    TaskData getTopTaskOnDisplay(int displayId) {
        TaskData last = null;
        for (TaskData value : mData.values()) {
            if (value.mDisplayId == displayId) last = value;
        }
        return last;
    }

    TaskData get(int taskId) {
        return mData.get(taskId);
    }

    void remove(int taskId) {
        mData.remove(taskId);
    }

    boolean isEmpty() {
        return mData.isEmpty();
    }

    static class TaskData {
        int mTaskId;
        int mDisplayId;
        Intent mBaseIntent;

        TaskData(int taskId, int displayId, Intent baseIntent) {
            mTaskId = taskId;
            mDisplayId = displayId;
            mBaseIntent = baseIntent;
        }
    }
}
