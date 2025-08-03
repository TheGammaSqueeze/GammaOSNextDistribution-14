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

package com.android.federatedcompute.services.http;

import android.annotation.Nullable;

import com.android.internal.util.Preconditions;

import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federatedcompute.v1.RejectionInfo;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;

/**
 * The result after client calls TaskAssignemnt API. It includes init checkpoint data and plan data.
 */
public class CheckinResult {
    private String mInputCheckpoint = null;
    private ClientOnlyPlan mPlanData = null;
    private TaskAssignment mTaskAssignment = null;
    private RejectionInfo mRejectionInfo = null;
    public CheckinResult(
            String inputCheckpoint, ClientOnlyPlan planData, TaskAssignment taskAssignment) {
        this.mInputCheckpoint = inputCheckpoint;
        this.mPlanData = planData;
        this.mTaskAssignment = taskAssignment;
    }

    public CheckinResult(RejectionInfo mRejectionInfo) {
        this.mRejectionInfo = mRejectionInfo;
    }

    @Nullable
    public String getInputCheckpointFile() {
        Preconditions.checkArgument(
                mInputCheckpoint != null && !mInputCheckpoint.isEmpty(),
                "Input checkpoint file should not be none or empty");
        return mInputCheckpoint;
    }

    @Nullable
    public ClientOnlyPlan getPlanData() {
        return mPlanData;
    }

    @Nullable
    public TaskAssignment getTaskAssignment() {
        return mTaskAssignment;
    }

    @Nullable
    public RejectionInfo getRejectionInfo() {
        return mRejectionInfo;
    }
}
