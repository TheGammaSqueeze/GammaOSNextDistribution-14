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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.util.Slogf;
import android.media.AudioAttributes;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.annotation.AttributeUsage;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Groupings of {@link AttributeUsage}s to simplify configuration of car audio routing, volume
 * groups, and focus interactions for similar usages.
 */
public final class CarAudioContext {

    private static final String TAG = CarLog.tagFor(CarAudioContext.class);

    /*
     * Shouldn't be used
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.INVALID
     */
    private static final int INVALID = 0;
    /*
     * Music playback
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.INVALID implicitly + 1
     */
    static final int MUSIC = 1;
    /*
     * Navigation directions
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.MUSIC implicitly + 1
     */
    static final int NAVIGATION = 2;
    /*
     * Voice command session
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.NAVIGATION implicitly + 1
     */
    static final int VOICE_COMMAND = 3;
    /*
     * Voice call ringing
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber
     *     .VOICE_COMMAND implicitly + 1
     */
    static final int CALL_RING = 4;
    /*
     * Voice call
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.CALL_RING implicitly + 1
     */
    static final int CALL = 5;
    /*
     * Alarm sound from Android
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.CALL implicitly + 1
     */
    static final int ALARM = 6;
    /*
     * Notifications
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber.ALARM implicitly + 1
     */
    static final int NOTIFICATION = 7;
    /*
     * System sounds
     * ::android::hardware::automotive::audiocontrol::V1_0::ContextNumber
     *     .NOTIFICATION implicitly + 1
     */
    static final int SYSTEM_SOUND = 8;
    /*
     * Emergency related sounds such as collision warnings
     */
    static final int EMERGENCY = 9;
    /*
     * Safety sounds such as obstacle detection when backing up or when changing lanes
     */
    static final int SAFETY = 10;
    /*
     * Vehicle Status related sounds such as check engine light or seat belt chimes
     */
    static final int VEHICLE_STATUS = 11;
    /*
     * Announcement such as traffic announcements
     */
    static final int ANNOUNCEMENT = 12;

