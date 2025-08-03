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

package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.provider.Browser;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.build.SdkLevel;
import com.android.wifi.resources.R;

import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Class to manage launching dialogs and returning the user reply.
 * All methods run on the main Wi-Fi thread runner except those annotated with @AnyThread, which can
 * run on any thread.
 */
public class WifiDialogManager {
    private static final String TAG = "WifiDialogManager";
    @VisibleForTesting
    static final String WIFI_DIALOG_ACTIVITY_CLASSNAME =
            "com.android.wifi.dialog.WifiDialogActivity";

    private boolean mVerboseLoggingEnabled;

    private int mNextDialogId = 0;
    private final Set<Integer> mActiveDialogIds = new ArraySet<>();
    private final @NonNull SparseArray<DialogHandleInternal> mActiveDialogHandles =
            new SparseArray<>();
    private final @NonNull ArraySet<LegacySimpleDialogHandle> mActiveLegacySimpleDialogs =
            new ArraySet<>();

    private final @NonNull WifiContext mContext;
    private final @NonNull WifiThreadRunner mWifiThreadRunner;
    private final @NonNull FrameworkFacade mFrameworkFacade;

    private final BroadcastReceiver mBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mWifiThreadRunner.post(
                            () -> {
                                String action = intent.getAction();
                                if (mVerboseLoggingEnabled) {
                                    Log.v(TAG, "Received action: " + action);
                                }
                                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                                    // Change all window types to TYPE_KEYGUARD_DIALOG to show the
                                    // dialogs over the QuickSettings after the screen is unlocked.
                                    for (LegacySimpleDialogHandle dialogHandle :
                                            mActiveLegacySimpleDialogs) {
                                        dialogHandle.changeWindowType(
                                                WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                                    }
                                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                                    if (intent.getBooleanExtra(
                                            WifiManager.EXTRA_CLOSE_SYSTEM_DIALOGS_EXCEPT_WIFI,
                                            false)) {
                                        return;
                                    }
                                    if (mVerboseLoggingEnabled) {
                                        Log.v(
                                                TAG,
                                                "ACTION_CLOSE_SYSTEM_DIALOGS received, cancelling"
                                                        + " all legacy dialogs.");
                                    }
                                    for (LegacySimpleDialogHandle dialogHandle :
                                            mActiveLegacySimpleDialogs) {
                                        dialogHandle.cancelDialog();
                                    }
                                }
                            });
                }
            };

    /**
     * Constructs a WifiDialogManager
     *
     * @param context          Main Wi-Fi context.
     * @param wifiThreadRunner Main Wi-Fi thread runner.
     * @param frameworkFacade  FrameworkFacade for launching legacy dialogs.
     */
    public WifiDialogManager(
            @NonNull WifiContext context,
            @NonNull WifiThreadRunner wifiThreadRunner,
            @NonNull FrameworkFacade frameworkFacade, WifiInjector wifiInjector) {
        mContext = context;
        mWifiThreadRunner = wifiThreadRunner;
        mFrameworkFacade = frameworkFacade;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        int flags = 0;
        if (SdkLevel.isAtLeastT()) {
            flags = Context.RECEIVER_EXPORTED;
        }
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, flags);
        wifiInjector.getWifiDeviceStateChangeManager()
                .registerStateChangeCallback(
                        new WifiDeviceStateChangeManager.StateChangeCallback() {
                            @Override
                            public void onScreenStateChanged(boolean screenOn) {
                                handleScreenStateChanged(screenOn);
                            }
                        });
    }

    private void handleScreenStateChanged(boolean screenOn) {
        // Change all window types to TYPE_APPLICATION_OVERLAY to
        // prevent the dialogs from appearing over the lock screen when
        // the screen turns on again.
        if (!screenOn) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "onScreenStateChanged: screen off");
            }
            // Change all window types to TYPE_APPLICATION_OVERLAY to
            // prevent the dialogs from appearing over the lock screen when
            // the screen turns on again.
            for (LegacySimpleDialogHandle dialogHandle :
                    mActiveLegacySimpleDialogs) {
                dialogHandle.changeWindowType(
                        WindowManager.LayoutParams
                                .TYPE_APPLICATION_OVERLAY);
            }
        }
    }

    /**
     * Enables verbose logging.
     */
    public void enableVerboseLogging(boolean enabled) {
        mVerboseLoggingEnabled = enabled;
    }

    private int getNextDialogId() {
        if (mActiveDialogIds.isEmpty() || mNextDialogId == WifiManager.INVALID_DIALOG_ID) {
            mNextDialogId = 0;
        }
        return mNextDialogId++;
    }

    private @Nullable Intent getBaseLaunchIntent(@WifiManager.DialogType int dialogType) {
        Intent intent = new Intent(WifiManager.ACTION_LAUNCH_DIALOG)
                .putExtra(WifiManager.EXTRA_DIALOG_TYPE, dialogType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String wifiDialogApkPkgName = mContext.getWifiDialogApkPkgName();
        if (wifiDialogApkPkgName == null) {
            Log.w(TAG, "Could not get WifiDialog APK package name!");
            return null;
        }
        intent.setClassName(wifiDialogApkPkgName, WIFI_DIALOG_ACTIVITY_CLASSNAME);
        return intent;
    }

    private @Nullable Intent getDismissIntent(int dialogId) {
        Intent intent = new Intent(WifiManager.ACTION_DISMISS_DIALOG);
        intent.putExtra(WifiManager.EXTRA_DIALOG_ID, dialogId);
        String wifiDialogApkPkgName = mContext.getWifiDialogApkPkgName();
        if (wifiDialogApkPkgName == null) {
            Log.w(TAG, "Could not get WifiDialog APK package name!");
            return null;
        }
        intent.setClassName(wifiDialogApkPkgName, WIFI_DIALOG_ACTIVITY_CLASSNAME);
        return intent;
    }

    /**
     * Handle for launching and dismissing a dialog from any thread.
     */
    @ThreadSafe
    public class DialogHandle {
        DialogHandleInternal mInternalHandle;
        LegacySimpleDialogHandle mLegacyHandle;

        private DialogHandle(DialogHandleInternal internalHandle) {
            mInternalHandle = internalHandle;
        }

        private DialogHandle(LegacySimpleDialogHandle legacyHandle) {
            mLegacyHandle = legacyHandle;
        }

        /**
         * Launches the dialog.
         */
        @AnyThread
        public void launchDialog() {
            if (mInternalHandle != null) {
                mWifiThreadRunner.post(() -> mInternalHandle.launchDialog(0));
            } else if (mLegacyHandle != null) {
                mWifiThreadRunner.post(() -> mLegacyHandle.launchDialog(0));
            }
        }

        /**
         * Launches the dialog with a timeout before it is auto-cancelled.
         * @param timeoutMs timeout in milliseconds before the dialog is auto-cancelled. A value <=0
         *                  indicates no timeout.
         */
        @AnyThread
        public void launchDialog(long timeoutMs) {
            if (mInternalHandle != null) {
                mWifiThreadRunner.post(() -> mInternalHandle.launchDialog(timeoutMs));
            } else if (mLegacyHandle != null) {
                mWifiThreadRunner.post(() -> mLegacyHandle.launchDialog(timeoutMs));
            }
        }

        /**
         * Dismisses the dialog. Dialogs will automatically be dismissed once the user replies, but
         * this method may be used to dismiss unanswered dialogs that are no longer needed.
         */
        @AnyThread
        public void dismissDialog() {
            if (mInternalHandle != null) {
                mWifiThreadRunner.post(() -> mInternalHandle.dismissDialog());
            } else if (mLegacyHandle != null) {
                mWifiThreadRunner.post(() -> mLegacyHandle.dismissDialog());
            }
        }
    }

    /**
     * Internal handle for launching and dismissing a dialog via the WifiDialog app from the main
     * Wi-Fi thread runner.
     * @see {@link DialogHandle}
     */
    private class DialogHandleInternal {
        private int mDialogId = WifiManager.INVALID_DIALOG_ID;
        private @Nullable Intent mIntent;
        private int mDisplayId = Display.DEFAULT_DISPLAY;

        void setIntent(@Nullable Intent intent) {
            mIntent = intent;
        }

        void setDisplayId(int displayId) {
            mDisplayId = displayId;
        }

        /**
         * @see {@link DialogHandle#launchDialog(long)}
         */
        void launchDialog(long timeoutMs) {
            if (mIntent == null) {
                Log.e(TAG, "Cannot launch dialog with null Intent!");
                return;
            }
            if (mDialogId != WifiManager.INVALID_DIALOG_ID) {
                // Dialog is already active, ignore.
                return;
            }
            registerDialog();
            mIntent.putExtra(WifiManager.EXTRA_DIALOG_TIMEOUT_MS, timeoutMs);
            mIntent.putExtra(WifiManager.EXTRA_DIALOG_ID, mDialogId);
            boolean launched = false;
            // Collapse the QuickSettings since we can't show WifiDialog dialogs over it.
            mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    .putExtra(WifiManager.EXTRA_CLOSE_SYSTEM_DIALOGS_EXCEPT_WIFI, true));
            if (SdkLevel.isAtLeastT() && mDisplayId != Display.DEFAULT_DISPLAY) {
                try {
                    mContext.startActivityAsUser(mIntent,
                            ActivityOptions.makeBasic().setLaunchDisplayId(mDisplayId).toBundle(),
                            UserHandle.CURRENT);
                    launched = true;
                } catch (Exception e) {
                    Log.e(TAG, "Error startActivityAsUser - " + e);
                }
            }
            if (!launched) {
                mContext.startActivityAsUser(mIntent, UserHandle.CURRENT);
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Launching dialog with id=" + mDialogId);
            }
        }

        /**
         * @see {@link DialogHandle#dismissDialog()}
         */
        void dismissDialog() {
            if (mDialogId == WifiManager.INVALID_DIALOG_ID) {
                // Dialog is not active, ignore.
                return;
            }
            Intent dismissIntent = getDismissIntent(mDialogId);
            if (dismissIntent == null) {
                Log.e(TAG, "Could not create intent for dismissing dialog with id: "
                        + mDialogId);
                return;
            }
            mContext.startActivityAsUser(dismissIntent, UserHandle.CURRENT);
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Dismissing dialog with id=" + mDialogId);
            }
            unregisterDialog();
        }

        /**
         * Assigns a dialog id to the dialog and registers it as an active dialog.
         */
        void registerDialog() {
            if (mDialogId != WifiManager.INVALID_DIALOG_ID) {
                // Already registered.
                return;
            }
            mDialogId = getNextDialogId();
            mActiveDialogIds.add(mDialogId);
            mActiveDialogHandles.put(mDialogId, this);
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Registered dialog with id=" + mDialogId
                        + ". Active dialogs ids: " + mActiveDialogIds);
            }
        }

        /**
         * Unregisters the dialog as an active dialog and removes its dialog id.
         * This should be called after a dialog is replied to or dismissed.
         */
        void unregisterDialog() {
            if (mDialogId == WifiManager.INVALID_DIALOG_ID) {
                // Already unregistered.
                return;
            }
            mActiveDialogIds.remove(mDialogId);
            mActiveDialogHandles.remove(mDialogId);
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Unregistered dialog with id=" + mDialogId
                        + ". Active dialogs ids: " + mActiveDialogIds);
            }
            mDialogId = WifiManager.INVALID_DIALOG_ID;
            if (mActiveDialogIds.isEmpty()) {
                String wifiDialogApkPkgName = mContext.getWifiDialogApkPkgName();
                if (wifiDialogApkPkgName == null) {
                    Log.wtf(TAG, "Could not get WifiDialog APK package name to force stop!");
                    return;
                }
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Force stopping WifiDialog app");
                }
                mContext.getSystemService(ActivityManager.class)
                        .forceStopPackage(wifiDialogApkPkgName);
            }
        }
    }

    private class SimpleDialogHandle extends DialogHandleInternal {
        @Nullable private final SimpleDialogCallback mCallback;
        @Nullable private final WifiThreadRunner mCallbackThreadRunner;

        SimpleDialogHandle(
                final String title,
                final String message,
                final String messageUrl,
                final int messageUrlStart,
                final int messageUrlEnd,
                final String positiveButtonText,
                final String negativeButtonText,
                final String neutralButtonText,
                @Nullable final SimpleDialogCallback callback,
                @Nullable final WifiThreadRunner callbackThreadRunner) {
            Intent intent = getBaseLaunchIntent(WifiManager.DIALOG_TYPE_SIMPLE);
            if (intent != null) {
                intent.putExtra(WifiManager.EXTRA_DIALOG_TITLE, title)
                        .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE, message)
                        .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE_URL, messageUrl)
                        .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE_URL_START, messageUrlStart)
                        .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE_URL_END, messageUrlEnd)
                        .putExtra(WifiManager.EXTRA_DIALOG_POSITIVE_BUTTON_TEXT, positiveButtonText)
                        .putExtra(WifiManager.EXTRA_DIALOG_NEGATIVE_BUTTON_TEXT, negativeButtonText)
                        .putExtra(WifiManager.EXTRA_DIALOG_NEUTRAL_BUTTON_TEXT, neutralButtonText);
                setIntent(intent);
            }
            setDisplayId(Display.DEFAULT_DISPLAY);
            mCallback = callback;
            mCallbackThreadRunner = callbackThreadRunner;
        }

        void notifyOnPositiveButtonClicked() {
            if (mCallbackThreadRunner != null && mCallback != null) {
                mCallbackThreadRunner.post(mCallback::onPositiveButtonClicked);
            }
            unregisterDialog();
        }

        void notifyOnNegativeButtonClicked() {
            if (mCallbackThreadRunner != null && mCallback != null) {
                mCallbackThreadRunner.post(mCallback::onNegativeButtonClicked);
            }
            unregisterDialog();
        }

        void notifyOnNeutralButtonClicked() {
            if (mCallbackThreadRunner != null && mCallback != null) {
                mCallbackThreadRunner.post(mCallback::onNeutralButtonClicked);
            }
            unregisterDialog();
        }

        void notifyOnCancelled() {
            if (mCallbackThreadRunner != null && mCallback != null) {
                mCallbackThreadRunner.post(mCallback::onCancelled);
            }
            unregisterDialog();
        }
    }

    /**
     * Implementation of a simple dialog using AlertDialogs created directly in the system process.
     */
    private class LegacySimpleDialogHandle {
        final String mTitle;
        final SpannableString mMessage;
        final String mPositiveButtonText;
        final String mNegativeButtonText;
        final String mNeutralButtonText;
        @Nullable final SimpleDialogCallback mCallback;
        @Nullable final WifiThreadRunner mCallbackThreadRunner;
        private Runnable mTimeoutRunnable;
        private AlertDialog mAlertDialog;
        int mWindowType = WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
        long mTimeoutMs = 0;

        LegacySimpleDialogHandle(
                final String title,
                final String message,
                final String messageUrl,
                final int messageUrlStart,
                final int messageUrlEnd,
                final String positiveButtonText,
                final String negativeButtonText,
                final String neutralButtonText,
                @Nullable final SimpleDialogCallback callback,
                @Nullable final WifiThreadRunner callbackThreadRunner) {
            mTitle = title;
            if (message != null) {
                mMessage = new SpannableString(message);
                if (messageUrl != null) {
                    if (messageUrlStart < 0) {
                        Log.w(TAG, "Span start cannot be less than 0!");
                    } else if (messageUrlEnd > message.length()) {
                        Log.w(TAG, "Span end index " + messageUrlEnd + " cannot be greater than "
                                + "message length " + message.length() + "!");
                    } else if (messageUrlStart > messageUrlEnd) {
                        Log.w(TAG, "Span start index cannot be greater than end index!");
                    } else {
                        mMessage.setSpan(new URLSpan(messageUrl) {
                            @Override
                            public void onClick(@NonNull View widget) {
                                Context c = widget.getContext();
                                Intent openLinkIntent = new Intent(Intent.ACTION_VIEW)
                                        .setData(Uri.parse(messageUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(Browser.EXTRA_APPLICATION_ID, c.getPackageName());
                                c.startActivityAsUser(openLinkIntent, UserHandle.CURRENT);
                                LegacySimpleDialogHandle.this.dismissDialog();
                            }}, messageUrlStart, messageUrlEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            } else {
                mMessage = null;
            }
            mPositiveButtonText = positiveButtonText;
            mNegativeButtonText = negativeButtonText;
            mNeutralButtonText = neutralButtonText;
            mCallback = callback;
            mCallbackThreadRunner = callbackThreadRunner;
        }

        void launchDialog(long timeoutMs) {
            if (mAlertDialog != null && mAlertDialog.isShowing()) {
                // Dialog is already launched. Dismiss and create a new one.
                mAlertDialog.setOnDismissListener(null);
                mAlertDialog.dismiss();
            }
            if (mTimeoutRunnable != null) {
                // Reset the timeout runnable if one has already been created.
                mWifiThreadRunner.removeCallbacks(mTimeoutRunnable);
                mTimeoutRunnable = null;
            }
            mTimeoutMs = timeoutMs;
            mAlertDialog = mFrameworkFacade.makeAlertDialogBuilder(
                    new ContextThemeWrapper(mContext, R.style.wifi_dialog))
                    .setTitle(mTitle)
                    .setMessage(mMessage)
                    .setPositiveButton(mPositiveButtonText, (dialogPositive, which) -> {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Positive button pressed for legacy simple dialog");
                        }
                        if (mCallbackThreadRunner != null && mCallback != null) {
                            mCallbackThreadRunner.post(mCallback::onPositiveButtonClicked);
                        }
                    })
                    .setNegativeButton(mNegativeButtonText, (dialogNegative, which) -> {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Negative button pressed for legacy simple dialog");
                        }
                        if (mCallbackThreadRunner != null && mCallback != null) {
                            mCallbackThreadRunner.post(mCallback::onNegativeButtonClicked);
                        }
                    })
                    .setNeutralButton(mNeutralButtonText, (dialogNeutral, which) -> {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Neutral button pressed for legacy simple dialog");
                        }
                        if (mCallbackThreadRunner != null && mCallback != null) {
                            mCallbackThreadRunner.post(mCallback::onNeutralButtonClicked);
                        }
                    })
                    .setOnCancelListener((dialogCancel) -> {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Legacy simple dialog cancelled.");
                        }
                        if (mCallbackThreadRunner != null && mCallback != null) {
                            mCallbackThreadRunner.post(mCallback::onCancelled);
                        }
                    })
                    .setOnDismissListener((dialogDismiss) -> {
                        mWifiThreadRunner.post(() -> {
                            if (mTimeoutRunnable != null) {
                                mWifiThreadRunner.removeCallbacks(mTimeoutRunnable);
                                mTimeoutRunnable = null;
                            }
                            mAlertDialog = null;
                            mActiveLegacySimpleDialogs.remove(this);
                        });
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(mContext.getResources().getBoolean(
                    R.bool.config_wifiDialogCanceledOnTouchOutside));
            final Window window = mAlertDialog.getWindow();
            int gravity = mContext.getResources().getInteger(R.integer.config_wifiDialogGravity);
            if (gravity != Gravity.NO_GRAVITY) {
                window.setGravity(gravity);
            }
            final WindowManager.LayoutParams lp = window.getAttributes();
            window.setType(mWindowType);
            lp.setFitInsetsTypes(WindowInsets.Type.statusBars()
                    | WindowInsets.Type.navigationBars());
            lp.setFitInsetsSides(WindowInsets.Side.all());
            lp.setFitInsetsIgnoringVisibility(true);
            window.setAttributes(lp);
            window.addSystemFlags(
                    WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS);
            mAlertDialog.show();
            TextView messageView = mAlertDialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }
            if (mTimeoutMs > 0) {
                mTimeoutRunnable = mAlertDialog::cancel;
                mWifiThreadRunner.postDelayed(mTimeoutRunnable, mTimeoutMs);
            }
            mActiveLegacySimpleDialogs.add(this);
        }

        void dismissDialog() {
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
            }
        }

        void cancelDialog() {
            if (mAlertDialog != null) {
                mAlertDialog.cancel();
            }
        }

        void changeWindowType(int windowType) {
            mWindowType = windowType;
            if (mActiveLegacySimpleDialogs.contains(this)) {
                launchDialog(mTimeoutMs);
            }
        }
    }

    /**
     * Callback for receiving simple dialog responses.
     */
    public interface SimpleDialogCallback {
        /**
         * The positive button was clicked.
         */
        void onPositiveButtonClicked();

        /**
         * The negative button was clicked.
         */
        void onNegativeButtonClicked();

        /**
         * The neutral button was clicked.
         */
        void onNeutralButtonClicked();

        /**
         * The dialog was cancelled (back button or home button or timeout).
         */
        void onCancelled();
    }

    /**
     * Creates a simple dialog with optional title, message, and positive/negative/neutral buttons.
     *
     * @param title                Title of the dialog.
     * @param message              Message of the dialog.
     * @param positiveButtonText   Text of the positive button or {@code null} for no button.
     * @param negativeButtonText   Text of the negative button or {@code null} for no button.
     * @param neutralButtonText    Text of the neutral button or {@code null} for no button.
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @NonNull
    public DialogHandle createSimpleDialog(
            @Nullable String title,
            @Nullable String message,
            @Nullable String positiveButtonText,
            @Nullable String negativeButtonText,
            @Nullable String neutralButtonText,
            @NonNull SimpleDialogCallback callback,
            @NonNull WifiThreadRunner callbackThreadRunner) {
        return createSimpleDialogWithUrl(
                title,
                message,
                null /* messageUrl */,
                0 /* messageUrlStart */,
                0 /* messageUrlEnd */,
                positiveButtonText,
                negativeButtonText,
                neutralButtonText,
                callback,
                callbackThreadRunner);
    }

    /**
     * Creates a simple dialog with a URL embedded in the message.
     *
     * @param title                Title of the dialog.
     * @param message              Message of the dialog.
     * @param messageUrl           URL to embed in the message. If non-null, then message must also
     *                             be non-null.
     * @param messageUrlStart      Start index (inclusive) of the URL in the message. Must be
     *                             non-negative.
     * @param messageUrlEnd        End index (exclusive) of the URL in the message. Must be less
     *                             than the length of message.
     * @param positiveButtonText   Text of the positive button or {@code null} for no button.
     * @param negativeButtonText   Text of the negative button or {@code null} for no button.
     * @param neutralButtonText    Text of the neutral button or {@code null} for no button.
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @NonNull
    public DialogHandle createSimpleDialogWithUrl(
            @Nullable String title,
            @Nullable String message,
            @Nullable String messageUrl,
            int messageUrlStart,
            int messageUrlEnd,
            @Nullable String positiveButtonText,
            @Nullable String negativeButtonText,
            @Nullable String neutralButtonText,
            @NonNull SimpleDialogCallback callback,
            @NonNull WifiThreadRunner callbackThreadRunner) {
        if (SdkLevel.isAtLeastT()) {
            return new DialogHandle(
                    new SimpleDialogHandle(
                            title,
                            message,
                            messageUrl,
                            messageUrlStart,
                            messageUrlEnd,
                            positiveButtonText,
                            negativeButtonText,
                            neutralButtonText,
                            callback,
                            callbackThreadRunner)
            );
        } else {
            // TODO(b/238353074): Remove this fallback to the legacy implementation once the
            //                    AlertDialog style on pre-T platform is fixed.
            return new DialogHandle(
                    new LegacySimpleDialogHandle(
                            title,
                            message,
                            messageUrl,
                            messageUrlStart,
                            messageUrlEnd,
                            positiveButtonText,
                            negativeButtonText,
                            neutralButtonText,
                            callback,
                            callbackThreadRunner)
            );
        }
    }

    /**
     * Creates a legacy simple dialog on the system process with optional title, message, and
     * positive/negative/neutral buttons.
     *
     * @param title                Title of the dialog.
     * @param message              Message of the dialog.
     * @param positiveButtonText   Text of the positive button or {@code null} for no button.
     * @param negativeButtonText   Text of the negative button or {@code null} for no button.
     * @param neutralButtonText    Text of the neutral button or {@code null} for no button.
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @NonNull
    public DialogHandle createLegacySimpleDialog(
            @Nullable String title,
            @Nullable String message,
            @Nullable String positiveButtonText,
            @Nullable String negativeButtonText,
            @Nullable String neutralButtonText,
            @NonNull SimpleDialogCallback callback,
            @NonNull WifiThreadRunner callbackThreadRunner) {
        return createLegacySimpleDialogWithUrl(
                title,
                message,
                null /* messageUrl */,
                0 /* messageUrlStart */,
                0 /* messageUrlEnd */,
                positiveButtonText,
                negativeButtonText,
                neutralButtonText,
                callback,
                callbackThreadRunner);
    }

    /**
     * Creates a legacy simple dialog on the system process with a URL embedded in the message.
     *
     * @param title                Title of the dialog.
     * @param message              Message of the dialog.
     * @param messageUrl           URL to embed in the message. If non-null, then message must also
     *                             be non-null.
     * @param messageUrlStart      Start index (inclusive) of the URL in the message. Must be
     *                             non-negative.
     * @param messageUrlEnd        End index (exclusive) of the URL in the message. Must be less
     *                             than the length of message.
     * @param positiveButtonText   Text of the positive button or {@code null} for no button.
     * @param negativeButtonText   Text of the negative button or {@code null} for no button.
     * @param neutralButtonText    Text of the neutral button or {@code null} for no button.
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @NonNull
    public DialogHandle createLegacySimpleDialogWithUrl(
            @Nullable String title,
            @Nullable String message,
            @Nullable String messageUrl,
            int messageUrlStart,
            int messageUrlEnd,
            @Nullable String positiveButtonText,
            @Nullable String negativeButtonText,
            @Nullable String neutralButtonText,
            @Nullable SimpleDialogCallback callback,
            @Nullable WifiThreadRunner callbackThreadRunner) {
        return new DialogHandle(
                new LegacySimpleDialogHandle(
                        title,
                        message,
                        messageUrl,
                        messageUrlStart,
                        messageUrlEnd,
                        positiveButtonText,
                        negativeButtonText,
                        neutralButtonText,
                        callback,
                        callbackThreadRunner)
        );
    }

    /**
     * Returns the reply to a simple dialog to the callback of matching dialogId.
     * @param dialogId id of the replying dialog.
     * @param reply    reply of the dialog.
     */
    public void replyToSimpleDialog(int dialogId, @WifiManager.DialogReply int reply) {
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Response received for simple dialog. id=" + dialogId + " reply=" + reply);
        }
        DialogHandleInternal internalHandle = mActiveDialogHandles.get(dialogId);
        if (internalHandle == null) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "No matching dialog handle for simple dialog id=" + dialogId);
            }
            return;
        }
        if (!(internalHandle instanceof SimpleDialogHandle)) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "Dialog handle with id " + dialogId + " is not for a simple dialog.");
            }
            return;
        }
        switch (reply) {
            case WifiManager.DIALOG_REPLY_POSITIVE:
                ((SimpleDialogHandle) internalHandle).notifyOnPositiveButtonClicked();
                break;
            case WifiManager.DIALOG_REPLY_NEGATIVE:
                ((SimpleDialogHandle) internalHandle).notifyOnNegativeButtonClicked();
                break;
            case WifiManager.DIALOG_REPLY_NEUTRAL:
                ((SimpleDialogHandle) internalHandle).notifyOnNeutralButtonClicked();
                break;
            case WifiManager.DIALOG_REPLY_CANCELLED:
                ((SimpleDialogHandle) internalHandle).notifyOnCancelled();
                break;
            default:
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "Received invalid reply=" + reply);
                }
        }
    }

    private class P2pInvitationReceivedDialogHandle extends DialogHandleInternal {
        @Nullable private final P2pInvitationReceivedDialogCallback mCallback;
        @Nullable private final WifiThreadRunner mCallbackThreadRunner;

        P2pInvitationReceivedDialogHandle(
                final @Nullable String deviceName,
                final boolean isPinRequested,
                @Nullable String displayPin,
                int displayId,
                @Nullable P2pInvitationReceivedDialogCallback callback,
                @Nullable WifiThreadRunner callbackThreadRunner) {
            Intent intent = getBaseLaunchIntent(WifiManager.DIALOG_TYPE_P2P_INVITATION_RECEIVED);
            if (intent != null) {
                intent.putExtra(WifiManager.EXTRA_P2P_DEVICE_NAME, deviceName)
                        .putExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED, isPinRequested)
                        .putExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN, displayPin);
                setIntent(intent);
            }
            setDisplayId(displayId);
            mCallback = callback;
            mCallbackThreadRunner = callbackThreadRunner;
        }

        void notifyOnAccepted(@Nullable String optionalPin) {
            if (mCallbackThreadRunner != null && mCallback != null) {
                mCallbackThreadRunner.post(() -> mCallback.onAccepted(optionalPin));
            }
            unregisterDialog();
        }

        void notifyOnDeclined() {
            if (mCallbackThreadRunner != null && mCallback != null) {
                mCallbackThreadRunner.post(mCallback::onDeclined);
            }
            unregisterDialog();
        }
    }

    /**
     * Callback for receiving P2P Invitation Received dialog responses.
     */
    public interface P2pInvitationReceivedDialogCallback {
        /**
         * Invitation was accepted.
         *
         * @param optionalPin Optional PIN if a PIN was requested, or {@code null} otherwise.
         */
        void onAccepted(@Nullable String optionalPin);

        /**
         * Invitation was declined or cancelled (back button or home button or timeout).
         */
        void onDeclined();
    }

    /**
     * Creates a P2P Invitation Received dialog.
     *
     * @param deviceName           Name of the device sending the invitation.
     * @param isPinRequested       True if a PIN was requested and a PIN input UI should be shown.
     * @param displayPin           Display PIN, or {@code null} if no PIN should be displayed
     * @param displayId            The ID of the Display on which to place the dialog
     *                             (Display.DEFAULT_DISPLAY
     *                             refers to the default display)
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @NonNull
    public DialogHandle createP2pInvitationReceivedDialog(
            @Nullable String deviceName,
            boolean isPinRequested,
            @Nullable String displayPin,
            int displayId,
            @Nullable P2pInvitationReceivedDialogCallback callback,
            @Nullable WifiThreadRunner callbackThreadRunner) {
        return new DialogHandle(
                new P2pInvitationReceivedDialogHandle(
                        deviceName,
                        isPinRequested,
                        displayPin,
                        displayId,
                        callback,
                        callbackThreadRunner)
        );
    }

    /**
     * Returns the reply to a P2P Invitation Received dialog to the callback of matching dialogId.
     * Note: Must be invoked only from the main Wi-Fi thread.
     *
     * @param dialogId    id of the replying dialog.
     * @param accepted    Whether the invitation was accepted.
     * @param optionalPin PIN of the reply, or {@code null} if none was supplied.
     */
    public void replyToP2pInvitationReceivedDialog(
            int dialogId,
            boolean accepted,
            @Nullable String optionalPin) {
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Response received for P2P Invitation Received dialog."
                    + " id=" + dialogId
                    + " accepted=" + accepted
                    + " pin=" + optionalPin);
        }
        DialogHandleInternal internalHandle = mActiveDialogHandles.get(dialogId);
        if (internalHandle == null) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "No matching dialog handle for P2P Invitation Received dialog"
                        + " id=" + dialogId);
            }
            return;
        }
        if (!(internalHandle instanceof P2pInvitationReceivedDialogHandle)) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "Dialog handle with id " + dialogId
                        + " is not for a P2P Invitation Received dialog.");
            }
            return;
        }
        if (accepted) {
            ((P2pInvitationReceivedDialogHandle) internalHandle).notifyOnAccepted(optionalPin);
        } else {
            ((P2pInvitationReceivedDialogHandle) internalHandle).notifyOnDeclined();
        }
    }

    private class P2pInvitationSentDialogHandle extends DialogHandleInternal {
        P2pInvitationSentDialogHandle(
                @Nullable final String deviceName,
                @Nullable final String displayPin,
                int displayId) {
            Intent intent = getBaseLaunchIntent(WifiManager.DIALOG_TYPE_P2P_INVITATION_SENT);
            if (intent != null) {
                intent.putExtra(WifiManager.EXTRA_P2P_DEVICE_NAME, deviceName)
                        .putExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN, displayPin);
                setIntent(intent);
            }
            setDisplayId(displayId);
        }
    }

    /**
     * Creates a P2P Invitation Sent dialog.
     *
     * @param deviceName           Name of the device the invitation was sent to.
     * @param displayPin           display PIN
     * @param displayId            display ID
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @NonNull
    public DialogHandle createP2pInvitationSentDialog(
            @Nullable String deviceName,
            @Nullable String displayPin,
            int displayId) {
        return new DialogHandle(new P2pInvitationSentDialogHandle(deviceName, displayPin,
                displayId));
    }
}
