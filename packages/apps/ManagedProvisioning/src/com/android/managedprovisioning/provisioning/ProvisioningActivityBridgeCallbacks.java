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

/**
 * Callbacks intended to be used with {@link ProvisioningActivityBridge}.
 *
 * <p>It is the responsibility of the {@link ProvisioningActivityBridge} implementation
 * to call through these callbacks.
 */
public interface ProvisioningActivityBridgeCallbacks {

    /**
     * Callback invoked when the next button has been clicked
     */
    void onNextButtonClicked();

    /**
     * Callback invoked when the abort button has been clicked
     */
    void onAbortButtonClicked();

    /**
     * Returns {@code true} if provisioning is finalized
     */
    boolean isProvisioningFinalized();
}
