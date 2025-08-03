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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.topics.TopicsMapper;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedTopicsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;

import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.function.Function;

/**
 * ViewAdapter to handle data binding for the list of {@link Topic}s on {@link
 * AdServicesSettingsTopicsFragment} and blocked {@link Topic}s on {@link
 * AdServicesSettingsBlockedTopicsFragment}.
 */
public class TopicsListViewAdapter extends RecyclerView.Adapter {
    private final Context mContext;
    private final Function<Topic, OnClickListener> mGetOnclickListener;
    private final LiveData<ImmutableList<Topic>> mTopicsList;
    private final boolean mIsBlockedTopicsList;

    public TopicsListViewAdapter(
            Context context,
            LiveData<ImmutableList<Topic>> topicsList,
            Function<Topic, OnClickListener> getOnclickListener,
            boolean isBlockedTopicsList) {
        mContext = context;
        mTopicsList = topicsList;
        mGetOnclickListener = getOnclickListener;
        mIsBlockedTopicsList = isBlockedTopicsList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new TopicsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((TopicsViewHolder) holder)
                .initTopicItem(
                        mContext,
                        mGetOnclickListener,
                        Objects.requireNonNull(mTopicsList.getValue()).get(position),
                        mIsBlockedTopicsList);
    }

    @Override
    public int getItemCount() {
        return Objects.requireNonNull(mTopicsList.getValue()).size();
    }

    @Override
    public int getItemViewType(final int position) {
        return R.layout.topic_item;
    }

    /** ViewHolder to display the text for a topic item */
    public static class TopicsViewHolder extends RecyclerView.ViewHolder {

        private final TextView mTopicTextView;
        private final Button mOptionButtonView;

        public TopicsViewHolder(View itemView) {
            super(itemView);
            mTopicTextView = itemView.findViewById(R.id.topic_text);
            mOptionButtonView = itemView.findViewById(R.id.option_button);
        }

        /** Set the human readable string for the topic and listener for block topic logic. */
        public void initTopicItem(
                Context context,
                Function<Topic, OnClickListener> getOnclickListener,
                Topic topic,
                boolean mIsBlockedTopicsListItem) {
            int resourceId = TopicsMapper.getResourceIdByTopic(topic, context);
            if (resourceId == 0) {
                throw new IllegalArgumentException(
                        String.format("Android resource id for topic %s doesn't exist.", topic));
            }
            mTopicTextView.setText(resourceId);
            if (mIsBlockedTopicsListItem) {
                mOptionButtonView.setText(R.string.settingsUI_unblock_topic_title);
            } else {
                mOptionButtonView.setText(R.string.settingsUI_block_topic_title);
            }
            mOptionButtonView.setOnClickListener(getOnclickListener.apply(topic));
        }
    }
}
