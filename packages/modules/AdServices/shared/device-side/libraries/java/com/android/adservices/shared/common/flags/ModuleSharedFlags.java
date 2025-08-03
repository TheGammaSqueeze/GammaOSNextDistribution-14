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

package com.android.adservices.shared.common.flags;

import static com.android.adservices.shared.common.flags.FeatureFlag.Type.SHARED;

/**
 * This class contains common flags used by multiple modules. In principle if a module wants to use
 * a flag, it should implement the method in this class by pointing to the method to its own flags.
 * This is because the shared directory contains only libraries, the roll-out should happen in the
 * module which actually uses the libraries.
 */
public interface ModuleSharedFlags {
    /** The default value of whether background job logging is enabled. */
    @FeatureFlag(SHARED)
    boolean BACKGROUND_JOB_LOGGING_ENABLED = false;

    /** Get if background job logging is enabled or not. */
    default boolean getBackgroundJobsLoggingEnabled() {
        return BACKGROUND_JOB_LOGGING_ENABLED;
    }

    /** The default value of background job sampling logging rate. */
    @ConfigFlag int BACKGROUND_JOB_SAMPLING_LOGGING_RATE = 1;

    /** Gets the value of background job sampling logging rate. */
    default int getBackgroundJobSamplingLoggingRate() {
        return BACKGROUND_JOB_SAMPLING_LOGGING_RATE;
    }
}
