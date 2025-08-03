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

package com.android.car.settings.profiles;

import static android.os.UserManager.DISALLOW_ADD_USER;
import static android.os.UserManager.SWITCHABILITY_STATUS_OK;

import android.annotation.IntDef;
import android.app.Activity;
import android.app.ActivityManager;
import android.car.Car;
import android.car.user.CarUserManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.admin.ui.UserAvatarView;
import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.ErrorDialog;
import com.android.internal.util.UserIcons;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Displays a GridLayout with icons for the profiles in the system to allow switching between
 * profiles. One of the uses of this is for the lock screen in auto.
 */
public class ProfileGridRecyclerView extends RecyclerView {

    private static final String MAX_PROFILES_LIMIT_REACHED_DIALOG_TAG =
            "com.android.car.settings.profiles.MaxProfilesLimitReachedDialog";
    private static final String CONFIRM_CREATE_NEW_PROFILE_DIALOG_TAG =
            "com.android.car.settings.profiles.ConfirmCreateNewProfileDialog";

    private ProfileAdapter mAdapter;
    private UserManager mUserManager;
    private Context mContext;
    private BaseFragment mBaseFragment;
    private AddNewProfileTask mAddNewProfileTask;
    private boolean mEnableAddProfileButton;
    private ProfileIconProvider mProfileIconProvider;
    private Car mCar;
    private CarUserManager mCarUserManager;

