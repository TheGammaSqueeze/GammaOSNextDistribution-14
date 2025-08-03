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
package android.car.app;

import android.app.ActivityManager.RunningTaskInfo;
import android.graphics.Rect;
import android.view.SurfaceControl;


/**
 * Binder API to be implemented by the client side of RemoteCarTaskView. This will be used by the
 * host to call into the client.
 * See {@link CarTaskViewClient} for details.
 * @hide
 */
interface ICarTaskViewClient {
    Rect getCurrentBoundsOnScreen();
    void setResizeBackgroundColor(in SurfaceControl.Transaction transaction, int color);
    void onTaskAppeared(in RunningTaskInfo taskInfo, in SurfaceControl leash);
    void onTaskVanished(in RunningTaskInfo taskInfo);
    void onTaskInfoChanged(in RunningTaskInfo taskInfo);
}