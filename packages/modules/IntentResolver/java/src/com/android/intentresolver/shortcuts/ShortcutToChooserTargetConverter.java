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

package com.android.intentresolver.shortcuts;

import android.app.prediction.AppTarget;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class ShortcutToChooserTargetConverter {

    /**
     * Converts a list of ShareShortcutInfos to ChooserTargets.
     * @param matchingShortcuts List of shortcuts, all from the same package, that match the current
     *                         share intent filter.
     * @param allShortcuts List of all the shortcuts from all the packages on the device that are
     *                    returned for the current sharing action.
     * @param allAppTargets List of AppTargets. Null if the results are not from prediction service.
     * @param directShareAppTargetCache An optional map to store mapping for the new ChooserTarget
     *  instances back to original allAppTargets.
     * @param directShareShortcutInfoCache An optional map to store mapping from the new
     *  ChooserTarget instances back to the original matchingShortcuts' {@code getShortcutInfo()}
     * @return A list of ChooserTargets sorted by score in descending order.
     */
    @NonNull
    public List<ChooserTarget> convertToChooserTarget(
            @NonNull List<ShortcutManager.ShareShortcutInfo> matchingShortcuts,
            @NonNull List<ShortcutManager.ShareShortcutInfo> allShortcuts,
            @Nullable List<AppTarget> allAppTargets,
            @Nullable Map<ChooserTarget, AppTarget> directShareAppTargetCache,
            @Nullable Map<ChooserTarget, ShortcutInfo> directShareShortcutInfoCache) {
        // If |appTargets| is not null, results are from AppPredictionService and already sorted.
        final boolean isFromAppPredictor = allAppTargets != null;
        // A set of distinct scores for the matched shortcuts. We use index of a rank in the sorted
        // list instead of the actual rank value when converting a rank to a score.
        List<Integer> scoreList = new ArrayList<>();
        if (!isFromAppPredictor) {
            for (int i = 0; i < matchingShortcuts.size(); i++) {
                int shortcutRank = matchingShortcuts.get(i).getShortcutInfo().getRank();
                if (!scoreList.contains(shortcutRank)) {
                    scoreList.add(shortcutRank);
                }
            }
            Collections.sort(scoreList);
        }

        List<ChooserTarget> chooserTargetList = new ArrayList<>(matchingShortcuts.size());
        for (int i = 0; i < matchingShortcuts.size(); i++) {
            ShortcutInfo shortcutInfo = matchingShortcuts.get(i).getShortcutInfo();
            int indexInAllShortcuts = allShortcuts.indexOf(matchingShortcuts.get(i));

            float score;
            if (isFromAppPredictor) {
                // Incoming results are ordered. Create a score based on index in the original list.
                score = Math.max(1.0f - (0.01f * indexInAllShortcuts), 0.0f);
            } else {
                // Create a score based on the rank of the shortcut.
                int rankIndex = scoreList.indexOf(shortcutInfo.getRank());
                score = Math.max(1.0f - (0.01f * rankIndex), 0.0f);
            }

            Bundle extras = new Bundle();
            extras.putString(Intent.EXTRA_SHORTCUT_ID, shortcutInfo.getId());

            ChooserTarget chooserTarget = new ChooserTarget(
                    shortcutInfo.getLabel(),
                    null, // Icon will be loaded later if this target is selected to be shown.
                    score, matchingShortcuts.get(i).getTargetComponent().clone(), extras);

            chooserTargetList.add(chooserTarget);
            if (directShareAppTargetCache != null && allAppTargets != null) {
                directShareAppTargetCache.put(chooserTarget,
                        allAppTargets.get(indexInAllShortcuts));
            }
            if (directShareShortcutInfoCache != null) {
                directShareShortcutInfoCache.put(chooserTarget, shortcutInfo);
            }
        }
        // Sort ChooserTargets by score in descending order
        Comparator<ChooserTarget> byScore =
                (ChooserTarget a, ChooserTarget b) -> -Float.compare(a.getScore(), b.getScore());
        Collections.sort(chooserTargetList, byScore);
        return chooserTargetList;
    }
}
