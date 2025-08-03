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
import android.app.PendingIntent;
import android.car.app.ICarTaskViewClient;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceControl;


/**
 * Binder API for server side of RemoteCarTaskView i.e. CarTaskViewServerImpl.
 * See {@link CarTaskViewHost} for details.
 * @hide
 */
oneway interface ICarTaskViewHost {
   void release();
   void startActivity(in PendingIntent pendingIntent, in Intent intent, in Bundle options, in Rect launchBounds);
   void createRootTask(int displayId);
   void createLaunchRootTask(int displayId, boolean embedHomeTask, boolean embedRecentsTask,
     boolean embedAssistantTask);
   void notifySurfaceCreated(in SurfaceControl control);
   void setWindowBounds(in Rect bounds);
   void notifySurfaceDestroyed();
   void showEmbeddedTask();
   void addInsets(int index, int type, in Rect frame);
   void removeInsets(int index, int type);
   void setTaskVisibility(boolean visibility);
   void reorderTask(boolean onTop);
}
