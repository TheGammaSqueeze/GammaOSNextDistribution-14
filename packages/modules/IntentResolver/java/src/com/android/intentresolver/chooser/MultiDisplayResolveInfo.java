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

package com.android.intentresolver.chooser;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a "stack" of chooser targets for various activities within the same component.
 */
public class MultiDisplayResolveInfo extends DisplayResolveInfo {

    final ArrayList<DisplayResolveInfo> mTargetInfos;

    // Index of selected target
    private int mSelected = -1;

    /**
     * @param targetInfos A list of targets in this stack. The first item is treated as the
     * "representative" that provides the main icon, title, etc.
     */
    public static MultiDisplayResolveInfo newMultiDisplayResolveInfo(
            List<DisplayResolveInfo> targetInfos) {
        return new MultiDisplayResolveInfo(targetInfos);
    }

    /**
     * @param targetInfos A list of targets in this stack. The first item is treated as the
     * "representative" that provides the main icon, title, etc.
     */
    private MultiDisplayResolveInfo(List<DisplayResolveInfo> targetInfos) {
        super(targetInfos.get(0));
        mTargetInfos = new ArrayList<>(targetInfos);
    }

    @Override
    public final boolean isMultiDisplayResolveInfo() {
        return true;
    }

    @Override
    public CharSequence getExtendedInfo() {
        // Never show subtitle for stacked apps
        return null;
    }

    /**
     * List of all {@link DisplayResolveInfo}s included in this target.
     * TODO: provide as a generic {@code List<DisplayResolveInfo>} once
     *  {@link com.android.intentresolver.ChooserActivity} stops requiring the signature to match
     *  that of the other "lists" it builds up.
     */
    @Override
    public ArrayList<DisplayResolveInfo> getAllDisplayTargets() {
        return mTargetInfos;
    }

    public void setSelected(int selected) {
        mSelected = selected;
    }

    /**
     * Return selected target.
     */
    public DisplayResolveInfo getSelectedTarget() {
        return hasSelected() ? mTargetInfos.get(mSelected) : null;
    }

    /**
     * Whether or not the user has selected a specific target for this MultiInfo.
     */
    public boolean hasSelected() {
        return mSelected >= 0;
    }

    @Override
    @Nullable
    public MultiDisplayResolveInfo tryToCloneWithAppliedRefinement(Intent proposedRefinement) {
        final int size = mTargetInfos.size();
        ArrayList<DisplayResolveInfo> targetInfos = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            DisplayResolveInfo target = mTargetInfos.get(i);
            DisplayResolveInfo targetClone = (i == mSelected)
                    ? target.tryToCloneWithAppliedRefinement(proposedRefinement)
                    : new DisplayResolveInfo(target);
            if (targetClone == null) {
                return null;
            }
            targetInfos.add(targetClone);
        }
        MultiDisplayResolveInfo clone = new MultiDisplayResolveInfo(targetInfos);
        clone.mSelected = mSelected;
        return clone;
    }

    @Override
    public boolean startAsCaller(Activity activity, Bundle options, int userId) {
        return mTargetInfos.get(mSelected).startAsCaller(activity, options, userId);
    }

    @Override
    public ComponentName getResolvedComponentName() {
        if (hasSelected()) {
            return mTargetInfos.get(mSelected).getResolvedComponentName();
        }
        // It is not expected to have this method be called on an unselected multi-display item.
        // Call super to preserve the legacy (most likely erroneous) behavior.
        Log.wtf(
                "ChooserActivity",
                "retrieving ResolvedComponentName from an unselected MultiDisplayResolveInfo");
        return super.getResolvedComponentName();
    }

    @Override
    public boolean startAsUser(Activity activity, Bundle options, UserHandle user) {
        return mTargetInfos.get(mSelected).startAsUser(activity, options, user);
    }

    @Override
    public Intent getTargetIntent() {
        return mTargetInfos.get(mSelected).getTargetIntent();
    }

    @Override
    public List<Intent> getAllSourceIntents() {
        return hasSelected()
                ? mTargetInfos.get(mSelected).getAllSourceIntents()
                : Collections.emptyList();
    }
}
