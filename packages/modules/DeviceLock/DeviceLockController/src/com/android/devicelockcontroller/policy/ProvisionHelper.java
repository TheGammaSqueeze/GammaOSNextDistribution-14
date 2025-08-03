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

package com.android.devicelockcontroller.policy;

import androidx.lifecycle.LifecycleOwner;

import com.android.devicelockcontroller.activities.ProvisioningProgressController;

/**
 * A helper class provides helper functions for certain provision business logic.
 * Any blocking works should be executed on background thread as the APIs in this class are mostly
 * used by UI components ane running on the main thread.
 */
public interface ProvisionHelper {
    /** Pause the provision and schedule the resume. */
    void pauseProvision();

    /**
     * Start installation and open kiosk when it finish.
     */
    void scheduleKioskAppInstallation(LifecycleOwner owner,
            ProvisioningProgressController progressController,
            boolean isProvisionMandatory);
}
