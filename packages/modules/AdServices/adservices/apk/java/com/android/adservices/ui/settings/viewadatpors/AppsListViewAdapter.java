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
package com.android.adservices.ui.settings.viewadatpors;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.service.consent.App;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedAppsFragment;

import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.function.Function;

/**
 * ViewAdapter to handle data binding for the list of {@link App}s on {@link
 * AdServicesSettingsAppsFragment} and blocked {@link App}s on {@link
 * AdServicesSettingsBlockedAppsFragment}.
 */
public class AppsListViewAdapter extends RecyclerView.Adapter {
    private final Context mContext;
    private final Function<App, View.OnClickListener> mGetOnclickListener;
    private final LiveData<ImmutableList<App>> mAppsList;
    private final boolean mIsBlockedAppsList;

    public AppsListViewAdapter(
            Context context,
            LiveData<ImmutableList<App>> appsList,
            Function<App, View.OnClickListener> getOnclickListener,
            boolean isBlockedAppsList) {
        mContext = context;
        mAppsList = appsList;
        mGetOnclickListener = getOnclickListener;
        mIsBlockedAppsList = isBlockedAppsList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter
                .AppsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter.AppsViewHolder)
                        holder)
                .initAppItem(
                        mContext,
                        mGetOnclickListener,
                        Objects.requireNonNull(mAppsList.getValue()).get(position),
                        mIsBlockedAppsList);
    }

    @Override
    public int getItemCount() {
        return Objects.requireNonNull(mAppsList.getValue()).size();
    }

    @Override
    public int getItemViewType(final int position) {
        return R.layout.app_item;
    }

    /** ViewHolder to display the text for an app item */
    public static class AppsViewHolder extends RecyclerView.ViewHolder {
        private final TextView mAppTextView;
        private final Button mOptionButtonView;
        private final ImageView mImageView;

        public AppsViewHolder(View itemView) {
            super(itemView);
            mAppTextView = itemView.findViewById(R.id.app_text);
            mOptionButtonView = itemView.findViewById(R.id.option_button);
            mImageView = itemView.findViewById(R.id.app_icon);
        }

        /** Set the human readable string for the app and listener for block/unblock app logic. */
        public void initAppItem(
                Context context,
                Function<App, View.OnClickListener> getOnclickListener,
                App app,
                boolean mIsBlockedAppsListItem) {
            prepareAppName(app, context);
            prepareAppImageView(app, context);
            if (mIsBlockedAppsListItem) {
                mOptionButtonView.setText(R.string.settingsUI_unblock_app_title);
            } else {
                mOptionButtonView.setText(R.string.settingsUI_block_app_title);
            }
            mOptionButtonView.setOnClickListener(getOnclickListener.apply(app));
        }

        private void prepareAppName(App app, Context context) {
            mAppTextView.setText(app.getAppDisplayName(context.getPackageManager()));
        }

        private void prepareAppImageView(App app, Context context) {
            Drawable appIcon = app.getAppIcon(context);
            if (appIcon != null) {
                mImageView.setImageDrawable(appIcon);
            }
        }
    }
}
