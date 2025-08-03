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

package com.android.car.oem.focus;

import static android.media.AudioManager.AUDIOFOCUS_FLAG_DELAY_OK;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.android.car.oem.utils.AudioUtils.getAudioAttributeFromUsage;

import android.car.oem.AudioFocusEntry;
import android.car.oem.OemCarAudioFocusEvaluationRequest;
import android.car.oem.OemCarAudioFocusResult;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

import com.android.car.oem.utils.AudioAttributesWrapper;
import com.android.car.oem.utils.AudioUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FocusInteraction is responsible for evaluating how incoming focus requests should be handled
 * based on pre-defined interaction behaviors for each incoming {@link AudioAttributes}
 * in relation to a {@link AudioAttributes} that is currently holding focus.
 */
public final class FocusInteraction {

    private static final String TAG = FocusInteraction.class.getSimpleName();

    // Values for the internal interaction matrix we use to make focus decisions
    private static final int INTERACTION_INVALID = -2; // Focus not granted
    /**
     * Value for the interaction matrix for rejecting incoming focus request. Focus not granted.
     */
    public static final int INTERACTION_REJECT = -1;
    /**
     * Value for the interaction matrix for granting exclusive access to incoming focus request.
     * Focus granted, others lose focus
     */
    public static final int INTERACTION_EXCLUSIVE = 1;
    /**
     * Value for the interaction matrix for concurrent focus, incoming focus request and current
     * focus holders will concurrently hold focus. Focus granted, others keep focus.
     */
    public static final int INTERACTION_CONCURRENT = 2;

