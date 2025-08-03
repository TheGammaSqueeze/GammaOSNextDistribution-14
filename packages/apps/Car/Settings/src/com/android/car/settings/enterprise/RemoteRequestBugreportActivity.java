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

import static android.app.admin.DevicePolicyManager.NOTIFICATION_BUGREPORT_ACCEPTED_NOT_FINISHED;
import static android.app.admin.DevicePolicyManager.NOTIFICATION_BUGREPORT_FINISHED_NOT_ACCEPTED;
import static android.app.admin.DevicePolicyManager.NOTIFICATION_BUGREPORT_STARTED;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.fragment.app.FragmentActivity;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.Logger;
import com.android.settingslib.core.lifecycle.HideNonSystemOverlayMixin;

/**
 * A screen that shows a dialog for remote request of bugreport.
 */
//TODO(b/186905050): add unit tests
public final class RemoteRequestBugreportActivity extends FragmentActivity {

    private static final String TAG = RemoteRequestBugreportActivity.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));

        ConfirmationDialogFragment fragment = getInitialFragment();
        LOG.d("onCreate, initial fragment: " + fragment + " for user: " + getUserId());
        fragment.show(getSupportFragmentManager(), TAG);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void onBugreportConfirmed(String action) {
        LOG.d("onBugreportConfirmed, action: " + action + ", user: " + getUserId());

        Intent intent = new Intent(action);
        RemoteRequestBugreportActivity.this.sendBroadcastAsUser(intent,
                UserHandle.SYSTEM, android.Manifest.permission.DUMP);
        finish();
    }

    protected ConfirmationDialogFragment getInitialFragment() {
        int notificationType = getIntent().getIntExtra(
                DevicePolicyManager.EXTRA_BUGREPORT_NOTIFICATION_TYPE, -1);

        ConfirmationDialogFragment.Builder builder =
                new ConfirmationDialogFragment.Builder(this);

        ConfirmationDialogFragment.DismissListener dismissListener =
                (arguments, positiveResult) -> finish();

        switch (notificationType) {
            case NOTIFICATION_BUGREPORT_ACCEPTED_NOT_FINISHED:
                builder.setMessage(R.string.sharing_remote_bugreport_dialog_message)
                    .setDismissListener(dismissListener)
                    .setNegativeButton(android.R.string.ok, arguments -> finish());
                break;
            case NOTIFICATION_BUGREPORT_STARTED:
            case NOTIFICATION_BUGREPORT_FINISHED_NOT_ACCEPTED:
                ConfirmationDialogFragment.RejectListener rejectListener = arguments ->
                        onBugreportConfirmed(
                                DevicePolicyManager.ACTION_BUGREPORT_SHARING_DECLINED);
                ConfirmationDialogFragment.ConfirmListener confirmListener = arguments ->
                        onBugreportConfirmed(
                                DevicePolicyManager.ACTION_BUGREPORT_SHARING_ACCEPTED);

                builder.setTitle(R.string.share_remote_bugreport_dialog_title)
                        .setMessage(notificationType == NOTIFICATION_BUGREPORT_STARTED
                                ? R.string.share_remote_bugreport_dialog_message
                                : R.string.share_remote_bugreport_dialog_message_finished)
                    .setDismissListener(dismissListener)
                    .setNegativeButton(R.string.decline_remote_bugreport_action, rejectListener)
                    .setPositiveButton(R.string.share_remote_bugreport_action, confirmListener);
                break;
            default:
                LOG.e("Incorrect dialog type, no dialog shown. Received: " + notificationType);
        }
        ConfirmationDialogFragment dialog = builder.build();
        LOG.d("getInitialFragment(), ConfirmationDialogFragment built.");
        return dialog;
    }
}
