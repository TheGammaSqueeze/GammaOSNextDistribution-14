/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import android.app.Activity;

/**
 * A bridge that provides methods related to the provisioning phase UX.
 *
 * @see ProvisioningActivityBridgeCallbacks
 */
public interface ProvisioningActivityBridge {
    /**
     * Initiates the UI.
     */
    void initiateUi(Activity activity);

    /**
     * Callback for when the associated {@link android.app.Activity} is started.
     */
    void onStart(Activity activity);

    /**
     * Callback for when the associated {@link android.app.Activity} is stopped.
     */
    void onStop();

    /**
     * Callback for when provisioning is finalized
     */
    void onProvisioningFinalized(Activity activity);

    /**
     * Whether the buttons should be shown when pre provisioning completes.
     *
     * <p>If {@code false}, it is the responsibility of the {@link ProvisioningActivityBridge}
     * implementor to call {@link ProvisioningActivity#updateProvisioningFinalizedScreen()}
     */
    boolean shouldShowButtonsWhenPreProvisioningCompletes();
}
