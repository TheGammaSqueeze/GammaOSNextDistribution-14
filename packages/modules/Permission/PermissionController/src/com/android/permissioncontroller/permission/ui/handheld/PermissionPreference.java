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

import static android.app.admin.DevicePolicyResources.Strings.PermissionSettings.BACKGROUND_ACCESS_DISABLED_BY_ADMIN_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.PermissionSettings.BACKGROUND_ACCESS_ENABLED_BY_ADMIN_MESSAGE;
import static android.app.admin.DevicePolicyResources.Strings.PermissionSettings.FOREGROUND_ACCESS_ENABLED_BY_ADMIN_MESSAGE;

import static com.android.permissioncontroller.permission.utils.Utils.getRequestMessage;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.widget.Switch;

import androidx.annotation.LayoutRes;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup;
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.PermissionSummary;
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.PermissionTarget;
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.SummaryMessage;
import com.android.permissioncontroller.permission.utils.LocationUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

/**
 * A preference for representing a permission group requested by an app.
 */
class PermissionPreference extends MultiTargetSwitchPreference {

    /**
     * holds state for the permission group represented by this preference.
     */
    private PermissionTarget mState = PermissionTarget.PERMISSION_NONE;
    private final LightAppPermGroup mGroup;
    private final ReviewPermissionsViewModel mViewModel;
    private final PreferenceFragmentCompat mFragment;
    private final PermissionPreferenceChangeListener mCallBacks;
    private final @LayoutRes int mOriginalWidgetLayoutRes;

    /** Callbacks for the permission to the fragment showing a list of permissions */
    interface PermissionPreferenceChangeListener {
        /**
         * Checks if the user has to confirm a revocation of a permission granted by default.
         *
         * @return {@code true} iff the user has to confirm it
         */
        boolean shouldConfirmDefaultPermissionRevoke();

        /**
         * Notify the listener that the user confirmed that she/he wants to revoke permissions that
         * were granted by default.
         */
        void hasConfirmDefaultPermissionRevoke();

        /**
         * Notify the listener that this preference has changed.
         *
         * @param key The key uniquely identifying this preference
         */
        void onPreferenceChanged(String key);
    }

    /**
     * Callbacks from dialogs to the fragment. These callbacks are supposed to directly cycle back
     * to the permission that created the dialog.
     */
    interface PermissionPreferenceOwnerFragment {
        /**
         * The {@link DefaultDenyDialog} can only interact with the fragment, not the preference
         * that created it. Hence this call goes to the fragment, which then finds the preference an
         * calls {@link #onDenyAnyWay(PermissionTarget)}.
         *
         * @param key Key uniquely identifying the preference that created the default deny dialog
         * @param changeTarget Whether background or foreground permissions should be changed
         *
         * @see #showDefaultDenyDialog(PermissionTarget, boolean)
         */
        void onDenyAnyWay(String key, PermissionTarget changeTarget);

        /**
         * The {@link BackgroundAccessChooser} can only interact with the fragment, not the
         * preference that created it. Hence this call goes to the fragment, which then finds the
         * preference an calls {@link #onBackgroundAccessChosen(int)}}.
         *
         * @param key Key uniquely identifying the preference that created the background access
         *            chooser
         * @param chosenItem The index of the item selected by the user.
         *
         * @see #showBackgroundChooserDialog()
         */
        void onBackgroundAccessChosen(String key, int chosenItem);
    }

    PermissionPreference(PreferenceFragmentCompat fragment, LightAppPermGroup group,
            PermissionPreferenceChangeListener callbacks,
            ReviewPermissionsViewModel reviewPermissionsViewModel) {
        super(fragment.getPreferenceManager().getContext());

        mFragment = fragment;
        mGroup = group;
        mViewModel = reviewPermissionsViewModel;
        mCallBacks = callbacks;
        mOriginalWidgetLayoutRes = getWidgetLayoutResource();
        setState(group);
        setPersistent(false);
        updateUi();
    }

    PermissionTarget getState() {
        return mState;
    }

    private void setState(LightAppPermGroup appPermGroup) {
        if (appPermGroup.isReviewRequired()) {
            mState = PermissionTarget.PERMISSION_FOREGROUND;
            if (appPermGroup.getHasBackgroundGroup()) {
                mState = PermissionTarget.PERMISSION_BOTH;
            }
        }
    }

