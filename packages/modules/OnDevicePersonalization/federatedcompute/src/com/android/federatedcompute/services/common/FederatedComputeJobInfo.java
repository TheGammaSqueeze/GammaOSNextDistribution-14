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

import java.util.Map;

public class FederatedComputeJobInfo {

    private FederatedComputeJobInfo() {}

    /** JOB ID to periodically download encryption key. */
    public static final int ENCRYPTION_KEY_FETCH_JOB_ID = 1000;

    public static final String ENCRYPTION_KEY_FETCH_JOB_NAME = "ENCRYPTION_KEY_FETCH_JOB";

    public static final int DELETE_EXPIRED_JOB_ID = 1001;

    public static final String DELETE_EXPIRED_JOB_NAME = "DELETE_EXPIRED_JOB";

    public static final Map<Integer, String> JOB_ID_TO_NAME_MAP =
            Map.of(
                    ENCRYPTION_KEY_FETCH_JOB_ID,
                    ENCRYPTION_KEY_FETCH_JOB_NAME,
                    DELETE_EXPIRED_JOB_ID,
                    DELETE_EXPIRED_JOB_NAME);
}