    private final BroadcastReceiver mProfileUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onProfilesUpdate();
        }
    };

    public ProfileGridRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mUserManager = UserManager.get(mContext);
        mProfileIconProvider = new ProfileIconProvider();
        mEnableAddProfileButton = true;
        mCar = Car.createCar(mContext);
        mCarUserManager = (CarUserManager) mCar.getCarManager(Car.CAR_USER_SERVICE);

        addItemDecoration(new ItemSpacingDecoration(context.getResources().getDimensionPixelSize(
                R.dimen.profile_switcher_vertical_spacing_between_profiles)));
    }

    /**
     * Register listener for any update to the profiles
     */
    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        registerForProfileEvents();
    }

    /**
     * Unregisters listener checking for any change to the profiles
     */
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterForProfileEvents();
        if (mAddNewProfileTask != null) {
            mAddNewProfileTask.cancel(/* mayInterruptIfRunning= */ false);
        }
        if (mCar != null) {
            mCar.disconnect();
        }
    }

    /**
     * Initializes the adapter that populates the grid layout
     */
    public void buildAdapter() {
        List<ProfileRecord> profileRecords = createProfileRecords(getProfilesForProfileGrid());
        mAdapter = new ProfileAdapter(mContext, profileRecords);
        super.setAdapter(mAdapter);
    }

    private List<ProfileRecord> createProfileRecords(List<UserInfo> userInfoList) {
        int fgUserId = ActivityManager.getCurrentUser();
        UserHandle fgUserHandle = UserHandle.of(fgUserId);
        List<ProfileRecord> profileRecords = new ArrayList<>();

        // If the foreground profile CANNOT switch to other profiles, only display the foreground
        // profile.
        if (mUserManager.getUserSwitchability(fgUserHandle) != SWITCHABILITY_STATUS_OK) {
            profileRecords.add(createForegroundProfileRecord());
            return profileRecords;
        }

        // If the foreground profile CAN switch to other profiles, iterate through all profiles.
        for (UserInfo userInfo : userInfoList) {
            boolean isForeground = fgUserId == userInfo.id;

            if (!isForeground && userInfo.isGuest()) {
                // Don't display temporary running background guests in the switcher.
                continue;
            }

            ProfileRecord record = new ProfileRecord(userInfo, isForeground
                    ? ProfileRecord.FOREGROUND_PROFILE : ProfileRecord.BACKGROUND_PROFILE);
            profileRecords.add(record);
        }

        // Add start guest profile record if the system is not logged in as guest already.
        if (!getCurrentForegroundProfileInfo().isGuest()) {
            profileRecords.add(createStartGuestProfileRecord());
        }

        // Add "add profile" record if the foreground profile can add profiles
        if (!mUserManager.hasUserRestriction(DISALLOW_ADD_USER, fgUserHandle)) {
            profileRecords.add(createAddProfileRecord());
        }

        return profileRecords;
    }

    private ProfileRecord createForegroundProfileRecord() {
        return new ProfileRecord(getCurrentForegroundProfileInfo(),
                ProfileRecord.FOREGROUND_PROFILE);
    }

    private UserInfo getCurrentForegroundProfileInfo() {
        return mUserManager.getUserInfo(ActivityManager.getCurrentUser());
    }

    /**
     * Show the "Add Profile" Button
     */
    public void enableAddProfile() {
        mEnableAddProfileButton = true;
        onProfilesUpdate();
    }

    /**
     * Hide the "Add Profile" Button
     */
    public void disableAddProfile() {
        mEnableAddProfileButton = false;
        onProfilesUpdate();
    }

    /**
     * Create guest profile record
     */
    private ProfileRecord createStartGuestProfileRecord() {
        return new ProfileRecord(/* profileInfo= */ null, ProfileRecord.START_GUEST);
    }

    /**
     * Create add profile record
     */
    private ProfileRecord createAddProfileRecord() {
        return new ProfileRecord(/* profileInfo= */ null, ProfileRecord.ADD_PROFILE);
    }

    public void setFragment(BaseFragment fragment) {
        mBaseFragment = fragment;
    }

    private void onProfilesUpdate() {
        // If you can show the add profile button, there is no restriction
        mAdapter.setAddProfileRestricted(!mEnableAddProfileButton);
        mAdapter.clearProfiles();
        mAdapter.updateProfiles(createProfileRecords(getProfilesForProfileGrid()));
        mAdapter.notifyDataSetChanged();
    }

    private List<UserInfo> getProfilesForProfileGrid() {
        List<UserInfo> users = UserManager.get(mContext).getAliveUsers();
        return users.stream()
                .filter(userInfo -> userInfo.supportsSwitchTo() && userInfo.isFull())
                .sorted((u1, u2) -> Long.signum(u1.creationTime - u2.creationTime))
                .collect(Collectors.toList());
    }

    private void registerForProfileEvents() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_STOPPED);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiverAsUser(
                mProfileUpdateReceiver,
                UserHandle.ALL,
                filter,
                /* broadcastPermission= */ null,
                /* scheduler= */ null);
    }

    private void unregisterForProfileEvents() {
        mContext.unregisterReceiver(mProfileUpdateReceiver);
    }

    /**
     * Adapter to populate the grid layout with the available user profiles
     */
    public final class ProfileAdapter extends
            RecyclerView.Adapter<ProfileAdapter.ProfileAdapterViewHolder>
            implements AddNewProfileTask.AddNewProfileListener {

        private final Resources mRes;
        private final String mGuestName;

        private Context mContext;
        private List<ProfileRecord> mProfiles;
        private String mNewProfileName;
        // View that holds the add profile button.  Used to enable/disable the view
        private View mAddProfileView;
        private float mOpacityDisabled;
        private float mOpacityEnabled;
        private boolean mIsAddProfileRestricted;

        private final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
            mAddNewProfileTask = new AddNewProfileTask(mContext,
                    mCarUserManager, /* addNewProfileListener= */this);
            mAddNewProfileTask.execute(mNewProfileName);
        };

        /**
         * Enable the "add profile" button if the user cancels adding a profile
         */
        private final ConfirmationDialogFragment.RejectListener mRejectListener =
                arguments -> enableAddView();


        public ProfileAdapter(Context context, List<ProfileRecord> profiles) {
            mRes = context.getResources();
            mContext = context;
            updateProfiles(profiles);
            mGuestName = mRes.getString(com.android.internal.R.string.guest_name);
            mNewProfileName = mRes.getString(R.string.user_new_user_name);
            mOpacityDisabled = mRes.getFloat(R.dimen.opacity_disabled);
            mOpacityEnabled = mRes.getFloat(R.dimen.opacity_enabled);
            resetDialogListeners();
        }

        /**
         * Removes all the profiles from the Profile Grid.
         */
        public void clearProfiles() {
            mProfiles.clear();
        }

        /**
         * Refreshes the Profile Grid with the new List of profiles.
         */
        public void updateProfiles(List<ProfileRecord> profiles) {
            mProfiles = profiles;
        }

        @Override
        public ProfileAdapterViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(mContext)
                    .inflate(R.layout.profile_switcher_pod, parent, false);
            view.setAlpha(mOpacityEnabled);
            view.bringToFront();
            return new ProfileAdapterViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ProfileAdapterViewHolder holder, int position) {
            ProfileRecord profileRecord = mProfiles.get(position);
            Drawable circleIcon = getCircularProfileRecordIcon(profileRecord);
            if (profileRecord.mType == ProfileRecord.ADD_PROFILE) {
                // 'Add Profile' has badges if device admin exists.
                holder.mProfileAvatarImageView.setDrawableWithBadge(circleIcon);
            } else if (profileRecord.mInfo != null) {
                // Profile might have badges (like managed profile)
                holder.mProfileAvatarImageView.setDrawableWithBadge(circleIcon,
                        profileRecord.mInfo.id);
            } else {
                // Guest does not have badges
                holder.mProfileAvatarImageView.setDrawable(circleIcon);
            }
            holder.mProfileNameTextView.setText(getProfileRecordName(profileRecord));

            // Defaults to 100% opacity and no circle around the icon.
            holder.mView.setAlpha(mOpacityEnabled);
            holder.mFrame.setBackgroundResource(0);

            // Foreground profile record.
            switch (profileRecord.mType) {
                case ProfileRecord.FOREGROUND_PROFILE:
                    // Add a circle around the icon.
                    holder.mFrame.setBackgroundResource(R.drawable.profile_avatar_bg_circle);
                    // Go back to quick settings if profile selected is already the foreground
                    // profile.
                    holder.mView.setOnClickListener(v
                            -> mBaseFragment.getActivity().onBackPressed());
                    break;

                case ProfileRecord.START_GUEST:
                    holder.mView.setOnClickListener(v -> handleGuestSessionClicked());
                    break;

                case ProfileRecord.ADD_PROFILE:
                    if (mIsAddProfileRestricted) {
                        // If there are restrictions, show a 50% opaque "add profile" view
                        holder.mView.setAlpha(mOpacityDisabled);
                        holder.mView.setOnClickListener(
                                v -> mBaseFragment.getFragmentHost().showBlockingMessage());
                    } else {
                        holder.mView.setOnClickListener(v -> handleAddProfileClicked(v));
                    }
                    break;

                default:
                    // Profile record;
                    holder.mView.setOnClickListener(v -> handleProfileSwitch(profileRecord.mInfo));
            }
        }

        /**
         * Specify if adding a profile should be restricted.
         *
         * @param isAddProfileRestricted should adding a profile be restricted
         */
        public void setAddProfileRestricted(boolean isAddProfileRestricted) {
            mIsAddProfileRestricted = isAddProfileRestricted;
        }

        /** Resets listeners for shown dialog fragments. */
        private void resetDialogListeners() {
            if (mBaseFragment != null) {
                ConfirmationDialogFragment dialog =
                        (ConfirmationDialogFragment) mBaseFragment
                                .getFragmentManager()
                                .findFragmentByTag(CONFIRM_CREATE_NEW_PROFILE_DIALOG_TAG);
                ConfirmationDialogFragment.resetListeners(
                        dialog,
                        mConfirmListener,
                        mRejectListener,
                        /* neutralListener= */ null);
            }
        }

        private void handleProfileSwitch(UserInfo userInfo) {
            mCarUserManager.switchUser(userInfo.id).whenCompleteAsync((r, e) -> {
                // Successful switch, close Settings app.
                closeSettingsTask();
            }, Runnable::run);
        }

        private void handleGuestSessionClicked() {
            UserInfo guest =
                    ProfileHelper.getInstance(mContext).createNewOrFindExistingGuest(mContext);
            if (guest != null) {
                mCarUserManager.switchUser(guest.id).whenCompleteAsync((r, e) -> {
                    // Successful start, will switch to guest now. Close Settings app.
                    closeSettingsTask();
                }, Runnable::run);
            }
        }

        private void handleAddProfileClicked(View addProfileView) {
            if (!mUserManager.canAddMoreUsers()) {
                showMaxProfilesLimitReachedDialog();
            } else {
                mAddProfileView = addProfileView;
                // Disable button so it cannot be clicked multiple times
                mAddProfileView.setEnabled(false);
                showConfirmCreateNewProfileDialog();
            }
        }

        private void showMaxProfilesLimitReachedDialog() {
            ConfirmationDialogFragment dialogFragment =
                    ProfilesDialogProvider.getMaxProfilesLimitReachedDialogFragment(getContext(),
                            ProfileHelper.getInstance(mContext).getMaxSupportedRealProfiles());
            dialogFragment.show(
                    mBaseFragment.getFragmentManager(), MAX_PROFILES_LIMIT_REACHED_DIALOG_TAG);
        }

        private void showConfirmCreateNewProfileDialog() {
            ConfirmationDialogFragment dialogFragment =
                    ProfilesDialogProvider.getConfirmCreateNewProfileDialogFragment(getContext(),
                            mConfirmListener, mRejectListener);
            dialogFragment.show(
                    mBaseFragment.getFragmentManager(), CONFIRM_CREATE_NEW_PROFILE_DIALOG_TAG);
        }

        private Drawable getCircularProfileRecordIcon(ProfileRecord profileRecord) {
            Drawable circleIcon;
            switch (profileRecord.mType) {
                case ProfileRecord.START_GUEST:
                    circleIcon = mProfileIconProvider.getRoundedGuestDefaultIcon(mContext);
                    break;
                case ProfileRecord.ADD_PROFILE:
                    circleIcon = getCircularAddProfileIcon();
                    break;
                default:
                    circleIcon = mProfileIconProvider.getRoundedProfileIcon(profileRecord.mInfo,
                            mContext);
            }
            return circleIcon;
        }

        private RoundedBitmapDrawable getCircularAddProfileIcon() {
            RoundedBitmapDrawable circleIcon =
                    RoundedBitmapDrawableFactory.create(mRes, UserIcons.convertToBitmap(
                            mContext.getDrawable(R.drawable.profile_add_circle)));
            circleIcon.setCircular(true);
            return circleIcon;
        }

        private String getProfileRecordName(ProfileRecord profileRecord) {
            String recordName;
            switch (profileRecord.mType) {
                case ProfileRecord.START_GUEST:
                    recordName = mContext.getString(com.android.internal.R.string.guest_name);
                    break;
                case ProfileRecord.ADD_PROFILE:
                    recordName = mContext.getString(R.string.user_add_user_menu);
                    break;
                default:
                    recordName = profileRecord.mInfo.name;
            }
            return recordName;
        }

        @Override
        public void onProfileAddedSuccess() {
            enableAddView();
            // New profile added. Will switch to new profile, therefore close the app.
            closeSettingsTask();
        }

        @Override
        public void onProfileAddedFailure() {
            enableAddView();
            // Display failure dialog.
            if (mBaseFragment != null) {
                ErrorDialog.show(mBaseFragment, R.string.add_user_error_title);
            }
        }

        /**
         * When we switch profiles, we also want to finish the QuickSettingActivity, so we send back
         * a result telling the QuickSettingActivity to finish.
         */
        private void closeSettingsTask() {
            mBaseFragment.getActivity().setResult(Activity.FINISH_TASK_WITH_ACTIVITY, new Intent());
            mBaseFragment.getActivity().finish();
        }

        @Override
        public int getItemCount() {
            return mProfiles.size();
        }

        /**
         * Layout for each individual pod in the Grid RecyclerView
         */
        public class ProfileAdapterViewHolder extends RecyclerView.ViewHolder {

            public UserAvatarView mProfileAvatarImageView;
            public TextView mProfileNameTextView;
            public View mView;
            public FrameLayout mFrame;

            public ProfileAdapterViewHolder(View view) {
                super(view);
                mView = view;
                mProfileAvatarImageView = view.findViewById(R.id.profile_avatar);
                mProfileNameTextView = view.findViewById(R.id.profile_name);
                mFrame = view.findViewById(R.id.current_profile_frame);
            }
        }

        private void enableAddView() {
            if (mAddProfileView != null) {
                mAddProfileView.setEnabled(true);
            }
        }
    }

    /**
     * Object wrapper class for the userInfo.  Use it to distinguish if a profile is a
     * guest profile, add user profile, or the foreground profile.
     */
    public static final class ProfileRecord {

        public final UserInfo mInfo;
        public final @ProfileRecordType int mType;

        public static final int START_GUEST = 0;
        public static final int ADD_PROFILE = 1;
        public static final int FOREGROUND_PROFILE = 2;
        public static final int BACKGROUND_PROFILE = 3;

        @IntDef({START_GUEST, ADD_PROFILE, FOREGROUND_PROFILE, BACKGROUND_PROFILE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface ProfileRecordType {}

        public ProfileRecord(@Nullable UserInfo userInfo, @ProfileRecordType int recordType) {
            mInfo = userInfo;
            mType = recordType;
        }
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add spacing between each item in the
     * RecyclerView that it is added to.
     */
    private static class ItemSpacingDecoration extends RecyclerView.ItemDecoration {
        private int mItemSpacing;

        private ItemSpacingDecoration(int itemSpacing) {
            mItemSpacing = itemSpacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            // Skip offset for last item except for GridLayoutManager.
            if (position == state.getItemCount() - 1
                    && !(parent.getLayoutManager() instanceof GridLayoutManager)) {
                return;
            }

            outRect.bottom = mItemSpacing;
        }
    }
}
