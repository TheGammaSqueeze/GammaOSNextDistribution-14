/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.OUTLINE;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_OFF_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SAVE_TO_PERSONAL_MESSAGE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SAVE_TO_PERSONAL_TITLE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SAVE_TO_WORK_MESSAGE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SAVE_TO_WORK_TITLE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_PERSONAL_FILES_MESSAGE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_PERSONAL_FILES_TITLE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_WORK_FILES_MESSAGE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_WORK_FILES_TITLE;
import static com.android.documentsui.DevicePolicyResources.Strings.CROSS_PROFILE_NOT_ALLOWED_MESSAGE;
import static com.android.documentsui.DevicePolicyResources.Strings.CROSS_PROFILE_NOT_ALLOWED_TITLE;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_PROFILE_OFF_ENABLE_BUTTON;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_PROFILE_OFF_ERROR_TITLE;

import android.Manifest;
import android.app.AuthenticationRequiredException;
import android.app.admin.DevicePolicyManager;
import android.content.pm.PackageManager;
import android.content.pm.UserProperties;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.CrossProfileException;
import com.android.documentsui.CrossProfileNoPermissionException;
import com.android.documentsui.CrossProfileQuietModeException;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.Metrics;
import com.android.documentsui.Model.Update;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.dirlist.DocumentsAdapter.Environment;
import com.android.modules.utils.build.SdkLevel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Data object used by {@link InflateMessageDocumentHolder} and {@link HeaderMessageDocumentHolder}.
 */

abstract class Message {
    private static final int ACCESS_CROSS_PROFILE_FILES = -1;

    protected final Environment mEnv;
    // If the message has a button, this will be the default button call back.
    protected final Runnable mDefaultCallback;
    // If a message has a new callback when updated, this field should be updated.
    protected @Nullable Runnable mCallback;

    private @Nullable CharSequence mMessageTitle;
    private @Nullable CharSequence mMessageString;
    private @Nullable CharSequence mButtonString;
    private @Nullable Drawable mIcon;
    private boolean mShouldShow = false;
    protected boolean mShouldKeep = false;
    protected int mLayout;
    protected ConfigStore mConfigStore;

    Message(Environment env, Runnable defaultCallback, ConfigStore configStore) {
        mEnv = env;
        mDefaultCallback = defaultCallback;
        mConfigStore = configStore;
    }

    abstract void update(Update event);

    protected void update(@Nullable CharSequence messageTitle, CharSequence messageString,
            @Nullable CharSequence buttonString, Drawable icon) {
        if (messageString == null) {
            return;
        }
        mMessageTitle = messageTitle;
        mMessageString = messageString;
        mButtonString = buttonString;
        mIcon = icon;
        mShouldShow = true;
    }

    void reset() {
        mMessageString = null;
        mIcon = null;
        mShouldShow = false;
        mLayout = 0;
    }

    void runCallback() {
        if (mCallback != null) {
            mCallback.run();
        } else {
            mDefaultCallback.run();
        }
    }

    Drawable getIcon() {
        return mIcon;
    }

    int getLayout() {
        return mLayout;
    }

    boolean shouldShow() {
        return mShouldShow;
    }

    /**
     * Return this message should keep showing or not.
     *
     * @return true if this message should keep showing.
     */
    boolean shouldKeep() {
        return mShouldKeep;
    }

    CharSequence getTitleString() {
        return mMessageTitle;
    }

    CharSequence getMessageString() {
        return mMessageString;
    }

    CharSequence getButtonString() {
        return mButtonString;
    }

    final static class HeaderMessage extends Message {

        private static final String TAG = "HeaderMessage";

        HeaderMessage(Environment env, Runnable callback, ConfigStore configStore) {
            super(env, callback, configStore);
        }

        @Override
        void update(Update event) {
            reset();
            // Error gets first dibs ... for now
            // TODO: These should be different Message objects getting updated instead of
            // overwriting.
            if (event.hasAuthenticationException()) {
                updateToAuthenticationExceptionHeader(event);
            } else if (mEnv.getModel().error != null) {
                update(null, mEnv.getModel().error, null,
                        mEnv.getContext().getDrawable(R.drawable.ic_dialog_alert));
            } else if (mEnv.getModel().info != null) {
                update(null, mEnv.getModel().info, null,
                        mEnv.getContext().getDrawable(R.drawable.ic_dialog_info));
            } else if (mEnv.getDisplayState().action == State.ACTION_OPEN_TREE
                    && mEnv.getDisplayState().stack.peek() != null
                    && mEnv.getDisplayState().stack.peek().isBlockedFromTree()
                    && mEnv.getDisplayState().restrictScopeStorage) {
                updateBlockFromTreeMessage();
                mCallback = () -> {
                    mEnv.getActionHandler().showCreateDirectoryDialog();
                };
            }
        }