    /**
     * Update the preference after the state might have changed.
     */
    void updateUi() {
        boolean arePermissionsIndividuallyControlled =
                Utils.areGroupPermissionsIndividuallyControlled(getContext(),
                        mGroup.getPermGroupName());
        EnforcedAdmin admin = mViewModel.getAdmin(getContext(), mGroup);

        // Reset ui state
        setEnabled(true);
        setWidgetLayoutResource(mOriginalWidgetLayoutRes);
        setOnPreferenceClickListener(null);
        setSwitchOnClickListener(null);
        setSummary(null);

        setChecked(mState != PermissionTarget.PERMISSION_NONE);

        if (mViewModel.isFixedOrForegroundDisabled(mGroup)) {
            if (admin != null) {
                setWidgetLayoutResource(
                        com.android.settingslib.widget.restricted.R.layout.restricted_icon);

                setOnPreferenceClickListener((v) -> {
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin);
                    return true;
                });
            } else {
                setEnabled(false);
            }

            updateSummaryForFixedByPolicyPermissionGroup();
        } else if (arePermissionsIndividuallyControlled) {
            setOnPreferenceClickListener((pref) -> {
                Bundle args = AllAppPermissionsFragment.createArgs(mGroup.getPackageName(),
                                mGroup.getPermGroupName(), UserHandle.getUserHandleForUid(
                                mGroup.getPackageInfo().getUid()));
                mViewModel.showAllPermissions(mFragment, args);
                return false;
            });

            setSwitchOnClickListener(v -> {
                Switch switchView = (Switch) v;
                requestChange(switchView.isChecked(), PermissionTarget.PERMISSION_BOTH);

                // Update UI as the switch widget might be in wrong state
                updateUi();
            });

            updateSummaryForIndividuallyControlledPermissionGroup();
        } else {
            if (mGroup.getHasPermWithBackgroundMode()) {
                if (!mGroup.getHasBackgroundGroup()) {
                    // The group has background permissions but the app did not request any. I.e.
                    // The app can only switch between 'never" and "only in foreground".
                    setOnPreferenceChangeListener((pref, newValue) ->
                            requestChange((Boolean) newValue,
                                    PermissionTarget.PERMISSION_FOREGROUND));

                    updateSummaryForPermissionGroupWithBackgroundPermission();
                } else {
                    if (mGroup.getBackground().isPolicyFixed()) {
                        setOnPreferenceChangeListener((pref, newValue) ->
                                requestChange((Boolean) newValue,
                                        PermissionTarget.PERMISSION_FOREGROUND));

                        updateSummaryForFixedByPolicyPermissionGroup();
                    } else if (mGroup.getForeground().isPolicyFixed()) {
                        setOnPreferenceChangeListener((pref, newValue) ->
                                requestChange((Boolean) newValue,
                                        PermissionTarget.PERMISSION_BACKGROUND));

                        updateSummaryForFixedByPolicyPermissionGroup();
                    } else {
                        updateSummaryForPermissionGroupWithBackgroundPermission();

                        setOnPreferenceClickListener((pref) -> {
                            showBackgroundChooserDialog();
                            return true;
                        });

                        setSwitchOnClickListener(v -> {
                            Switch switchView = (Switch) v;

                            if (switchView.isChecked()) {
                                showBackgroundChooserDialog();
                            } else {
                                requestChange(false, PermissionTarget.PERMISSION_BOTH);
                            }

                            // Update UI as the switch widget might be in wrong state
                            updateUi();
                        });
                    }
                }
            } else {
                setOnPreferenceChangeListener((pref, newValue) ->
                        requestChange((Boolean) newValue, PermissionTarget.PERMISSION_BOTH));
            }
        }
    }

    /**
     * Update the summary in the case the permission group has individually controlled permissions.
     */
    private void updateSummaryForIndividuallyControlledPermissionGroup() {
        PermissionSummary summary = mViewModel.getSummaryForIndividuallyControlledPermGroup(mGroup);
        setSummary(getContext().getString(getResource(summary.getMsg()), summary.getRevokeCount()));
    }

    /**
     * Update the summary of a permission group that has background permission.
     *
     * <p>This does not apply to permission groups that are fixed by policy</p>
     */
    private void updateSummaryForPermissionGroupWithBackgroundPermission() {
        PermissionSummary summary = mViewModel.getSummaryForPermGroupWithBackgroundPermission(
                mState);
        setSummary(getResource(summary.getMsg()));
    }

    /**
     * Update the summary of a permission group that is at least partially fixed by policy.
     */
    private void updateSummaryForFixedByPolicyPermissionGroup() {
        PermissionSummary summary = mViewModel.getSummaryForFixedByPolicyPermissionGroup(mState,
                mGroup, getContext());
        if (summary.getMsg() == SummaryMessage.NO_SUMMARY) {
            return;
        }
        if (summary.isEnterprise()) {
            switch (summary.getMsg()) {
                case ENABLED_BY_ADMIN_BACKGROUND_ONLY:
                    setSummary(Utils.getEnterpriseString(
                            getContext(),
                            BACKGROUND_ACCESS_ENABLED_BY_ADMIN_MESSAGE,
                            getResource(summary.getMsg())));
                    break;
                case DISABLED_BY_ADMIN_BACKGROUND_ONLY:
                    setSummary(Utils.getEnterpriseString(
                            getContext(),
                            BACKGROUND_ACCESS_DISABLED_BY_ADMIN_MESSAGE,
                            getResource(summary.getMsg())));
                    break;
                case ENABLED_BY_ADMIN_FOREGROUND_ONLY:
                    setSummary(Utils.getEnterpriseString(
                            getContext(),
                            FOREGROUND_ACCESS_ENABLED_BY_ADMIN_MESSAGE,
                            getResource(summary.getMsg())));
                    break;
                default:
                    throw new IllegalArgumentException("Missing enterprise summary "
                            + "case for " + summary.getMsg());
            }
        } else {
            setSummary(getResource(summary.getMsg()));
        }
    }

    int getResource(SummaryMessage summary) {
        switch (summary) {
            case DISABLED_BY_ADMIN:
                return com.android.settingslib.widget.restricted.R.string.disabled_by_admin;
            case ENABLED_BY_ADMIN:
                return com.android.settingslib.widget.restricted.R.string.enabled_by_admin;
            case ENABLED_SYSTEM_FIXED:
                return R.string.permission_summary_enabled_system_fixed;
            case ENFORCED_BY_POLICY:
                return R.string.permission_summary_enforced_by_policy;
            case ENABLED_BY_ADMIN_FOREGROUND_ONLY:
                return R.string.permission_summary_enabled_by_admin_foreground_only;
            case ENABLED_BY_POLICY_FOREGROUND_ONLY:
                return R.string.permission_summary_enabled_by_policy_foreground_only;
            case ENABLED_BY_ADMIN_BACKGROUND_ONLY:
                return R.string.permission_summary_enabled_by_admin_background_only;
            case ENABLED_BY_POLICY_BACKGROUND_ONLY:
                return R.string.permission_summary_enabled_by_policy_foreground_only;
            case DISABLED_BY_ADMIN_BACKGROUND_ONLY:
                return R.string.permission_summary_disabled_by_admin_background_only;
            case DISABLED_BY_POLICY_BACKGROUND_ONLY:
                return R.string.permission_summary_disabled_by_policy_background_only;
            case REVOKED_NONE:
                return R.string.permission_revoked_none;
            case REVOKED_ALL:
                return R.string.permission_revoked_all;
            case REVOKED_COUNT:
                return R.string.permission_revoked_count;
            case ACCESS_ALWAYS:
                return R.string.permission_access_always;
            case ACCESS_ONLY_FOREGROUND:
                return R.string.permission_access_only_foreground;
            case ACCESS_NEVER:
                return R.string.permission_access_never;
            default:
                throw new IllegalArgumentException("No resource found");
        }
    }

    /**
     * Get the label of the app the permission group belongs to. (App permission groups are all
     * permissions of a group an app has requested.)
     *
     * @return The label of the app
     */
    private String getAppLabel() {
        String label = Utils.getAppLabel(mViewModel.getPackageInfo().applicationInfo,
                mViewModel.getApp());
        return BidiFormatter.getInstance().unicodeWrap(label);
    }

    /**
     * Request to grant/revoke permissions group.
     *
     * <p>Does <u>not</u> handle:
     * <ul>
     * <li>Individually granted permissions</li>
     * <li>Permission groups with background permissions</li>
     * </ul>
     * <p><u>Does</u> handle:
     * <ul>
     * <li>Default grant permissions</li>
     * </ul>
     *
     * @param requestGrant If this group should be granted
     * @param changeTarget Which permission group (foreground/background/both) should be changed
     * @return If the request was processed.
     */
    private boolean requestChange(boolean requestGrant, PermissionTarget changeTarget) {
        if (LocationUtils.isLocationGroupAndProvider(getContext(), mGroup.getPermGroupName(),
                mGroup.getPackageName())) {
            LocationUtils.showLocationDialog(getContext(), getAppLabel());
            return false;
        }
        if (requestGrant) {
            mCallBacks.onPreferenceChanged(getKey());
            //allow additional state
            mState = PermissionTarget.Companion.fromInt(mState.or(changeTarget));
        } else {
            boolean requestToRevokeGrantedByDefault = false;
            if (changeTarget.and(PermissionTarget.PERMISSION_FOREGROUND)
                    != PermissionTarget.PERMISSION_NONE.getValue()) {
                requestToRevokeGrantedByDefault = mGroup.isGrantedByDefault();
            }
            if (changeTarget.and(PermissionTarget.PERMISSION_BACKGROUND)
                    != PermissionTarget.PERMISSION_NONE.getValue()) {
                if (mGroup.getHasBackgroundGroup()) {
                    requestToRevokeGrantedByDefault |=
                            mGroup.getBackground().isGrantedByDefault();
                }
            }

            if ((requestToRevokeGrantedByDefault || !mGroup.getSupportsRuntimePerms())
                    && mCallBacks.shouldConfirmDefaultPermissionRevoke()) {
                showDefaultDenyDialog(changeTarget, requestToRevokeGrantedByDefault);
                return false;
            } else {
                mCallBacks.onPreferenceChanged(getKey());
                mState = PermissionTarget.Companion.fromInt(mState.and(~changeTarget.getValue()));
            }
        }

        updateUi();

        return true;
    }

    /**
     * Show a dialog that warns the user that she/he is about to revoke permissions that were
     * granted by default.
     *
     * <p>The order of operation to revoke a permission granted by default is:
     * <ol>
     *     <li>{@code showDefaultDenyDialog}</li>
     *     <li>{@link DefaultDenyDialog#onCreateDialog}</li>
     *     <li>{@link PermissionPreferenceOwnerFragment#onDenyAnyWay}</li>
     *     <li>{@link PermissionPreference#onDenyAnyWay}</li>
     * </ol>
     *
     * @param changeTarget Whether background or foreground should be changed
     */
    private void showDefaultDenyDialog(PermissionTarget changeTarget,
            boolean showGrantedByDefaultWarning) {
        if (!mFragment.isResumed()) {
            return;
        }

        Bundle args = new Bundle();
        args.putInt(DefaultDenyDialog.MSG, showGrantedByDefaultWarning ? R.string.system_warning
                : R.string.old_sdk_deny_warning);
        args.putString(DefaultDenyDialog.KEY, getKey());
        args.putInt(DefaultDenyDialog.CHANGE_TARGET, changeTarget.getValue());

        DefaultDenyDialog deaultDenyDialog = new DefaultDenyDialog();
        deaultDenyDialog.setArguments(args);
        deaultDenyDialog.show(mFragment.getChildFragmentManager().beginTransaction(),
                "denyDefault");
    }

    /**
     * Show a dialog that asks the user if foreground/background/none access should be enabled.
     *
     * <p>The order of operation to grant foreground/background/none access is:
     * <ol>
     *     <li>{@code showBackgroundChooserDialog}</li>
     *     <li>{@link BackgroundAccessChooser#onCreateDialog}</li>
     *     <li>{@link PermissionPreferenceOwnerFragment#onBackgroundAccessChosen}</li>
     *     <li>{@link PermissionPreference#onBackgroundAccessChosen}</li>
     * </ol>
     */
    private void showBackgroundChooserDialog() {
        if (!mFragment.isResumed()) {
            return;
        }

        if (LocationUtils.isLocationGroupAndProvider(getContext(), mGroup.getPermGroupName(),
                mGroup.getPackageName())) {
            LocationUtils.showLocationDialog(getContext(), getAppLabel());
            return;
        }

        Bundle args = new Bundle();
        args.putCharSequence(BackgroundAccessChooser.TITLE,
                getRequestMessage(getAppLabel(), mGroup.getPackageName(), mGroup.getPermGroupName(),
                        getContext(), Utils.getRequest(mGroup.getPermGroupName())));
        args.putString(BackgroundAccessChooser.KEY, getKey());


        if (mState != PermissionTarget.PERMISSION_NONE) {
            if (mState == PermissionTarget.PERMISSION_BOTH) {
                args.putInt(BackgroundAccessChooser.SELECTION,
                        BackgroundAccessChooser.ALWAYS_OPTION);
            } else {
                args.putInt(BackgroundAccessChooser.SELECTION,
                        BackgroundAccessChooser.FOREGROUND_ONLY_OPTION);
            }
        } else {
            args.putInt(BackgroundAccessChooser.SELECTION, BackgroundAccessChooser.NEVER_OPTION);
        }

        BackgroundAccessChooser chooserDialog = new BackgroundAccessChooser();
        chooserDialog.setArguments(args);
        chooserDialog.show(mFragment.getChildFragmentManager().beginTransaction(),
                "backgroundChooser");
    }

    /**
     * Once we user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @see #showDefaultDenyDialog(PermissionTarget, boolean)
     */
    void onDenyAnyWay(PermissionTarget changeTarget) {
        mCallBacks.onPreferenceChanged(getKey());

        boolean hasDefaultPermissions = false;
        if (changeTarget.and(PermissionTarget.PERMISSION_FOREGROUND)
                != PermissionTarget.PERMISSION_NONE.getValue()) {
            hasDefaultPermissions = mGroup.isGrantedByDefault();
            mState = PermissionTarget.Companion.fromInt(mState.and(
                    ~PermissionTarget.PERMISSION_FOREGROUND.getValue()));
        }
        if (changeTarget.and(PermissionTarget.PERMISSION_BACKGROUND)
                != PermissionTarget.PERMISSION_NONE.getValue()) {
            if (mGroup.getHasBackgroundGroup()) {
                hasDefaultPermissions |= mGroup.getBackground().isGrantedByDefault();
                mState = PermissionTarget.Companion.fromInt(mState.and(
                        ~PermissionTarget.PERMISSION_BACKGROUND.getValue()));
            }
        }

        if (hasDefaultPermissions || !mGroup.getSupportsRuntimePerms()) {
            mCallBacks.hasConfirmDefaultPermissionRevoke();
        }
        updateUi();
    }

    /**
     * Process the return from a {@link BackgroundAccessChooser} dialog.
     *
     * <p>These dialog are started when the user want to grant a permission group that has
     * background permissions.
     *
     * @param choosenItem The item that the user chose
     */
    void onBackgroundAccessChosen(int choosenItem) {

        switch (choosenItem) {
            case BackgroundAccessChooser.ALWAYS_OPTION:
                requestChange(true, PermissionTarget.PERMISSION_BOTH);
                break;
            case BackgroundAccessChooser.FOREGROUND_ONLY_OPTION:
                if (mState.and(PermissionTarget.PERMISSION_BACKGROUND)
                        != PermissionTarget.PERMISSION_NONE.getValue()) {
                    requestChange(false, PermissionTarget.PERMISSION_BACKGROUND);
                }
                requestChange(true, PermissionTarget.PERMISSION_FOREGROUND);
                break;
            case BackgroundAccessChooser.NEVER_OPTION:
                if (mState != PermissionTarget.PERMISSION_NONE) {
                    requestChange(false, PermissionTarget.PERMISSION_BOTH);
                }
                break;
        }
    }

    /**
     * A dialog warning the user that she/he is about to deny a permission that was granted by
     * default.
     *
     * @see #showDefaultDenyDialog(PermissionTarget, boolean)
     */
    public static class DefaultDenyDialog extends DialogFragment {
        private static final String MSG = DefaultDenyDialog.class.getName() + ".arg.msg";
        private static final String CHANGE_TARGET = DefaultDenyDialog.class.getName()
                + ".arg.changeTarget";
        private static final String KEY = DefaultDenyDialog.class.getName() + ".arg.key";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (DialogInterface dialog, int which) -> (
                                    (PermissionPreferenceOwnerFragment) getParentFragment())
                                    .onDenyAnyWay(getArguments().getString(KEY),
                                            PermissionTarget.Companion.fromInt(
                                                    getArguments().getInt(CHANGE_TARGET))));

            return b.create();
        }
    }

    /**
     * If a permission group has background permission this chooser is used to let the user
     * choose how the permission group should be granted.
     *
     * @see #showBackgroundChooserDialog()
     */
    public static class BackgroundAccessChooser extends DialogFragment {
        private static final String TITLE = BackgroundAccessChooser.class.getName() + ".arg.title";
        private static final String KEY = BackgroundAccessChooser.class.getName() + ".arg.key";
        private static final String SELECTION = BackgroundAccessChooser.class.getName()
                + ".arg.selection";

        // Needs to match the entries in R.array.background_access_chooser_dialog_choices
        static final int ALWAYS_OPTION = 0;
        static final int FOREGROUND_ONLY_OPTION = 1;
        static final int NEVER_OPTION = 2;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(getArguments().getCharSequence(TITLE))
                    .setSingleChoiceItems(R.array.background_access_chooser_dialog_choices,
                            getArguments().getInt(SELECTION),
                            (dialog, which) -> {
                                dismissAllowingStateLoss();
                                ((PermissionPreferenceOwnerFragment) getParentFragment())
                                        .onBackgroundAccessChosen(getArguments().getString(KEY),
                                                which);
                            }
                    );

            return b.create();
        }
    }
}
