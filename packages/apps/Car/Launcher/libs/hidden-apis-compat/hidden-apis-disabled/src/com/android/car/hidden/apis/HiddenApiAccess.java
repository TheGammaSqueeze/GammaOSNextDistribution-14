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

package com.android.car.hidden.apis;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Display;
import android.view.DragEvent;
import android.view.SurfaceControl;

/**
 * This classes is a place to surface all hidden/blocked apis which are not available to be accessed
 * by unbundled application.
 * All of the apis here are only visible to platform apps.
 * We will deprecate this class once we find alternative public apis.
 * Note: This folder location is compiled only with Gradle builds. There exists alternative Soong
 * build version under (hidden_apis_enabled) folder.
 * Gradle version fails softly when we need this api, so some functionality might appear broken.
 * <p>
 * Gradle builds are testing/development only. Please build with Soong once before merging changes
 */
public class HiddenApiAccess {

    /**
     * @return true: For gradle builds can always consider as debuggable
     */
    public static boolean isDebuggable() {
        return true;
    }

    /**
     * Empty/Null SurfaceControl
     */
    public static SurfaceControl getDragSurface(DragEvent event) {
        return null;
    }

    /**
     * Fake android.os.UserManager#hasBaseUserRestriction function
     * always returns false.
     */
    public static boolean hasBaseUserRestriction(UserManager userManager, String restriction,
            UserHandle user) {
        return false;
    }

    /**
     * Directly resolves int value of the hidden int flag
     * android.view.View.DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION
     */
    public static int DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION = 1 << 11;

    /**
     * Always returns {@link Display.DEFAULT_DISPLAY}
     */
    public static int getDisplayId(ActivityManager.RunningTaskInfo taskInfo) {
        return Display.DEFAULT_DISPLAY;
    }

    /**
     * Start Activity with the current user
     */
    public static void startActivityAsUser(Context context, Intent intent, UserHandle userHandle) {
        context.startActivity(intent);
    }
}
