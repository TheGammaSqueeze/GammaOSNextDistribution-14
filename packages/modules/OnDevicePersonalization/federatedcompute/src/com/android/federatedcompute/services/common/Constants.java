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

package com.android.federatedcompute.services.common;

/** Constants used internally in the FederatedCompute APK. */
public class Constants {

    public static final String EXTRA_EXAMPLE_STORE_ITERATOR_BINDER =
            "android.federatedcompute.example_store_iterator_binder";
    public static final String EXTRA_INPUT_CHECKPOINT_FD =
            "android.federatedcompute.input_checkpoint_fd";
    public static final String EXTRA_OUTPUT_CHECKPOINT_FD =
            "android.federatedcompute.output_checkpoint_fd";
    public static final String EXTRA_FL_RUNNER_RESULT = "android.federatedcompute.fl_runner_result";
    public static final String EXTRA_JOB_ID = "android.federatedcompute.job_id";
    public static final String EXTRA_EXAMPLE_SELECTOR = "android.federatedcompute.example_selector";
    public static final String EXTRA_CLIENT_ONLY_PLAN_FD =
            "android.federatedcompute.client_only_plan_fd";

    public static final String CLIENT_ONLY_PLAN_FILE_NAME = "federated_client_only_plan";

    public static final String ISOLATED_TRAINING_SERVICE_NAME =
            "com.android.federatedcompute.services.training.IsolatedTrainingService";

    public static final String TRACE_HTTP_ISSUE_CHECKIN = "Http#issueCheckin";
    public static final String TRACE_HTTP_REPORT_RESULT = "Http#reportResult";
    public static final String TRACE_ISOLATED_PROCESS_RUN_FL_TRAINING =
            "IsolatedProcess#runFlTraining";
    public static final String TRACE_NATIVE_RUN_FEDERATED_COMPUTATION =
            "Native#runFederatedComputation";
    public static final String TRACE_WORKER_RUN_FL_COMPUTATION = "Worker#runFlComputation";
    public static final String TRACE_WORKER_START_TRAINING_RUN = "Worker#startTrainingRun";

    private Constants() {}
}
