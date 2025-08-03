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

package com.android.permissioncontroller.role.ui;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.os.Process;
import android.permission.flags.Flags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.util.List;
import java.util.Objects;

/**
 * Activity to handle {@link android.nfc.cardemulation.CardEmulation#ACTION_CHANGE_DEFAULT}.
 */
public class ChangeDefaultCardEmulationActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent;
        if (SdkLevel.isAtLeastV() && Flags.walletRoleEnabled()) {
            intent = DefaultAppActivity.createIntent(RoleManager.ROLE_WALLET,
                    Process.myUserHandle(), this);
        } else {
            intent = getIntent();
            setDefaultPaymentChangeHandlerDialogComponent(intent);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        startActivity(intent);
        finish();
    }

    // The only other handler of this intent is in the NFC stack.
    private void setDefaultPaymentChangeHandlerDialogComponent(@NonNull Intent intent) {
        Intent queryIntent = new Intent(CardEmulation.ACTION_CHANGE_DEFAULT);
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(queryIntent,
                PackageManager.MATCH_SYSTEM_ONLY);
        int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            String packageName = resolveInfo.activityInfo.packageName;
            if (!Objects.equals(packageName, getPackageName())) {
                intent.setClassName(packageName,
                        resolveInfo.activityInfo.name);
                return;
            }
        }
    }
}
