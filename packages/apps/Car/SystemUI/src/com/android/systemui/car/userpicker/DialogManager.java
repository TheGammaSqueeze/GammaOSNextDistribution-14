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

package com.android.systemui.car.userpicker;

import static com.android.systemui.car.userpicker.UserEventManager.getMaxSupportedUsers;

import android.annotation.IntDef;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Inject;

@UserPickerScope
final class DialogManager {
    private static final String TAG = DialogManager.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    static final int DIALOG_TYPE_SWITCHING = 0;
    static final int DIALOG_TYPE_ADDING_USER = 1;
    static final int DIALOG_TYPE_MAX_USER_COUNT_REACHED = 2;
    static final int DIALOG_TYPE_CONFIRM_ADD_USER = 3;
    static final int DIALOG_TYPE_CONFIRM_LOGOUT = 4;

    @IntDef(prefix = { "DIALOG_TYPE_" }, value = {
        DIALOG_TYPE_SWITCHING,
        DIALOG_TYPE_ADDING_USER,
        DIALOG_TYPE_MAX_USER_COUNT_REACHED,
        DIALOG_TYPE_CONFIRM_ADD_USER,
        DIALOG_TYPE_CONFIRM_LOGOUT,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DialogType {}

    @VisibleForTesting
    final UserPickerDialogs mUserPickerDialogs = new UserPickerDialogs();

    private String mUserSwitchingMessage;
    private String mUserAddingMessage;
    private String mMaxUserLimitReachedTitle;
    private String mMaxUserLimitReachedMessage;
    private String mConfirmAddUserTitle;
    private String mConfirmAddUserMessage;
    private String mConfirmLogoutTitle;
    private String mConfirmLogoutMessage;

    private Context mContext;
    private int mDisplayId;

    @Inject
    DialogManager() { }

    void initContextFromView(View rootView) {
        // Dialog manager needs activity context, so sets view's context and display id.
        mContext = rootView.getContext();
        mDisplayId = mContext.getDisplayId();
        updateTexts(mContext);
    }

    void updateTexts(Context context) {
        String lineSeparator = System.getProperty("line.separator");
        mUserSwitchingMessage = context.getString(R.string.user_switching_message);
        mUserAddingMessage = context.getString(R.string.user_adding_message);
        mMaxUserLimitReachedTitle = context.getString(R.string.max_user_limit_reached_title);
        mMaxUserLimitReachedMessage = context.getString(R.string.max_user_limit_reached_message);
        mConfirmAddUserTitle = context.getString(R.string.confirm_add_user_title);
        mConfirmAddUserMessage = context.getString(R.string.user_add_user_message_setup)
                .concat(lineSeparator)
                .concat(lineSeparator)
                .concat(context.getString(R.string.user_add_user_message_update));
        mConfirmLogoutTitle = context.getString(R.string.user_logout_title);
        mConfirmLogoutMessage = context.getString(R.string.user_logout_message);
    }

    void showDialog(@DialogType int type) {
        showDialog(type, null);
    }

    void showDialog(@DialogType int type, Runnable positiveCallback) {
        showDialog(type, positiveCallback, null);
    }

    void showDialog(@DialogType int type, Runnable positiveCallback, Runnable cancelCallback) {
        if (DEBUG) {
            Slog.d(TAG, "showDialog: displayId=" + mDisplayId + " type=" + type);
        }

        Dialog dialog = mUserPickerDialogs.get(type);
        if (dialog == null) {
            dialog = createAlertDialog(mContext, getDialogTitle(type), getDialogMessage(type),
                    positiveCallback, cancelCallback, type);
        }

        if (dialog != null) {
            applyFlags(dialog);
            mUserPickerDialogs.set(type, dialog);
            dialog.show();
        }
    }

    void dismissDialog(@DialogType int type) {
        if (DEBUG) {
            Slog.d(TAG, "dismissDialog: displayId=" + mDisplayId + " type=" + type);
        }
        Dialog dialog = mUserPickerDialogs.get(type);
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            mUserPickerDialogs.remove(type);
        }
    }

    void removeDialog(@DialogType int type) {
        if (DEBUG) {
            Slog.d(TAG, "removeDialog: displayId=" + mDisplayId + " type=" + type);
        }
        mUserPickerDialogs.remove(type);
    }

    private void applyFlags(Dialog dialog) {
        Window window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    private void hideSystemBars(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window.getDecorView() != null) {
            WindowInsetsController insetsController = window.getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
            }
        }
    }

