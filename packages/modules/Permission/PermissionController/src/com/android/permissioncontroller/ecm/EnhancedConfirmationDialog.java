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

package com.android.permissioncontroller.ecm;

import android.app.Activity;
import android.app.ecm.EnhancedConfirmationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.permission.flags.Flags;
import android.text.TextUtils;

import androidx.annotation.Keep;

import com.android.modules.utils.build.SdkLevel;

@Keep
public class EnhancedConfirmationDialog extends Activity implements
        DialogInterface.OnDismissListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SdkLevel.isAtLeastV() || !Flags.enhancedConfirmationModeApisEnabled()) {
            return;
        }

        final String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (TextUtils.isEmpty(packageName)) {
            throw new IllegalArgumentException("EXTRA_PACKAGE_NAME cannot be null or empty");
        }

        final int uid = getIntent().getIntExtra(Intent.EXTRA_UID, android.os.Process.INVALID_UID);
        if (uid == android.os.Process.INVALID_UID) {
            throw new IllegalArgumentException("EXTRA_UID cannot be null or invalid");
        }
        final UserHandle user = UserHandle.getUserHandleForUid(uid);

        final String settingIdentifier = getIntent().getStringExtra(Intent.EXTRA_SUBJECT);
        if (settingIdentifier == null) {
            throw new IllegalArgumentException("EXTRA_SUBJECT cannot be null or invalid");
        }

        EnhancedConfirmationDialogHelper dialogHelper = new EnhancedConfirmationDialogHelper(this);
        dialogHelper.show(settingIdentifier, this);

        setClearRestrictionAllowed(packageName, user);
    }

    private void setClearRestrictionAllowed(String packageName, UserHandle user) {
        Context userContext = createContextAsUser(user, 0);
        EnhancedConfirmationManager ecm = userContext.getSystemService(
                EnhancedConfirmationManager.class);
        try {
            ecm.setClearRestrictionAllowed(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalArgumentException("unknown package: " + packageName);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }
}
