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
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A TargetInfo plus additional information needed to render it (such as icon and label) and
 * resolve it to an activity.
 */
public class DisplayResolveInfo implements TargetInfo {
    private final ResolveInfo mResolveInfo;
    private volatile CharSequence mDisplayLabel;
    private volatile CharSequence mExtendedInfo;
    private final Intent mResolvedIntent;
    private final List<Intent> mSourceIntents = new ArrayList<>();
    private final boolean mIsSuspended;
    private boolean mPinned = false;
    private final IconHolder mDisplayIconHolder = new SettableIconHolder();

    /** Create a new {@code DisplayResolveInfo} instance. */
    public static DisplayResolveInfo newDisplayResolveInfo(
            Intent originalIntent,
            ResolveInfo resolveInfo,
            @NonNull Intent resolvedIntent) {
        return newDisplayResolveInfo(
                originalIntent,
                resolveInfo,
                /* displayLabel=*/ null,
                /* extendedInfo=*/ null,
                resolvedIntent);
    }

    /** Create a new {@code DisplayResolveInfo} instance. */
    public static DisplayResolveInfo newDisplayResolveInfo(
            Intent originalIntent,
            ResolveInfo resolveInfo,
            CharSequence displayLabel,
            CharSequence extendedInfo,
            @NonNull Intent resolvedIntent) {
        return new DisplayResolveInfo(
                originalIntent,
                resolveInfo,
                displayLabel,
                extendedInfo,
                resolvedIntent);
    }

    private DisplayResolveInfo(
            Intent originalIntent,
            ResolveInfo resolveInfo,
            CharSequence displayLabel,
            CharSequence extendedInfo,
            @NonNull Intent resolvedIntent) {
        mSourceIntents.add(originalIntent);
        mResolveInfo = resolveInfo;
        mDisplayLabel = displayLabel;
        mExtendedInfo = extendedInfo;

        final ActivityInfo ai = mResolveInfo.activityInfo;
        mIsSuspended = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0;

        mResolvedIntent = createResolvedIntent(resolvedIntent, ai);
    }

    private DisplayResolveInfo(
            DisplayResolveInfo other,
            @Nullable Intent baseIntentToSend) {
        mSourceIntents.addAll(other.getAllSourceIntents());
        mResolveInfo = other.mResolveInfo;
        mIsSuspended = other.mIsSuspended;
        mDisplayLabel = other.mDisplayLabel;
        mExtendedInfo = other.mExtendedInfo;

        mResolvedIntent = createResolvedIntent(
                baseIntentToSend == null ? other.mResolvedIntent : baseIntentToSend,
                mResolveInfo.activityInfo);

        mDisplayIconHolder.setDisplayIcon(other.mDisplayIconHolder.getDisplayIcon());
    }

    protected DisplayResolveInfo(DisplayResolveInfo other) {
        mSourceIntents.addAll(other.getAllSourceIntents());
        mResolveInfo = other.mResolveInfo;
        mIsSuspended = other.mIsSuspended;
        mDisplayLabel = other.mDisplayLabel;
        mExtendedInfo = other.mExtendedInfo;
        mResolvedIntent = other.mResolvedIntent;

        mDisplayIconHolder.setDisplayIcon(other.mDisplayIconHolder.getDisplayIcon());
    }

    private static Intent createResolvedIntent(Intent resolvedIntent, ActivityInfo ai) {
        final Intent result = new Intent(resolvedIntent);
        result.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT
                | Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        result.setComponent(new ComponentName(ai.applicationInfo.packageName, ai.name));
        return result;
    }

    @Override
    public final boolean isDisplayResolveInfo() {
        return true;
    }

    public ResolveInfo getResolveInfo() {
        return mResolveInfo;
    }

    public CharSequence getDisplayLabel() {
        return mDisplayLabel;
    }

    public boolean hasDisplayLabel() {
        return mDisplayLabel != null;
    }

    public void setDisplayLabel(CharSequence displayLabel) {
        mDisplayLabel = displayLabel;
    }

    public void setExtendedInfo(CharSequence extendedInfo) {
        mExtendedInfo = extendedInfo;
    }

    @Override
    public IconHolder getDisplayIconHolder() {
        return mDisplayIconHolder;
    }

    @Override
    @Nullable
    public DisplayResolveInfo tryToCloneWithAppliedRefinement(Intent proposedRefinement) {
        Intent matchingBase =
                getAllSourceIntents()
                        .stream()
                        .filter(i -> i.filterEquals(proposedRefinement))
                        .findFirst()
                        .orElse(null);
        if (matchingBase == null) {
            return null;
        }

        return new DisplayResolveInfo(
                this,
                TargetInfo.mergeRefinementIntoMatchingBaseIntent(matchingBase, proposedRefinement));
    }

    @Override
    public List<Intent> getAllSourceIntents() {
        return mSourceIntents;
    }

    @Override
    public ArrayList<DisplayResolveInfo> getAllDisplayTargets() {
        return new ArrayList<>(List.of(this));
    }

    public void addAlternateSourceIntent(Intent alt) {
        mSourceIntents.add(alt);
    }

    public CharSequence getExtendedInfo() {
        return mExtendedInfo;
    }

    public Intent getResolvedIntent() {
        return mResolvedIntent;
    }

    @Override
    public ComponentName getResolvedComponentName() {
        return new ComponentName(mResolveInfo.activityInfo.packageName,
                mResolveInfo.activityInfo.name);
    }

    @Override
    public boolean startAsCaller(Activity activity, Bundle options, int userId) {
        TargetInfo.prepareIntentForCrossProfileLaunch(mResolvedIntent, userId);
        activity.startActivityAsCaller(mResolvedIntent, options, false, userId);
        return true;
    }

    @Override
    public boolean startAsUser(Activity activity, Bundle options, UserHandle user) {
        TargetInfo.prepareIntentForCrossProfileLaunch(mResolvedIntent, user.getIdentifier());
        // TODO: is this equivalent to `startActivityAsCaller` with `ignoreTargetSecurity=true`? If
        // so, we can consolidate on the one API method to show that this flag is the only
        // distinction between `startAsCaller` and `startAsUser`. We can even bake that flag into
        // the `TargetActivityStarter` upfront since it just reflects our "safe forwarding mode" --
        // which is constant for the duration of our lifecycle, leaving clients no other
        // responsibilities in this logic.
        activity.startActivityAsUser(mResolvedIntent, options, user);
        return false;
    }

    @Override
    public Intent getTargetIntent() {
        return mResolvedIntent;
    }

    public boolean isSuspended() {
        return mIsSuspended;
    }

    @Override
    public boolean isPinned() {
        return mPinned;
    }

    public void setPinned(boolean pinned) {
        mPinned = pinned;
    }
}
