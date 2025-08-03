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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;

import com.android.systemui.R;

import java.io.PrintWriter;
import java.util.List;

final class UserPickerAdapter extends Adapter<UserPickerAdapter.UserPickerAdapterViewHolder> {
    private final Context mContext;
    private final int mDisplayId;
    private final float mDisabledAlpha;
    @ColorInt
    private final int mCurrentUserSubtitleColor;
    @ColorInt
    private final int mOtherUserSubtitleColor;
    private final int mVerticalSpacing;
    private final int mHorizontalSpacing;
    private final int mNumCols;

    private List<UserRecord> mUsers;
    private String mLoggedInText;
    private String mPrefixOtherSeatLoggedInInfo;
    private String mStoppingUserText;

    UserPickerAdapter(Context context) {
        mContext = context;
        mDisplayId = mContext.getDisplayId();
        mDisabledAlpha = mContext.getResources().getFloat(R.fraction.user_picker_disabled_alpha);
        mCurrentUserSubtitleColor = mContext.getColor(
                R.color.user_picker_current_login_state_color);
        mOtherUserSubtitleColor = mContext.getColor(R.color.user_picker_other_login_state_color);
        mVerticalSpacing = mContext.getResources().getDimensionPixelSize(
                R.dimen.user_picker_vertical_space_between_users);
        mHorizontalSpacing = mContext.getResources().getDimensionPixelSize(
                R.dimen.user_picker_horizontal_space_between_users);
        mNumCols = mContext.getResources().getInteger(R.integer.user_fullscreen_switcher_num_col);

        updateTexts();
    }

    void updateUsers(List<UserRecord> users) {
        mUsers = users;
    }

    private void setUserLoggedInInfo(UserPickerAdapterViewHolder holder, UserRecord userRecord) {
        if (!userRecord.mIsStopping && !userRecord.mIsLoggedIn) {
            holder.mUserBorderImageView.setVisibility(View.INVISIBLE);
            holder.mLoggedInTextView.setText("");
            updateAlpha(holder, /* disabled= */ false);
            return;
        }

        if (userRecord.mIsStopping) {
            holder.mUserBorderImageView.setVisibility(View.INVISIBLE);
            holder.mLoggedInTextView.setText(mStoppingUserText);
            holder.mLoggedInTextView.setTextColor(mOtherUserSubtitleColor);
            updateAlpha(holder, /* disabled= */ true);
        } else if (userRecord.mIsLoggedIn) {
            if (userRecord.mLoggedInDisplay == mDisplayId) {
                holder.mUserBorderImageView.setVisibility(View.VISIBLE);
                holder.mLoggedInTextView.setText(mLoggedInText);
                holder.mLoggedInTextView.setTextColor(mCurrentUserSubtitleColor);
                updateAlpha(holder, /* disabled= */ false);
            } else {
                holder.mUserBorderImageView.setVisibility(View.INVISIBLE);
                holder.mLoggedInTextView.setText(String.format(mPrefixOtherSeatLoggedInInfo,
                        userRecord.mSeatLocationName));
                holder.mLoggedInTextView.setTextColor(mOtherUserSubtitleColor);
                updateAlpha(holder, /* disabled= */ true);
            }
        }
    }

    private void updateAlpha(UserPickerAdapterViewHolder holder, boolean disabled) {
        float alpha = disabled ? mDisabledAlpha : 1.0f;
        holder.mUserAvatarImageView.setAlpha(alpha);
        holder.mUserNameTextView.setAlpha(alpha);
        holder.mLoggedInTextView.setAlpha(alpha);
    }

    @Override
    public UserPickerAdapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.user_picker_user_pod, parent, false);
        view.setAlpha(1f);
        view.bringToFront();
        return new UserPickerAdapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserPickerAdapterViewHolder holder, int position) {
        setItemSpacing(holder.mView, position);
        UserRecord userRecord = mUsers.get(position);
        holder.mUserAvatarImageView.setImageDrawable(userRecord.mIcon);
        holder.mFrame.setBackgroundResource(0);
        holder.mUserNameTextView.setText(userRecord.mName);
        setUserLoggedInInfo(holder, userRecord);
        holder.mView.setOnClickListener(userRecord.mOnClickListener);
    }

    @Override
    public int getItemCount() {
        return mUsers != null ? mUsers.size() : 0;
    }

    void onConfigurationChanged() {
        updateTexts();
    }

    private void updateTexts() {
        mLoggedInText = mContext.getString(R.string.logged_in_text);
        mPrefixOtherSeatLoggedInInfo = mContext
                .getString(R.string.prefix_logged_in_info_for_other_seat);
        mStoppingUserText = mContext.getString(R.string.stopping_user_text);
    }

    // TODO(b/281729191) use RecyclerView.ItemDecoration when supported by CarUiRecyclerView
    private void setItemSpacing(View rootItemView, int position) {
        ViewGroup.LayoutParams params = rootItemView.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) params;
            marginLayoutParams.bottomMargin = mVerticalSpacing;

            int splitHorizontalSpacing = mHorizontalSpacing / mNumCols;
            int col = position % mNumCols;
            marginLayoutParams.leftMargin = col * splitHorizontalSpacing;
            marginLayoutParams.rightMargin = (mNumCols - (col + 1)) * splitHorizontalSpacing;

            rootItemView.setLayoutParams(marginLayoutParams);
        }
    }

    void dump(@NonNull PrintWriter pw) {
        pw.println("  UserRecords : ");
        for (int i = 0; i < mUsers.size(); i++) {
            UserRecord userRecord = mUsers.get(i);
            pw.println("    " + userRecord.toString());
        }
    }

    static final class UserPickerAdapterViewHolder extends RecyclerView.ViewHolder {
        public final ImageView mUserAvatarImageView;
        public final TextView mUserNameTextView;
        public final ImageView mUserBorderImageView;
        public final TextView mLoggedInTextView;
        public final View mView;
        public final FrameLayout mFrame;

        UserPickerAdapterViewHolder(View view) {
            super(view);
            mView = view;
            mUserAvatarImageView = view.findViewById(R.id.user_avatar);
            mUserNameTextView = view.findViewById(R.id.user_name);
            mUserBorderImageView = view.findViewById(R.id.user_avatar_border);
            mLoggedInTextView = view.findViewById(R.id.logged_in_info);
            mFrame = view.findViewById(R.id.current_user_frame);
        }
    }
}
