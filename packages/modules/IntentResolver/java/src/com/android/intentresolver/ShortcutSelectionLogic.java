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

package com.android.intentresolver;

import android.app.prediction.AppTarget;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.service.chooser.ChooserTarget;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.SelectableTargetInfo;
import com.android.intentresolver.chooser.TargetInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class ShortcutSelectionLogic {
    private static final String TAG = "ShortcutSelectionLogic";
    private static final boolean DEBUG = false;
    private static final float PINNED_SHORTCUT_TARGET_SCORE_BOOST = 1000.f;
    private static final int MAX_CHOOSER_TARGETS_PER_APP = 2;

    private final int mMaxShortcutTargetsPerApp;
    private final boolean mApplySharingAppLimits;

    // Descending order
    private final Comparator<ChooserTarget> mBaseTargetComparator =
            (lhs, rhs) -> Float.compare(rhs.getScore(), lhs.getScore());

    ShortcutSelectionLogic(
            int maxShortcutTargetsPerApp,
            boolean applySharingAppLimits) {
        mMaxShortcutTargetsPerApp = maxShortcutTargetsPerApp;
        mApplySharingAppLimits = applySharingAppLimits;
    }

    /**
     * Evaluate targets for inclusion in the direct share area. May not be included
     * if score is too low.
     */
    public boolean addServiceResults(
            @Nullable DisplayResolveInfo origTarget,
            float origTargetScore,
            List<ChooserTarget> targets,
            boolean isShortcutResult,
            Map<ChooserTarget, ShortcutInfo> directShareToShortcutInfos,
            Map<ChooserTarget, AppTarget> directShareToAppTargets,
            Context userContext,
            Intent targetIntent,
            Intent referrerFillInIntent,
            int maxRankedTargets,
            List<TargetInfo> serviceTargets) {
        if (DEBUG) {
            Log.d(TAG, "addServiceResults "
                    + (origTarget == null ? null : origTarget.getResolvedComponentName()) + ", "
                    + targets.size()
                    + " targets");
        }
        if (targets.size() == 0) {
            return false;
        }
        Collections.sort(targets, mBaseTargetComparator);
        final int maxTargets = isShortcutResult ? mMaxShortcutTargetsPerApp
                : MAX_CHOOSER_TARGETS_PER_APP;
        final int targetsLimit = mApplySharingAppLimits ? Math.min(targets.size(), maxTargets)
                : targets.size();
        float lastScore = 0;
        boolean shouldNotify = false;
        for (int i = 0, count = targetsLimit; i < count; i++) {
            final ChooserTarget target = targets.get(i);
            float targetScore = target.getScore();
            if (mApplySharingAppLimits) {
                targetScore *= origTargetScore;
                if (i > 0 && targetScore >= lastScore) {
                    // Apply a decay so that the top app can't crowd out everything else.
                    // This incents ChooserTargetServices to define what's truly better.
                    targetScore = lastScore * 0.95f;
                }
            }
            ShortcutInfo shortcutInfo = isShortcutResult ? directShareToShortcutInfos.get(target)
                    : null;
            if ((shortcutInfo != null) && shortcutInfo.isPinned()) {
                targetScore += PINNED_SHORTCUT_TARGET_SCORE_BOOST;
            }
            ResolveInfo backupResolveInfo;
            Intent resolvedIntent;
            if (origTarget == null) {
                resolvedIntent = createResolvedIntentForCallerTarget(target, targetIntent);
                backupResolveInfo = userContext.getPackageManager()
                        .resolveActivity(
                                resolvedIntent,
                                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA));
            } else {
                resolvedIntent = origTarget.getResolvedIntent();
                backupResolveInfo = null;
            }
            boolean isInserted = insertServiceTarget(
                    SelectableTargetInfo.newSelectableTargetInfo(
                            origTarget,
                            backupResolveInfo,
                            resolvedIntent,
                            target,
                            targetScore,
                            shortcutInfo,
                            directShareToAppTargets.get(target),
                            referrerFillInIntent),
                    maxRankedTargets,
                    serviceTargets);

            shouldNotify |= isInserted;

            if (DEBUG) {
                Log.d(TAG, " => " + target + " score=" + targetScore
                        + " base=" + target.getScore()
                        + " lastScore=" + lastScore
                        + " baseScore=" + origTargetScore
                        + " applyAppLimit=" + mApplySharingAppLimits);
            }

            lastScore = targetScore;
        }

        return shouldNotify;
    }

    /**
     * Creates a resolved intent for a caller-specified target.
     * @param target, a caller-specified target.
     * @param targetIntent, a target intent for the Chooser (see {@link Intent#EXTRA_INTENT}).
     */
    private static Intent createResolvedIntentForCallerTarget(
            ChooserTarget target, Intent targetIntent) {
        final Intent resolvedIntent = new Intent(targetIntent);
        resolvedIntent.setComponent(target.getComponentName());
        resolvedIntent.putExtras(target.getIntentExtras());
        return resolvedIntent;
    }

    private boolean insertServiceTarget(
            TargetInfo chooserTargetInfo,
            int maxRankedTargets,
            List<TargetInfo> serviceTargets) {

        // Check for duplicates and abort if found
        for (TargetInfo otherTargetInfo : serviceTargets) {
            if (chooserTargetInfo.isSimilar(otherTargetInfo)) {
                return false;
            }
        }

        int currentSize = serviceTargets.size();
        final float newScore = chooserTargetInfo.getModifiedScore();
        for (int i = 0; i < Math.min(currentSize, maxRankedTargets);
                i++) {
            final TargetInfo serviceTarget = serviceTargets.get(i);
            if (serviceTarget == null) {
                serviceTargets.set(i, chooserTargetInfo);
                return true;
            } else if (newScore > serviceTarget.getModifiedScore()) {
                serviceTargets.add(i, chooserTargetInfo);
                return true;
            }
        }

        if (currentSize < maxRankedTargets) {
            serviceTargets.add(chooserTargetInfo);
            return true;
        }

        return false;
    }
}
