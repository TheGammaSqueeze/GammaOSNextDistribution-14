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

package com.android.devicelockcontroller.activities;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.activities.ProvisionInfo.ProvisionInfoType;
import com.android.devicelockcontroller.activities.util.UrlUtils;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * An Adapter which provides the binding between {@link ProvisionInfo} and the
 * {@link androidx.recyclerview.widget.RecyclerView}.
 */
public final class ProvisionInfoListAdapter extends
        ListAdapter<ProvisionInfo, ProvisionInfoListAdapter.ProvisionInfoViewHolder> {

    ProvisionInfoListAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull ProvisionInfo oldItem,
                    @NonNull ProvisionInfo newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull ProvisionInfo oldItem,
                    @NonNull ProvisionInfo newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    @NonNull
    @Override
    public ProvisionInfoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_provision_info,
                parent, false);
        return new ProvisionInfoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProvisionInfoViewHolder provisionInfoViewHolder,
            int position) {
        provisionInfoViewHolder.bindProvisionInfo(getItem(position));
    }

    /**
     * A {@link androidx.recyclerview.widget.RecyclerView.ViewHolder} class which describes the item
     * views used in the {@link RecyclerView}
     */
    static final class ProvisionInfoViewHolder extends RecyclerView.ViewHolder {

        private static final String TAG = ProvisionInfoViewHolder.class.getSimpleName();

        private final TextView mTextView;

        ProvisionInfoViewHolder(@NonNull View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.text_view_item_provision_info);
        }

        /**
         * Bind the item view with url.
         *
         * @param provisionInfo The {@link ProvisionInfo} data to bind to the held view.
         */
        void bindProvisionInfo(ProvisionInfo provisionInfo) {
            Context context = mTextView.getContext();
            String providerName = provisionInfo.getProviderName();
            if (TextUtils.isEmpty(providerName)) {
                LogUtil.w(TAG, "Provider name is not provided!");
                mTextView.setText("");
            } else if (provisionInfo.getType() == ProvisionInfoType.REGULAR) {
                mTextView.setText(context.getString(provisionInfo.getTextId(), providerName));
            } else {
                UrlUtils.setUrlText(mTextView,
                        String.format(context.getString(provisionInfo.getTextId()), providerName,
                                provisionInfo.getUrl()));
            }
            mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(provisionInfo.getDrawableId(),
                    /* top=*/ 0,
                    /* end=*/ 0,
                    /* bottom=*/ 0);
        }
    }
}
