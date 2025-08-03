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

package com.android.ondevicepersonalization.services.serviceflow;

import static android.adservices.ondevicepersonalization.Constants.OP_DOWNLOAD;
import static android.adservices.ondevicepersonalization.Constants.OP_EXECUTE;
import static android.adservices.ondevicepersonalization.Constants.OP_RENDER;
import static android.adservices.ondevicepersonalization.Constants.OP_TRAINING_EXAMPLE;
import static android.adservices.ondevicepersonalization.Constants.OP_WEB_TRIGGER;
import static android.adservices.ondevicepersonalization.Constants.OP_WEB_VIEW_EVENT;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_RENDER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS;

import com.android.ondevicepersonalization.services.FlagsFactory;

/** Collection of on-device personalization service flows. */
public enum ServiceFlowType {

    APP_REQUEST_FLOW(
            "AppRequest", OP_EXECUTE, Priority.HIGH,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_APP_REQUEST_FLOW_DEADLINE_SECONDS)),

    RENDER_FLOW(
            "Render", OP_RENDER, Priority.HIGH,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_RENDER_FLOW_DEADLINE_SECONDS)),

    WEB_TRIGGER_FLOW(
            "WebTrigger", OP_WEB_TRIGGER, Priority.NORMAL,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_WEB_TRIGGER_FLOW_DEADLINE_SECONDS)),

    WEB_VIEW_FLOW(
            "ComputeEventMetrics", OP_WEB_VIEW_EVENT, Priority.NORMAL,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_WEB_VIEW_FLOW_DEADLINE_SECONDS)),

    EXAMPLE_STORE_FLOW(
            "ExampleStore", OP_TRAINING_EXAMPLE, Priority.NORMAL,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_EXAMPLE_STORE_FLOW_DEADLINE_SECONDS)),

    DOWNLOAD_FLOW(
            "DownloadJob", OP_DOWNLOAD, Priority.LOW,
            (int) FlagsFactory.getFlags().getStableFlag(KEY_DOWNLOAD_FLOW_DEADLINE_SECONDS));

    final String mTaskName;
    final int mOperationCode;
    final Priority mPriority;
    final int mExecutionTimeout;

    ServiceFlowType(String taskName, int operationCode, Priority priority, int executionTimeout) {
        mTaskName = taskName;
        mOperationCode = operationCode;
        mPriority = priority;
        mExecutionTimeout = executionTimeout;
    }

    public String getTaskName() {
        return mTaskName;
    }

    public int getOperationCode() {
        return mOperationCode;
    }

    public Priority getPriority() {
        return mPriority;
    }

    public int getExecutionTimeout() {
        return mExecutionTimeout;
    }

    public enum Priority {
        HIGH,
        NORMAL,
        LOW
    }
}
