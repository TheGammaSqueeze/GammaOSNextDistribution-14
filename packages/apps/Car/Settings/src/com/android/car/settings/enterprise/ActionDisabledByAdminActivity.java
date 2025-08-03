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

package com.android.car.settings.enterprise;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;

import com.android.settingslib.core.lifecycle.HideNonSystemOverlayMixin;

/**
 * Shows a dialog explaining that an action is not enabled due to restrictions imposed by an active
 * device administrator.
 */
// TODO(b/186905050): add unit tests
public final class ActionDisabledByAdminActivity extends FragmentActivity {

    @VisibleForTesting
    static final String FRAGMENT_TAG =
            "ActionDisabledByAdminActivity.ActionDisabledByAdminDialogFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));

        String restriction = getRestrictionFromIntent(getIntent());
        ActionDisabledByAdminDialogFragment fragment =
                EnterpriseUtils.getActionDisabledByAdminDialog(this, restriction);
        fragment.setDismissListener(res -> {
            if (!isChangingConfigurations()) {
                finish();
            }
        });
        fragment.show(getSupportFragmentManager(), FRAGMENT_TAG);
    }

    private String getRestrictionFromIntent(Intent intent) {
        if (intent == null) return null;
        return intent.getStringExtra(DevicePolicyManager.EXTRA_RESTRICTION);
    }
}
