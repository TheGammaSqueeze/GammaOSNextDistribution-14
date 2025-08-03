/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.userswitcher;

import static android.car.settings.CarSettings.Global.ENABLE_USER_SWITCH_DEVELOPER_MESSAGE;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.util.concurrency.DelayableExecutor;

import javax.inject.Inject;

/**
 * Handles showing and hiding UserSwitchTransitionView that is mounted to SystemUiOverlayWindow.
 */
@SysUISingleton
public class UserSwitchTransitionViewController extends OverlayViewController {
    private static final String TAG = "UserSwitchTransition";
    private static final String ENABLE_DEVELOPER_MESSAGE_TRUE = "true";
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final Resources mResources;
    private final DelayableExecutor mMainExecutor;
    private final ActivityManager mActivityManager;
    private final UserManager mUserManager;
    private final IWindowManager mWindowManagerService;
    private final UserIconProvider mUserIconProvider = new UserIconProvider();
    private final int mWindowShownTimeoutMs;
    private final Runnable mWindowShownTimeoutCallback = () -> {
        if (DEBUG) {
            Log.w(TAG, "Window was not hidden within " + getWindowShownTimeoutMs() + " ms, so it"
                    + "was hidden by mWindowShownTimeoutCallback.");
        }

        handleHide();
    };

    @GuardedBy("this")
    private boolean mShowing;
    private int mNewUserId = UserHandle.USER_NULL;
    private int mPreviousUserId = UserHandle.USER_NULL;
    private Runnable mCancelRunnable;

    @Inject
    public UserSwitchTransitionViewController(
            Context context,
            @Main Resources resources,
            @Main DelayableExecutor delayableExecutor,
            ActivityManager activityManager,
            UserManager userManager,
            IWindowManager windowManagerService,
            OverlayViewGlobalStateController overlayViewGlobalStateController) {

        super(R.id.user_switching_dialog_stub, overlayViewGlobalStateController);

        mContext = context;
        mResources = resources;
        mMainExecutor = delayableExecutor;
        mActivityManager = activityManager;
        mUserManager = userManager;
        mWindowManagerService = windowManagerService;
        mWindowShownTimeoutMs = mResources.getInteger(
                R.integer.config_userSwitchTransitionViewShownTimeoutMs);
    }

    @Override
    protected int getInsetTypesToFit() {
        return 0;
    }

    @Override
    protected void showInternal() {
        populateDialog(mPreviousUserId, mNewUserId);
        super.showInternal();
    }

    /**
     * Makes the user switch transition view appear and draws the content inside of it if a user
     * that is different from the previous user is provided and if the dialog is not already
     * showing.
     */
    void handleShow(@UserIdInt int newUserId) {
        mMainExecutor.execute(() -> {
            if (mPreviousUserId == newUserId || mShowing) return;
            mShowing = true;
            try {
                mWindowManagerService.setSwitchingUser(true);
                mWindowManagerService.lockNow(null);
            } catch (RemoteException e) {
                Log.e(TAG, "unable to notify window manager service regarding user switch");
            }

            mNewUserId = newUserId;
            start();
            // In case the window is still showing after WINDOW_SHOWN_TIMEOUT_MS, then hide the
            // window and log a warning message.
            mCancelRunnable = mMainExecutor.executeDelayed(mWindowShownTimeoutCallback,
                    mWindowShownTimeoutMs);
        });
    }

    void handleHide() {
        if (!mShowing) return;
        mMainExecutor.execute(() -> {
            // next time a new user is selected, this current new user will be the previous user.
            mPreviousUserId = mNewUserId;
            mShowing = false;
            stop();
            if (mCancelRunnable != null) {
                mCancelRunnable.run();
            }
        });
    }

    @VisibleForTesting
    int getWindowShownTimeoutMs() {
        return mWindowShownTimeoutMs;
    }

    private void populateDialog(@UserIdInt int previousUserId, @UserIdInt int newUserId) {
        drawUserIcon(newUserId);
        populateLoadingText(previousUserId, newUserId);
    }

    private void drawUserIcon(int newUserId) {
        Drawable userIcon = mUserIconProvider.getDrawableWithBadge(mContext,
                mUserManager.getUserInfo(newUserId));
        ((ImageView) getLayout().findViewById(R.id.user_loading_avatar))
                .setImageDrawable(userIcon);
    }

    private void populateLoadingText(@UserIdInt int previousUserId, @UserIdInt int newUserId) {
        TextView msgView = getLayout().findViewById(R.id.user_loading);

        boolean showInfo = ENABLE_DEVELOPER_MESSAGE_TRUE.equals(
                Settings.Global.getString(mContext.getContentResolver(),
                        ENABLE_USER_SWITCH_DEVELOPER_MESSAGE));

        if (showInfo && mPreviousUserId != UserHandle.USER_NULL) {
            msgView.setText(
                    mResources.getString(R.string.car_loading_profile_developer_message,
                            previousUserId, newUserId));
        } else {
            // Show the switchingFromUserMessage if it was set.
            String switchingFromUserMessage = mActivityManager.getSwitchingFromUserMessage();
            msgView.setText(switchingFromUserMessage != null ? switchingFromUserMessage
                    : mResources.getString(R.string.car_loading_profile));
        }
    }
}
