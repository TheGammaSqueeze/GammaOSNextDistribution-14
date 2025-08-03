/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.idea.validator;

import com.android.tools.idea.validator.ValidatorData.Issue;
import com.android.tools.idea.validator.ValidatorData.Level;
import com.android.tools.layoutlib.annotations.NotNull;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;

/**
 * Results of layout validation.
 */
public class ValidatorResult {

    @NotNull private final ImmutableBiMap<Long, View> mSrcMap;
    @NotNull private final ImmutableBiMap<Long, AccessibilityNodeInfo> mNodeInfoMap;
    @NotNull private final ArrayList<Issue> mIssues;
    @NotNull private final Metric mMetric;

    /**
     * Please use {@link Builder} for creating results.
     */
    private ValidatorResult(BiMap<Long, View> srcMap,
            BiMap<Long, AccessibilityNodeInfo> nodeInfoMap,
            ArrayList<Issue> issues,
            Metric metric) {
        mSrcMap = ImmutableBiMap.<Long, View>builder().putAll(srcMap).build();
        mNodeInfoMap = ImmutableBiMap.<Long, AccessibilityNodeInfo>builder()
                .putAll(nodeInfoMap)
                .build();
        mIssues = issues;
        mMetric = metric;
    }

    /**
     * @return the source map of all the Views.
     */
    public ImmutableBiMap<Long, View> getSrcMap() {
        return mSrcMap;
    }

    /**
     * @return the map from source ID to AccessibilityNodeInfo.
     */
    public ImmutableBiMap<Long, AccessibilityNodeInfo> getNodeInfoMap() {
        return mNodeInfoMap;
    }

    /**
     * @return list of issues.
     */
    public List<Issue> getIssues() {
        return mIssues;
    }

    /**
     * @return metric for validation.
     */
    public Metric getMetric() {
        return mMetric;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("Result containing ")
                .append(mIssues.size())
                .append(" issues:\n");

        for (Issue issue : mIssues) {
            if (issue.mLevel == Level.ERROR) {
                builder.append(" - [")
                        .append(issue.mLevel.name())
                        .append("] ")
                        .append(issue.mMsg)
                        .append("\n");
            }
        }
        return builder.toString();
    }

    public static class Builder {
        @NotNull public final BiMap<Long, View> mSrcMap = HashBiMap.create();
        @NotNull public final BiMap<Long, AccessibilityNodeInfo> mNodeInfoMap = HashBiMap.create();
        @NotNull public final ArrayList<Issue> mIssues = new ArrayList<>();
        @NotNull public final Metric mMetric = new Metric();

        public ValidatorResult build() {
            return new ValidatorResult(mSrcMap, mNodeInfoMap, mIssues, mMetric);
        }
    }

    /**
     * Contains metric specific data.
     */
    public static class Metric {
        /** Error message. If null no error was thrown. */
        public String mErrorMessage = null;

        /** Record how long hierarchy creation took */
        public long mHierarchyCreationMs = 0;

        /** Record how long generating results took */
        public long mGenerateResultsMs = 0;

        /** How many new memories (bytes) validator creates for images. */
        public long mImageMemoryBytes = 0;

        /** Debugging purpose only. Use it with {@link LayoutValidator#shouldSaveCroppedImages()} */
        public List<ImageSize> mImageSizes = new ArrayList<>();

        private long mHierarchyCreationTimeStart;

        private long mGenerateRulesTimeStart;

        private Metric() { }

        public void startHierarchyCreationTimer() {
            mHierarchyCreationTimeStart = System.currentTimeMillis();
        }

        public void recordHierarchyCreationTime() {
            mHierarchyCreationMs = System.currentTimeMillis() - mHierarchyCreationTimeStart;
        }

        public void startGenerateResultsTimer() {
            mGenerateRulesTimeStart = System.currentTimeMillis();
        }

        public void recordGenerateResultsTime() {
            mGenerateResultsMs = System.currentTimeMillis() - mGenerateRulesTimeStart;
        }

        @Override
        public String toString() {
            return "Validation result metric: { hierarchy creation=" + mHierarchyCreationMs
                    +"ms, image memory=" + readableBytes() + " }";
        }

        private String readableBytes() {
            if (mImageMemoryBytes > 1000000000) {
                return mImageMemoryBytes / 1000000000 + "gb";
            }
            else if (mImageMemoryBytes > 1000000) {
                return mImageMemoryBytes / 1000000 + "mb";
            }
            else if (mImageMemoryBytes > 1000) {
                return mImageMemoryBytes / 1000 + "kb";
            }
            return mImageMemoryBytes + "bytes";
        }
    }

    public static class ImageSize {
        private final int mLeft;
        private final int mTop;
        private final int mWidth;
        private final int mHeight;

        public ImageSize(int left, int top, int width, int height) {
            mLeft = left;
            mTop = top;
            mWidth = width;
            mHeight = height;
        }

        @Override
        public String toString() {
            return "ImageSize{" + "mLeft=" + mLeft + ", mTop=" + mTop + ", mWidth=" + mWidth +
                    ", mHeight=" + mHeight + '}';
        }
    }
}
