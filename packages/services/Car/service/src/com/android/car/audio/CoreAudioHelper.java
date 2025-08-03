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

package com.android.car.audio;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.PRIVATE_CONSTRUCTOR;

import android.annotation.Nullable;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.util.Slogf;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.media.audiopolicy.AudioVolumeGroup;
import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.Objects;

/**
 * Helper for audio related operations for core audio routing and volume management
 * based on {@link AudioProductStrategy} and {@link AudioVolumeGroup}
 */
final class CoreAudioHelper {
    static final String TAG = "CoreAudioHelper";

    private static final boolean DEBUG = false;

    @ExcludeFromCodeCoverageGeneratedReport(reason = PRIVATE_CONSTRUCTOR)
    private CoreAudioHelper() {
        throw new UnsupportedOperationException("CoreAudioHelper class is non-instantiable, "
                + "contains static members only");
    }

    /** Invalid strategy id returned when none matches a given request. */
    static final int INVALID_STRATEGY = -1;

    /** Invalid group id returned when none matches a given request. */
    static final int INVALID_GROUP_ID = -1;

    /**
     * Default {@link AudioAttributes} used to identify the default {@link AudioProductStrategy}
     * and {@link AudioVolumeGroup}.
     */
    static final AudioAttributes DEFAULT_ATTRIBUTES = new AudioAttributes.Builder().build();

    /**
     * Due to testing issue with static mock, use lazy initialize pattern for static variables
     */
    private static List<AudioProductStrategy> getAudioProductStrategies() {
        return StaticLazyInitializer.sAudioProductStrategies;
    }
    private static List<AudioVolumeGroup> getAudioVolumeGroups() {
        return StaticLazyInitializer.sAudioVolumeGroups;
    }
    private static SparseArray<String> getGroupIdToNames() {
        return StaticLazyInitializer.sGroupIdToNames;
    }

    private static class StaticLazyInitializer {
        /**
         * @see AudioProductStrategy
         */
        static final List<AudioProductStrategy> sAudioProductStrategies =
                AudioManager.getAudioProductStrategies();
        /**
         * @see AudioVolumeGroup
         */
        static final List<AudioVolumeGroup> sAudioVolumeGroups =
                AudioManager.getAudioVolumeGroups();
        static final SparseArray<String> sGroupIdToNames = new SparseArray<>() {
            {
                for (int index = 0; index < sAudioVolumeGroups.size(); index++) {
                    AudioVolumeGroup group = sAudioVolumeGroups.get(index);
                    put(group.getId(), group.name());
                }
            }
        };
    }

    /**
     * Identifies the {@link AudioProductStrategy} supporting the given {@link AudioAttributes}.
     *
     * @param attributes {@link AudioAttributes} to look for.
     * @return the id of the {@link AudioProductStrategy} supporting the
     * given {@link AudioAttributes} if found, {@link #INVALID_STRATEGY} id otherwise.
     */
    public static int getStrategyForAudioAttributes(AudioAttributes attributes) {
        Preconditions.checkNotNull(attributes, "Audio Attributes can not be null");
        for (int index = 0; index < getAudioProductStrategies().size(); index++) {
            AudioProductStrategy strategy = getAudioProductStrategies().get(index);
            if (strategy.supportsAudioAttributes(attributes)) {
                return strategy.getId();
            }
        }
        return INVALID_STRATEGY;
    }

    public static int getStrategyForContextName(String contextName) {
        Preconditions.checkNotNull(contextName, "Context name can not be null");
        for (int index = 0; index < getAudioProductStrategies().size(); index++) {
            AudioProductStrategy strategy = getAudioProductStrategies().get(index);
            if (Objects.equals(strategy.getName(), contextName)) {
                return strategy.getId();
            }
        }
        return INVALID_STRATEGY;
    }

    /**
     * Identifies the {@link AudioProductStrategy} supporting the given {@link AudioAttributes}
     * and fallbacking on the default strategy supporting {@code DEFAULT_ATTRIBUTES} otherwise.
     *
     * @param attributes {@link AudioAttributes} supported by the
     * {@link AudioProductStrategy} to look for.
     * @return the id of the {@link AudioProductStrategy} supporting the
     * given {@link AudioAttributes}, otherwise the id of the default strategy, aka the
     * strategy supporting {@code DEFAULT_ATTRIBUTES}, {@code INVALID_STRATEGY} id otherwise.
     */
    public static int getStrategyForAudioAttributesOrDefault(AudioAttributes attributes) {
        int strategyId = getStrategyForAudioAttributes(attributes);
        return strategyId == INVALID_STRATEGY
                ? getStrategyForAudioAttributes(DEFAULT_ATTRIBUTES) : strategyId;
    }

    @Nullable
    static AudioProductStrategy getProductStrategyForAudioAttributes(
            AudioAttributes attributes) {
        Preconditions.checkNotNull(attributes, "Audio attributes can not be null");
        for (int index = 0; index < getAudioProductStrategies().size(); index++) {
            AudioProductStrategy strategy = getAudioProductStrategies().get(index);
            if (!strategy.supportsAudioAttributes(attributes)) {
                continue;
            }
            return strategy;
        }
        return null;
    }

