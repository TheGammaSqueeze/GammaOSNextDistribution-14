/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;

import java.util.Objects;

public class LaunchItem implements Comparable<LaunchItem> {
    protected final Intent mIntent;
    private final Drawable mBanner;
    private final Drawable mIcon;
    private final CharSequence mLabel;
    private final long mPriority;

    LaunchItem(Context context, Intent intent, Drawable icon, CharSequence label) {
        mIntent = intent;
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PackageManager packageManager = context.getPackageManager();
        mBanner = null;
        mIcon = icon;
        mLabel = label;
        mPriority = 0;
    }

    LaunchItem(Context context, ResolveInfo info, long priority) {
        mIntent = Intent.makeMainActivity(
                new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PackageManager packageManager = context.getPackageManager();
        mBanner = info.activityInfo.loadBanner(packageManager);
        mIcon = info.loadIcon(packageManager);
        mLabel = info.loadLabel(packageManager);
        mPriority = priority;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public Drawable getBanner() {
        return mBanner;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    /**
     * Returns whether this item should appear identical to the given item.
     * @param another Item to compare to
     * @return True if items should appear identical
     */
    public boolean areContentsTheSame(LaunchItem another) {
        return Objects.equals(another.getBanner(), getBanner())
                && Objects.equals(another.getIcon(), getIcon())
                && Objects.equals(another.getLabel(), getLabel());
    }

    @Override
    public int compareTo(@NonNull LaunchItem another) {
        long priorityDiff = another.mPriority - mPriority;
        if (priorityDiff != 0) {
            return priorityDiff < 0L ? -1 : 1;
        } else {
            return getLabel().toString().compareTo(another.getLabel().toString());
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LaunchItem && Objects.equals(mIntent, ((LaunchItem)o).getIntent());
    }

    @Override
    public int hashCode() {
        return mIntent.hashCode();
    }

    @Override
    public String toString() {
        return mLabel + " -- " + mIntent.getComponent().toString();
    }

    public String toDebugString() {
        return "Label: " + mLabel
                + " Intent: " + mIntent
                + " Banner: " + mBanner
                + " Icon: " + mIcon
                + " Priority: " + Long.toString(mPriority);
    }
}