        private void updateToAuthenticationExceptionHeader(Update event) {
            assert (mEnv.getFeatures().isRemoteActionsEnabled());

            RootInfo root = mEnv.getDisplayState().stack.getRoot();
            String appName = DocumentsApplication.getProvidersCache(
                    mEnv.getContext()).getApplicationName(root.userId, root.authority);
            update(null, mEnv.getContext().getString(R.string.authentication_required, appName),
                    mEnv.getContext().getResources().getText(R.string.sign_in),
                    mEnv.getContext().getDrawable(R.drawable.ic_dialog_info));
            mCallback = () -> {
                AuthenticationRequiredException exception =
                        (AuthenticationRequiredException) event.getException();
                mEnv.getActionHandler().startAuthentication(exception.getUserAction());
            };
        }

        private void updateBlockFromTreeMessage() {
            mShouldKeep = true;
            update(mEnv.getContext().getString(R.string.directory_blocked_header_title),
                    mEnv.getContext().getString(R.string.directory_blocked_header_subtitle),
                    mEnv.getContext().getString(R.string.create_new_folder_button),
                    mEnv.getContext().getDrawable(R.drawable.ic_dialog_info));
        }
    }

    final static class InflateMessage extends Message {

        private static final String TAG = "InflateMessage";
        private UserId mSourceUserId = null;
        private UserId mSelectedUserId = null;
        private Map<UserId, String> mUserIdToLabelMap = new HashMap<>();
        private final boolean mCanModifyQuietMode;
        private UserManager mUserManager = null;

        InflateMessage(Environment env, Runnable callback, ConfigStore configStore) {
            super(env, callback, configStore);
            mCanModifyQuietMode =
                    mEnv.getContext().checkSelfPermission(Manifest.permission.MODIFY_QUIET_MODE)
                            == PackageManager.PERMISSION_GRANTED;
        }

        InflateMessage(Environment env, Runnable callback, UserId sourceUserId,
                UserId selectedUserId, Map<UserId, String> userIdToLabelMap,
                UserManager userManager, ConfigStore configStore) {
            super(env, callback, configStore);
            mSourceUserId = sourceUserId;
            mSelectedUserId = selectedUserId;
            mUserIdToLabelMap = userIdToLabelMap;
            mUserManager = userManager != null ? userManager
                    : mEnv.getContext().getSystemService(UserManager.class);
            mCanModifyQuietMode = setCanModifyQuietMode();
        }

        private boolean setCanModifyQuietMode() {
            if (SdkLevel.isAtLeastV() && mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
                if (mUserManager == null) {
                    Log.e(TAG, "can not obtain user manager class");
                    return false;
                }

                UserProperties userProperties = mUserManager.getUserProperties(
                        UserHandle.of(mSelectedUserId.getIdentifier()));
                return userProperties.getShowInQuietMode()
                        == UserProperties.SHOW_IN_QUIET_MODE_PAUSED
                        && mEnv.getContext().checkSelfPermission(
                        Manifest.permission.MODIFY_QUIET_MODE)
                        == PackageManager.PERMISSION_GRANTED;
            } else {
                return mEnv.getContext().checkSelfPermission(Manifest.permission.MODIFY_QUIET_MODE)
                        == PackageManager.PERMISSION_GRANTED;
            }
        }

        @Override
        void update(Update event) {
            reset();
            if (event.hasCrossProfileException()) {
                CrossProfileException e = (CrossProfileException) event.getException();
                Metrics.logCrossProfileEmptyState(e);
                if (e instanceof CrossProfileQuietModeException) {
                    updateToQuietModeErrorMessage(
                            ((CrossProfileQuietModeException) event.getException()).mUserId);
                } else if (event.getException() instanceof CrossProfileNoPermissionException) {
                    updateToCrossProfileNoPermissionErrorMessage();
                } else {
                    updateToInflatedErrorMessage();
                }
            } else if (event.hasException() && !event.hasAuthenticationException()) {
                updateToInflatedErrorMessage();
            } else if (event.hasAuthenticationException()) {
                updateToCantDisplayContentMessage();
            } else if (mEnv.getModel().getModelIds().length == 0) {
                updateToInflatedEmptyMessage();
            }
        }

