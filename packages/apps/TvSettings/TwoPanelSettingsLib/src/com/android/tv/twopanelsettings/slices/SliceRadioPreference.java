/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tv.twopanelsettings.slices;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.preference.PreferenceViewHolder;
import androidx.slice.core.SliceActionImpl;

/**
 * Slice version of RadioPreference.
 */
public class SliceRadioPreference extends RadioPreference implements HasSliceAction, HasSliceUri,
        HasCustomContentDescription {
    private int mActionId;
    private SliceActionImpl mSliceAction;
    private String mUri;
    private SliceActionImpl mFollowupSliceAction;
    private String mContentDescription;

    public SliceRadioPreference(Context context, SliceActionImpl action) {
        super(context);
        mSliceAction = action;
        update();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        if (!TextUtils.isEmpty(mContentDescription)) {
            holder.itemView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            holder.itemView.setContentDescription(mContentDescription);
        }
    }

    @Override
    public int getActionId() {
        return mActionId;
    }

    @Override
    public void setActionId(int actionId) {
        mActionId = actionId;
    }

    @Override
    public SliceActionImpl getSliceAction() {
        return mSliceAction;
    }

    @Override
    public void setSliceAction(SliceActionImpl sliceAction) {
        mSliceAction = sliceAction;
    }

    @Override
    public SliceActionImpl getFollowupSliceAction() {
        return mFollowupSliceAction;
    }

    @Override
    public void setFollowupSliceAction(SliceActionImpl sliceAction) {
        mFollowupSliceAction = sliceAction;
    }

    private void update() {
        this.setChecked(mSliceAction.isChecked());
    }

    @Override
    public void setUri(String uri) {
        this.mUri = uri;
    }

    @Override
    public String getUri() {
        return mUri;
    }

    /**
     * Sets the accessibility content description that will be read to the TalkBack users when they
     * select this preference.
     */
    @Override
    public void setContentDescription(String contentDescription) {
        this.mContentDescription = contentDescription;
    }

    @Override
    public String getContentDescription() {
        return mContentDescription;
    }
}
