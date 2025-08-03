/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.example.helloverifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;

public class DelayedRejectReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (!Intent.ACTION_PACKAGE_NEEDS_VERIFICATION.equals(action)) {
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        int id = extras.getInt("android.content.pm.extra.VERIFICATION_ID");
        // Allow after 30secs.
        context.getPackageManager().extendVerificationTimeout(id, PackageManager.VERIFICATION_ALLOW,
                30000);
        // Reject after 15secs.
        (new Handler(context.getMainLooper())).postDelayed(() -> {
            context.getPackageManager().verifyPendingInstall(id,
                    PackageManager.VERIFICATION_REJECT);
        }, 15000);
    }
}
