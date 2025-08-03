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

package com.android.permissioncontroller.role.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.PackageRemovalMonitor;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.role.model.UserDeniedManager;
import com.android.permissioncontroller.role.utils.PackageUtils;
import com.android.permissioncontroller.role.utils.RoleUiBehaviorUtils;
import com.android.permissioncontroller.role.utils.UiUtils;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.Roles;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code Fragment} for a role request.
 */
public class RequestRoleFragment extends DialogFragment {

    private static final String LOG_TAG = RequestRoleFragment.class.getSimpleName();

    private static final String STATE_DONT_ASK_AGAIN = RequestRoleFragment.class.getName()
            + ".state.DONT_ASK_AGAIN";

    private String mRoleName;
    private String mPackageName;

    private Role mRole;

    private ListView mListView;
    private Adapter mAdapter;
    @Nullable
    private CheckBox mDontAskAgainCheck;

    private RequestRoleViewModel mViewModel;

    @Nullable
    private PackageRemovalMonitor mPackageRemovalMonitor;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the requested role
     * @param packageName the package name of the application requesting the role
     *
     * @return a new instance of this fragment
     */
    public static RequestRoleFragment newInstance(@NonNull String roleName,
            @NonNull String packageName) {
        RequestRoleFragment fragment = new RequestRoleFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_ROLE_NAME, roleName);
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mPackageName = arguments.getString(Intent.EXTRA_PACKAGE_NAME);
        mRoleName = arguments.getString(Intent.EXTRA_ROLE_NAME);