        private void updateToQuietModeErrorMessage(UserId userId) {
            mLayout = InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR;
            String buttonText = null;
            Resources res = null;
            String selectedProfile = null;
            if (mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
                res = mEnv.getContext().getResources();
                assert mUserIdToLabelMap != null;
                selectedProfile = mUserIdToLabelMap.get(userId);
            }
            if (mCanModifyQuietMode) {
                buttonText = mConfigStore.isPrivateSpaceInDocsUIEnabled()
                        ? res.getString(R.string.profile_quiet_mode_button,
                        selectedProfile.toLowerCase(Locale.getDefault()))
                        : getEnterpriseString(
                                WORK_PROFILE_OFF_ENABLE_BUTTON, R.string.quiet_mode_button);
                mCallback = () -> mEnv.getActionHandler().requestQuietModeDisabled(
                        mEnv.getDisplayState().stack.getRoot(), userId);
            }

            update(mConfigStore.isPrivateSpaceInDocsUIEnabled()
                            ? res.getString(R.string.profile_quiet_mode_error_title,
                            selectedProfile)
                            : getEnterpriseString(
                                    WORK_PROFILE_OFF_ERROR_TITLE, R.string.quiet_mode_error_title),
                    /* messageString= */ "",
                    buttonText,
                    getWorkProfileOffIcon());
        }

        private void updateToCrossProfileNoPermissionErrorMessage() {
            mLayout = InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR;
            update(getCrossProfileNoPermissionErrorTitle(),
                    getCrossProfileNoPermissionErrorMessage(),
                    /* buttonString= */ null,
                    mEnv.getContext().getDrawable(R.drawable.share_off));
        }

        private CharSequence getCrossProfileNoPermissionErrorTitle() {
            switch (mEnv.getDisplayState().action) {
                case State.ACTION_GET_CONTENT:
                case State.ACTION_OPEN:
                case State.ACTION_OPEN_TREE:
                    return mConfigStore.isPrivateSpaceInDocsUIEnabled()
                            ? getErrorTitlePrivateSpaceEnabled(ACCESS_CROSS_PROFILE_FILES)
                            : getErrorTitlePrivateSpaceDisabled(ACCESS_CROSS_PROFILE_FILES);
                case State.ACTION_CREATE:
                    return mConfigStore.isPrivateSpaceInDocsUIEnabled()
                            ? getErrorTitlePrivateSpaceEnabled(State.ACTION_CREATE)
                            : getErrorTitlePrivateSpaceDisabled(State.ACTION_CREATE);
            }
            return getEnterpriseString(
                    CROSS_PROFILE_NOT_ALLOWED_TITLE,
                    R.string.cross_profile_action_not_allowed_title);
        }

        private CharSequence getErrorTitlePrivateSpaceEnabled(int action) {
            Resources res = mEnv.getContext().getResources();
            String selectedProfileLabel = mUserIdToLabelMap.get(mSelectedUserId);
            if (selectedProfileLabel == null) return "";
            if (action == ACCESS_CROSS_PROFILE_FILES) {
                return res.getString(R.string.cant_select_cross_profile_files_error_title,
                        selectedProfileLabel.toLowerCase(Locale.getDefault()));
            } else if (action == State.ACTION_CREATE) {
                return res.getString(R.string.cant_save_to_cross_profile_error_title,
                        selectedProfileLabel.toLowerCase(Locale.getDefault()));
            } else {
                Log.e(TAG, "Unexpected intent action received.");
                return "";
            }
        }

        private CharSequence getErrorTitlePrivateSpaceDisabled(int action) {
            boolean currentUserIsSystem = UserId.CURRENT_USER.isSystem();
            if (action == ACCESS_CROSS_PROFILE_FILES) {
                return currentUserIsSystem
                        ? getEnterpriseString(CANT_SELECT_WORK_FILES_TITLE,
                        R.string.cant_select_work_files_error_title)
                        : getEnterpriseString(CANT_SELECT_PERSONAL_FILES_TITLE,
                                R.string.cant_select_personal_files_error_title);
            } else if (action == State.ACTION_CREATE) {
                return currentUserIsSystem
                        ? getEnterpriseString(CANT_SAVE_TO_WORK_TITLE,
                        R.string.cant_save_to_work_error_title)
                        : getEnterpriseString(CANT_SAVE_TO_PERSONAL_TITLE,
                                R.string.cant_save_to_personal_error_title);
            } else {
                Log.e(TAG, "Unexpected intent action received.");
                return "";
            }
        }

        private CharSequence getCrossProfileNoPermissionErrorMessage() {
            switch (mEnv.getDisplayState().action) {
                case State.ACTION_GET_CONTENT:
                case State.ACTION_OPEN:
                case State.ACTION_OPEN_TREE:
                    return mConfigStore.isPrivateSpaceInDocsUIEnabled()
                            ? getErrorMessagePrivateSpaceEnabled(ACCESS_CROSS_PROFILE_FILES)
                            : getErrorMessagePrivateSpaceDisabled(ACCESS_CROSS_PROFILE_FILES);
                case State.ACTION_CREATE:
                    return mConfigStore.isPrivateSpaceInDocsUIEnabled()
                            ? getErrorMessagePrivateSpaceEnabled(State.ACTION_CREATE)
                            : getErrorMessagePrivateSpaceDisabled(State.ACTION_CREATE);

            }
            return getEnterpriseString(
                    CROSS_PROFILE_NOT_ALLOWED_MESSAGE,
                    R.string.cross_profile_action_not_allowed_message);
        }

