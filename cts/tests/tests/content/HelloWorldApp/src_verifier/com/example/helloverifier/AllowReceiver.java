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

public class AllowReceiver extends BroadcastReceiver {
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
        context.getPackageManager().verifyPendingInstall(id, PackageManager.VERIFICATION_ALLOW);
    }
}
