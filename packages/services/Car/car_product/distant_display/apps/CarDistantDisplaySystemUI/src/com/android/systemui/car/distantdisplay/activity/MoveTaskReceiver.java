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
package com.android.systemui.car.distantdisplay.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receiver for testing purpose ONLY. This is acting as an entry point for moving the tasks until
 * UX figure out the specific place to trigger the move ia UI.
 *
 * Usage:
 * When TaskView host is a window in systemUI
 * Move to DD:
 * adb shell am broadcast -a com.android.systemui.car.intent.action.MOVE_TASK \
 * --user 0 --es move "to_dd"
 * Move from DD:
 * adb shell am broadcast -a com.android.systemui.car.intent.action.MOVE_TASK \
 * --user 0 --es move "from_dd"
 * When TaskView host is a window in systemUI
 * Move to DD:
 * adb shell am broadcast -a com.android.systemui.car.intent.action.MOVE_TASK \
 * --user 10 --es move "to_dd"
 * Move from DD:
 * adb shell am broadcast -a com.android.systemui.car.intent.action.MOVE_TASK \
 * --user 10 --es move "from_dd"
 *
 * TODO(b/302548275) : once the CUJs are completed via UX remove this receiver.
 */
public class MoveTaskReceiver extends BroadcastReceiver {
    public static final String TAG = "MoveTaskReceiver";
    public static final String MOVE_ACTION = "com.android.systemui.car.intent.action.MOVE_TASK";
    public static final String MOVE_TO_DISTANT_DISPLAY = "to_dd";
    public static final String MOVE_FROM_DISTANT_DISPLAY = "from_dd";

    /**
     * Called when a request to move the task from one display to another comes in.
     */
    public interface Callback {
        /**
         * Called when the request to move the task to another display comes in.
         *
         * @param movement command which shows the movement direction
         */
        void onTaskDisplayChangeRequest(String movement);
    }

    private Callback mOnChangeDisplayForTask;

    @Override
    public final void onReceive(final Context context, final Intent intent) {

        if (mOnChangeDisplayForTask == null) {
            return;
        }
        String data = intent.getStringExtra("move");

        if (data.equals(MOVE_TO_DISTANT_DISPLAY) || data.equals(MOVE_FROM_DISTANT_DISPLAY)) {
            mOnChangeDisplayForTask.onTaskDisplayChangeRequest(data);
        }
    }

    /**
     * @param listener register's {@link Callback}
     */
    public void registerOnChangeDisplayForTask(Callback listener) {
        mOnChangeDisplayForTask = listener;
    }

    /**
     * Uregister's {@link Callback}
     */
    public void unRegisterOnChangeDisplayForTask() {
        mOnChangeDisplayForTask = null;
    }
}