        private CharSequence getErrorMessagePrivateSpaceEnabled(int action) {
            Resources res = mEnv.getContext().getResources();
            String sourceProfileLabel = mUserIdToLabelMap.get(mSourceUserId);
            String selectedProfileLabel = mUserIdToLabelMap.get(mSelectedUserId);
            if (sourceProfileLabel == null || selectedProfileLabel == null) return "";
            if (action == ACCESS_CROSS_PROFILE_FILES) {
                return res.getString(R.string.cant_select_cross_profile_files_error_message,
                        selectedProfileLabel.toLowerCase(Locale.getDefault()),
                        sourceProfileLabel.toLowerCase(Locale.getDefault()));
            } else if (action == State.ACTION_CREATE) {
                return res.getString(R.string.cant_save_to_cross_profile_error_message,
                        sourceProfileLabel.toLowerCase(Locale.getDefault()),
                        selectedProfileLabel.toLowerCase(Locale.getDefault()));
            } else {
                Log.e(TAG, "Unexpected intent action received.");
                return "";
            }
        }

        private CharSequence getErrorMessagePrivateSpaceDisabled(int action) {
            boolean currentUserIsSystem = UserId.CURRENT_USER.isSystem();
            if (action == ACCESS_CROSS_PROFILE_FILES) {
                return currentUserIsSystem
                        ? getEnterpriseString(CANT_SELECT_WORK_FILES_MESSAGE,
                        R.string.cant_select_work_files_error_message)
                        : getEnterpriseString(CANT_SELECT_PERSONAL_FILES_MESSAGE,
                                R.string.cant_select_personal_files_error_message);
            } else if (action == State.ACTION_CREATE) {
                return currentUserIsSystem
                        ? getEnterpriseString(CANT_SAVE_TO_WORK_MESSAGE,
                        R.string.cant_save_to_work_error_message)
                        : getEnterpriseString(CANT_SAVE_TO_PERSONAL_MESSAGE,
                                R.string.cant_save_to_personal_error_message);
            } else {
                Log.e(TAG, "Unexpected intent action received.");
                return "";
            }
        }

        private void updateToInflatedErrorMessage() {
            update(null, mEnv.getContext().getResources().getText(R.string.query_error), null,
                    mEnv.getContext().getDrawable(R.drawable.hourglass));
        }

        private void updateToCantDisplayContentMessage() {
            update(null, mEnv.getContext().getResources().getText(R.string.cant_display_content),
                    null, mEnv.getContext().getDrawable(R.drawable.empty));
        }

        private void updateToInflatedEmptyMessage() {
            final CharSequence message;
            if (mEnv.isInSearchMode()) {
                message = String.format(
                        String.valueOf(
                                mEnv.getContext().getResources().getText(R.string.no_results)),
                        mEnv.getDisplayState().stack.getRoot().title);
            } else {
                message = mEnv.getContext().getResources().getText(R.string.empty);
            }
            update(null, message, null, mEnv.getContext().getDrawable(R.drawable.empty));
        }

        private String getEnterpriseString(String updatableStringId, int defaultStringId) {
            if (SdkLevel.isAtLeastT()) {
                return getUpdatableEnterpriseString(updatableStringId, defaultStringId);
            } else {
                return mEnv.getContext().getString(defaultStringId);
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private String getUpdatableEnterpriseString(String updatableStringId, int defaultStringId) {
            DevicePolicyManager dpm = mEnv.getContext().getSystemService(
                    DevicePolicyManager.class);
            return dpm.getResources().getString(
                    updatableStringId, () -> mEnv.getContext().getString(defaultStringId));
        }

        private Drawable getWorkProfileOffIcon() {
            if (SdkLevel.isAtLeastT()) {
                return getUpdatableWorkProfileIcon();
            } else {
                return mEnv.getContext().getDrawable(R.drawable.work_off);
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private Drawable getUpdatableWorkProfileIcon() {
            DevicePolicyManager dpm = mEnv.getContext().getSystemService(
                    DevicePolicyManager.class);
            return dpm.getResources().getDrawable(
                    WORK_PROFILE_OFF_ICON, OUTLINE,
                    () -> mEnv.getContext().getDrawable(R.drawable.work_off));
        }
    }
}
