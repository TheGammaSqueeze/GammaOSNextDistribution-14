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

package com.android.safetycenter.internaldata;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.os.Bundle;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterEntryGroup;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetyCenterStaticEntry;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;

/** A class to facilitate working with Safety Center {@link Bundle}s. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterBundles {

    private SafetyCenterBundles() {}

    /**
     * A key used in {@link SafetyCenterData#getExtras()} that returns a {@link Bundle} mapping
     * {@link SafetyCenterIssue} ids to their associated {@link SafetyCenterEntryGroup} ids.
     */
    public static final String ISSUES_TO_GROUPS_BUNDLE_KEY = "IssuesToGroups";

    /**
     * A key used in {@link SafetyCenterData#getExtras()} that returns a {@link Bundle} mapping
     * {@link SafetyCenterStaticEntry} to their associated {@link SafetyCenterEntryId}.
     *
     * @see #getStaticEntryId(SafetyCenterData, SafetyCenterStaticEntry)
     * @see #toBundleKey(SafetyCenterStaticEntry)
     */
    public static final String STATIC_ENTRIES_TO_IDS_BUNDLE_KEY = "StaticEntriesToIds";

    /**
     * Returns the {@link SafetyCenterEntryId} associated with a {@link SafetyCenterStaticEntry}
     * using the {@link SafetyCenterData#getExtras()} {@link Bundle}.
     *
     * <p>Returns {@code null} if the {@link SafetyCenterEntryId} couldn't be retrieved.
     *
     * <p>This is a hack to workaround the fact that {@link SafetyCenterStaticEntry} doesn't expose
     * an associated ID.
     */
    @Nullable
    public static SafetyCenterEntryId getStaticEntryId(
            SafetyCenterData safetyCenterData, SafetyCenterStaticEntry safetyCenterStaticEntry) {
        if (!SdkLevel.isAtLeastU()) {
            return null;
        }
        Bundle staticEntriesToIds =
                safetyCenterData.getExtras().getBundle(STATIC_ENTRIES_TO_IDS_BUNDLE_KEY);
        if (staticEntriesToIds == null) {
            return null;
        }
        String entryIdString = staticEntriesToIds.getString(toBundleKey(safetyCenterStaticEntry));
        if (entryIdString == null) {
            return null;
        }
        return SafetyCenterIds.entryIdFromString(entryIdString);
    }

    /**
     * Returns a {@code String} that uniquely identifies the {@link SafetyCenterStaticEntry} in the
     * {@link SafetyCenterData#getExtras()} {@link Bundle}.
     *
     * <p>This key is generated based on the {@link SafetyCenterStaticEntry} content. This comes
     * with the restriction that two separate {@link SafetyCenterStaticEntry} returned by Safety
     * Center cannot be equal, as they would otherwise collide in the {@link Bundle}.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    public static String toBundleKey(SafetyCenterStaticEntry safetyCenterStaticEntry) {
        SafetyCenterStaticEntryBundleKey.Builder keyBuilder =
                SafetyCenterStaticEntryBundleKey.newBuilder()
                        .setTitle(safetyCenterStaticEntry.getTitle().toString());
        CharSequence summary = safetyCenterStaticEntry.getSummary();
        if (summary != null) {
            keyBuilder.setSummary(summary.toString());
        }
        return SafetyCenterIds.encodeToString(keyBuilder.build());
    }
}