    /**
     * Gets the {@link AudioProductStrategy} referred by its unique identifier.
     *
     * @param strategyId id of the {@link AudioProductStrategy} to look for
     * @return the {@link AudioProductStrategy} referred by the given id if found, {@code null}
     * otherwise.
     */
    @Nullable
    public static AudioProductStrategy getStrategy(int strategyId) {
        for (int index = 0; index < getAudioProductStrategies().size(); index++) {
            AudioProductStrategy strategy = getAudioProductStrategies().get(index);
            if (strategy.getId() == strategyId) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * Checks if the {@link AudioProductStrategy} referred by it id is the default.
     *
     * @param strategyId to look for
     * @return {@code true} if the {@link AudioProductStrategy} referred by
     * its id is the default, aka supports {@code DEFAULT_ATTRIBUTES}, {@code false} otherwise.
     */
    public static boolean isDefaultStrategy(int strategyId) {
        for (int index = 0; index < getAudioProductStrategies().size(); index++) {
            AudioProductStrategy strategy = getAudioProductStrategies().get(index);
            if (strategy.getId() == strategyId) {
                return strategy.supportsAudioAttributes(DEFAULT_ATTRIBUTES);
            }
        }
        return false;
    }

    /**
     * Gets the {@link AudioVolumeGroup} referred by it name.
     *
     * @param groupName name of the {@link AudioVolumeGroup} to look for.
     * @return the {@link AudioVolumeGroup} referred by the given id if found, {@code null}
     * otherwise.
     */
    @Nullable
    public static AudioVolumeGroup getVolumeGroup(String groupName) {
        for (int index = 0; index < getAudioVolumeGroups().size(); index++) {
            AudioVolumeGroup group = getAudioVolumeGroups().get(index);
            if (DEBUG) {
                Slogf.d(TAG, "requested %s has %s,", groupName, group);
            }
            if (group.name().equals(groupName)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Gets the most representative {@link AudioAttributes} of a given {@link AudioVolumeGroup}
     * referred by it s name.
     * <p>When relying on core audio to control volume, Volume APIs are based on AudioAttributes,
     * thus, selecting the most representative attributes (not default without tag, with tag as
     * fallback, {@link #DEFAULT_ATTRIBUTES} otherwise) will help identify the request.
     *
     * @param groupName name of the {@link AudioVolumeGroup} to look for.
     * @return the best {@link AudioAttributes} for a given volume group id,
     * {@link #DEFAULT_ATTRIBUTES} otherwise.
     */
    public static AudioAttributes selectAttributesForVolumeGroupName(String groupName) {
        AudioVolumeGroup group = getVolumeGroup(groupName);
        AudioAttributes bestAttributes = DEFAULT_ATTRIBUTES;
        if (group == null) {
            return bestAttributes;
        }
        for (int index = 0; index < group.getAudioAttributes().size(); index++) {
            AudioAttributes attributes = group.getAudioAttributes().get(index);
            // bestAttributes attributes are not default and without tag (most generic as possible)
            if (!attributes.equals(DEFAULT_ATTRIBUTES)) {
                bestAttributes = attributes;
                if (Objects.equals(AudioManagerHelper.getFormattedTags(attributes), "")) {
                    break;
                }
            }
        }
        return bestAttributes;
    }

    /**
     * Gets the name of the {@link AudioVolumeGroup} supporting given {@link AudioAttributes},
     * {@code null} is returned if none is found.
     *
     * @param attributes {@link AudioAttributes} supported by the group to look for.
     *
     * @return the name of the {@link AudioVolumeGroup} supporting the given audio attributes,
     * {@code null} otherwise.
     */
    @Nullable
    public static String getVolumeGroupNameForAudioAttributes(AudioAttributes attributes) {
        Preconditions.checkNotNull(attributes, "Audio Attributes can not be null");
        int volumeGroupId = getVolumeGroupIdForAudioAttributes(attributes);
        return volumeGroupId != AudioVolumeGroup.DEFAULT_VOLUME_GROUP
                ? getVolumeGroupNameFromCoreId(volumeGroupId) : null;
    }

    /**
     * Gets the name of the {@link AudioVolumeGroup} referred by its id.
     *
     * @param coreGroupId id of the volume group to look for.
     * @return the volume group id referred by its name if found, throws an exception otherwise.
     */
    @Nullable
    public static String getVolumeGroupNameFromCoreId(int coreGroupId) {
        return getGroupIdToNames().get(coreGroupId);
    }

    /**
     * Gets the {@link AudioVolumeGroup} id associated to the given {@link AudioAttributes}.
     *
     * @param attributes {@link AudioAttributes} to be considered
     * @return the id of the {@link AudioVolumeGroup} supporting the given {@link AudioAttributes}
     * if found, {@link #INVALID_GROUP_ID} otherwise.
     */
    public static int getVolumeGroupIdForAudioAttributes(AudioAttributes attributes) {
        Preconditions.checkNotNull(attributes, "Audio Attributes can not be null");
        for (int index = 0; index < getAudioProductStrategies().size(); index++) {
            AudioProductStrategy strategy = getAudioProductStrategies().get(index);
            int volumeGroupId =
                    AudioManagerHelper.getVolumeGroupIdForAudioAttributes(strategy, attributes);
            Slogf.d(TAG, "getVolumeGroupIdForAudioAttributes %s %s,", volumeGroupId, strategy);
            if (volumeGroupId != AudioVolumeGroup.DEFAULT_VOLUME_GROUP) {
                return volumeGroupId;
            }
        }
        return INVALID_GROUP_ID;
    }
}