    static final AudioAttributes[] MUSIC_ATTRIBUTES = new AudioAttributes[] {
            getAudioAttributeFromUsage(AudioAttributes.USAGE_UNKNOWN),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_GAME),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA)
    };

    static final AudioAttributes[] NAVIGATION_ATTRIBUTES = new AudioAttributes[] {
            getAudioAttributeFromUsage(AudioAttributes
                    .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
    };

    static final AudioAttributes[] VOICE_COMMAND_ATTRIBUTES = new AudioAttributes[] {
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANT)
    };

    static final AudioAttributes[] CALL_RING_ATTRIBUTES = new AudioAttributes[] {
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE),
    };

    static final AudioAttributes[] CALL_ATTRIBUTES = new AudioAttributes[] {
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT),
            getAudioAttributeFromUsage(AudioAttributes
                    .USAGE_VOICE_COMMUNICATION_SIGNALLING)
    };

    static final AudioAttributes[] ALARM_ATTRIBUTES = new AudioAttributes[]{
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ALARM)
    };

    static final AudioAttributes[] NOTIFICATION_ATTRIBUTES = new AudioAttributes[]{
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
    };

    static final AudioAttributes[] SYSTEM_ATTRIBUTES = new AudioAttributes[]{
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
    };

    static final AudioAttributes[] EMERGENCY_ATTRIBUTES = new AudioAttributes[]{
            getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY)
    };

    static final AudioAttributes[] SAFETY_ATTRIBUTES = new AudioAttributes[]{
            getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY)
    };

    static final AudioAttributes[] VEHICLE_STATUS_ATTRIBUTES = new AudioAttributes[]{
        getAudioAttributeFromUsage(AudioAttributes.USAGE_VEHICLE_STATUS)
    };

    static final AudioAttributes[] ANNOUNCEMENT_ATTRIBUTES = new AudioAttributes[]{
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT)
    };

    private static final int[][] INTERACTION_MATRIX = {
            // Each Row represents audio group of current focus holder
            // Each Column represents audio group of incoming request (labels along the right)
            // Cell value is one of INTERACTION_REJECT, INTERACTION_EXCLUSIVE,
            // or INTERACTION_CONCURRENT

            // Focus holder: MUSIC
            {
                    INTERACTION_EXCLUSIVE, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_EXCLUSIVE, // VOICE_COMMAND
                    INTERACTION_EXCLUSIVE, // CALL_RING
                    INTERACTION_EXCLUSIVE, // CALL
                    INTERACTION_EXCLUSIVE, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_EXCLUSIVE, // ANNOUNCEMENT
            },
            // Focus holder: NAVIGATION
            {
                    INTERACTION_CONCURRENT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_EXCLUSIVE, // VOICE_COMMAND
                    INTERACTION_CONCURRENT, // CALL_RING
                    INTERACTION_CONCURRENT, // CALL
                    INTERACTION_CONCURRENT, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_CONCURRENT, // ANNOUNCEMENT
            },
            // Focus holder: VOICE_COMMAND
            {
                    INTERACTION_CONCURRENT, // MUSIC
                    INTERACTION_REJECT, // NAVIGATION
                    INTERACTION_CONCURRENT, // VOICE_COMMAND
                    INTERACTION_EXCLUSIVE, // CALL_RING
                    INTERACTION_EXCLUSIVE, // CALL
                    INTERACTION_REJECT, // ALARM
                    INTERACTION_REJECT, // NOTIFICATION
                    INTERACTION_REJECT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_REJECT, // ANNOUNCEMENT
            },
            // Focus holder: CALL_RING
            {
                    INTERACTION_REJECT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_CONCURRENT, // VOICE_COMMAND
                    INTERACTION_CONCURRENT, // CALL_RING
                    INTERACTION_CONCURRENT, // CALL
                    INTERACTION_REJECT, // ALARM
                    INTERACTION_REJECT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_REJECT, // ANNOUNCEMENT
            },
            // Focus holder: CALL
            {
                    INTERACTION_REJECT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_REJECT, // VOICE_COMMAND
                    INTERACTION_CONCURRENT, // CALL_RING
                    INTERACTION_CONCURRENT, // CALL
                    INTERACTION_CONCURRENT, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_REJECT, // SYSTEM_SOUND
                    INTERACTION_CONCURRENT, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_REJECT, // ANNOUNCEMENT
            },
            // Focus holder: ALARM
            {
                    INTERACTION_CONCURRENT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_EXCLUSIVE, // VOICE_COMMAND
                    INTERACTION_EXCLUSIVE, // CALL_RING
                    INTERACTION_EXCLUSIVE, // CALL
                    INTERACTION_CONCURRENT, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_REJECT, // ANNOUNCEMENT
            },
            // Focus holder: NOTIFICATION
            {
                    INTERACTION_CONCURRENT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_EXCLUSIVE, // VOICE_COMMAND
                    INTERACTION_EXCLUSIVE, // CALL_RING
                    INTERACTION_EXCLUSIVE, // CALL
                    INTERACTION_CONCURRENT, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_CONCURRENT, // ANNOUNCEMENT
            },
            // Focus holder: SYSTEM_SOUND
            {
                    INTERACTION_CONCURRENT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_EXCLUSIVE, // VOICE_COMMAND
                    INTERACTION_EXCLUSIVE, // CALL_RING
                    INTERACTION_EXCLUSIVE, // CALL
                    INTERACTION_CONCURRENT, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_CONCURRENT, // ANNOUNCEMENT
            },
            // Focus holder: EMERGENCY
            {
                    INTERACTION_REJECT, // MUSIC
                    INTERACTION_REJECT, // NAVIGATION
                    INTERACTION_REJECT, // VOICE_COMMAND
                    INTERACTION_REJECT, // CALL_RING
                    INTERACTION_CONCURRENT, // CALL
                    INTERACTION_REJECT, // ALARM
                    INTERACTION_REJECT, // NOTIFICATION
                    INTERACTION_REJECT, // SYSTEM_SOUND
                    INTERACTION_CONCURRENT, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_REJECT, // VEHICLE_STATUS
                    INTERACTION_REJECT, // ANNOUNCEMENT
            },
            // Focus holder: SAFETY
            {
                    INTERACTION_CONCURRENT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_CONCURRENT, // VOICE_COMMAND
                    INTERACTION_CONCURRENT, // CALL_RING
                    INTERACTION_CONCURRENT, // CALL
                    INTERACTION_CONCURRENT, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_CONCURRENT, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_CONCURRENT, // ANNOUNCEMENT
            },
            // Focus holder: VEHICLE_STATUS
            {
                    INTERACTION_CONCURRENT, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_CONCURRENT, // VOICE_COMMAND
                    INTERACTION_CONCURRENT, // CALL_RING
                    INTERACTION_CONCURRENT, // CALL
                    INTERACTION_CONCURRENT, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_CONCURRENT, // ANNOUNCEMENT
            },
            // Focus holder: ANNOUNCEMENT
            {
                    INTERACTION_EXCLUSIVE, // MUSIC
                    INTERACTION_CONCURRENT, // NAVIGATION
                    INTERACTION_EXCLUSIVE, // VOICE_COMMAND
                    INTERACTION_EXCLUSIVE, // CALL_RING
                    INTERACTION_EXCLUSIVE, // CALL
                    INTERACTION_EXCLUSIVE, // ALARM
                    INTERACTION_CONCURRENT, // NOTIFICATION
                    INTERACTION_CONCURRENT, // SYSTEM_SOUND
                    INTERACTION_EXCLUSIVE, // EMERGENCY
                    INTERACTION_CONCURRENT, // SAFETY
                    INTERACTION_CONCURRENT, // VEHICLE_STATUS
                    INTERACTION_EXCLUSIVE, // ANNOUNCEMENT
            },
    };

    /**
     * Attributes interaction resembling default interaction in car audio service.
     *
     * <p>The key are the audio attribute for current focus holder. The value is another map for
     * the possible interactions for the incoming focus request. The latter map has the
     * incoming audio attribute as the key, its value is the actual interaction
     * {@code INTERACTION_REJECT}, {@code INTERACTION_EXCLUSIVE}, or {@code INTERACTION_CONCURRENT}.
     */
    public static final ArrayMap<AudioAttributes, ArrayMap<AudioAttributes, Integer>>
            ATTRIBUTES_INTERACTIONS = new ArrayMap<>();

    private final ArrayMap<AudioAttributesWrapper, ArrayMap<AudioAttributesWrapper, Integer>>
            mHolderToIncomingAttributesInteractions;

    static {
        List<AudioAttributes[]> interactionsGroups = List.of(
                MUSIC_ATTRIBUTES,
                NAVIGATION_ATTRIBUTES,
                VOICE_COMMAND_ATTRIBUTES,
                CALL_RING_ATTRIBUTES,
                CALL_ATTRIBUTES,
                ALARM_ATTRIBUTES,
                NOTIFICATION_ATTRIBUTES,
                SYSTEM_ATTRIBUTES,
                EMERGENCY_ATTRIBUTES,
                SAFETY_ATTRIBUTES,
                VEHICLE_STATUS_ATTRIBUTES,
                ANNOUNCEMENT_ATTRIBUTES
        );

        for (int group = 0; group < interactionsGroups.size(); group++) {
            AudioAttributes[] holderAttributes = interactionsGroups.get(group);
            for (int index = 0; index < holderAttributes.length; index++) {
                ArrayMap<AudioAttributes, Integer> attributeInteractions =
                        getAudioAttributesInteractions(interactionsGroups, group);
                ATTRIBUTES_INTERACTIONS.put(holderAttributes[index], attributeInteractions);
            }
        }

    }

    private static ArrayMap<AudioAttributes, Integer> getAudioAttributesInteractions(
            List<AudioAttributes[]> interactionsGroups, int group) {
        ArrayMap<AudioAttributes, Integer> attributeInteractions = new ArrayMap<>();
        for (int incomingGroup = 0; incomingGroup < interactionsGroups.size();
                incomingGroup++) {
            AudioAttributes[] incomingAttributes = interactionsGroups.get(incomingGroup);
            Integer interaction = INTERACTION_MATRIX[group][incomingGroup];
            for (int index = 0; index < incomingAttributes.length; index++) {
                attributeInteractions.put(incomingAttributes[index], interaction);
            }
        }
        return attributeInteractions;
    }

    /**
     * Constructs a focus interaction instance.
     */
    public FocusInteraction(ArrayMap<AudioAttributes, ArrayMap<AudioAttributes, Integer>>
            audioAttributesInteractions) {
        mHolderToIncomingAttributesInteractions = new ArrayMap<>();
        for (int holderIndex = 0; holderIndex < audioAttributesInteractions.size(); holderIndex++) {
            ArrayMap<AudioAttributes, Integer> interactions =
                    audioAttributesInteractions.valueAt(holderIndex);
            AudioAttributesWrapper holderWrapper =
                    new AudioAttributesWrapper(audioAttributesInteractions.keyAt(holderIndex));
            ArrayMap<AudioAttributesWrapper, Integer> wrappedInteractions = new ArrayMap<>();

            for (int incomingIndex = 0; incomingIndex < interactions.size(); incomingIndex++) {
                wrappedInteractions
                        .put(new AudioAttributesWrapper(interactions.keyAt(incomingIndex)),
                                interactions.valueAt(incomingIndex));
            }
            mHolderToIncomingAttributesInteractions.put(holderWrapper, wrappedInteractions);
        }
    }

    /**
     * Evaluates interaction between incoming focus {@link OemCarAudioFocusEvaluationRequest}
     * and the current focus request based on interaction matrix.
     */
    public OemCarAudioFocusResult evaluateFocusRequest(OemCarAudioFocusEvaluationRequest request) {
        FocusEvaluation holdersEvaluation =
                evaluateAgainstFocusList(request.getAudioFocusRequest(),
                        request.getFocusHolders(), /* evalTag= */ "holders");

        if (holdersEvaluation.equals(FocusEvaluation.FOCUS_EVALUATION_FAILED)) {
            return OemCarAudioFocusResult.EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS;
        }

        FocusEvaluation losersEvaluation = evaluateAgainstFocusList(request.getAudioFocusRequest(),
                request.getFocusLosers(), /* evalTag= */ "losers");

        if (losersEvaluation.equals(FocusEvaluation.FOCUS_EVALUATION_FAILED)) {
            return OemCarAudioFocusResult.EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS;
        }

        boolean delayFocus = holdersEvaluation.mAudioFocusEvalResults == AUDIOFOCUS_REQUEST_DELAYED
                || losersEvaluation.mAudioFocusEvalResults == AUDIOFOCUS_REQUEST_DELAYED;

        int results = delayFocus ? AUDIOFOCUS_REQUEST_DELAYED : AUDIOFOCUS_REQUEST_GRANTED;

        AudioFocusEntry currenRequest = request.getAudioFocusRequest();
        AudioFocusEntry focusEntry =
                new AudioFocusEntry.Builder(currenRequest.getAudioFocusInfo(),
                        currenRequest.getAudioContextId(),
                        currenRequest.getAudioVolumeGroupId(),
                        AUDIOFOCUS_GAIN).build();

        return new OemCarAudioFocusResult.Builder(holdersEvaluation.mChangedEntries,
                losersEvaluation.mChangedEntries, results).setAudioFocusEntry(focusEntry)
                .build();
    }

    private FocusEvaluation evaluateAgainstFocusList(AudioFocusEntry request,
            List<AudioFocusEntry> focusEntries, String evalTag) {
        boolean delayFocusForCurrentRequest = false;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Scanning focus " + evalTag);
        }
        ArrayList<AudioFocusEntry> changed = new ArrayList<AudioFocusEntry>();
        for (int index = 0; index < focusEntries.size(); index++) {
            AudioFocusEntry entry = focusEntries.get(index);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Evaluating focus entry: " + entry);
            }

            int interactionResult = evaluateRequest(request, entry, changed);
            switch (interactionResult) {
                case AUDIOFOCUS_REQUEST_FAILED:
                    return FocusEvaluation.FOCUS_EVALUATION_FAILED;
                case AUDIOFOCUS_REQUEST_DELAYED:
                    delayFocusForCurrentRequest = true;
                    // fall through
                case AUDIOFOCUS_REQUEST_GRANTED:
                    // fall through
                default:
                    continue;
            }
        }

        int results = delayFocusForCurrentRequest
                ? AUDIOFOCUS_REQUEST_DELAYED : AUDIOFOCUS_REQUEST_GRANTED;
        return new FocusEvaluation(changed, results);
    }

    /**
     * Evaluates interaction between incoming focus {@link AudioFocusEntry} and the current focus
     * request based on interaction matrix.
     *
     * <p>Note: In addition to returning the request results
     * for the incoming request based on this interaction, this method also adds the current {@code
     * focusHolder} to the {@code focusLosers} list when appropriate.
     *
     * @param request {@link AudioFocusEntry} to evaluate
     * @param focusHolder {@link AudioFocusEntry} for current focus holder
     * @param focusLosers Mutable array to add focusHolder to if it should lose focus
     * @return result of focus interaction, can be any of {@code AUDIOFOCUS_REQUEST_DELAYED},
     *      {@code AUDIOFOCUS_REQUEST_FAILED}, or {@code AUDIOFOCUS_REQUEST_GRANTED}
     */
    private int evaluateRequest(AudioFocusEntry request, AudioFocusEntry focusHolder,
            List<AudioFocusEntry> focusLosers) {
        boolean allowDucking = canReceiveDucking(request.getAudioFocusInfo());
        boolean allowsDelayedFocus = canReceiveDelayedFocus(request.getAudioFocusInfo());

        AudioAttributesWrapper holderAttribute =
                new AudioAttributesWrapper(focusHolder.getAudioFocusInfo().getAttributes());
        AudioAttributesWrapper requestAttribute =
                new AudioAttributesWrapper(request.getAudioFocusInfo().getAttributes());

        int interaction = mHolderToIncomingAttributesInteractions
                .get(holderAttribute).getOrDefault(requestAttribute, INTERACTION_INVALID);

        switch (interaction) {
            case INTERACTION_REJECT:
                return allowsDelayedFocus ? AUDIOFOCUS_REQUEST_DELAYED : AUDIOFOCUS_REQUEST_FAILED;
            case INTERACTION_EXCLUSIVE:
                focusLosers.add(focusHolder);
                return AUDIOFOCUS_REQUEST_GRANTED;
            case INTERACTION_CONCURRENT:
                // If ducking isn't allowed by the focus requester, then everybody else
                // must get a LOSS.
                // If a focus holder has set the AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS flag,
                // they must get a LOSS message even if ducking would otherwise be allowed.
                // If a focus holder holds the RECEIVE_CAR_AUDIO_DUCKING_EVENTS permission,
                // they must receive all audio focus losses.
                if (!allowDucking || wantsPauseInsteadOfDucking(focusHolder.getAudioFocusInfo())) {
                    focusLosers.add(focusHolder);
                }
                return AUDIOFOCUS_REQUEST_GRANTED;
            default:
                Slog.e(TAG, "Unsupported attributes " + request + " - rejecting request");
                return AUDIOFOCUS_REQUEST_FAILED;
        }
    }

    private boolean canReceiveDucking(AudioFocusInfo audioFocusInfo) {
        return (audioFocusInfo.getGainRequest() == AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }

    private boolean canReceiveDelayedFocus(AudioFocusInfo audioFocusInfo) {
        if (audioFocusInfo.getGainRequest() != AUDIOFOCUS_GAIN) {
            return false;
        }
        return (audioFocusInfo.getFlags() & AUDIOFOCUS_FLAG_DELAY_OK) == AUDIOFOCUS_FLAG_DELAY_OK;
    }

    private boolean wantsPauseInsteadOfDucking(AudioFocusInfo focusHolder) {
        return (focusHolder.getFlags() & AudioManager.AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS)
                != 0;
    }

    public void dump(PrintWriter writer, String indent) {
        writer.printf("%sInteractions: \n", indent);
        Set<AudioAttributesWrapper> audioAttributesWrapperSet =
                mHolderToIncomingAttributesInteractions.keySet();
        for (AudioAttributesWrapper holder : audioAttributesWrapperSet) {
            String holderUsageString = AudioUtils.getUsageString(holder.getAudioAttributes());
            writer.printf("%s%sHolder: %s\n", indent, indent, holderUsageString);
            for (AudioAttributesWrapper incoming : audioAttributesWrapperSet) {
                String incomingUsageString = AudioUtils.getUsageString(incoming
                        .getAudioAttributes());
                String interaction = getInteractionString(
                        mHolderToIncomingAttributesInteractions.get(holder).get(incoming));
                writer.printf("%s%s%sIncoming: %s interaction %s\n", indent, indent, indent,
                        incomingUsageString, interaction);
            }
            writer.flush();
        }
    }

    private static String getInteractionString(int interaction) {
        switch (interaction) {
            case INTERACTION_CONCURRENT:
                return "CONCURRENT";
            case INTERACTION_EXCLUSIVE:
                return "EXCLUSIVE";
            case INTERACTION_REJECT:
                return "REJECT";
            case INTERACTION_INVALID:
                // fall through
            default:
                return "INVALID";
        }
    }

    private static final class FocusEvaluation {

        private static final FocusEvaluation FOCUS_EVALUATION_FAILED =
                new FocusEvaluation(/* changedEntries= */ new ArrayList<>(/* initialCap= */ 0),
                        AUDIOFOCUS_REQUEST_FAILED);

        private final List<AudioFocusEntry> mChangedEntries;
        private final int mAudioFocusEvalResults;

        FocusEvaluation(List<AudioFocusEntry> changedEntries, int audioFocusEvalResults) {
            mChangedEntries = changedEntries;
            mAudioFocusEvalResults = audioFocusEvalResults;
        }

        @Override
        public String toString() {
            return new StringBuilder().append("{Changed Entries: ").append(mChangedEntries)
                    .append(", Results: ").append(mAudioFocusEvalResults)
                    .append(" }").toString();
        }
    }
}
