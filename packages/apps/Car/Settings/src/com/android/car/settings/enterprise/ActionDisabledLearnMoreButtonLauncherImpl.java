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

import static java.util.Objects.requireNonNull;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.ui.AlertDialogBuilder;
import com.android.settingslib.enterprise.ActionDisabledLearnMoreButtonLauncher;

/**
 * Car's implementation of {@link ActionDisabledLearnMoreButtonLauncher}.
 */
// TODO(b/186905050): add unit tests
final class ActionDisabledLearnMoreButtonLauncherImpl
        extends ActionDisabledLearnMoreButtonLauncher {

    private static final Logger LOG = new Logger(ActionDisabledLearnMoreButtonLauncherImpl.class);
    /**
     * The {@link UserHandle} which is preferred for launching the help page or admin policies in
     */
    @Nullable private final UserHandle mPreferredUser;
    private final AlertDialogBuilder mBuilder;

    ActionDisabledLearnMoreButtonLauncherImpl(AlertDialogBuilder builder) {
        this(builder, /* preferredUser= */ null);
    }

    ActionDisabledLearnMoreButtonLauncherImpl(AlertDialogBuilder builder,
            UserHandle preferredUser) {
        mBuilder = requireNonNull(builder, "builder cannot be null");
        mPreferredUser = preferredUser;
    }

    @Override
    public void setLearnMoreButton(Runnable action) {
        requireNonNull(action, "action cannot be null");

        mBuilder.setNeutralButton(R.string.learn_more, (dialog, which) -> action.run());
    }

    @Override
    protected void launchShowAdminPolicies(Context context, UserHandle adminUser,
            ComponentName admin) {
        requireNonNull(context, "context cannot be null");
        requireNonNull(adminUser, "user cannot be null");
        requireNonNull(admin, "admin cannot be null");

        Intent intent = new Intent()
                .setClass(context, DeviceAdminAddActivity.class)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DeviceAdminAddActivity.EXTRA_CALLED_FROM_SUPPORT_DIALOG, true);
        if (mPreferredUser != null) {
            context.startActivityAsUser(intent, mPreferredUser);
            LOG.d("launching " + intent + " for preferred user: " + mPreferredUser
                    + " instead of adminUser");
            return;
        }
        LOG.d("launching " + intent + " for admin user: " + adminUser);
        context.startActivityAsUser(intent, adminUser);
    }

    @Override
    protected void launchShowAdminSettings(Context context) {
        requireNonNull(context, "context cannot be null");

        // TODO(b/185182679): implement it
        LOG.w("launchShowAdminSettings(): not implemented yet");
    }
}
