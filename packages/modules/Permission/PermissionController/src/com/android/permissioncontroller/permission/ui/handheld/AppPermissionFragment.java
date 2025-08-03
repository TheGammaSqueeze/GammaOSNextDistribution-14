/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld;

import static android.Manifest.permission_group.STORAGE;
import static android.app.Activity.RESULT_OK;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__PHOTOS_SELECTED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT;
import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData.FullStoragePackageState;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonState;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ChangeRequest;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModelFactory;
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.widget.ActionBarShadowController;

import kotlin.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Show and manage a single permission group for an app.
 *
 * <p>Allows the user to control whether the app is granted the permission.
 */
public class AppPermissionFragment extends SettingsWithLargeHeader
        implements AppPermissionViewModel.ConfirmDialogShowingFragment {
    private static final String LOG_TAG = "AppPermissionFragment";
    private static final long POST_DELAY_MS = 20;
    private static final long EDIT_PHOTOS_BUTTON_ANIMATION_LENGTH_MS = 200L;

    static final String GRANT_CATEGORY = "grant_category";

    private @NonNull AppPermissionViewModel mViewModel;
    private @NonNull ViewGroup mAppPermissionRationaleContainer;
    private @NonNull ViewGroup mAppPermissionRationaleContent;
    private @NonNull RadioButton mAllowButton;
    private @NonNull RadioButton mAllowAlwaysButton;
    private @NonNull RadioButton mAllowForegroundButton;
    private @NonNull RadioButton mAskOneTimeButton;
    private @NonNull RadioButton mAskButton;
    private @NonNull RadioButton mSelectPhotosButton;
    private @NonNull RadioButton mDenyButton;
    private @NonNull RadioButton mDenyForegroundButton;
    private @NonNull ImageView mEditPhotosButton;
    private @NonNull View mSelectPhotosLayout;
    private @NonNull View mEditPhotosDivider;
    private @NonNull View mLocationAccuracy;
    private @NonNull Switch mLocationAccuracySwitch;
    private @NonNull View mDivider;
    private @NonNull ViewGroup mWidgetFrame;
    private @NonNull TextView mPermissionDetails;
    private @NonNull NestedScrollView mNestedScrollView;
    private @NonNull String mPackageName;
    private @NonNull String mPermGroupName;
    private @NonNull UserHandle mUser;
    private boolean mIsStorageGroup;
    private boolean mIsInitialLoad;
    // This prevents the user from clicking the photo picker button multiple times in succession
    private boolean mPhotoPickerTriggered;
    private long mSessionId;

    private @NonNull String mPackageLabel;
    private @NonNull String mPermGroupLabel;
    private Drawable mPackageIcon;
    private @NonNull RoleManager mRoleManager;

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName   The name of the package
     * @param permName      The name of the permission whose group this fragment is for (optional)
     * @param groupName     The name of the permission group (required if permName not specified)
     * @param userHandle    The user of the app permission group
     * @param caller        The name of the fragment we called from
     * @param sessionId     The current session ID
     * @param grantCategory The grant status of this app permission group. Used to initially set
     *                      the button state
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName,
            @Nullable String permName, @Nullable String groupName,
            @NonNull UserHandle userHandle, @Nullable String caller, long sessionId, @Nullable
            String grantCategory) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        if (groupName == null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName);
        } else {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putString(EXTRA_CALLER_NAME, caller);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        arguments.putString(GRANT_CATEGORY, grantCategory);
        return arguments;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        mIsStorageGroup = Objects.equals(mPermGroupName, STORAGE);
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mPackageLabel = BidiFormatter.getInstance().unicodeWrap(
                KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(), mPackageName,
                        mUser));
        mPermGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                mPermGroupName).toString();
        mPackageIcon = KotlinUtils.INSTANCE.getBadgedPackageIcon(getActivity().getApplication(),
                mPackageName, mUser);
        mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        AppPermissionViewModelFactory factory = new AppPermissionViewModelFactory(
                getActivity().getApplication(), mPackageName, mPermGroupName, mUser, mSessionId);
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionViewModel.class);
        Handler delayHandler = new Handler(Looper.getMainLooper());
        mViewModel.getButtonStateLiveData().observe(this, buttonState -> {
            if (mIsInitialLoad) {
                setRadioButtonsState(buttonState);
            } else {
                delayHandler.removeCallbacksAndMessages(null);
                delayHandler.postDelayed(() -> setRadioButtonsState(buttonState), POST_DELAY_MS);
            }
        });
        mViewModel.getDetailResIdLiveData().observe(this, this::setDetail);
        mViewModel.getShowAdminSupportLiveData().observe(this, this::setAdminSupportDetail);
        if (mIsStorageGroup) {
            mViewModel.getFullStorageStateLiveData().observe(this, this::setSpecialStorageState);
        }

        mRoleManager = Utils.getSystemServiceSafe(getContext(), RoleManager.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getContext();
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.app_permission, container, false);

        mIsInitialLoad = true;

        setHeader(mPackageIcon, mPackageLabel, null, null, false);
        updateHeader(root.requireViewById(R.id.large_header));

        ((TextView) root.requireViewById(R.id.permission_message)).setText(
                context.getString(R.string.app_permission_header, mPermGroupLabel));

        String caller = getArguments().getString(EXTRA_CALLER_NAME);

        TextView footer1Link = root.requireViewById(R.id.footer_link_1);
        footer1Link.setText(context.getString(R.string.app_permission_footer_app_permissions_link,
                mPackageLabel));
        setBottomLinkState(footer1Link, caller, Intent.ACTION_MANAGE_APP_PERMISSIONS);

        TextView footer2Link = root.requireViewById(R.id.footer_link_2);
        footer2Link.setText(context.getString(R.string.app_permission_footer_permission_apps_link));
        setBottomLinkState(footer2Link, caller, Intent.ACTION_MANAGE_PERMISSION_APPS);

        Set<String> exemptedPackages = Utils.getExemptedPackages(mRoleManager);
        ImageView footerInfoIcon = root.requireViewById(R.id.app_additional_info_icon);
        TextView footerInfoText = root.requireViewById(R.id.app_additional_info_text);
        if (exemptedPackages.contains(mPackageName)) {
            int additional_info_label = Utils.isStatusBarIndicatorPermission(mPermGroupName)
                    ? R.string.exempt_mic_camera_info_label : R.string.exempt_info_label;
            footerInfoText.setText(context.getString(additional_info_label, mPackageLabel));
            footerInfoIcon.setVisibility(View.VISIBLE);
            footerInfoText.setVisibility(View.VISIBLE);
        } else {
            footerInfoIcon.setVisibility(View.GONE);
            footerInfoText.setVisibility(View.GONE);
        }

        mAllowButton = root.requireViewById(R.id.allow_radio_button);
        mAllowAlwaysButton = root.requireViewById(R.id.allow_always_radio_button);
        mAllowForegroundButton = root.requireViewById(R.id.allow_foreground_only_radio_button);
        mAskOneTimeButton = root.requireViewById(R.id.ask_one_time_radio_button);
        mAskButton = root.requireViewById(R.id.ask_radio_button);
        mSelectPhotosButton = root.requireViewById(R.id.select_radio_button);
        mDenyButton = root.requireViewById(R.id.deny_radio_button);
        mDenyForegroundButton = root.requireViewById(R.id.deny_foreground_radio_button);

        mDivider = root.requireViewById(R.id.two_target_divider);
        mWidgetFrame = root.requireViewById(R.id.widget_frame);
        mPermissionDetails = root.requireViewById(R.id.permission_details);
        mLocationAccuracy = root.requireViewById(R.id.location_accuracy);
        mLocationAccuracySwitch = root.requireViewById(R.id.location_accuracy_switch);
        mSelectPhotosLayout = root.requireViewById(R.id.radio_select_layout);
        mEditPhotosButton = root.requireViewById(R.id.edit_selected_button);
        mEditPhotosDivider = root.requireViewById(R.id.edit_photos_divider);
        mNestedScrollView = root.requireViewById(R.id.nested_scroll_view);

        if (mViewModel.getButtonStateLiveData().getValue() != null) {
            setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
        } else {
            mAllowButton.setVisibility(View.GONE);
            mAllowAlwaysButton.setVisibility(View.GONE);
            mAllowForegroundButton.setVisibility(View.GONE);
            mAskOneTimeButton.setVisibility(View.GONE);
            mAskButton.setVisibility(View.GONE);
            mDenyButton.setVisibility(View.GONE);
            mDenyForegroundButton.setVisibility(View.GONE);
            mLocationAccuracy.setVisibility(View.GONE);
            mSelectPhotosLayout.setVisibility(View.GONE);
            mEditPhotosDivider.setAlpha(0f);
            mEditPhotosButton.setAlpha(0f);
        }

        if (mViewModel.getFullStorageStateLiveData().isInitialized() && mIsStorageGroup) {
            setSpecialStorageState(mViewModel.getFullStorageStateLiveData().getValue(), root);
        } else {
            TextView storageFooter = root.requireViewById(R.id.footer_storage_special_app_access);
            storageFooter.setVisibility(View.GONE);
        }
        mAppPermissionRationaleContainer =
                root.requireViewById(R.id.app_permission_rationale_container);
        mAppPermissionRationaleContent =
                root.requireViewById(R.id.app_permission_rationale_content);
        mViewModel.getShowPermissionRationaleLiveData().observe(this, show -> {
            showPermissionRationaleDialog(Optional.ofNullable(show).orElse(false));
        });

        getActivity().setTitle(
                getPreferenceManager().getContext().getString(R.string.app_permission_title,
                        mPermGroupLabel));
        return root;
    }

    public void onResume() {
        super.onResume();
        // If we're returning to the fragment, photo picker hasn't been triggered
        mPhotoPickerTriggered = false;
    }

    private void showPermissionRationaleDialog(boolean showPermissionRationale) {
        if (!showPermissionRationale) {
            mAppPermissionRationaleContainer.setVisibility(View.GONE);
        } else {
            mAppPermissionRationaleContainer.setVisibility(View.VISIBLE);
            mAppPermissionRationaleContent.setOnClickListener((v) -> {
                if (!SdkLevel.isAtLeastU()) {
                    return;
                }
                mViewModel.showPermissionRationaleActivity(getActivity(), mPermGroupName);
            });
        }
    }

    private void setBottomLinkState(TextView view, String caller, String action) {
        if ((Objects.equals(caller, AppPermissionGroupsFragment.class.getName())
                && action.equals(Intent.ACTION_MANAGE_APP_PERMISSIONS))
                || (Objects.equals(caller, PermissionAppsFragment.class.getName())
                && action.equals(Intent.ACTION_MANAGE_PERMISSION_APPS))) {
            view.setVisibility(View.GONE);
        } else {
            view.setOnClickListener((v) -> {
                Bundle args;
                if (action.equals(Intent.ACTION_MANAGE_APP_PERMISSIONS)) {
                    args = AppPermissionGroupsFragment.createArgs(mPackageName, mUser,
                            mSessionId, true);
                } else {
                    args = PermissionAppsFragment.createArgs(mPermGroupName, mSessionId);
                }
                mViewModel.showBottomLinkPage(this, action, args);
            });
        }
    }

    private void setSpecialStorageState(FullStoragePackageState storageState) {
        setSpecialStorageState(storageState, getView());
    }

    @Override
    public void onStart() {
        super.onStart();

        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setElevation(0);
        }

        ActionBarShadowController.attachToView(getActivity(), getLifecycle(), mNestedScrollView);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            pressBack(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setRadioButtonsState(Map<ButtonType, ButtonState> states) {
        if (states == null && !mViewModel.getButtonStateLiveData().isStale()) {
            pressBack(this);
            Log.w(LOG_TAG, "invalid package " + mPackageName + " or perm group "
                    + mPermGroupName);
            Toast.makeText(
                    getActivity(), R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            return;
        } else if (states == null) {
            return;
        }

        mAllowButton.setOnClickListener((v) -> {
            mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW);
            setResult(GRANTED_ALWAYS);
        });
        mAllowAlwaysButton.setOnClickListener((v) -> {
            if (mIsStorageGroup) {
                showConfirmDialog(ChangeRequest.GRANT_ALL_FILE_ACCESS,
                        R.string.special_file_access_dialog, -1, false);
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_BOTH,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS);
            }
            setResult(GRANTED_ALWAYS);
        });
        mAllowForegroundButton.setOnClickListener((v) -> {
            if (mIsStorageGroup) {
                mViewModel.setAllFilesAccess(false);
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_BOTH,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW);
                setResult(GRANTED_ALWAYS);
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FOREGROUND_ONLY,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND);
                setResult(GRANTED_FOREGROUND_ONLY);
            }
        });
        // mAskOneTimeButton only shows if checked hence should do nothing
        mAskButton.setOnClickListener((v) -> {
            mViewModel.requestChange(true, this, this, ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME);
            setResult(DENIED);
        });
        mSelectPhotosButton.setOnClickListener((v) -> {
            int buttonPressed =
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__PHOTOS_SELECTED;
            mViewModel.requestChange(false, this, this, ChangeRequest.PHOTOS_SELECTED,
                    buttonPressed);
        });
        mEditPhotosButton.setOnClickListener((v) -> {
            ButtonState selectState = states.get(ButtonType.SELECT_PHOTOS);
            if (selectState != null && selectState.isChecked() && !mPhotoPickerTriggered) {
                mPhotoPickerTriggered = true;
                mViewModel.openPhotoPicker(this);
            }
        });
        mDenyButton.setOnClickListener((v) -> {
            if (mViewModel.getFullStorageStateLiveData().getValue() != null
                    && !mViewModel.getFullStorageStateLiveData().getValue().isLegacy()) {
                mViewModel.setAllFilesAccess(false);
            }
            mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
        });
        mDenyForegroundButton.setOnClickListener((v) -> {
            mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
        });
        // Set long variable names to new variables to bypass linter errors.
        int grantFineLocation =
                APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION;
        int revokeFineLocation =
                APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION;
        mLocationAccuracy.setOnClickListener((v) -> {
            mLocationAccuracySwitch.performClick();
            if (mLocationAccuracySwitch.isChecked()) {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FINE_LOCATION,
                        grantFineLocation);
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_FINE_LOCATION,
                        revokeFineLocation);
            }
        });

        setButtonState(mAllowButton, states.get(ButtonType.ALLOW));
        setButtonState(mAllowAlwaysButton, states.get(ButtonType.ALLOW_ALWAYS));
        setButtonState(mAllowForegroundButton, states.get(ButtonType.ALLOW_FOREGROUND));
        setButtonState(mAskOneTimeButton, states.get(ButtonType.ASK_ONCE));
        setButtonState(mAskButton, states.get(ButtonType.ASK));
        setButtonState(mDenyButton, states.get(ButtonType.DENY));
        setButtonState(mDenyForegroundButton, states.get(ButtonType.DENY_FOREGROUND));
        setButtonState(mSelectPhotosButton, states.get(ButtonType.SELECT_PHOTOS));
        if (mSelectPhotosButton.getVisibility() == View.VISIBLE) {
            mAllowButton.setText(R.string.app_permission_button_always_allow_all);
        } else {
            mAllowButton.setText(R.string.app_permission_button_allow);
        }

        ButtonState locationAccuracyState = states.get(ButtonType.LOCATION_ACCURACY);
        if (!locationAccuracyState.isShown()) {
            mLocationAccuracy.setVisibility(View.GONE);
        } else {
            mLocationAccuracy.setVisibility(View.VISIBLE);
        }
        mLocationAccuracySwitch.setChecked(locationAccuracyState.isChecked());
        if (!locationAccuracyState.isEnabled()) {
            mLocationAccuracy.setEnabled(false);
            mLocationAccuracySwitch.setEnabled(false);
        }

        mIsInitialLoad = false;

        if (mViewModel.getFullStorageStateLiveData().isInitialized()) {
            setSpecialStorageState(mViewModel.getFullStorageStateLiveData().getValue());
        }
    }

    private void setButtonState(CompoundButton button, AppPermissionViewModel.ButtonState state) {
        int visible = state.isShown() ? View.VISIBLE : View.GONE;
        button.setVisibility(visible);
        if (state.isShown()) {
            button.setChecked(state.isChecked());
            button.setEnabled(state.isEnabled());
        }
        if (mIsInitialLoad) {
            button.jumpDrawablesToCurrentState();
        }

        if (button == mSelectPhotosButton) {
            mSelectPhotosLayout.setVisibility(visible);
            float endOpacity = state.isChecked() ? 1f : 0f;
            // On initial load, do not show the fade in/out animation
            if (mIsInitialLoad) {
                mEditPhotosDivider.setAlpha(endOpacity);
                mEditPhotosButton.setAlpha(endOpacity);
                return;
            }
            mEditPhotosButton.animate().alpha(endOpacity)
                    .setDuration(EDIT_PHOTOS_BUTTON_ANIMATION_LENGTH_MS);
            mEditPhotosDivider.animate().alpha(endOpacity)
                    .setDuration(EDIT_PHOTOS_BUTTON_ANIMATION_LENGTH_MS);
        }
    }

    private void setSpecialStorageState(FullStoragePackageState storageState, View v) {
        if (v == null) {
            return;
        }

        TextView textView = v.requireViewById(R.id.footer_storage_special_app_access);
        if (mAllowButton == null || !mIsStorageGroup) {
            textView.setVisibility(View.GONE);
            return;
        }

        mAllowAlwaysButton.setText(R.string.app_permission_button_allow_all_files);
        mAllowForegroundButton.setText(R.string.app_permission_button_allow_media_only);

        if (storageState == null) {
            textView.setVisibility(View.GONE);
            return;
        }

        if (storageState.isLegacy()) {
            mAllowButton.setText(R.string.app_permission_button_allow_all_files);
            textView.setVisibility(View.GONE);
            return;
        }

        textView.setText(R.string.app_permission_footer_special_file_access);
        textView.setVisibility(View.VISIBLE);
    }

    private void setResult(@GrantPermissionsViewHandler.Result int result) {
        if (!mPackageName.equals(
                getActivity().getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME))) {
            return;
        }
        Intent intent = new Intent()
                .putExtra(EXTRA_RESULT_PERMISSION_INTERACTED, mPermGroupName)
                .putExtra(EXTRA_RESULT_PERMISSION_RESULT, result);
        getActivity().setResult(RESULT_OK, intent);
    }

    private void setDetail(Pair<Integer, Integer> detailResIds) {
        if (detailResIds == null) {
            mWidgetFrame.setVisibility(View.GONE);
            mDivider.setVisibility(View.GONE);
            return;
        }
        mWidgetFrame.setVisibility(View.VISIBLE);
        if (detailResIds.getSecond() != null) {
            // If the permissions are individually controlled, also show a link to the page that
            // lets you control them.
            mDivider.setVisibility(View.VISIBLE);
            showRightIcon(R.drawable.ic_settings);
            Bundle args = AllAppPermissionsFragment.createArgs(mPackageName, mPermGroupName, mUser);
            mWidgetFrame.setOnClickListener(v -> mViewModel.showAllPermissions(this, args));
            mPermissionDetails.setText(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst(), detailResIds.getSecond()));
        } else {
            mPermissionDetails.setText(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst()));
        }
        mPermissionDetails.setVisibility(View.VISIBLE);

    }

    private void setAdminSupportDetail(EnforcedAdmin admin) {
        if (admin != null) {
            showRightIcon(R.drawable.ic_info);
            mWidgetFrame.setOnClickListener(v ->
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin)
            );
        } else {
            mWidgetFrame.removeAllViews();
        }
    }

    /**
     * Show the given icon on the right of the first radio button.
     *
     * @param iconId the resourceId of the drawable to use.
     */
    private void showRightIcon(int iconId) {
        mWidgetFrame.removeAllViews();
        ImageView imageView = new ImageView(getPreferenceManager().getContext());
        imageView.setImageResource(iconId);
        mWidgetFrame.addView(imageView);
        mWidgetFrame.setVisibility(View.VISIBLE);
    }

    /**
     * Show a dialog that warns the users that they are about to revoke permissions that were
     * granted by default, or that they are about to grant full file access to an app.
     *
     *
     * The order of operation to revoke a permission granted by default is:
     * 1. `showConfirmDialog`
     * 1. [ConfirmDialog.onCreateDialog]
     * 1. [AppPermissionViewModel.onDenyAnyWay] or [AppPermissionViewModel.onConfirmFileAccess]
     * TODO: Remove once data can be passed between dialogs and fragments with nav component
     *
     * @param changeRequest Whether background or foreground should be changed
     * @param messageId     The Id of the string message to show
     * @param buttonPressed Button which was pressed to initiate the dialog, one of
     *                      AppPermissionFragmentActionReported.button_pressed constants
     * @param oneTime       Whether the one-time (ask) button was clicked rather than the deny
     *                      button
     */
    @Override
    public void showConfirmDialog(ChangeRequest changeRequest, @StringRes int messageId,
            int buttonPressed, boolean oneTime) {
        Bundle args = getArguments().deepCopy();
        args.putInt(ConfirmDialog.MSG, messageId);
        args.putSerializable(ConfirmDialog.CHANGE_REQUEST, changeRequest);
        args.putInt(ConfirmDialog.BUTTON, buttonPressed);
        args.putBoolean(ConfirmDialog.ONE_TIME, oneTime);
        ConfirmDialog defaultDenyDialog = new ConfirmDialog();
        defaultDenyDialog.setCancelable(true);
        defaultDenyDialog.setArguments(args);
        defaultDenyDialog.show(getChildFragmentManager().beginTransaction(),
                ConfirmDialog.class.getName());
    }

    /**
     * A dialog warning the user that they are about to deny a permission that was granted by
     * default, or that they are denying a permission on a Pre-M app
     *
     * @see AppPermissionViewModel.ConfirmDialogShowingFragment#showConfirmDialog(ChangeRequest,
     * int, int, boolean)
     * @see #showConfirmDialog(ChangeRequest, int, int)
     */
    public static class ConfirmDialog extends DialogFragment {
        static final String MSG = ConfirmDialog.class.getName() + ".arg.msg";
        static final String CHANGE_REQUEST = ConfirmDialog.class.getName()
                + ".arg.changeRequest";
        private static final String KEY = ConfirmDialog.class.getName() + ".arg.key";
        private static final String BUTTON = ConfirmDialog.class.getName() + ".arg.button";
        private static final String ONE_TIME = ConfirmDialog.class.getName() + ".arg.onetime";
        private static int sCode =  APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW;
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // TODO(b/229024576): This code is duplicated, refactor ConfirmDialog for easier
            // NFF sharing
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            boolean isGrantFileAccess = getArguments().getSerializable(CHANGE_REQUEST)
                    == ChangeRequest.GRANT_ALL_FILE_ACCESS;
            int positiveButtonStringResId = R.string.grant_dialog_button_deny_anyway;
            if (isGrantFileAccess) {
                positiveButtonStringResId = R.string.grant_dialog_button_allow;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (DialogInterface dialog, int which) -> dialog.cancel())
                    .setPositiveButton(positiveButtonStringResId,
                            (DialogInterface dialog, int which) -> {
                                if (isGrantFileAccess) {
                                    fragment.mViewModel.setAllFilesAccess(true);
                                    fragment.mViewModel.requestChange(false, fragment,
                                            fragment, ChangeRequest.GRANT_BOTH, sCode);
                                } else {
                                    fragment.mViewModel.onDenyAnyWay((ChangeRequest)
                                                    getArguments().getSerializable(CHANGE_REQUEST),
                                            getArguments().getInt(BUTTON),
                                            getArguments().getBoolean(ONE_TIME));
                                }
                            });
            Dialog d = b.create();
            d.setCanceledOnTouchOutside(true);
            return d;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            fragment.setRadioButtonsState(fragment.mViewModel.getButtonStateLiveData().getValue());
        }
    }

    @Override
    public void showAdvancedConfirmDialog(AdvancedConfirmDialogArgs args) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                .setIcon(args.getIconId())
                .setMessage(args.getMessageId())
                .setOnCancelListener((DialogInterface dialog) -> {
                    setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
                })
                .setNegativeButton(args.getNegativeButtonTextId(),
                        (DialogInterface dialog, int which) -> {
                            setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
                        })
                .setPositiveButton(args.getPositiveButtonTextId(),
                        (DialogInterface dialog, int which) -> {
                            mViewModel.requestChange(args.getSetOneTime(),
                                    AppPermissionFragment.this, AppPermissionFragment.this,
                                    args.getChangeRequest(), args.getButtonClicked());
                        });
        if (args.getTitleId() != 0) {
            b.setTitle(args.getTitleId());
        }
        b.show();
    }
}
