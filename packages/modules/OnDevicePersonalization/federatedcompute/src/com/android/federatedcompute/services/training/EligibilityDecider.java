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

package com.android.federatedcompute.services.training;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.TaskHistory;

import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityPolicyEvalSpec;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityTaskInfo;
import com.google.ondevicepersonalization.federatedcompute.proto.MinimumSeparationPolicy;

/** Runs eligibility evaluation and decide if device is qualified for each task. */
public class EligibilityDecider {
    private static final String TAG = EligibilityDecider.class.getSimpleName();
    private final FederatedTrainingTaskDao mFederatedTrainingTaskDao;

    public EligibilityDecider(FederatedTrainingTaskDao taskDao) {
        this.mFederatedTrainingTaskDao = taskDao;
    }

    /**
     * Computes the eligibility of the client for the given tasks in the population eligibility
     * spec. Returns true if device is eligible to execute this task.
     */
    public boolean computeEligibility(
            String populationName,
            String taskId,
            int jobId,
            EligibilityTaskInfo eligibilityTaskInfo) {
        boolean eligible = true;
        for (EligibilityPolicyEvalSpec policyEvalSpec :
                eligibilityTaskInfo.getEligibilityPoliciesList()) {
            if (policyEvalSpec.getPolicyTypeCase()
                    == EligibilityPolicyEvalSpec.PolicyTypeCase.MIN_SEP_POLICY) {
                eligible =
                        computePerTaskMinSeparation(
                                policyEvalSpec.getMinSepPolicy(), populationName, taskId, jobId);
                // Device has to meet all eligibility policies in order to execute task.
                if (!eligible) {
                    break;
                }
            } else {
                throw new IllegalStateException(
                        String.format("Unsupported policy %s", policyEvalSpec.getId()));
            }
        }
        return eligible;
    }

    private boolean computePerTaskMinSeparation(
            MinimumSeparationPolicy minSepPolicy, String populationName, String taskId, int jobId) {
        TaskHistory taskHistory =
                mFederatedTrainingTaskDao.getLatestTaskHistory(jobId, populationName, taskId);
        // Treat null as the task never run before, then device is qualified.
        if (taskHistory == null) {
            LogUtil.d(
                    TAG,
                    "population name %s task id %s job id %d doesn't have TaskHistory record.",
                    populationName,
                    taskId,
                    jobId);
            return true;
        }
        LogUtil.d(
                TAG,
                "Execute minimum separartion policy: min sep %d, current index %d, round %d",
                minSepPolicy.getMinimumSeparation(),
                minSepPolicy.getCurrentIndex(),
                taskHistory.getContributionRound());
        return minSepPolicy.getMinimumSeparation()
                <= minSepPolicy.getCurrentIndex() - taskHistory.getContributionRound();
    }
}
