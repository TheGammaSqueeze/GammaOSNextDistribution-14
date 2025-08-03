/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.managedprovisioning.common.ProvisionLogger;

/**
 * Class that acts as the final receiver of the intent ACTION_PROFILE_PROVISIONING_COMPLETE
 * which is broadcast using
 * {@link Context#sendOrderedBroadcast(Intent, String, BroadcastReceiver, android.os.Handler, int, String, android.os.Bundle)}
 * after profile owner or device owner provisioning is completed.
 */
public class DpcReceivedSuccessReceiver extends BroadcastReceiver {

    interface Callback {
        void cleanup();
    }

    private final PrimaryProfileFinalizationHelper mHelper;
    private final Callback mCallback;

    public DpcReceivedSuccessReceiver(Account migratedAccount, UserHandle managedUserHandle,
            String mdmPackageName, Callback callback) {
        mCallback = callback;
        mHelper = new PrimaryProfileFinalizationHelper(
                migratedAccount, managedUserHandle, mdmPackageName);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ProvisionLogger.logd("ACTION_PROFILE_PROVISIONING_COMPLETE broadcast received by mdm");
        mHelper.finalizeProvisioningInPrimaryProfile(context, mCallback);
    }
}
