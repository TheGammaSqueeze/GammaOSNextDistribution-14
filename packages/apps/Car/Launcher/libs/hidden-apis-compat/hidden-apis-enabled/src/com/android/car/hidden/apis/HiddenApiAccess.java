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

package com.android.car.hidden.apis;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.DragEvent;
import android.view.SurfaceControl;
import android.view.View;


/***
 * This classes is a place to surface all hidden/blocked apis which are not available to be accessed
 * by unbundled application.
 * We will deprecate this class once we find alternative public apis.
 * Note: This folder location is compiled only with Soong builds. Alternative gradle build
 * version under (hidden_apis_disabled) folder.
 */
public class HiddenApiAccess {

    /**
     * @return true: For gradle builds can always consider as debuggable
     */
    public static boolean isDebuggable() {
        return Build.isDebuggable();
    }

    /**
     * Calls hidden api {@link  android.view.DragEvent#getDragSurface}
     */
    public static SurfaceControl getDragSurface(DragEvent event) {
        return event.getDragSurface();
    }

    /**
     * Calls hidden api {@link  android.os.UserManager#hasBaseUserRestriction}
     */
    @SuppressLint("MissingPermission")
    public static boolean hasBaseUserRestriction(UserManager userManager, String restriction,
            UserHandle user) {
        return userManager.hasBaseUserRestriction(restriction, user);
    }

    /**
     * Gets hidden api {@link  android.view.View#DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION}
     */
    public static int DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION =
            View.DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION;


    /**
     * Calls the hidden api
     */
    public static int getDisplayId(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.getDisplayId();
    }

    /**
     * Start Activity with the current user
     */
    @SuppressLint("MissingPermission")
    public static void startActivityAsUser(Context context, Intent intent, UserHandle userHandle) {
        context.startActivityAsUser(intent, userHandle);
    }
}
