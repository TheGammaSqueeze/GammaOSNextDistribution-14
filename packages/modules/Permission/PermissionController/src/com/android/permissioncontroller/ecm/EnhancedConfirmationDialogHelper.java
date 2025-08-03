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
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.PermissionMapping;
import com.android.settingslib.HelpUtils;

import java.util.Objects;

final class EnhancedConfirmationDialogHelper {

    private final ViewGroup mDialogView;
    private final Activity mActivity;

    EnhancedConfirmationDialogHelper(Activity activity) {
        mActivity = activity;
        mDialogView = (ViewGroup) LayoutInflater.from(mActivity).inflate(
                R.layout.enhanced_confirmation_dialog, null);
    }

    public void show(String settingIdentifier,
            DialogInterface.OnDismissListener onDismissListener) {
        final String helpUrl = mActivity.getString(
                R.string.help_url_action_disabled_by_restricted_settings);
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity,
                    com.android.settingslib.widget.theme.R.style.Theme_AlertDialog_SettingsLib)
                .setView(mDialogView)
                .setPositiveButton(R.string.enhanced_confirmation_dialog_ok, null)
                .setNeutralButton(R.string.enhanced_confirmation_dialog_learn_more,
                    (DialogInterface.OnClickListener) (dialog, which) -> {
                        final Intent intent = HelpUtils.getHelpIntent(mActivity, helpUrl,
                                mActivity.getClass().getName());
                        if (intent != null) {
                            mActivity.startActivity(intent);
                        }
                    });
        String permGroup = PermissionMapping.isPlatformPermissionGroup(settingIdentifier)
                ? settingIdentifier
                : PermissionMapping.getGroupOfPlatformPermission(settingIdentifier);
        if (permGroup != null) {
            CharSequence permGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(mActivity,
                    permGroup);
            setTitle(mActivity.getString(R.string.enhanced_confirmation_dialog_title_permission,
                    permGroupLabel));
            setDesc(mActivity.getString(R.string.enhanced_confirmation_dialog_desc_permission,
                    permGroupLabel));
        }

        AlertDialog dialog = builder.setOnDismissListener(onDismissListener).create();
        dialog.setOnShowListener(new AlertDialog.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
                ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL).setAllCaps(false);
            }
        });
        dialog.show();
    }

    void setTitle(String title) {
        TextView textView = Objects.requireNonNull(
                mDialogView.findViewById(R.id.enhanced_confirmation_dialog_title));
        textView.setText(title);
    }

    void setDesc(String description) {
        TextView textView = Objects.requireNonNull(
                mDialogView.findViewById(R.id.enhanced_confirmation_dialog_desc));
        textView.setText(description);
    }
}