    @IntDef({
            INVALID,
            MUSIC,
            NAVIGATION,
            VOICE_COMMAND,
            CALL_RING,
            CALL,
            ALARM,
            NOTIFICATION,
            SYSTEM_SOUND,
            EMERGENCY,
            SAFETY,
            VEHICLE_STATUS,
            ANNOUNCEMENT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AudioContext {
    }

    private static final List<Integer> CONTEXTS = List.of(
            // The items are in a sorted order
            // Starting at one
            MUSIC,
            NAVIGATION,
            VOICE_COMMAND,
            CALL_RING,
            CALL,
            ALARM,
            NOTIFICATION,
            SYSTEM_SOUND,
            EMERGENCY,
            SAFETY,
            VEHICLE_STATUS,
            ANNOUNCEMENT
    );

    // Contexts related to non-car audio system, this covers the general use case of context
    // that would exist in the phone.
    private static final List<Integer> NON_CAR_SYSTEM_CONTEXTS = List.of(
            MUSIC,
            NAVIGATION,
            VOICE_COMMAND,
            CALL_RING,
            CALL,
            ALARM,
            NOTIFICATION,
            SYSTEM_SOUND
    );

    // Contexts related to car audio system, this covers the general use case of context
    // that are generally related to car system.
    private static final List<Integer> CAR_SYSTEM_CONTEXTS = List.of(
            EMERGENCY,
            SAFETY,
            VEHICLE_STATUS,
            ANNOUNCEMENT
    );

    private static final AudioAttributes[] SYSTEM_ATTRIBUTES = new AudioAttributes[] {
            getAudioAttributeFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VEHICLE_STATUS),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT)
    };

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_MUSIC =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_UNKNOWN),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_GAME),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA)
            }, "MUSIC", MUSIC);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_NAVIGATION =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes
                    .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            }, "NAVIGATION", NAVIGATION);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_VOICE_COMMAND =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANT)
            }, "VOICE_COMMAND", VOICE_COMMAND);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_CALL_RING =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            }, "CALL_RING", CALL_RING);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_CALL =
            new CarAudioContextInfo(new AudioAttributes[] {
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT),
                    getAudioAttributeFromUsage(AudioAttributes
                            .USAGE_VOICE_COMMUNICATION_SIGNALLING),
            }, "CALL", CALL);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_ALARM =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ALARM)
            }, "ALARM", ALARM);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_NOTIFICATION =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION),
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            }, "NOTIFICATION", NOTIFICATION);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_SYSTEM_SOUND =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            }, "SYSTEM_SOUND", SYSTEM_SOUND);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_EMERGENCY =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY)
            }, "EMERGENCY", EMERGENCY);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_SAFETY =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY)
            }, "SAFETY", SAFETY);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_VEHICLE_STATUS =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_VEHICLE_STATUS)
            }, "VEHICLE_STATUS", VEHICLE_STATUS);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_ANNOUNCEMENT =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT)
            }, "ANNOUNCEMENT", ANNOUNCEMENT);

    private static final CarAudioContextInfo CAR_CONTEXT_INFO_INVALID =
            new CarAudioContextInfo(new AudioAttributes[]{
                    getAudioAttributeFromUsage(AudioManagerHelper.getUsageVirtualSource())
            }, "INVALID", INVALID);

    private static final List<CarAudioContextInfo> CAR_CONTEXT_INFO = List.of(
            CAR_CONTEXT_INFO_MUSIC,
            CAR_CONTEXT_INFO_NAVIGATION,
            CAR_CONTEXT_INFO_VOICE_COMMAND,
            CAR_CONTEXT_INFO_CALL_RING,
            CAR_CONTEXT_INFO_CALL,
            CAR_CONTEXT_INFO_ALARM,
            CAR_CONTEXT_INFO_NOTIFICATION,
            CAR_CONTEXT_INFO_SYSTEM_SOUND,
            CAR_CONTEXT_INFO_EMERGENCY,
            CAR_CONTEXT_INFO_SAFETY,
            CAR_CONTEXT_INFO_VEHICLE_STATUS,
            CAR_CONTEXT_INFO_ANNOUNCEMENT,
            CAR_CONTEXT_INFO_INVALID
    );

    @VisibleForTesting
    static final SparseArray<List<Integer>> sContextsToDuck =
            new SparseArray<>(/* initialCapacity= */ 13);

    static {
        // INVALID ducks nothing
        sContextsToDuck.append(INVALID, Collections.emptyList());
        // MUSIC ducks nothing
        sContextsToDuck.append(MUSIC, Collections.emptyList());
        sContextsToDuck.append(NAVIGATION, List.of(
                MUSIC,
                CALL_RING,
                CALL,
                ALARM,
                NOTIFICATION,
                SYSTEM_SOUND,
                VEHICLE_STATUS,
                ANNOUNCEMENT
        ));
        sContextsToDuck.append(VOICE_COMMAND, List.of(
                CALL_RING
        ));
        sContextsToDuck.append(CALL_RING, Collections.emptyList());
        sContextsToDuck.append(CALL, List.of(
                CALL_RING,
                ALARM,
                NOTIFICATION,
                VEHICLE_STATUS
        ));
        sContextsToDuck.append(ALARM, List.of(
                MUSIC
        ));
        sContextsToDuck.append(NOTIFICATION, List.of(
                MUSIC,
                ALARM,
                ANNOUNCEMENT
        ));
        sContextsToDuck.append(SYSTEM_SOUND, List.of(
                MUSIC,
                ALARM,
                ANNOUNCEMENT
        ));
        sContextsToDuck.append(EMERGENCY, List.of(
                CALL
        ));
        sContextsToDuck.append(SAFETY, List.of(
                MUSIC,
                NAVIGATION,
                VOICE_COMMAND,
                CALL_RING,
                CALL,
                ALARM,
                NOTIFICATION,
                SYSTEM_SOUND,
                VEHICLE_STATUS,
                ANNOUNCEMENT
        ));
        sContextsToDuck.append(VEHICLE_STATUS, List.of(
                MUSIC,
                CALL_RING,
                ANNOUNCEMENT
        ));
        // ANNOUNCEMENT ducks nothing
        sContextsToDuck.append(ANNOUNCEMENT, Collections.emptyList());
    }

    static int[] getSystemUsages() {
        return convertAttributesToUsage(SYSTEM_ATTRIBUTES);
    }

    private static final SparseArray<String> CONTEXT_NAMES = new SparseArray<>(CONTEXTS.size() + 1);
    private static final SparseArray<AudioAttributes[]> CONTEXT_TO_ATTRIBUTES = new SparseArray<>();
    private static final Map<AudioAttributesWrapper, Integer> AUDIO_ATTRIBUTE_TO_CONTEXT =
            new ArrayMap<>();
    private static final List<AudioAttributesWrapper> ALL_SUPPORTED_ATTRIBUTES = new ArrayList<>();

    static {
        for (int index = 0; index < CAR_CONTEXT_INFO.size(); index++) {
            CarAudioContextInfo info = CAR_CONTEXT_INFO.get(index);
            CONTEXT_NAMES.append(info.getId(), info.getName());
            CONTEXT_TO_ATTRIBUTES.put(info.getId(), info.getAudioAttributes());

            AudioAttributes[] attributes = info.getAudioAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.length; attributeIndex++) {
                AudioAttributesWrapper attributesWrapper =
                        new AudioAttributesWrapper(attributes[attributeIndex]);
                if (AUDIO_ATTRIBUTE_TO_CONTEXT.containsKey(attributesWrapper)) {
                    int mappedContext = AUDIO_ATTRIBUTE_TO_CONTEXT.get(attributesWrapper);
                    Slogf.wtf(TAG, "%s already mapped to context %s, can not remap to context %s",
                            attributesWrapper, mappedContext, info.getId());
                }
                AUDIO_ATTRIBUTE_TO_CONTEXT.put(attributesWrapper, info.getId());
                if (isInvalidContextId(info.getId())) {
                    continue;
                }
                ALL_SUPPORTED_ATTRIBUTES.add(attributesWrapper);
            }
        }
    }

    private final boolean mUseCoreAudioRouting;
    private final List<CarAudioContextInfo> mCarAudioContextInfos;
    private final Map<AudioAttributesWrapper, Integer> mAudioAttributesToContext =
            new ArrayMap<>();
    private final SparseArray<String> mContextToNames = new SparseArray<>();
    private final SparseArray<AudioAttributes[]> mContextToAttributes = new SparseArray<>();
    /**
     * Oem Extension CarAudioContext cannot be addressed by usage only
     */
    private final List<Integer> mOemExtensionContexts = new ArrayList<>();

    /**
     * Creates a car audio context which contains the logical grouping of
     * audio attributes into contexts
     *
     * @param carAudioContexts list of audio attributes grouping
     * @param useCoreAudioRouting if set, indicate contexts are mapped on core
     * {@link android.media.audiopolicy.AudioProductStrategy}       .
     */
    public CarAudioContext(List<CarAudioContextInfo> carAudioContexts,
            boolean useCoreAudioRouting) {
        Objects.requireNonNull(carAudioContexts,
                "Car audio contexts must not be null");
        Preconditions.checkArgument(!carAudioContexts.isEmpty(),
                "Car audio contexts must not be empty");
        mCarAudioContextInfos = carAudioContexts;
        mUseCoreAudioRouting = useCoreAudioRouting;
        for (int index = 0; index < carAudioContexts.size(); index++) {
            CarAudioContextInfo info = carAudioContexts.get(index);
            int contextId = info.getId();
            mContextToNames.put(info.getId(), info.getName());
            mContextToAttributes.put(info.getId(), info.getAudioAttributes());
            if (mUseCoreAudioRouting) {
                int[] sdkUsages = convertAttributesToUsage(info.getAudioAttributes());
                boolean isOemExtension = false;
                // At least one of the attributes prevents this context from being addressed only
                // by usage, so we will not be able to use DynamicPolicyMixes.
                for (int indexUsage = 0; indexUsage < sdkUsages.length; indexUsage++) {
                    int usage = sdkUsages[indexUsage];
                    AudioAttributes attributes = getAudioAttributeFromUsage(usage);
                    if (CoreAudioHelper.getStrategyForAudioAttributes(attributes) != contextId) {
                        isOemExtension = true;
                        break;
                    }
                }
                if (isOemExtension) {
                    mOemExtensionContexts.add(info.getId());
                }
                // Bypass initialization of Map attribute to context as relying on strategy rules
                continue;
            }
            AudioAttributes[] attributes = info.getAudioAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.length; attributeIndex++) {
                AudioAttributesWrapper attributesWrapper =
                        new AudioAttributesWrapper(attributes[attributeIndex]);
                if (mAudioAttributesToContext.containsKey(attributesWrapper)) {
                    int mappedContext = mAudioAttributesToContext.get(attributesWrapper);
                    Slogf.wtf(TAG, "%s already mapped to context %s, can not remap to context %s",
                            attributesWrapper, mappedContext, info.getId());
                }
                if (isInvalidContextId(info.getId())) {
                    continue;
                }
                mAudioAttributesToContext.put(attributesWrapper, info.getId());
            }
        }
    }

    static int getLegacyContextForUsage(int usage) {
        AudioAttributesWrapper wrapper = getAudioAttributeWrapperFromUsage(usage);
        return AUDIO_ATTRIBUTE_TO_CONTEXT.getOrDefault(wrapper, INVALID);
    }

    static List<AudioAttributes> evaluateAudioAttributesToDuck(
            List<AudioAttributes> activePlaybackAttributes) {
        ArraySet<AudioAttributesWrapper> attributesToDuck = new ArraySet<>();
        List<AudioAttributesWrapper> wrappers = new ArrayList<>(activePlaybackAttributes.size());
        for (int index = 0; index < activePlaybackAttributes.size(); index++) {
            AudioAttributesWrapper wrapper =
                    new AudioAttributesWrapper(activePlaybackAttributes.get(index));
            wrappers.add(wrapper);
            int context = AUDIO_ATTRIBUTE_TO_CONTEXT.getOrDefault(wrapper, INVALID);
            if (isInvalidContextId(context)) {
                continue;
            }
            List<Integer> contextsToDuck = sContextsToDuck.get(context);
            for (int contextIndex = 0; contextIndex < contextsToDuck.size(); contextIndex++) {
                AudioAttributes[] duckedAttributes =
                        CONTEXT_TO_ATTRIBUTES.get(contextsToDuck.get(contextIndex));
                for (int i = 0; i < duckedAttributes.length; i++) {
                    attributesToDuck.add(new AudioAttributesWrapper(duckedAttributes[i]));
                }
            }
        }
        attributesToDuck.retainAll(wrappers);

        List<AudioAttributes> duckedAudioAttributes = new ArrayList<>(attributesToDuck.size());
        for (int index = 0; index < attributesToDuck.size(); index++) {
            duckedAudioAttributes.add(attributesToDuck.valueAt(index).getAudioAttributes());
        }

        return duckedAudioAttributes;
    }

    boolean useCoreAudioRouting() {
        return mUseCoreAudioRouting;
    }

    boolean isOemExtensionAudioContext(@AudioContext int audioContext) {
        return mOemExtensionContexts.contains(audioContext);
    }

    /**
     * Checks if the audio attribute usage is valid, throws an {@link IllegalArgumentException}
     * if the {@code usage} is not valid.
     *
     * @param usage audio attribute usage to check
     * @throws IllegalArgumentException in case of invalid audio attribute usage
     */
    public static void checkAudioAttributeUsage(@AttributeUsage int usage)
            throws IllegalArgumentException {
        if (isValidAudioAttributeUsage(usage)) {
            return;
        }

        throw new IllegalArgumentException("Invalid audio attribute " + usage);
    }

    /**
     * Determines if the audio attribute usage is valid
     *
     * @param usage audio attribute usage to check
     * @return {@code true} if valid, {@code false} otherwise
     */
    public static boolean isValidAudioAttributeUsage(@AttributeUsage int usage) {
        switch (usage) {
            case AudioAttributes.USAGE_UNKNOWN:
            case AudioAttributes.USAGE_MEDIA:
            case AudioAttributes.USAGE_VOICE_COMMUNICATION:
            case AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING:
            case AudioAttributes.USAGE_ALARM:
            case AudioAttributes.USAGE_NOTIFICATION:
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE:
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
            case AudioAttributes.USAGE_NOTIFICATION_EVENT:
            case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY:
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION:
            case AudioAttributes.USAGE_GAME:
            case AudioAttributes.USAGE_ASSISTANT:
            case AudioAttributes.USAGE_CALL_ASSISTANT:
            case AudioAttributes.USAGE_EMERGENCY:
            case AudioAttributes.USAGE_SAFETY:
            case AudioAttributes.USAGE_VEHICLE_STATUS:
            case AudioAttributes.USAGE_ANNOUNCEMENT:
                return true;
            default:
                // Virtual usage is hidden and thus it must be taken care here.
                return usage == AudioManagerHelper.getUsageVirtualSource();
        }
    }

    /**
     * Checks if the audio context is within the valid range from MUSIC to SYSTEM_SOUND
     */
    void preconditionCheckAudioContext(@AudioContext int audioContext) {

        Preconditions.checkArgument(!isInvalidContextId(audioContext)
                        && mContextToAttributes.indexOfKey(audioContext) >= 0,
                "Car audio context %d is invalid", audioContext);
    }

    AudioAttributes[] getAudioAttributesForContext(@AudioContext int carAudioContext) {
        preconditionCheckAudioContext(carAudioContext);
        return mContextToAttributes.get(carAudioContext);
    }

    @AudioContext int getContextForAttributes(AudioAttributes attributes) {
        return getContextForAudioAttribute(attributes);
    }

    /**
     * @return Context number for a given audio usage, {@code INVALID} if the given usage is
     * unrecognized.
     */
    public @AudioContext int getContextForAudioAttribute(AudioAttributes attributes) {
        if (mUseCoreAudioRouting) {
            int strategyId = CoreAudioHelper.getStrategyForAudioAttributes(attributes);
            if ((strategyId != CoreAudioHelper.INVALID_STRATEGY)
                    && (mContextToNames.indexOfKey(strategyId) >= 0)) {
                return strategyId;
            }
            return INVALID;
        }
        return mAudioAttributesToContext.getOrDefault(
                new AudioAttributesWrapper(attributes), INVALID);
    }

    /**
     * Returns an audio attribute for a given usage
     * @param usage input usage, can be an audio attribute system usage
     */
    public static AudioAttributes getAudioAttributeFromUsage(@AttributeUsage int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        if (AudioAttributes.isSystemUsage(usage)) {
            builder.setSystemUsage(usage);
        } else {
            builder.setUsage(usage);
        }
        return builder.build();
    }

    /**
     * Returns an audio attribute wrapper for a given usage
     * @param usage input usage, can be an audio attribute system usage
     */
    public static AudioAttributesWrapper getAudioAttributeWrapperFromUsage(
            @AttributeUsage int usage) {
        return new AudioAttributesWrapper(getAudioAttributeFromUsage(usage));
    }

    /**
     * Returns an audio attribute wrapper for a given {@code AudioAttributes}
     * @param attributes input {@code AudioAttributes}
     */
    public AudioAttributesWrapper getAudioAttributeWrapperFromAttributes(
            AudioAttributes attributes) {
        return mUseCoreAudioRouting
                ? new AudioAttributesWrapper(attributes, getContextForAudioAttribute(attributes))
                : new AudioAttributesWrapper(attributes);
    }

    Set<Integer> getUniqueContextsForAudioAttributes(List<AudioAttributes> audioAttributes) {
        Objects.requireNonNull(audioAttributes, "Audio attributes can not be null");
        Set<Integer> uniqueContexts = new ArraySet<>();
        for (int index = 0; index < audioAttributes.size(); index++) {
            uniqueContexts.add(getContextForAudioAttribute(audioAttributes.get(index)));
        }

        uniqueContexts.remove(INVALID);
        return uniqueContexts;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.println("CarAudioContext");
        writer.increaseIndent();
        for (int index = 0; index < mCarAudioContextInfos.size(); index++) {
            mCarAudioContextInfos.get(index).dump(writer);
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long carAudioContextInfosToken = proto.start(CarAudioDumpProto.CAR_AUDIO_CONTEXT);
        for (int index = 0; index < mCarAudioContextInfos.size(); index++) {
            mCarAudioContextInfos.get(index).dumpProto(proto);
        }
        proto.end(carAudioContextInfosToken);
    }

    static boolean isNotificationAudioAttribute(AudioAttributes attributes) {
        AudioAttributesWrapper wrapper = new AudioAttributesWrapper(attributes);
        return getAudioAttributeWrapperFromUsage(AudioAttributes.USAGE_NOTIFICATION).equals(wrapper)
                || getAudioAttributeWrapperFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .equals(wrapper);
    }

    static boolean isCriticalAudioAudioAttribute(AudioAttributes attributes) {
        AudioAttributesWrapper wrapper = new AudioAttributesWrapper(attributes);
        return getAudioAttributeWrapperFromUsage(AudioAttributes.USAGE_EMERGENCY).equals(wrapper)
                || getAudioAttributeWrapperFromUsage(AudioAttributes.USAGE_SAFETY).equals(wrapper);
    }

    static boolean isRingerOrCallAudioAttribute(AudioAttributes attributes) {
        AudioAttributesWrapper wrapper = new AudioAttributesWrapper(attributes);
        return getAudioAttributeWrapperFromUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .equals(wrapper)
                || getAudioAttributeWrapperFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .equals(wrapper)
                || getAudioAttributeWrapperFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT)
                .equals(wrapper)
                || getAudioAttributeWrapperFromUsage(
                        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                        .equals(wrapper);

    }

    String toString(@AudioContext int audioContext) {
        String name = mContextToNames.get(audioContext);
        if (name != null) {
            return name;
        }
        return "Unsupported Context 0x" + Integer.toHexString(audioContext);
    }

    static List<AudioAttributes> getUniqueAttributesHoldingFocus(
            List<AudioAttributes> audioAttributes) {
        Set<AudioAttributesWrapper> uniqueAudioAttributes = new ArraySet<>();
        List<AudioAttributes> uniqueAttributes = new ArrayList<>(uniqueAudioAttributes.size());
        for (int index = 0; index < audioAttributes.size(); index++) {
            AudioAttributes audioAttribute = audioAttributes.get(index);
            if (uniqueAudioAttributes.contains(new AudioAttributesWrapper(audioAttribute))) {
                continue;
            }
            uniqueAudioAttributes.add(new AudioAttributesWrapper(audioAttributes.get(index)));
            uniqueAttributes.add(new AudioAttributes.Builder(audioAttribute).build());
        }

        return uniqueAttributes;
    }

    List<Integer> getAllContextsIds() {
        List<Integer> contextIds = new ArrayList<>(mContextToAttributes.size());
        for (int index = 0; index < mContextToAttributes.size(); index++) {
            if (isInvalidContextId(mContextToAttributes.keyAt(index))) {
                continue;
            }
            contextIds.add(mContextToAttributes.keyAt(index));
        }
        return contextIds;
    }

    static List<Integer> getNonCarSystemContextIds() {
        return NON_CAR_SYSTEM_CONTEXTS;
    }

    static List<Integer> getCarSystemContextIds() {
        return CAR_SYSTEM_CONTEXTS;
    }

    /**
     * Return static list of logical audio attributes grouping.
     */
    public static List<CarAudioContextInfo> getAllContextsInfo() {
        return CAR_CONTEXT_INFO;
    }

    @Nullable
    public List<CarAudioContextInfo> getContextsInfo() {
        return mCarAudioContextInfos;
    }

    static List<CarAudioContextInfo> getAllNonCarSystemContextsInfo() {
        return List.of(
                CAR_CONTEXT_INFO_MUSIC,
                CAR_CONTEXT_INFO_NAVIGATION,
                CAR_CONTEXT_INFO_VOICE_COMMAND,
                CAR_CONTEXT_INFO_CALL_RING,
                CAR_CONTEXT_INFO_CALL,
                CAR_CONTEXT_INFO_ALARM,
                CAR_CONTEXT_INFO_NOTIFICATION,
                CAR_CONTEXT_INFO_SYSTEM_SOUND
        );
    }

    static List<CarAudioContextInfo> getAllCarSystemContextsInfo() {
        return List.of(
                CAR_CONTEXT_INFO_EMERGENCY,
                CAR_CONTEXT_INFO_SAFETY,
                CAR_CONTEXT_INFO_VEHICLE_STATUS,
                CAR_CONTEXT_INFO_ANNOUNCEMENT
        );
    }

    static @AudioContext int getInvalidContext() {
        return INVALID;
    }

    static boolean isInvalidContextId(@AudioContext int id) {
        return id == INVALID;
    }

    boolean validateAllAudioAttributesSupported(List<Integer> contexts) {
        ArraySet<AudioAttributesWrapper> supportedAudioAttributes =
                new ArraySet<>(ALL_SUPPORTED_ATTRIBUTES);

        for (int contextIndex = 0; contextIndex < contexts.size(); contextIndex++) {
            int contextId = contexts.get(contextIndex);
            AudioAttributes[] attributes = getAudioAttributesForContext(contextId);
            List<AudioAttributesWrapper> wrappers = new ArrayList<>(attributes.length);
            for (int index = 0; index < attributes.length; index++) {
                wrappers.add(new AudioAttributesWrapper(attributes[index]));
            }

            supportedAudioAttributes.removeAll(wrappers);
        }

        for (int index = 0; index < supportedAudioAttributes.size(); index++) {
            AudioAttributesWrapper wrapper = supportedAudioAttributes.valueAt(index);
            Slogf.e(CarLog.TAG_AUDIO,
                    "AudioAttribute %s not supported in current configuration", wrapper);
        }

        return supportedAudioAttributes.isEmpty();
    }

    private static int[] convertAttributesToUsage(AudioAttributes[] audioAttributes) {
        int[] usages = new int[audioAttributes.length];
        for (int index = 0; index < audioAttributes.length; index++) {
            usages[index] = audioAttributes[index].getSystemUsage();
        }
        return usages;
    }

    /**
     * Class wraps an audio attributes object. This can be used for comparing audio attributes.
     * Current the audio attributes class compares all the attributes in the two objects.
     *
     * In automotive only the audio attribute usage is currently used, thus this class can be used
     * to compare that audio attribute usage.
     *
     * When core routing is enabled, rules are based on all attributes fields, thus makes more
     * sense to compare associated audio context id.
     */
    public static final class AudioAttributesWrapper {

        private final AudioAttributes mAudioAttributes;
        // Legacy wrapper does not make use of context id to match, keep it uninitialized.
        private final int mCarAudioContextId;

        AudioAttributesWrapper(AudioAttributes audioAttributes) {
            mAudioAttributes = audioAttributes;
            mCarAudioContextId = INVALID;
        }

        AudioAttributesWrapper(AudioAttributes audioAttributes, int carAudioContextId) {
            Preconditions.checkArgument(!isInvalidContextId(carAudioContextId),
                    "Car audio contexts can not be invalid");
            mAudioAttributes = audioAttributes;
            mCarAudioContextId = carAudioContextId;
        }

        static boolean audioAttributeMatches(AudioAttributes audioAttributes,
                AudioAttributes inputAudioAttribute) {
            return audioAttributes.getSystemUsage() == inputAudioAttribute.getSystemUsage();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || !(object instanceof AudioAttributesWrapper)) {
                return false;
            }

            AudioAttributesWrapper that = (AudioAttributesWrapper) object;
            // Wrapping on context: equality is based on associated context, otherwise based on
            // attributes matching (limited to usage matching).
            return (mCarAudioContextId != INVALID || that.mCarAudioContextId != INVALID)
                    ? mCarAudioContextId == that.mCarAudioContextId
                    : audioAttributeMatches(mAudioAttributes, that.mAudioAttributes);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(mCarAudioContextId == INVALID
                    ? mAudioAttributes.getSystemUsage() : mCarAudioContextId);
        }

        @Override
        public String toString() {
            return mAudioAttributes.toString();
        }

        /**
         * Returns the audio attributes for the wrapper
         */
        public AudioAttributes getAudioAttributes() {
            return mAudioAttributes;
        }

        /**
         * Returns the id of the {@code CarAudioContextInfo} for the wrapper
         */
        public int getCarAudioContextId() {
            return mCarAudioContextId;
        }
    }
}
