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

package com.android.federatedcompute.services.scheduling;

import android.content.Context;

/** Used to generate job scheduler ids for federated compute jobs. */
public class FederatedJobIdGenerator {
    private static FederatedJobIdGenerator sSingleton = null;

    private FederatedJobIdGenerator() {}

    /** Gets a singleton instance of {@link FederatedJobIdGenerator}. */
    public static FederatedJobIdGenerator getInstance() {
        synchronized (FederatedJobIdGenerator.class) {
            if (sSingleton == null) {
                sSingleton = new FederatedJobIdGenerator();
            }
            return sSingleton;
        }
    }

    /** Generates a new job id used for JobScheduler. */
    public int generateJobId(Context context, String populationName, String callingPackageName) {
        // TODO(b/295952797): return job id based on storage.
        return (populationName + callingPackageName).hashCode();
    }
}