        mRole = Roles.get(requireContext()).get(mRoleName);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), getTheme());
        Context context = builder.getContext();

        RoleManager roleManager = context.getSystemService(RoleManager.class);
        List<String> currentPackageNames = roleManager.getRoleHolders(mRoleName);
        if (currentPackageNames.contains(mPackageName)) {
            Log.i(LOG_TAG, "Application is already a role holder, role: " + mRoleName
                    + ", package: " + mPackageName);
            reportRequestResult(PermissionControllerStatsLog
                    .ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED_ALREADY_GRANTED, null);
            clearDeniedSetResultOkAndFinish();
            return super.onCreateDialog(savedInstanceState);
        }

        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(mPackageName, context);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Unknown application: " + mPackageName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED,
                    null);
            finish();
            return super.onCreateDialog(savedInstanceState);
        }
        Drawable icon = Utils.getBadgedIcon(context, applicationInfo);
        String applicationLabel = Utils.getAppLabel(applicationInfo, context);
        String title = getString(mRole.getRequestTitleResource(), applicationLabel);

        LayoutInflater inflater = LayoutInflater.from(context);
        View titleLayout = inflater.inflate(R.layout.request_role_title, null);
        ImageView iconImage = titleLayout.requireViewById(R.id.icon);
        iconImage.setImageDrawable(icon);
        TextView titleText = titleLayout.requireViewById(R.id.title);
        titleText.setText(title);

        View viewLayout = inflater.inflate(R.layout.request_role_view, null);
        mListView = viewLayout.requireViewById(R.id.list);
        mListView.setOnItemClickListener((parent, view, position, id) -> onItemClicked(position));
        mAdapter = new Adapter(mListView, mRole);
        if (savedInstanceState != null) {
            mAdapter.onRestoreInstanceState(savedInstanceState);
        }
        mListView.setAdapter(mAdapter);
        if (!mListView.isInTouchMode()) {
            mListView.post(() -> {
                mListView.setSelection(0);
                mListView.requestFocus();
            });
        }

        CheckBox dontAskAgainCheck = viewLayout.requireViewById(R.id.dont_ask_again);
        boolean isDeniedOnce = UserDeniedManager.getInstance(context).isDeniedOnce(mRoleName,
                mPackageName);
        dontAskAgainCheck.setVisibility(isDeniedOnce ? View.VISIBLE : View.GONE);
        if (isDeniedOnce) {
            mDontAskAgainCheck = dontAskAgainCheck;
            mDontAskAgainCheck.setOnClickListener(view -> updateUi());
            if (savedInstanceState != null) {
                boolean dontAskAgain = savedInstanceState.getBoolean(STATE_DONT_ASK_AGAIN);
                mDontAskAgainCheck.setChecked(dontAskAgain);
                mAdapter.setDontAskAgain(dontAskAgain);
            }
        }

        AlertDialog dialog = builder
                .setCustomTitle(titleLayout)
                .setView(viewLayout)
                // Set the positive button listener later to avoid the automatic dismiss behavior.
                .setPositiveButton(R.string.request_role_set_as_default, null)
                // The default behavior for a null listener is to dismiss the dialog, not cancel.
                .setNegativeButton(android.R.string.cancel, (dialog2, which) -> dialog2.cancel())
                .create();
        dialog.getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        dialog.setOnShowListener(dialog2 -> dialog.getButton(Dialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> onSetAsDefault()));
        return dialog;
    }

    @Override
    public AlertDialog getDialog() {
        return (AlertDialog) super.getDialog();
    }

    @Override
    public void onStart() {
        super.onStart();

        Context context = requireContext();
        if (PackageUtils.getApplicationInfo(mPackageName, context) == null) {
            Log.w(LOG_TAG, "Unknown application: " + mPackageName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED,
                    null);
            finish();
            return;
        }

        mPackageRemovalMonitor = new PackageRemovalMonitor(context, mPackageName) {
            @Override
            protected void onPackageRemoved() {
                Log.w(LOG_TAG, "Application is uninstalled, role: " + mRoleName + ", package: "
                        + mPackageName);
                reportRequestResult(
                        PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED,
                        null);
                finish();
            }
        };
        mPackageRemovalMonitor.register();

        // Postponed to onStart() so that the list view in dialog is created.
        mViewModel = ViewModelProviders.of(this, new RequestRoleViewModel.Factory(mRole,
                requireActivity().getApplication())).get(RequestRoleViewModel.class);
        mViewModel.getRoleLiveData().observe(this, this::onRoleDataChanged);
        mViewModel.getManageRoleHolderStateLiveData().observe(this,
                this::onManageRoleHolderStateChanged);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        mAdapter.onSaveInstanceState(outState);
        if (mDontAskAgainCheck != null) {
            outState.putBoolean(STATE_DONT_ASK_AGAIN, mDontAskAgainCheck.isChecked());
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mPackageRemovalMonitor != null) {
            mPackageRemovalMonitor.unregister();
            mPackageRemovalMonitor = null;
        }
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);

        Log.i(LOG_TAG, "Dialog cancelled, role: " + mRoleName + ", package: " + mPackageName);
        reportRequestResult(
                PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED,
                null);
        setDeniedOnceAndFinish();
    }

    private void onRoleDataChanged(
            @NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
        mAdapter.replace(qualifyingApplications);
        updateUi();
    }

    private void onItemClicked(int position) {
        mAdapter.onItemClicked(position);
        updateUi();
    }

    private void onSetAsDefault() {
        if (mDontAskAgainCheck != null && mDontAskAgainCheck.isChecked()) {
            Log.i(LOG_TAG, "Request denied with don't ask again, role: " + mRoleName + ", package: "
                    + mPackageName);
            reportRequestResult(PermissionControllerStatsLog
                    .ROLE_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_ALWAYS, null);
            setDeniedAlwaysAndFinish();
        } else {
            setRoleHolder();
        }
    }

    private void setRoleHolder() {
        String packageName = mAdapter.getCheckedPackageName();
        Context context = requireContext();
        UserHandle user = Process.myUserHandle();
        if (packageName == null) {
            reportRequestResult(PermissionControllerStatsLog
                            .ROLE_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_GRANTED_ANOTHER,
                    null);
            mRole.onNoneHolderSelectedAsUser(user, context);
            mViewModel.getManageRoleHolderStateLiveData().clearRoleHoldersAsUser(mRoleName, 0, user,
                    context);
        } else {
            boolean isRequestingApplication = Objects.equals(packageName, mPackageName);
            if (isRequestingApplication) {
                reportRequestResult(PermissionControllerStatsLog
                        .ROLE_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED, null);
            } else {
                reportRequestResult(PermissionControllerStatsLog
                        .ROLE_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_GRANTED_ANOTHER,
                        packageName);
            }
            int flags = isRequestingApplication ? RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP : 0;
            mViewModel.getManageRoleHolderStateLiveData().setRoleHolderAsUser(mRoleName,
                    packageName, true, flags, user, context);
        }
    }

    private void onManageRoleHolderStateChanged(int state) {
        switch (state) {
            case ManageRoleHolderStateLiveData.STATE_IDLE:
            case ManageRoleHolderStateLiveData.STATE_WORKING:
                updateUi();
                break;
            case ManageRoleHolderStateLiveData.STATE_SUCCESS: {
                ManageRoleHolderStateLiveData liveData =
                        mViewModel.getManageRoleHolderStateLiveData();
                String packageName = liveData.getLastPackageName();
                if (packageName != null) {
                    mRole.onHolderSelectedAsUser(packageName, liveData.getLastUser(),
                            requireContext());
                }
                if (Objects.equals(packageName, mPackageName)) {
                    Log.i(LOG_TAG, "Application added as a role holder, role: " + mRoleName
                            + ", package: " + mPackageName);
                    clearDeniedSetResultOkAndFinish();
                } else {
                    Log.i(LOG_TAG, "Request denied with another application added as a role holder,"
                            + " role: " + mRoleName + ", package: " + mPackageName);
                    setDeniedOnceAndFinish();
                }
                break;
            }
            case ManageRoleHolderStateLiveData.STATE_FAILURE:
                finish();
                break;
        }
    }

    private void updateUi() {
        boolean enabled = mViewModel.getManageRoleHolderStateLiveData().getValue()
                == ManageRoleHolderStateLiveData.STATE_IDLE;
        mListView.setEnabled(enabled);
        boolean dontAskAgain = mDontAskAgainCheck != null && mDontAskAgainCheck.isChecked();
        mAdapter.setDontAskAgain(dontAskAgain);
        AlertDialog dialog = getDialog();
        boolean hasRoleData = mViewModel.getRoleLiveData().getValue() != null;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled && hasRoleData
                && (dontAskAgain || !mAdapter.isHolderApplicationChecked()));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
    }

    private void clearDeniedSetResultOkAndFinish() {
        UserDeniedManager.getInstance(requireContext()).clearDenied(mRoleName, mPackageName);
        requireActivity().setResult(Activity.RESULT_OK);
        finish();
    }

    private void setDeniedOnceAndFinish() {
        UserDeniedManager.getInstance(requireContext()).setDeniedOnce(mRoleName, mPackageName);
        finish();
    }

    private void setDeniedAlwaysAndFinish() {
        UserDeniedManager.getInstance(requireContext()).setDeniedAlways(mRoleName, mPackageName);
        finish();
    }

    private void finish() {
        requireActivity().finish();
    }

    private void reportRequestResult(int result, @Nullable String grantedAnotherPackageName) {
        String holderPackageName = getHolderPackageName();
        reportRequestResult(getApplicationUid(mPackageName), mPackageName, mRoleName,
                getQualifyingApplicationCount(), getQualifyingApplicationUid(holderPackageName),
                holderPackageName, getQualifyingApplicationUid(grantedAnotherPackageName),
                grantedAnotherPackageName, result);
    }

    private int getApplicationUid(@NonNull String packageName) {
        int uid = getQualifyingApplicationUid(packageName);
        if (uid != -1) {
            return uid;
        }
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(packageName,
                requireActivity());
        if (applicationInfo == null) {
            return -1;
        }
        return applicationInfo.uid;
    }

    private int getQualifyingApplicationUid(@Nullable String packageName) {
        if (packageName == null || mAdapter == null) {
            return -1;
        }
        int count = mAdapter.getCount();
        for (int i = 0; i < count; i++) {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = mAdapter.getItem(i);
            if (qualifyingApplication == null) {
                // Skip the "None" item.
                continue;
            }
            ApplicationInfo applicationInfo = qualifyingApplication.first;
            if (Objects.equals(applicationInfo.packageName, packageName)) {
                return applicationInfo.uid;
            }
        }
        return -1;
    }

    private int getQualifyingApplicationCount() {
        if (mAdapter == null) {
            return -1;
        }
        int count = mAdapter.getCount();
        if (count > 0 && mAdapter.getItem(0) == null) {
            // Exclude the "None" item.
            --count;
        }
        return count;
    }

    @Nullable
    private String getHolderPackageName() {
        if (mAdapter == null) {
            return null;
        }
        return mAdapter.mHolderPackageName;
    }

    static void reportRequestResult(int requestingUid, String requestingPackageName,
            String roleName, int qualifyingCount, int currentUid, String currentPackageName,
            int grantedAnotherUid, String grantedAnotherPackageName, int result) {
        Log.i(LOG_TAG, "Role request result"
                + " requestingUid=" + requestingUid
                + " requestingPackageName=" + requestingPackageName
                + " roleName=" + roleName
                + " qualifyingCount=" + qualifyingCount
                + " currentUid=" + currentUid
                + " currentPackageName=" + currentPackageName
                + " grantedAnotherUid=" + grantedAnotherUid
                + " grantedAnotherPackageName=" + grantedAnotherPackageName
                + " result=" + result);
        PermissionControllerStatsLog.write(
                PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED, requestingUid,
                requestingPackageName, roleName, qualifyingCount, currentUid, currentPackageName,
                grantedAnotherUid, grantedAnotherPackageName, result);
    }

    private static class Adapter extends BaseAdapter {

        private static final String STATE_USER_CHECKED = Adapter.class.getName()
                + ".state.USER_CHECKED";
        private static final String STATE_CHECKED_PACKAGE_NAME = Adapter.class.getName()
                + ".state.CHECKED_PACKAGE_NAME";

        private static final int LAYOUT_TRANSITION_DURATION_MILLIS = 150;

        @NonNull
        private final ListView mListView;

        @NonNull
        private final Role mRole;

        // We'll use a null to represent the "None" item.
        @NonNull
        private final List<Pair<ApplicationInfo, Boolean>> mQualifyingApplications =
                new ArrayList<>();

        @Nullable
        private String mHolderPackageName;

        private boolean mDontAskAgain;

        // If user has ever clicked an item to mark it as checked, we no longer automatically mark
        // the current holder as checked.
        private boolean mUserChecked;

        @Nullable
        private String mCheckedPackageName;

        Adapter(@NonNull ListView listView, @NonNull Role role) {
            mListView = listView;
            mRole = role;
        }

        public void onSaveInstanceState(@NonNull Bundle outState) {
            outState.putBoolean(STATE_USER_CHECKED, mUserChecked);
            if (mUserChecked) {
                outState.putString(STATE_CHECKED_PACKAGE_NAME, mCheckedPackageName);
            }
        }

        public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
            mUserChecked = savedInstanceState.getBoolean(STATE_USER_CHECKED);
            if (mUserChecked) {
                mCheckedPackageName = savedInstanceState.getString(STATE_CHECKED_PACKAGE_NAME);
            }
        }

        public void setDontAskAgain(boolean dontAskAgain) {
            if (mDontAskAgain == dontAskAgain) {
                return;
            }
            mDontAskAgain = dontAskAgain;
            if (mDontAskAgain) {
                mUserChecked = false;
                mCheckedPackageName = mHolderPackageName;
            }
            notifyDataSetChanged();
        }

        public void onItemClicked(int position) {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(position);
            if (qualifyingApplication == null) {
                mUserChecked = true;
                mCheckedPackageName = null;
            } else {
                ApplicationInfo applicationInfo = qualifyingApplication.first;
                Intent restrictionIntent = mRole.getApplicationRestrictionIntentAsUser(
                        applicationInfo, Process.myUserHandle(), mListView.getContext());
                if (restrictionIntent != null) {
                    mListView.getContext().startActivity(restrictionIntent);
                    return;
                } else {
                    mUserChecked = true;
                    mCheckedPackageName = applicationInfo.packageName;
                }
            }
            notifyDataSetChanged();
        }

        public void replace(@NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
            mQualifyingApplications.clear();
            if (mRole.shouldShowNone()) {
                mQualifyingApplications.add(0, null);
            }
            mQualifyingApplications.addAll(qualifyingApplications);
            mHolderPackageName = getHolderPackageName(qualifyingApplications);

            if (mUserChecked && mCheckedPackageName != null) {
                boolean isCheckedPackageNameFound = false;
                int count = getCount();
                for (int i = 0; i < count; i++) {
                    Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(i);
                    if (qualifyingApplication == null) {
                        continue;
                    }
                    String packageName = qualifyingApplication.first.packageName;

                    if (Objects.equals(packageName, mCheckedPackageName)) {
                        mUserChecked = true;
                        isCheckedPackageNameFound = true;
                        break;
                    }
                }
                if (!isCheckedPackageNameFound) {
                    mUserChecked = false;
                    mCheckedPackageName = null;
                }
            }

            if (!mUserChecked) {
                mCheckedPackageName = mHolderPackageName;
            }

            notifyDataSetChanged();
        }

        @Nullable
        private static String getHolderPackageName(
                @NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
            int qualifyingApplicationSize = qualifyingApplications.size();
            for (int i = 0; i < qualifyingApplicationSize; i++) {
                Pair<ApplicationInfo, Boolean> qualifyingApplication = qualifyingApplications.get(
                        i);
                if (qualifyingApplication == null) {
                    continue;
                }
                ApplicationInfo applicationInfo = qualifyingApplication.first;
                boolean isHolderApplication = qualifyingApplication.second;

                if (isHolderApplication) {
                    return applicationInfo.packageName;
                }
            }
            return null;
        }

        @Nullable
        public String getCheckedPackageName() {
            return mCheckedPackageName;
        }

        public boolean isHolderApplicationChecked() {
            return Objects.equals(mCheckedPackageName, mHolderPackageName);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public int getCount() {
            return mQualifyingApplications.size();
        }

        @Nullable
        @Override
        public Pair<ApplicationInfo, Boolean> getItem(int position) {
            return mQualifyingApplications.get(position);
        }

        @Override
        public long getItemId(int position) {
            if (position >= getCount()) {
                // Work around AbsListView.confirmCheckedPositionsById() not respecting our count.
                return ListView.INVALID_ROW_ID;
            }
            Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(position);
            return qualifyingApplication == null ? 0
                    : qualifyingApplication.first.packageName.hashCode();
        }

        @Override
        public boolean isEnabled(int position) {
            if (!mDontAskAgain) {
                return true;
            }
            Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(position);
            if (qualifyingApplication == null) {
                return mHolderPackageName == null;
            } else {
                boolean isHolderApplication = qualifyingApplication.second;
                return isHolderApplication;
            }
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            Context context = parent.getContext();
            CheckableLinearLayout view = (CheckableLinearLayout) convertView;
            ViewHolder holder;
            if (view != null) {
                holder = (ViewHolder) view.getTag();
            } else {
                view = (CheckableLinearLayout) LayoutInflater.from(context).inflate(
                        R.layout.request_role_item, parent, false);
                holder = new ViewHolder(view);
                view.setTag(holder);

                holder.titleAndSubtitleLayout.getLayoutTransition().setDuration(
                        LAYOUT_TRANSITION_DURATION_MILLIS);
            }

            Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(position);
            ApplicationInfo applicationInfo;
            boolean restricted;
            boolean checked;
            Drawable icon;
            String title;
            String subtitle;
            if (qualifyingApplication == null) {
                applicationInfo = null;
                restricted = false;
                checked = mCheckedPackageName == null;
                icon = AppCompatResources.getDrawable(context, R.drawable.ic_remove_circle);
                title = context.getString(R.string.default_app_none);
                subtitle = mHolderPackageName != null ? context.getString(
                        R.string.request_role_current_default) : null;
            } else {
                applicationInfo = qualifyingApplication.first;
                restricted = mRole.getApplicationRestrictionIntentAsUser(applicationInfo,
                        Process.myUserHandle(), context) != null;
                checked = Objects.equals(applicationInfo.packageName, mCheckedPackageName);
                icon = Utils.getBadgedIcon(context, applicationInfo);
                title = Utils.getAppLabel(applicationInfo, context);
                boolean isHolderApplication = qualifyingApplication.second;
                subtitle = isHolderApplication
                        ? context.getString(R.string.request_role_current_default)
                        : checked ? context.getString(mRole.getRequestDescriptionResource()) : null;
            }

            boolean enabled = isEnabled(position);
            UiUtils.setViewTreeEnabled(view, enabled && !restricted);
            view.setEnabled(enabled);
            view.setChecked(checked);
            holder.iconImage.setImageDrawable(icon);
            holder.titleText.setText(title);
            holder.subtitleText.setVisibility(!TextUtils.isEmpty(subtitle) ? View.VISIBLE
                    : View.GONE);
            holder.subtitleText.setText(subtitle);
            RoleUiBehaviorUtils.prepareRequestRoleItemViewAsUser(mRole, holder, applicationInfo,
                    Process.myUserHandle(), context);

            return view;
        }

        private static class ViewHolder implements RequestRoleItemView {

            @NonNull
            public final ImageView iconImage;
            @NonNull
            public final ViewGroup titleAndSubtitleLayout;
            @NonNull
            public final TextView titleText;
            @NonNull
            public final TextView subtitleText;

            ViewHolder(@NonNull View view) {
                iconImage = view.requireViewById(R.id.icon);
                titleAndSubtitleLayout = view.requireViewById(R.id.title_and_subtitle);
                titleText = view.requireViewById(R.id.title);
                subtitleText = view.requireViewById(R.id.subtitle);
            }

            @Override
            public ImageView getIconImageView() {
                return iconImage;
            }

            @Override
            public TextView getTitleTextView() {
                return titleText;
            }

            @Override
            public TextView getSubtitleTextView() {
                return subtitleText;
            }
        }
    }
}
