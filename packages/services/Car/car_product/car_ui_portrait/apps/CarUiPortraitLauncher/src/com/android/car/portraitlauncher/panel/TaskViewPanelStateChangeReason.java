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
package com.android.car.portraitlauncher.panel;

import android.annotation.StringDef;
import android.content.ComponentName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Reasons why {@link TaskViewPanel}'s {@link TaskViewPanel.State} changes.
 */
public final class TaskViewPanelStateChangeReason {
    public static final String ON_ACTIVITY_RESTART_ATTEMPT = "ON_ACTIVITY_RESTART_ATTEMPT";
    public static final String ON_COLLAPSE_MSG = "ON_COLLAPSE_MSG";
    public static final String ON_DRIVE_STATE_CHANGED = "ON_DRIVE_STATE_CHANGED";
    public static final String ON_GRIP_BAR_CLICKED = "ON_GRIP_BAR_CLICKED";
    public static final String ON_GRIP_BAR_DRAG = "ON_GRIP_BAR_DRAG";
    public static final String ON_HOME_INTENT = "ON_HOME_INTENT";
    public static final String ON_HOME_SCREEN_LAYOUT_CHANGED = "ON_HOME_SCREEN_LAYOUT_CHANGED";
    public static final String ON_IMMERSIVE_REQUEST = "ON_IMMERSIVE_REQUEST";
    public static final String ON_MEDIA_INTENT = "ON_MEDIA_INTENT";
    public static final String ON_PANEL_STATE_CHANGE_END = "ON_PANEL_STATE_CHANGE_END";
    public static final String ON_PANEL_READY = "ON_PANEL_READY";
    public static final String ON_SUW_STATE_CHANGED = "ON_SUW_STATE_CHANGED";
    public static final String ON_TASK_MOVED_TO_FRONT = "ON_TASK_MOVED_TO_FRONT";
    public static final String ON_TASK_REMOVED = "ON_TASK_REMOVED";
    public static final String ON_CALM_MODE_STARTED = "ON_CALM_MODE_STARTED";
    public static final String ON_TASK_INFO_CHANGED = "ON_TASK_INFO_CHANGED";

    private static final int EMPTY_TASK_ID = -1;
    private final String mReason;
    private final int mTaskId;
    private final ComponentName mComponentName;

    private TaskViewPanelStateChangeReason(@Reason String reason, int taskId,
            ComponentName componentName) {
        mReason = reason;
        mTaskId = taskId;
        mComponentName = componentName;
    }

    /**
     * Creates a {@link TaskViewPanelStateChangeReason} with {@link Reason}, taskId and
     * ComponentName.
     */
    public static TaskViewPanelStateChangeReason createReason(@Reason String reason, int taskId,
            ComponentName componentName) {
        return new TaskViewPanelStateChangeReason(reason, taskId, componentName);
    }

    /**
     * Creates a {@link TaskViewPanelStateChangeReason} with {@link Reason} and taskId.
     */
    public static TaskViewPanelStateChangeReason createReason(@Reason String reason, int taskId) {
        return new TaskViewPanelStateChangeReason(reason, taskId, /* componentName= */ null);
    }

    /**
     * Creates a {@link TaskViewPanelStateChangeReason} with {@link Reason} and ComponentName.
     */
    public static TaskViewPanelStateChangeReason createReason(@Reason String reason,
            ComponentName componentName) {
        return createReason(reason, EMPTY_TASK_ID, componentName);
    }

    /**
     * Creates a {@link TaskViewPanelStateChangeReason} with {@link Reason}.
     */
    public static TaskViewPanelStateChangeReason createReason(@Reason String reason) {
        return createReason(reason, EMPTY_TASK_ID, /* componentName= */ null);
    }

    @Override
    public String toString() {
        return "{ reason=" + mReason + ", taskId=" + mTaskId + ", componentName=" + mComponentName
                + "}";
    }

    @StringDef({ON_ACTIVITY_RESTART_ATTEMPT,
            ON_COLLAPSE_MSG,
            ON_DRIVE_STATE_CHANGED,
            ON_GRIP_BAR_CLICKED,
            ON_GRIP_BAR_DRAG,
            ON_HOME_INTENT,
            ON_HOME_SCREEN_LAYOUT_CHANGED,
            ON_IMMERSIVE_REQUEST,
            ON_MEDIA_INTENT,
            ON_PANEL_STATE_CHANGE_END,
            ON_PANEL_READY,
            ON_SUW_STATE_CHANGED,
            ON_TASK_MOVED_TO_FRONT,
            ON_TASK_REMOVED,
            ON_CALM_MODE_STARTED,
            ON_TASK_INFO_CHANGED,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Reason {
    }
}