    private String getDialogTitle(@DialogType int type) {
        String title = "";
        switch (type) {
            case DIALOG_TYPE_MAX_USER_COUNT_REACHED:
                title = mMaxUserLimitReachedTitle;
                break;
            case DIALOG_TYPE_CONFIRM_ADD_USER:
                title = mConfirmAddUserTitle;
                break;
            case DIALOG_TYPE_CONFIRM_LOGOUT:
                title = mConfirmLogoutTitle;
                break;
            default:
                Slog.w(TAG, "No dialog title for given type, " + typeToString(type));
        }
        return title;
    }

    private String getDialogMessage(@DialogType int type) {
        String message = "";
        switch (type) {
            case DIALOG_TYPE_SWITCHING:
                message = mUserSwitchingMessage;
                break;
            case DIALOG_TYPE_ADDING_USER:
                message = mUserAddingMessage;
                break;
            case DIALOG_TYPE_MAX_USER_COUNT_REACHED:
                message = String.format(mMaxUserLimitReachedMessage, getMaxSupportedUsers());
                break;
            case DIALOG_TYPE_CONFIRM_ADD_USER:
                message = mConfirmAddUserMessage;
                break;
            case DIALOG_TYPE_CONFIRM_LOGOUT:
                message = mConfirmLogoutMessage;
                break;
            default:
                Slog.w(TAG, "No dialog message for given type, " + typeToString(type));
        }
        return message;
    }

    private String typeToString(@DialogType int type) {
        switch (type) {
            case DIALOG_TYPE_SWITCHING:
                return "DIALOG_TYPE_SWITCHING";
            case DIALOG_TYPE_ADDING_USER:
                return "DIALOG_TYPE_ADDING_USER";
            case DIALOG_TYPE_MAX_USER_COUNT_REACHED:
                return "DIALOG_TYPE_MAX_USER_COUNT_REACHED";
            case DIALOG_TYPE_CONFIRM_ADD_USER:
                return "DIALOG_TYPE_CONFIRM_ADD_USER";
            case DIALOG_TYPE_CONFIRM_LOGOUT:
                return "DIALOG_TYPE_CONFIRM_LOGOUT";
            default:
                return "";
        }
    }

    private AlertDialog createAlertDialog(Context context, String title, String message,
                                          Runnable positiveCallback, Runnable cancelCallback,
                                          @DialogType int type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                com.android.internal.R.style.Theme_DeviceDefault_Dialog_Alert);
        TextView messageView = null;

        switch (type) {
            case DIALOG_TYPE_MAX_USER_COUNT_REACHED:
            case DIALOG_TYPE_CONFIRM_ADD_USER:
            case DIALOG_TYPE_CONFIRM_LOGOUT:
                builder.setTitle(title);
                builder.setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (positiveCallback != null) {
                        positiveCallback.run();
                    }
                });
                if (positiveCallback != null) {
                    builder.setNegativeButton(android.R.string.cancel, (d, w) -> {
                        if (cancelCallback != null) {
                            cancelCallback.run();
                        }
                    });
                }
                if (cancelCallback != null) {
                    builder.setOnCancelListener(dialog -> cancelCallback.run());
                }
                builder.setOnDismissListener(d -> removeDialog(type));
                messageView = (TextView) LayoutInflater.from(context)
                        .inflate(R.layout.user_picker_alert_dialog_normal_message, null);
                break;
            case DIALOG_TYPE_ADDING_USER:
            case DIALOG_TYPE_SWITCHING:
                messageView = (TextView) LayoutInflater.from(context)
                        .inflate(R.layout.user_picker_alert_dialog_large_center_message, null);
                builder.setCancelable(false);
                break;
        }

        messageView.setText(message);
        builder.setView(messageView);

        final AlertDialog alertDialog = builder.create();
        messageView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                // hide system bars on the message view attached to the window.
                // A decor view of alert dialog is instantiated when show() method is called.
                // So, we can not hide system bars because there is no decor view
                // before show() is called.
                hideSystemBars(alertDialog);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {}
        });
        return alertDialog;
    }

    void clearAllDialogs() {
        mUserPickerDialogs.clear();
    }

    @VisibleForTesting
    final class UserPickerDialogs {
        final SparseArray<Dialog> mDialogs = new SparseArray<>();

        Dialog get(int type) {
            return mDialogs.get(type);
        }

        void set(int type, Dialog dialog) {
            mDialogs.set(type, dialog);
        }

        void remove(int type) {
            mDialogs.remove(type);
        }

        void clear() {
            for (int i = 0; i < mDialogs.size(); i++) {
                Dialog d = mDialogs.valueAt(i);
                if (d != null && d.isShowing()) {
                    d.dismiss();
                }
            }
            mDialogs.clear();
        }
    }
}
