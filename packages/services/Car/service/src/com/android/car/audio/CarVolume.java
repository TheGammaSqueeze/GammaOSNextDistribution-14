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

package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.telephony.TelephonyManager.CALL_STATE_OFFHOOK;
import static android.telephony.TelephonyManager.CALL_STATE_RINGING;

import static com.android.car.audio.CarAudioService.CAR_DEFAULT_AUDIO_ATTRIBUTE;
import static com.android.car.audio.CarAudioService.SystemClockWrapper;
import static com.android.car.audio.CarAudioUtils.hasExpired;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.media.AudioAttributes;
import android.media.AudioPlaybackConfiguration;
import android.util.ArraySet;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.car.audio.CarAudioDumpProto.CarAudioContextInfoProto;
import com.android.car.audio.CarAudioDumpProto.CarVolumeProto;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * CarVolume is responsible for determining which audio contexts to prioritize when adjusting volume
 */
final class CarVolume {
    private static final String TAG = CarLog.tagFor(CarVolume.class);
    private static final int CONTEXT_HIGHEST_PRIORITY = 0;
    private static final int CONTEXT_NOT_PRIORITIZED = -1;

    static final int VERSION_ONE = 1;
    private static final List<AudioAttributes> AUDIO_ATTRIBUTE_VOLUME_PRIORITY_V1 = List.of(
            // CarAudioContext.getInvalidContext() is intentionally not prioritized
            // as it is not routed by CarAudioService and is not expected to be used.
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANT),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_SONIFICATION),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_VEHICLE_STATUS),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY)
    );

    static final int VERSION_TWO = 2;
    private static final List<AudioAttributes> AUDIO_ATTRIBUTE_VOLUME_PRIORITY_V2 = List.of(
            CarAudioContext.getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ANNOUNCEMENT),
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANT)
    );

    private final SparseIntArray mVolumePriorityByAudioContext = new SparseIntArray();
    private final SystemClockWrapper mClock;
    private final Object mLock = new Object();
    private final int mVolumeKeyEventTimeoutMs;
    private final int mLowestPriority;
    private final CarAudioContext mCarAudioContext;
    private final int mAudioVolumeAdjustmentContextsVersion;
    @GuardedBy("mLock")
    @AudioContext private int mLastActiveContext;
    @GuardedBy("mLock")
    private long mLastActiveContextStartTime;

    /**
     * Creates car volume for management of volume priority and last selected audio context.
     *
     * @param carAudioContext car audio context for the logical grouping of audio usages
     * @param clockWrapper time keeper for expiration of last selected context.
     * @param audioVolumeAdjustmentContextsVersion audio priority list version number, can be
     *      any version defined in {@link CarVolumeListVersion}
     * @param volumeKeyEventTimeoutMs timeout in ms used to measure expiration of last selected
     *      context
     */
    CarVolume(CarAudioContext carAudioContext, SystemClockWrapper clockWrapper,
            @CarVolumeListVersion int audioVolumeAdjustmentContextsVersion,
            int volumeKeyEventTimeoutMs) {
        mCarAudioContext = Objects.requireNonNull(carAudioContext,
                "Car audio context must not be null");
        mClock = Objects.requireNonNull(clockWrapper, "Clock must not be null.");
        mVolumeKeyEventTimeoutMs = Preconditions.checkArgumentNonnegative(volumeKeyEventTimeoutMs);
        mLastActiveContext = CarAudioContext.getInvalidContext();
        mLastActiveContextStartTime = mClock.uptimeMillis();
        @AudioContext int[] contextVolumePriority =
                getContextPriorityList(audioVolumeAdjustmentContextsVersion);

        for (int priority = CONTEXT_HIGHEST_PRIORITY;
                priority < contextVolumePriority.length; priority++) {
            mVolumePriorityByAudioContext.append(contextVolumePriority[priority], priority);
        }

        mLowestPriority = CONTEXT_HIGHEST_PRIORITY + mVolumePriorityByAudioContext.size();
        mAudioVolumeAdjustmentContextsVersion = audioVolumeAdjustmentContextsVersion;

    }

    private int[] getContextPriorityList(int audioVolumeAdjustmentContextsVersion) {
        Preconditions.checkArgumentInRange(audioVolumeAdjustmentContextsVersion, 1, 2,
                "audioVolumeAdjustmentContextsVersion");
        if (audioVolumeAdjustmentContextsVersion == VERSION_TWO) {
            return convertAttributesToContexts(AUDIO_ATTRIBUTE_VOLUME_PRIORITY_V2);
        }
        return convertAttributesToContexts(AUDIO_ATTRIBUTE_VOLUME_PRIORITY_V1);
    }

    private int[] convertAttributesToContexts(List<AudioAttributes> audioAttributesPriorities) {
        ArraySet<Integer> contexts = new ArraySet<>();
        List<Integer> contextByPriority = new ArrayList<>();
        for (int index = 0; index < audioAttributesPriorities.size(); index++) {
            int context = mCarAudioContext.getContextForAudioAttribute(
                    audioAttributesPriorities.get(index));
            if (contexts.contains(context)) {
                // Audio attribute was already group into another context,
                // use the higher priority if so.
                continue;
            }
            contexts.add(context);
            contextByPriority.add(context);
        }

        return CarServiceUtils.toIntArray(contextByPriority);
    }

    /**
     * @see CarAudioService#resetSelectedVolumeContext()
     */
    public void resetSelectedVolumeContext() {
        setAudioContextStillActive(CarAudioContext.getInvalidContext());
    }

    /**
     * Finds an active {@link AudioContext} that should be adjusted based on the current
     * {@link AudioPlaybackConfiguration}s,
     * {@code callState} (can be {@code CALL_STATE_OFFHOOK}, {@code CALL_STATE_RINGING}
     * or {@code CALL_STATE_IDLE}). {@code callState} is used to determined if the call context
     * or phone ringer context are active.
     *
     * <p> Note that if an active context is found it be will saved and retrieved later on.
     */
    @AudioContext int getSuggestedAudioContextAndSaveIfFound(
            List<AudioAttributes> activePlaybackAttributes, int callState,
            List<AudioAttributes> activeHalAttributes) {

        int activeContext = getAudioContextStillActive();
        if (!CarAudioContext.isInvalidContextId(activeContext)) {
            setAudioContextStillActive(activeContext);
            return activeContext;
        }

        ArraySet<AudioAttributes> activeAttributes =
                getActiveAttributes(activePlaybackAttributes, callState, activeHalAttributes);

        @AudioContext int context = findActiveContextWithHighestPriority(activeAttributes,
                        mVolumePriorityByAudioContext);

        setAudioContextStillActive(context);

        return context;
    }

    private @AudioContext int findActiveContextWithHighestPriority(
            ArraySet<AudioAttributes> activeAttributes, SparseIntArray contextPriorities) {
        int currentContext = mCarAudioContext.getContextForAttributes(
                CAR_DEFAULT_AUDIO_ATTRIBUTE);
        int currentPriority = mLowestPriority;

        for (int index = 0; index < activeAttributes.size(); index++) {
            @AudioContext int context = mCarAudioContext.getContextForAudioAttribute(
                    activeAttributes.valueAt(index));
            int priority = contextPriorities.get(context, CONTEXT_NOT_PRIORITIZED);
            if (priority == CONTEXT_NOT_PRIORITIZED) {
                continue;
            }

            if (priority < currentPriority) {
                currentContext = context;
                currentPriority = priority;
                // If the highest priority has been found, break early.
                if (currentPriority == CONTEXT_HIGHEST_PRIORITY) {
                    break;
                }
            }
        }

        return currentContext;
    }

    private void setAudioContextStillActive(@AudioContext int context) {
        synchronized (mLock) {
            mLastActiveContext = context;
            mLastActiveContextStartTime = mClock.uptimeMillis();
        }
    }

    boolean isAnyContextActive(@AudioContext int [] contexts,
            List<AudioAttributes> activePlaybackContext, int callState,
            List<AudioAttributes> activeHalAudioAttributes) {
        Objects.requireNonNull(contexts, "Contexts can not be null");
        Preconditions.checkArgument(contexts.length != 0, "Contexts can not be empty");
        Objects.requireNonNull(activeHalAudioAttributes, "Audio attributes can not be null");

        ArraySet<AudioAttributes> activeAttributes = getActiveAttributes(activePlaybackContext,
                callState, activeHalAudioAttributes);

        Set<Integer> activeContexts = new ArraySet<>(activeAttributes.size());

        for (int index = 0; index < activeAttributes.size(); index++) {
            activeContexts.add(mCarAudioContext
                    .getContextForAttributes(activeAttributes.valueAt(index)));
        }

        for (int index = 0; index < contexts.length; index++) {
            if (activeContexts.contains(contexts[index])) {
                return true;
            }
        }

        return false;
    }

    private static ArraySet<AudioAttributes> getActiveAttributes(
            List<AudioAttributes> activeAttributes, int callState,
            List<AudioAttributes> activeHalAudioAttributes) {
        Objects.requireNonNull(activeAttributes, "Playback audio attributes can not be null");
        Objects.requireNonNull(activeHalAudioAttributes, "Active HAL contexts can not be null");

        ArraySet<AudioAttributes> attributes = new ArraySet<>(activeHalAudioAttributes);

        switch (callState) {
            case CALL_STATE_RINGING:
                attributes.add(CarAudioContext
                        .getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE));
                break;
            case CALL_STATE_OFFHOOK:
                attributes.add(CarAudioContext
                        .getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION));
                break;
            default:
                break;
        }

        attributes.addAll(activeAttributes);
        return attributes;
    }

    private @AudioContext int getAudioContextStillActive() {
        @AudioContext int context;
        long contextStartTime;
        synchronized (mLock) {
            context = mLastActiveContext;
            contextStartTime = mLastActiveContextStartTime;
        }

        if (CarAudioContext.isInvalidContextId(context)) {
            return CarAudioContext.getInvalidContext();
        }

        if (hasExpired(contextStartTime, mClock.uptimeMillis(), mVolumeKeyEventTimeoutMs)) {
            return CarAudioContext.getInvalidContext();
        }

        return context;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.println("CarVolume");
        writer.increaseIndent();

        writer.printf("Volume priority list version %d\n",
                mAudioVolumeAdjustmentContextsVersion);
        writer.printf("Volume key event timeout %d ms\n", mVolumeKeyEventTimeoutMs);
        writer.println("Car audio contexts priorities");

        writer.increaseIndent();
        dumpSortedContexts(writer);
        writer.decreaseIndent();

        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpSortedContexts(IndentingPrintWriter writer) {
        List<Integer> sortedContexts = getSortedContexts();

        for (int index = 0; index < sortedContexts.size(); index++) {
            int contextId = sortedContexts.get(index);
            int priority = mVolumePriorityByAudioContext.get(contextId);
            writer.printf("Car audio context %s[id=%d] priority %d\n",
                    mCarAudioContext.toString(contextId), contextId, priority);
            AudioAttributes[] attributes =
                    mCarAudioContext.getAudioAttributesForContext(contextId);
            writer.increaseIndent();
            for (int counter = 0; counter < attributes.length; counter++) {
                writer.printf("Attribute: %s\n", attributes[counter]);
            }
            writer.decreaseIndent();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long carVolumeToken = proto.start(CarAudioDumpProto.CAR_VOLUME);
        proto.write(CarVolumeProto.AUDIO_VOLUME_ADJUSTMENT_CONTEXTS_VERSION,
                mAudioVolumeAdjustmentContextsVersion);
        proto.write(CarVolumeProto.VOLUME_KEY_EVENT_TIMEOUT_MS, mVolumeKeyEventTimeoutMs);
        dumpProtoSortedContexts(proto);
        proto.end(carVolumeToken);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpProtoSortedContexts(ProtoOutputStream proto) {
        List<Integer> sortedContexts = getSortedContexts();

        for (int index = 0; index < sortedContexts.size(); index++) {
            int contextId = sortedContexts.get(index);
            int priority = mVolumePriorityByAudioContext.get(contextId);
            long audioContextPrioritiesToken = proto.start(CarVolumeProto.AUDIO_CONTEXT_PRIORITIES);

            long audioContextToken = proto.start(CarVolumeProto.CarAudioContextPriority.CONTEXTS);
            proto.write(CarAudioContextInfoProto.NAME,
                    mCarAudioContext.toString(contextId));
            proto.write(CarAudioContextInfoProto.ID, contextId);
            AudioAttributes[] attributes =
                    mCarAudioContext.getAudioAttributesForContext(contextId);
            for (int counter = 0; counter < attributes.length; counter++) {
                CarAudioContextInfo.dumpCarAudioAttributesProto(attributes[counter],
                        CarAudioContextInfoProto.ATTRIBUTES, proto);
            }
            proto.end(audioContextToken);

            proto.write(CarVolumeProto.CarAudioContextPriority.PRIORITY, priority);
            proto.end(audioContextPrioritiesToken);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private List<Integer> getSortedContexts() {
        List<Integer> sortedContexts = new ArrayList<>(mVolumePriorityByAudioContext.size());
        for (int index = 0; index < mVolumePriorityByAudioContext.size(); index++) {
            int contextId = mVolumePriorityByAudioContext.keyAt(index);
            sortedContexts.add(contextId);
        }
        sortedContexts.sort(Comparator.comparingInt(mVolumePriorityByAudioContext::get));
        return sortedContexts;
    }

    @IntDef({
            VERSION_ONE,
            VERSION_TWO
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CarVolumeListVersion {
    }
}
