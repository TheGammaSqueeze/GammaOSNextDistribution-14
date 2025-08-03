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
package com.android.systemui.car.distantdisplay.activity.window;

import android.app.ActivityManager;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.UserHandle;

import com.android.systemui.car.distantdisplay.common.DistantDisplayReceiver;
import com.android.systemui.car.distantdisplay.common.TaskViewController;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;


/**
 * Controller that decides who will host the TaskView based on the flags. Host can either be an
 * activity window or a window hosted by systemUI. This controller is also responsible for creating
 * the win systemUI window and attaching view to it if needed.
 */
@SysUISingleton
public class ActivityWindowController {
    public static final String TAG = ActivityWindowController.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private final Context mContext;
    private final TaskViewController mTaskViewController;

    @Inject
    public ActivityWindowController(Context context,
            TaskViewController taskViewController) {
        mContext = context;
        mTaskViewController = taskViewController;
    }

    /**
     * Initializes the window / activity to host TaskViews.
     */
    public void initialize() {
        if (DEBUG) {
            // TODO(b/319879239): remove the broadcast receiver once the binder service is
            //  implemented.
            setupDebuggingThroughAdb();
        }
    }

    private void setupDebuggingThroughAdb() {
        IntentFilter filter = new IntentFilter(DistantDisplayReceiver.DISTANT_DISPLAY);
        DistantDisplayReceiver receiver = new DistantDisplayReceiver();
        receiver.register(displayId -> {
            mTaskViewController.unregister();
            mTaskViewController.initialize(displayId);
        });
        mContext.registerReceiverAsUser(receiver,
                UserHandle.of(ActivityManager.getCurrentUser()),
                filter, null, null,
                Context.RECEIVER_EXPORTED);
    }
}
