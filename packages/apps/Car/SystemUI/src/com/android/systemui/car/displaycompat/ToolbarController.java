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
package com.android.systemui.car.displaycompat;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.view.View;
import android.view.ViewGroup;

/**
 * Interface for controlling toolbar for apps that use display compatibility.
 */
public interface ToolbarController {
    /**
     * Call to initialize the toolbar.
     *
     * @param parent this is supposed to be the container of the toolbar which will be used to
     * set the visibility of the toolbar.
     */
    void init(ViewGroup parent);

    /**
     * Sets the visibility of the toolbar to {@link View#VISIBLE}
     */
    void show();

    /**
     * Sets the visibility of the toolbar to {@link View#GONE}
     */
    void hide();

    /**
     * Call this method when a task is moved to front.
     */
    void update(@NonNull RunningTaskInfo taskInfo);
}
