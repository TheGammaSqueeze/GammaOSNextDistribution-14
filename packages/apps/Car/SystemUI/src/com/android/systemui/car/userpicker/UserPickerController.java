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

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.lifecycleEventTypeToString;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.systemui.car.userpicker.DialogManager.DIALOG_TYPE_ADDING_USER;
import static com.android.systemui.car.userpicker.DialogManager.DIALOG_TYPE_CONFIRM_ADD_USER;
import static com.android.systemui.car.userpicker.DialogManager.DIALOG_TYPE_CONFIRM_LOGOUT;
import static com.android.systemui.car.userpicker.DialogManager.DIALOG_TYPE_MAX_USER_COUNT_REACHED;
import static com.android.systemui.car.userpicker.DialogManager.DIALOG_TYPE_SWITCHING;
import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_CHANGE_USER;
import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_LOGOUT;

import android.annotation.IntDef;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.user.UserCreationResult;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Slog;
import android.view.View.OnClickListener;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.R;
import com.android.systemui.car.userpicker.UserEventManager.OnUpdateUsersListener;
import com.android.systemui.car.userpicker.UserRecord.OnClickListenerCreatorBase;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.settings.DisplayTracker;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

@UserPickerScope
final class UserPickerController {
    private static final String TAG = UserPickerController.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int REQ_SHOW_ADDING_DIALOG = 1;
    private static final int REQ_DISMISS_ADDING_DIALOG = 2;
    private static final int REQ_SHOW_SWITCHING_DIALOG = 3;
    private static final int REQ_DISMISS_SWITCHING_DIALOG = 4;
    private static final int REQ_FINISH_ACTIVITY = 5;
    private static final int REQ_SHOW_SNACKBAR = 6;

