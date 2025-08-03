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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.TaskHistory;

import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityPolicyEvalSpec;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityTaskInfo;
import com.google.ondevicepersonalization.federatedcompute.proto.MinimumSeparationPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EligibilityDeciderTest {
    private static final int JOB_ID = 123;
    private static final String TASK_ID = "task_1";
    private static final String POPULATION_NAME = "population_name";
    private static final MinimumSeparationPolicy MIN_SEP_POLICY =
            MinimumSeparationPolicy.newBuilder()
                    .setMinimumSeparation(6)
                    .setCurrentIndex(10)
                    .build();
    private static final EligibilityTaskInfo ELIGIBILITY_TASK_INFO =
            EligibilityTaskInfo.newBuilder()
                    .addEligibilityPolicies(
                            EligibilityPolicyEvalSpec.newBuilder()
                                    .setMinSepPolicy(MIN_SEP_POLICY)
                                    .build())
                    .build();
    private Context mContext;
    private FederatedTrainingTaskDao mTrainingTaskDao;
    private EligibilityDecider mEligibilityDecider;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mTrainingTaskDao = FederatedTrainingTaskDao.getInstanceForTest(mContext);
        mEligibilityDecider = new EligibilityDecider(mTrainingTaskDao);
    }

    @After
    public void tearDown() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testMinSepPolicy_noRecord_eligible() {
        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        POPULATION_NAME, TASK_ID, JOB_ID, ELIGIBILITY_TASK_INFO);

        assertTrue(eligible);
    }

    @Test
    public void testMinSepPolicy_eligible() {
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(1)
                        .setContributionTime(120L)
                        .build());

        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        POPULATION_NAME, TASK_ID, JOB_ID, ELIGIBILITY_TASK_INFO);

        assertTrue(eligible);
    }

    @Test
    public void testMinSepPolicy_joinSameRound_notEligible() {
        // Device joins iteration round 1.
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(1)
                        .setContributionTime(120L)
                        .build());
        MinimumSeparationPolicy minimumSeparationPolicy =
                MinimumSeparationPolicy.newBuilder()
                        .setMinimumSeparation(1)
                        .setCurrentIndex(1)
                        .build();
        EligibilityTaskInfo eligibilityTaskInfo =
                EligibilityTaskInfo.newBuilder()
                        .addEligibilityPolicies(
                                EligibilityPolicyEvalSpec.newBuilder()
                                        .setMinSepPolicy(minimumSeparationPolicy)
                                        .build())
                        .build();

        // Device should not be able to join same round since min separate policy is 1.
        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        POPULATION_NAME, TASK_ID, JOB_ID, eligibilityTaskInfo);

        assertFalse(eligible);
    }

    @Test
    public void testMinSepPolicy_joinNextRound_eligible() {
        // Device joins iteration round 1.
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(1)
                        .setContributionTime(120L)
                        .build());
        // Current iteration round is 2 and min separation policy is 1.
        MinimumSeparationPolicy minimumSeparationPolicy =
                MinimumSeparationPolicy.newBuilder()
                        .setMinimumSeparation(1)
                        .setCurrentIndex(2)
                        .build();
        EligibilityTaskInfo eligibilityTaskInfo =
                EligibilityTaskInfo.newBuilder()
                        .addEligibilityPolicies(
                                EligibilityPolicyEvalSpec.newBuilder()
                                        .setMinSepPolicy(minimumSeparationPolicy)
                                        .build())
                        .build();

        // Device should be able to join iteration 2.
        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        POPULATION_NAME, TASK_ID, JOB_ID, eligibilityTaskInfo);

        assertTrue(eligible);
    }

    @Test
    public void testMinSepPolicy_notEligible() {
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(9)
                        .setContributionTime(120L)
                        .build());

        boolean eligible =
                mEligibilityDecider.computeEligibility(
                        POPULATION_NAME, TASK_ID, JOB_ID, ELIGIBILITY_TASK_INFO);

        assertFalse(eligible);
    }
}
