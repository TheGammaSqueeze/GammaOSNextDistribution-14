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

package com.android.permissioncontroller.permission.model.v31;

import static android.Manifest.permission_group.MICROPHONE;
import static android.app.AppOpsManager.OPSTR_PHONE_CALL_CAMERA;
import static android.app.AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE;

import static com.android.permissioncontroller.Constants.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.AppOpsManager.AttributedHistoricalOps;
import android.app.AppOpsManager.AttributedOpEntry;
import android.app.AppOpsManager.HistoricalOp;
import android.app.AppOpsManager.HistoricalPackageOps;
import android.app.AppOpsManager.OpEntry;
import android.app.AppOpsManager.OpEventProxyInfo;
import android.app.AppOpsManager.PackageOps;
import android.content.pm.Attribution;
import android.content.res.Resources;
import android.media.AudioRecordingConfiguration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.Permission;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kotlin.Triple;

/**
 * Stats for permission usage of an app. This data is for a given time period,
 * i.e. does not contain the full history.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class AppPermissionUsage {
    private final @NonNull List<GroupUsage> mGroupUsages = new ArrayList<>();
    private final @NonNull PermissionApp mPermissionApp;

    private static final int PRIVACY_HUB_FLAGS = AppOpsManager.OP_FLAG_SELF
            | AppOpsManager.OP_FLAG_TRUSTED_PROXIED | AppOpsManager.OP_FLAG_TRUSTED_PROXY;

    private AppPermissionUsage(@NonNull PermissionApp permissionApp,
            @NonNull List<AppPermissionGroup> groups, @Nullable PackageOps lastUsage,
            @Nullable HistoricalPackageOps historicalUsage,
            @Nullable ArrayList<AudioRecordingConfiguration> recordings) {
        mPermissionApp = permissionApp;
        final int groupCount = groups.size();
        for (int i = 0; i < groupCount; i++) {
            final AppPermissionGroup group = groups.get(i);

            /**
             * TODO: HACK HACK HACK.
             *
             * Exclude for the UIDs that are currently silenced. This happens if an app keeps
             * recording while in the background for more than a few seconds.
             */
            if (recordings != null && group.getName().equals(MICROPHONE)) {
                boolean isSilenced = false;
                int recordingsCount = recordings.size();
                for (int recordingNum = 0; recordingNum < recordingsCount; recordingNum++) {
                    AudioRecordingConfiguration recording = recordings.get(recordingNum);
                    if (recording.isClientSilenced()) {
                        isSilenced = true;
                        break;
                    }
                }

                if (isSilenced) {
                    continue;
                }
            }

            mGroupUsages.add(new GroupUsage(group, lastUsage, historicalUsage));
        }
    }

    public @NonNull PermissionApp getApp() {
        return mPermissionApp;
    }

    public @NonNull String getPackageName() {
        return mPermissionApp.getPackageName();
    }

    public int getUid() {
        return mPermissionApp.getUid();
    }

    public long getLastAccessTime() {
        long lastAccessTime = 0;
        final int permissionCount = mGroupUsages.size();
        for (int i = 0; i < permissionCount; i++) {
            final GroupUsage groupUsage = mGroupUsages.get(i);
            lastAccessTime = Math.max(lastAccessTime, groupUsage.getLastAccessTime());
        }
        return lastAccessTime;
    }

    public @NonNull List<GroupUsage> getGroupUsages() {
        return mGroupUsages;
    }

    /**
     * Stats for permission usage of a permission group. This data is for a
     * given time period, i.e. does not contain the full history.
     */
    public static class GroupUsage implements TimelineUsage {
        private final @NonNull AppPermissionGroup mGroup;
        private final @Nullable PackageOps mLastUsage;
        private final @Nullable HistoricalPackageOps mHistoricalUsage;

        public GroupUsage(@NonNull AppPermissionGroup group, @Nullable PackageOps lastUsage,
                @Nullable HistoricalPackageOps historicalUsage) {
            mGroup = group;
            mLastUsage = lastUsage;
            mHistoricalUsage = historicalUsage;
        }

        public long getLastAccessTime() {
            if (mLastUsage == null) {
                return 0;
            }

            return lastAccessAggregate((op) -> op.getLastAccessTime(PRIVACY_HUB_FLAGS));
        }

        /**
         * Get the access duration.
         */
        public long getAccessDuration() {
            if (mHistoricalUsage == null) {
                return 0;
            }
            return extractAggregate((HistoricalOp op) ->
                    op.getForegroundAccessDuration(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
                            + op.getBackgroundAccessDuration(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
            );
        }


        @Override
        public boolean hasDiscreteData() {
            if (mHistoricalUsage == null) {
                return false;
            }

            Set<String> allOps = getAllOps(mGroup);
            for (String opName : allOps) {
                final HistoricalOp historicalOp = mHistoricalUsage.getOp(opName);
                if (historicalOp != null && historicalOp.getDiscreteAccessCount() > 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<Triple<Long, Long, OpEventProxyInfo>> getAllDiscreteAccessTime() {
            List<Triple<Long, Long, OpEventProxyInfo>> allDiscreteAccessTime = new ArrayList<>();
            if (!hasDiscreteData()) {
                return allDiscreteAccessTime;
            }

            Set<String> allOps = getAllOps(mGroup);
            for (String opName : allOps) {
                final HistoricalOp historicalOp = mHistoricalUsage.getOp(opName);
                if (historicalOp == null) {
                    continue;
                }

                int discreteAccessCount = historicalOp.getDiscreteAccessCount();
                for (int j = 0; j < discreteAccessCount; j++) {
                    AppOpsManager.AttributedOpEntry opEntry = historicalOp.getDiscreteAccessAt(j);
                    allDiscreteAccessTime.add(new Triple<>(
                            opEntry.getLastAccessTime(PRIVACY_HUB_FLAGS),
                            opEntry.getLastDuration(PRIVACY_HUB_FLAGS),
                            opEntry.getLastProxyInfo(PRIVACY_HUB_FLAGS)));
                }
            }

            return allDiscreteAccessTime;
        }

        public boolean isRunning() {
            if (mLastUsage == null) {
                return false;
            }

            Set<String> allOps = getAllOps(mGroup);
            final List<OpEntry> ops = mLastUsage.getOps();
            final int opCount = ops.size();
            for (int j = 0; j < opCount; j++) {
                final OpEntry op = ops.get(j);
                if (allOps.contains(op.getOpStr()) && op.isRunning()) {
                    return true;
                }
            }

            return false;
        }

        private long extractAggregate(@NonNull Function<HistoricalOp, Long> extractor) {
            long aggregate = 0;

            Set<String> allOps = getAllOps(mGroup);
            for (String opName : allOps) {
                final HistoricalOp historicalOp = mHistoricalUsage.getOp(opName);
                if (historicalOp != null) {
                    aggregate += extractor.apply(historicalOp);
                }
            }

            return aggregate;
        }

        private long lastAccessAggregate(@NonNull Function<OpEntry, Long> extractor) {
            long aggregate = 0;

            Set<String> allOps = getAllOps(mGroup);
            final List<OpEntry> ops = mLastUsage.getOps();
            final int opCount = ops.size();

            for (int opNum = 0; opNum < opCount; opNum++) {
                final OpEntry op = ops.get(opNum);
                if (allOps.contains(op.getOpStr())) {
                    aggregate = Math.max(aggregate, extractor.apply(op));
                }
            }

            return aggregate;
        }

        private static Set<String> getAllOps(AppPermissionGroup appPermissionGroup) {
            Set<String> allOps = new HashSet<>();
            List<Permission> permissions = appPermissionGroup.getPermissions();
            final int permissionCount = permissions.size();
            for (int permissionNum = 0; permissionNum < permissionCount; permissionNum++) {
                final Permission permission = permissions.get(permissionNum);
                final String opName = permission.getAppOp();
                if (opName != null) {
                    allOps.add(opName);
                }
            }

            if (appPermissionGroup.getName().equals(Manifest.permission_group.MICROPHONE)) {
                allOps.add(OPSTR_PHONE_CALL_MICROPHONE);
                if (SdkLevel.isAtLeastT()) {
                    allOps.add(OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO);
                }
            }

            if (appPermissionGroup.getName().equals(Manifest.permission_group.CAMERA)) {
                allOps.add(OPSTR_PHONE_CALL_CAMERA);
            }

            return allOps;
        }

        @Override
        public @NonNull AppPermissionGroup getGroup() {
            return mGroup;
        }

        @Override
        public int getLabel() {
            return Resources.ID_NULL;
        }

        @Override
        public @Nullable ArrayList<String> getAttributionTags() {
            if (mHistoricalUsage == null || mHistoricalUsage.getAttributedOpsCount() == 0) {
                return null;
            }
            ArrayList<String> attributionTags = new ArrayList<>();
            int count = mHistoricalUsage.getAttributedOpsCount();
            for (int i = 0; i < count; i++) {
                attributionTags.add(mHistoricalUsage.getAttributedOpsAt(i).getTag());
            }
            return attributionTags;
        }

        /** Creates a lookup from the attribution tag to its label. **/
        @RequiresApi(Build.VERSION_CODES.S)
        private static Map<String, Integer> getAttributionTagToLabelMap(
                Attribution[] attributions) {
            Map<String, Integer> attributionTagToLabelMap = new HashMap<>();
            for (Attribution attribution : attributions) {
                attributionTagToLabelMap.put(attribution.getTag(), attribution.getLabel());
            }
            return attributionTagToLabelMap;
        }

        /** Partitions the usages based on the attribution tag label. */
        @RequiresApi(Build.VERSION_CODES.S)
        public List<AttributionLabelledGroupUsage> getAttributionLabelledGroupUsages() {
            if (mHistoricalUsage == null || mHistoricalUsage.getAttributedOpsCount() == 0) {
                return new ArrayList<AttributionLabelledGroupUsage>();
            }
            Map<String, Integer> attributionTagToLabelMap =
                    getAttributionTagToLabelMap(getGroup().getApp().attributions);

            Set<String> allOps = getAllOps(mGroup);

            // we need to collect discreteAccessTime for each label
            Map<Integer, AttributionLabelledGroupUsage.Builder> labelDiscreteAccessMap =
                    new HashMap<>();

            for (int i = 0; i < mHistoricalUsage.getAttributedOpsCount(); i++) {
                AttributedHistoricalOps attributedOp = mHistoricalUsage.getAttributedOpsAt(i);
                String attributionTag = attributedOp.getTag();

                for (String opName : allOps) {
                    final HistoricalOp historicalOp = attributedOp.getOp(opName);
                    if (historicalOp == null) {
                        continue;
                    }

                    int discreteAccessCount = historicalOp.getDiscreteAccessCount();
                    for (int j = 0; j < discreteAccessCount; j++) {
                        AttributedOpEntry opEntry = historicalOp.getDiscreteAccessAt(j);
                        Integer label = attributionTagToLabelMap.get(attributedOp.getTag());
                        if (label == null) {
                            label = Resources.ID_NULL;
                        }
                        if (!labelDiscreteAccessMap.containsKey(label)) {
                            labelDiscreteAccessMap.put(label,
                                    new AttributionLabelledGroupUsage.Builder(label, getGroup()));
                        }
                        labelDiscreteAccessMap.get(label).addAttributionTag(attributionTag);
                        labelDiscreteAccessMap.get(label).addDiscreteAccessTime(new Triple<>(
                                opEntry.getLastAccessTime(PRIVACY_HUB_FLAGS),
                                opEntry.getLastDuration(PRIVACY_HUB_FLAGS),
                                opEntry.getLastProxyInfo(PRIVACY_HUB_FLAGS)));
                    }
                }
            }

            return labelDiscreteAccessMap.entrySet().stream()
                    .map(e -> e.getValue().build())
                    .collect(Collectors.toList());
        }

        /**
         * Represents the slice of {@link GroupUsage} with a label.
         *
         * <p> {@link Resources#ID_NULL} as label means that there was no entry for the
         * attribution tag in the manifest.</p>
         */
        public static class AttributionLabelledGroupUsage implements TimelineUsage {
            private final int mLabel;
            private final AppPermissionGroup mAppPermissionGroup;
            private final List<String> mAttributionTags;
            private final List<Triple<Long, Long, OpEventProxyInfo>> mDiscreteAccessTime;

            AttributionLabelledGroupUsage(int label,
                    AppPermissionGroup appPermissionGroup,
                    List<String> attributionTags,
                    List<Triple<Long, Long, OpEventProxyInfo>> discreteAccessTime) {
                mLabel = label;
                mAppPermissionGroup = appPermissionGroup;
                mAttributionTags = attributionTags;
                mDiscreteAccessTime = discreteAccessTime;
            }

            @Override
            public int getLabel() {
                return mLabel;
            }

            @Override
            public boolean hasDiscreteData() {
                return mDiscreteAccessTime.size() > 0;
            }

            @Override
            public List<Triple<Long, Long, OpEventProxyInfo>> getAllDiscreteAccessTime() {
                return mDiscreteAccessTime;
            }

            @Override
            public List<String> getAttributionTags() {
                return mAttributionTags;
            }

            @Override
            public AppPermissionGroup getGroup() {
                return mAppPermissionGroup;
            }

            static class Builder {
                private final int mLabel;
                private final AppPermissionGroup mAppPermissionGroup;
                private Set<String> mAttributionTags;
                private List<Triple<Long, Long, OpEventProxyInfo>>  mDiscreteAccessTime;

                Builder(int label, AppPermissionGroup appPermissionGroup) {
                    mLabel = label;
                    mAppPermissionGroup = appPermissionGroup;
                    mAttributionTags = new HashSet<>();
                    mDiscreteAccessTime = new ArrayList<>();
                }

                @NonNull Builder addAttributionTag(String attributionTag) {
                    mAttributionTags.add(attributionTag);
                    return this;
                }

                @NonNull
                Builder addDiscreteAccessTime(
                        Triple<Long, Long, OpEventProxyInfo> discreteAccessTime) {
                    mDiscreteAccessTime.add(discreteAccessTime);
                    return this;
                }

                AttributionLabelledGroupUsage build() {
                    ArrayList<String> attributionTagsList = new ArrayList<>();
                    attributionTagsList.addAll(mAttributionTags);
                    return new AttributionLabelledGroupUsage(mLabel,
                            mAppPermissionGroup,
                            attributionTagsList,
                            mDiscreteAccessTime);
                }
            }
        }
    }

    public static class Builder {
        private final @NonNull List<AppPermissionGroup> mGroups = new ArrayList<>();
        private final @NonNull PermissionApp mPermissionApp;
        private @Nullable PackageOps mLastUsage;
        private @Nullable HistoricalPackageOps mHistoricalUsage;
        private @Nullable ArrayList<AudioRecordingConfiguration> mAudioRecordingConfigurations;

        public Builder(@NonNull PermissionApp permissionApp) {
            mPermissionApp = permissionApp;
        }

        public @NonNull Builder addGroup(@NonNull AppPermissionGroup group) {
            mGroups.add(group);
            return this;
        }

        public @NonNull Builder setLastUsage(@Nullable PackageOps lastUsage) {
            mLastUsage = lastUsage;
            return this;
        }

        public @NonNull Builder setHistoricalUsage(@Nullable HistoricalPackageOps historicalUsage) {
            mHistoricalUsage = historicalUsage;
            return this;
        }

        public @NonNull Builder setRecordingConfiguration(
                @Nullable ArrayList<AudioRecordingConfiguration> recordings) {
            mAudioRecordingConfigurations = recordings;
            return this;
        }

        public @NonNull AppPermissionUsage build() {
            if (mGroups.isEmpty()) {
                throw new IllegalStateException("mGroups cannot be empty.");
            }
            return new AppPermissionUsage(mPermissionApp, mGroups, mLastUsage, mHistoricalUsage,
                    mAudioRecordingConfigurations);
        }
    }

    /** Usage for showing timeline view for a specific permission group with a label. */
    public interface TimelineUsage {
        /**
         * Returns whether the usage has discrete data.
         */
        boolean hasDiscreteData();

        /**
         * Returns all discrete access time in millis.
         * Returns a list of triples of (access time, access duration, proxy)
         */
        List<Triple<Long, Long, OpEventProxyInfo>> getAllDiscreteAccessTime();

        /**
         * Returns attribution tags for the usage.
         */
        List<String> getAttributionTags();

        /**
         * Returns the permission group of the usage.
         */
        AppPermissionGroup getGroup();

        /**
         * Returns the user facing string's resource id.
         *
         * <p> {@link Resources#ID_NULL} means show the app name otherwise get the string
         * resource from the app context.</p>
         */
        int getLabel();
    }
}
