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

package com.android.managedprovisioning.common;

import android.content.Context;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.preprovisioning.EncryptionController;

/**
 * A provider for {@link EncryptionController}.
 */
public interface EncryptionControllerProvider {
    EncryptionControllerProvider DEFAULT = new EncryptionControllerProvider() {
        @Override
        public EncryptionController createEncryptionController(Context context) {
            return getApplicationContext(context).getEncryptionController();
        }

        private ManagedProvisioningBaseApplication getApplicationContext(Context context) {
            return (ManagedProvisioningBaseApplication) context.getApplicationContext();
        }
    };

    /**
     * Provides an instance for {@link EncryptionController}.
     */
    EncryptionController createEncryptionController(Context context);
}
