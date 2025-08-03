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

package com.android.intentresolver;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import com.android.intentresolver.chooser.TargetInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Record type to store all resolutions that are deduped to a single target component, along with
 * other metadata about the component (which applies to all of the resolutions in the record).
 * This record is assembled when we're first processing resolutions, and then later it's used to
 * derive the {@link TargetInfo} record(s) that specify how the resolutions will be presented as
 * targets in the UI.
 */
public final class ResolvedComponentInfo {
    public final ComponentName name;
    private final List<Intent> mIntents = new ArrayList<>();
    private final List<ResolveInfo> mResolveInfos = new ArrayList<>();
    private boolean mPinned;

    /**
     * @param name the name of the component that owns all the resolutions added to this record.
     * @param intent an initial {@link Intent} to add to this record
     * @param info the {@link ResolveInfo} associated with the given {@code intent}.
     */
    public ResolvedComponentInfo(ComponentName name, Intent intent, ResolveInfo info) {
        this.name = name;
        add(intent, info);
    }

    /**
     * Add an {@link Intent} and associated {@link ResolveInfo} as resolutions for this component.
     */
    public void add(Intent intent, ResolveInfo info) {
        mIntents.add(intent);
        mResolveInfos.add(info);
    }

    /** @return the number of {@link Intent}/{@link ResolveInfo} pairs added to this record. */
    public int getCount() {
        return mIntents.size();
    }

    /** @return the {@link Intent} at the specified {@code index}, if any, or else null. */
    public Intent getIntentAt(int index) {
        return (index >= 0) ? mIntents.get(index) : null;
    }

    /** @return the {@link ResolveInfo} at the specified {@code index}, if any, or else null. */
    public ResolveInfo getResolveInfoAt(int index) {
        return (index >= 0) ? mResolveInfos.get(index) : null;
    }

    /**
     * @return the index of the provided {@link Intent} among those that have been added to this
     * {@link ResolvedComponentInfo}, or -1 if it has't been added.
     */
    public int findIntent(Intent intent) {
        return mIntents.indexOf(intent);
    }

    /**
     * @return the index of the provided {@link ResolveInfo} among those that have been added to
     * this {@link ResolvedComponentInfo}, or -1 if it has't been added.
     */
    public int findResolveInfo(ResolveInfo info) {
        return mResolveInfos.indexOf(info);
    }

    /**
     * @return whether this component was pinned by a call to {@link #setPinned}.
     * TODO: consolidate sources of pinning data and/or document how this differs from other places
     * we make a "pinning" determination.
     */
    public boolean isPinned() {
        return mPinned;
    }

    /**
     * Set whether this component will be considered pinned in future calls to {@link #isPinned()}.
     * TODO: consolidate sources of pinning data and/or document how this differs from other places
     * we make a "pinning" determination.
     */
    public void setPinned(boolean pinned) {
        mPinned = pinned;
    }
}