    @IntDef(prefix = { "REQ_" }, value = {
            REQ_SHOW_ADDING_DIALOG,
            REQ_DISMISS_ADDING_DIALOG,
            REQ_SHOW_SWITCHING_DIALOG,
            REQ_DISMISS_SWITCHING_DIALOG,
            REQ_FINISH_ACTIVITY,
            REQ_SHOW_SNACKBAR,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PresenterRequestType {}

    private final CarServiceMediator mCarServiceMediator;
    private final DialogManager mDialogManager;
    private final SnackbarManager mSnackbarManager;
    private final LockPatternUtils mLockPatternUtils;
    private final ExecutorService mWorker;
    private final DisplayTracker mDisplayTracker;
    private final UserPickerSharedState mUserPickerSharedState;

    private Context mContext;
    private UserEventManager mUserEventManager;
    private UserIconProvider mUserIconProvider;
    private int mDisplayId;
    private Callbacks mCallbacks;
    private HeaderState mHeaderState;

    private boolean mIsUserPickerClickable = true;

    private String mDefaultGuestName;
    private String mAddUserButtonName;

    // Handler for main thread
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case REQ_SHOW_ADDING_DIALOG:
                    mDialogManager.showDialog(DIALOG_TYPE_ADDING_USER);
                    break;
                case REQ_DISMISS_ADDING_DIALOG:
                    mDialogManager.dismissDialog(DIALOG_TYPE_ADDING_USER);
                    break;
                case REQ_SHOW_SWITCHING_DIALOG:
                    mDialogManager.showDialog(DIALOG_TYPE_SWITCHING);
                    break;
                case REQ_DISMISS_SWITCHING_DIALOG:
                    mDialogManager.dismissDialog(DIALOG_TYPE_SWITCHING);
                    break;
                case REQ_FINISH_ACTIVITY:
                    mCallbacks.onFinishRequested();
                    break;
                case REQ_SHOW_SNACKBAR:
                    mSnackbarManager.showSnackbar((String) msg.obj);
                    break;
            }
        }
    };

    private OnUpdateUsersListener mUsersUpdateListener = (userId, userState) -> {
        onUserUpdate(userId, userState);
    };

    private Runnable mAddUserRunnable = () -> {
        UserCreationResult result = mUserEventManager.createNewUser();
        runOnMainHandler(REQ_DISMISS_ADDING_DIALOG);

        if (result.isSuccess()) {
            UserInfo newUserInfo = mUserEventManager.getUserInfo(result.getUser().getIdentifier());
            UserRecord userRecord = UserRecord.create(newUserInfo, newUserInfo.name,
                    /* isStartGuestSession= */ false, /* isAddUser= */ false,
                    /* isForeground= */ false,
                    /* icon= */ mUserIconProvider.getRoundedUserIcon(newUserInfo, mContext),
                    /* listenerMaker */ new OnClickListenerCreator());
            mIsUserPickerClickable = false;
            handleUserSelected(userRecord);
        } else {
            Slog.w(TAG, "Unsuccessful UserCreationResult:" + result.toString());
            // Show snack bar message for the failure of user creation.
            runOnMainHandler(REQ_SHOW_SNACKBAR,
                    mContext.getString(R.string.create_user_failed_message));
        }
    };

    @Inject
    UserPickerController(Context context, UserEventManager userEventManager,
            CarServiceMediator carServiceMediator, DialogManager dialogManager,
            SnackbarManager snackbarManager, DisplayTracker displayTracker,
            UserPickerSharedState userPickerSharedState) {
        mContext = context;
        mUserEventManager = userEventManager;
        mCarServiceMediator = carServiceMediator;
        mDialogManager = dialogManager;
        mSnackbarManager = snackbarManager;
        mLockPatternUtils = new LockPatternUtils(mContext);
        mUserIconProvider = new UserIconProvider();
        mDisplayTracker = displayTracker;
        mUserPickerSharedState = userPickerSharedState;
        mWorker = Executors.newSingleThreadExecutor();
    }

    void onConfigurationChanged() {
        updateTexts();
        updateUsers();
    }

    private void onUserUpdate(int userId, int userState) {
        if (DEBUG) {
            Slog.d(TAG, "OnUsersUpdateListener: userId=" + userId
                    + " userState=" + lifecycleEventTypeToString(userState)
                    + " displayId=" + mDisplayId);
        }
        if (userState == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
            if (mUserPickerSharedState.getUserLoginStarted(mDisplayId) == userId) {
                if (DEBUG) {
                    Slog.d(TAG, "user " + userId + " unlocked. finish user picker."
                            + " displayId=" + mDisplayId);
                }
                mCallbacks.onFinishRequested();
                mUserPickerSharedState.resetUserLoginStarted(mDisplayId);
            }
        }
        updateHeaderState();
        mCallbacks.onUpdateUsers(createUserRecords());
    }

    private void updateHeaderState() {
        // If a valid user is assigned to a display, show the change user state. Otherwise, show
        // the logged out state.
        int desiredState = mCarServiceMediator.getUserForDisplay(mDisplayId) != INVALID_USER_ID
                ? HEADER_STATE_CHANGE_USER : HEADER_STATE_LOGOUT;
        if (mHeaderState.getState() != desiredState) {
            if (DEBUG) {
                Slog.d(TAG,
                        "Change HeaderState to " + desiredState + " for displayId=" + mDisplayId);
            }
            mHeaderState.setState(desiredState);
        }
    }

    private void updateTexts() {
        mDefaultGuestName = mContext.getString(R.string.car_guest);
        mAddUserButtonName = mContext.getString(R.string.car_add_user);

        mDialogManager.updateTexts(mContext);
        mCarServiceMediator.updateTexts();
    }

    void runOnMainHandler(@PresenterRequestType int reqType) {
        mHandler.sendMessage(mHandler.obtainMessage(reqType));
    }

    void runOnMainHandler(@PresenterRequestType int reqType, Object params) {
        mHandler.sendMessage(mHandler.obtainMessage(reqType, params));
    }

    void init(Callbacks callbacks, int displayId) {
        mCallbacks = callbacks;
        mDisplayId = displayId;
        boolean isLoggedOutState = mCarServiceMediator.getUserForDisplay(mDisplayId)
                == INVALID_USER_ID;
        mHeaderState = new HeaderState(callbacks);
        mHeaderState.setState(isLoggedOutState ? HEADER_STATE_LOGOUT : HEADER_STATE_CHANGE_USER);
        mUserEventManager.registerOnUpdateUsersListener(mUsersUpdateListener, mDisplayId);
    }

    void updateUsers() {
        mCallbacks.onUpdateUsers(createUserRecords());
    }

    void onDestroy() {
        if (DEBUG) {
            Slog.d(TAG, "onDestroy: unregisterOnUsersUpdateListener. displayId=" + mDisplayId);
        }
        mUserPickerSharedState.resetUserLoginStarted(mDisplayId);
        mUserEventManager.unregisterOnUpdateUsersListener(mDisplayId);
        mUserEventManager.onDestroy();
    }

    OnClickListener getOnClickListener(UserRecord userRecord) {
        return holderView -> {
            if (!mIsUserPickerClickable) {
                return;
            }
            mIsUserPickerClickable = false;
            // If the user wants to add a user, show dialog to confirm adding a user
            if (userRecord != null && userRecord.mIsAddUser) {
                if (mUserEventManager.isUserLimitReached()) {
                    mDialogManager.showDialog(DIALOG_TYPE_MAX_USER_COUNT_REACHED);
                } else {
                    mDialogManager.showDialog(DIALOG_TYPE_CONFIRM_ADD_USER,
                            () -> startAddNewUser());
                }
                mIsUserPickerClickable = true;
                return;
            }
            handleUserSelected(userRecord);
        };
    }

    void screenOffDisplay() {
        mCarServiceMediator.screenOffDisplay(mDisplayId);
    }

    void logoutUser() {
        mIsUserPickerClickable = false;
        int userId = mCarServiceMediator.getUserForDisplay(mDisplayId);
        if (userId != INVALID_USER_ID) {
            mDialogManager.showDialog(
                    DIALOG_TYPE_CONFIRM_LOGOUT,
                    () -> logoutUserInternal(userId),
                    () -> mIsUserPickerClickable = true);
        } else {
            mIsUserPickerClickable = true;
        }
    }

    private void logoutUserInternal(int userId) {
        mUserPickerSharedState.resetUserLoginStarted(mDisplayId);
        mUserEventManager.stopUserUnchecked(userId, mDisplayId);
        mUserEventManager.runUpdateUsersOnMainThread(userId, 0);
        mIsUserPickerClickable = true;
    }

    @VisibleForTesting
    List<UserRecord> createUserRecords() {
        if (DEBUG) {
            Slog.d(TAG, "createUserRecords. displayId=" + mDisplayId);
        }
        List<UserInfo> userInfos = mUserEventManager.getAliveUsers();
        List<UserRecord> userRecords = new ArrayList<>(userInfos.size());
        UserInfo foregroundUser = mUserEventManager.getCurrentForegroundUserInfo();

        if (mDisplayId == mDisplayTracker.getDefaultDisplayId()) {
            if (mUserEventManager.isForegroundUserNotSwitchable(foregroundUser.getUserHandle())) {
                userRecords.add(UserRecord.create(foregroundUser, /* name= */ foregroundUser.name,
                        /* isStartGuestSession= */ false, /* isAddUser= */ false,
                        /* isForeground= */ true,
                        /* icon= */ mUserIconProvider.getRoundedUserIcon(foregroundUser, mContext),
                        /* listenerMaker */ new OnClickListenerCreator(),
                        /* isLoggedIn= */ true, /* loggedInDisplay= */ mDisplayId,
                        /* seatLocationName= */ mCarServiceMediator.getSeatString(mDisplayId),
                        /* isStopping= */ false));
                return userRecords;
            }
        }

        for (int i = 0; i < userInfos.size(); i++) {
            UserInfo userInfo = userInfos.get(i);
            if (userInfo.isManagedProfile()) {
                // Don't display guests or managed profile in the picker.
                continue;
            }
            int loggedInDisplayId = mCarServiceMediator.getDisplayIdForUser(userInfo.id);
            UserRecord record = UserRecord.create(userInfo, /* name= */ userInfo.name,
                    /* isStartGuestSession= */ false, /* isAddUser= */ false,
                    /* isForeground= */ userInfo.id == foregroundUser.id,
                    /* icon= */ mUserIconProvider.getRoundedUserIcon(userInfo, mContext),
                    /* listenerMaker */ new OnClickListenerCreator(),
                    /* isLoggedIn= */ loggedInDisplayId != INVALID_DISPLAY,
                    /* loggedInDisplay= */ loggedInDisplayId,
                    /* seatLocationName= */ mCarServiceMediator.getSeatString(loggedInDisplayId),
                    /* isStopping= */ mUserPickerSharedState.isStoppingUser(userInfo.id));
            userRecords.add(record);

            if (DEBUG) {
                Slog.d(TAG, "createUserRecord: userId=" + userInfo.id
                        + " logged-in=" + record.mIsLoggedIn
                        + " logged-in display=" + loggedInDisplayId
                        + " isStopping=" + record.mIsStopping);
            }
        }

        // Add button for starting guest session.
        userRecords.add(createStartGuestUserRecord());

        // Add add user record if the foreground user can add users
        if (mUserEventManager.canForegroundUserAddUsers()) {
            userRecords.add(createAddUserRecord());
        }

        return userRecords;
    }

    /**
     * Creates guest user record.
     */
    private UserRecord createStartGuestUserRecord() {
        boolean loggedIn = isGuestOnDisplay();
        int loggedInDisplay = loggedIn ? mDisplayId : INVALID_DISPLAY;
        return UserRecord.create(/* info= */ null, /* name= */ mDefaultGuestName,
                /* isStartGuestSession= */ true, /* isAddUser= */ false,
                /* isForeground= */ false,
                /* icon= */ mUserIconProvider.getRoundedGuestDefaultIcon(mContext),
                /* listenerMaker */ new OnClickListenerCreator(),
                loggedIn, loggedInDisplay,
                /* seatLocationName= */mCarServiceMediator.getSeatString(loggedInDisplay),
                /* isStopping= */ false);
    }

    /**
     * Creates add user record.
     */
    private UserRecord createAddUserRecord() {
        return UserRecord.create(/* mInfo= */ null, /* mName= */ mAddUserButtonName,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ true,
                /* mIsForeground= */ false,
                /* mIcon= */ mContext.getDrawable(R.drawable.car_add_circle_round),
                /* OnClickListenerMaker */ new OnClickListenerCreator());
    }

    void handleUserSelected(UserRecord userRecord) {
        if (userRecord == null) {
            return;
        }
        mWorker.execute(() -> {
            int userId = userRecord.mInfo != null ? userRecord.mInfo.id : INVALID_USER_ID;

            // First, check login itself.
            int prevUserId = mCarServiceMediator.getUserForDisplay(mDisplayId);
            if ((userId != INVALID_USER_ID && userId == prevUserId)
                    || (userRecord.mIsStartGuestSession && isGuestUser(prevUserId))) {
                runOnMainHandler(REQ_FINISH_ACTIVITY);
                return;
            }

            // Second, check user has been already logged-in in another display or is stopping.
            if (userRecord.mIsLoggedIn && userRecord.mLoggedInDisplay != mDisplayId
                    || mUserPickerSharedState.isStoppingUser(userId)) {
                String message;
                if (userRecord.mIsStopping) {
                    message = mContext.getString(R.string.wait_for_until_stopped_message,
                            userRecord.mName);
                } else {
                    message = mContext.getString(R.string.already_logged_in_message,
                            userRecord.mName, userRecord.mSeatLocationName);
                }
                runOnMainHandler(REQ_SHOW_SNACKBAR, message);
                mIsUserPickerClickable = true;
                return;
            }

            // Finally, start user if it has no problem.
            boolean result = false;
            try {
                if (userRecord.mIsStartGuestSession) {
                    runOnMainHandler(REQ_SHOW_SWITCHING_DIALOG);
                    UserCreationResult creationResult = mUserEventManager.createGuest();
                    if (creationResult == null || !creationResult.isSuccess()) {
                        if (creationResult == null) {
                            Slog.w(TAG, "Guest UserCreationResult is null");
                        } else if (!creationResult.isSuccess()) {
                            Slog.w(TAG, "Unsuccessful guest UserCreationResult: "
                                    + creationResult.toString());
                        }

                        runOnMainHandler(REQ_DISMISS_SWITCHING_DIALOG);
                        // Show snack bar message for the failure of guest creation.
                        runOnMainHandler(REQ_SHOW_SNACKBAR,
                                mContext.getString(R.string.guest_creation_failed_message));
                        return;
                    }
                    userId = creationResult.getUser().getIdentifier();
                }

                if (!mUserPickerSharedState.setUserLoginStarted(mDisplayId, userId)) {
                    return;
                }

                boolean isFgUserStart = prevUserId == ActivityManager.getCurrentUser();
                if (!isFgUserStart && !stopUserAssignedToDisplay(prevUserId)) {
                    return;
                }

                runOnMainHandler(REQ_SHOW_SWITCHING_DIALOG);
                result = mUserEventManager.startUserForDisplay(prevUserId, userId, mDisplayId,
                        isFgUserStart);
            } finally {
                mIsUserPickerClickable = !result;
                if (result) {
                    if (mLockPatternUtils.isSecure(userId)
                            || mUserEventManager.isUserRunningUnlocked(userId)) {
                        if (DEBUG) {
                            Slog.d(TAG, "handleUserSelected: result true, isUserRunningUnlocked="
                                    + mUserEventManager.isUserRunningUnlocked(userId)
                                    + " isSecure=" + mLockPatternUtils.isSecure(userId));
                        }
                        runOnMainHandler(REQ_FINISH_ACTIVITY);
                    }
                } else {
                    runOnMainHandler(REQ_DISMISS_SWITCHING_DIALOG);
                    mUserPickerSharedState.resetUserLoginStarted(mDisplayId);
                }
            }
        });
    }

    boolean stopUserAssignedToDisplay(@UserIdInt int prevUserId) {
        // First, check whether the previous user is assigned to this display.
        if (prevUserId == INVALID_USER_ID) {
            Slog.i(TAG, "There is no user assigned for this display " + mDisplayId);
            return true;
        }

        // Second, is starting user same with current user?
        int currentUser = ActivityManager.getCurrentUser();
        if (prevUserId == currentUser) {
            Slog.w(TAG, "Can not stop current user " + currentUser);
            return false;
        }

        // Finally, we don't need to stop user if the user is already stopped.
        if (!mUserEventManager.isUserRunning(prevUserId)) {
            if (DEBUG) {
                Slog.d(TAG, "User " + prevUserId + " is already stopping or stopped");
            }
            return true;
        }

        runOnMainHandler(REQ_SHOW_SWITCHING_DIALOG);
        return mUserEventManager.stopUserUnchecked(prevUserId, mDisplayId);
    }

    // This method is called only when creating user record.
    boolean isGuestOnDisplay() {
        int userId = mCarServiceMediator.getUserForDisplay(mDisplayId);
        return isGuestUser(userId);
    }

    private boolean isGuestUser(@UserIdInt int userId) {
        UserInfo userInfo = mUserEventManager.getUserInfo(userId);
        return userInfo == null ? false : userInfo.isGuest();
    }

    void startAddNewUser() {
        runOnMainHandler(REQ_SHOW_ADDING_DIALOG);
        mWorker.execute(mAddUserRunnable);
    }

    void dump(@NonNull PrintWriter pw) {
        pw.println("  " + getClass().getSimpleName() + ":");
        if (mHeaderState.getState() == HEADER_STATE_CHANGE_USER) {
            int loggedInUserId = mCarServiceMediator.getUserForDisplay(mDisplayId);
            pw.println("    Logged-in user : " + loggedInUserId
                    + (isGuestUser(loggedInUserId) ? "(guest)" : ""));
        }
        pw.println("    mHeaderState=" + mHeaderState.toString());
        pw.println("    mIsUserPickerClickable=" + mIsUserPickerClickable);
    }

    class OnClickListenerCreator extends OnClickListenerCreatorBase {
        @Override
        OnClickListener createOnClickListenerWithUserRecord() {
            return getOnClickListener(mUserRecord);
        }
    }

    interface Callbacks {
        void onUpdateUsers(List<UserRecord> users);
        void onHeaderStateChanged(HeaderState headerState);
        void onFinishRequested();
    }
}
