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

package com.android.car.audio.hal;

import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.android.car.audio.CarAudioDumpProto.HalAudioFocusProto.HAL_FOCUS_REQUESTS_BY_ZONE_AND_ATTRIBUTES;
import static com.android.car.audio.CarAudioDumpProto.HalAudioFocusProto.HalFocusRequestsByZoneAndAttributes.HAL_FOCUS_REQUESTS_BY_ATTRIBUTES;
import static com.android.car.audio.CarAudioDumpProto.HalAudioFocusProto.HalFocusRequestsByZoneAndAttributes.ZONE_ID;
import static com.android.car.audio.CarHalAudioUtils.audioAttributesWrapperToMetadata;
import static com.android.car.audio.CarHalAudioUtils.metadataToAudioAttribute;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioContext;
import com.android.car.audio.CarAudioContext.AudioAttributesWrapper;
import com.android.car.audio.CarAudioDumpProto;
import com.android.car.audio.CarAudioPlaybackMonitor;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manages focus requests from the HAL on a per-zone per-usage basis
 */
public final class HalAudioFocus implements HalFocusListener {
    private static final String TAG = CarLog.tagFor(HalAudioFocus.class);

    private final AudioManager mAudioManager;
    private final AudioControlWrapper mAudioControlWrapper;
    private final CarAudioContext mCarAudioContext;
    @Nullable
    private final CarAudioPlaybackMonitor mCarAudioPlaybackMonitor;

    private final Object mLock = new Object();

    // Map of Maps. Top level keys are ZoneIds. Second level keys are audio attribute wrapper.
    // Values are HalAudioFocusRequests
    @GuardedBy("mLock")
    private final SparseArray<Map<AudioAttributesWrapper, HalAudioFocusRequest>>
            mHalFocusRequestsByZoneAndAttributes;

    public HalAudioFocus(@NonNull AudioManager audioManager,
                         @NonNull AudioControlWrapper audioControlWrapper,
                         @Nullable CarAudioPlaybackMonitor carAudioPlaybackMonitor,
                         @NonNull CarAudioContext carAudioContext, @NonNull int[] audioZoneIds) {
        mAudioManager = Objects.requireNonNull(audioManager);
        mAudioControlWrapper = Objects.requireNonNull(audioControlWrapper);
        mCarAudioContext = Objects.requireNonNull(carAudioContext);
        mCarAudioPlaybackMonitor = carAudioPlaybackMonitor;
        Objects.requireNonNull(audioZoneIds, "Audio zone ID's can not be null");

        mHalFocusRequestsByZoneAndAttributes = new SparseArray<>(audioZoneIds.length);
        for (int index = 0; index < audioZoneIds.length; index++) {
            mHalFocusRequestsByZoneAndAttributes.put(audioZoneIds[index], new ArrayMap<>());
        }
    }

    /**
     * Registers {@code IFocusListener} on {@code AudioControlWrapper} to receive HAL audio focus
     * request and abandon calls.
     */
    public void registerFocusListener() {
        mAudioControlWrapper.registerFocusListener(this);
    }

    /**
     * Unregisters {@code IFocusListener} from {@code AudioControlWrapper}.
     */
    public void unregisterFocusListener() {
        mAudioControlWrapper.unregisterFocusListener();
    }

    /**
     * See {@link HalFocusListener#requestAudioFocus(PlaybackTrackMetadata, int, int)}
     */
    public void requestAudioFocus(PlaybackTrackMetadata metadata, int zoneId, int focusGain) {
        synchronized (mLock) {
            Preconditions.checkArgument(mHalFocusRequestsByZoneAndAttributes.contains(zoneId),
                    "Invalid zoneId %d provided in requestAudioFocus", zoneId);
            AudioAttributes attributes = metadataToAudioAttribute(metadata);
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "Requesting focus gain %d with attributes %s and zoneId %d",
                        focusGain, attributes, zoneId);
            }
            AudioAttributesWrapper audioAttributesWrapper =
                    mCarAudioContext.getAudioAttributeWrapperFromAttributes(attributes);
            HalAudioFocusRequest currentRequest =
                    mHalFocusRequestsByZoneAndAttributes.get(zoneId).get(audioAttributesWrapper);
            if (currentRequest != null) {
                if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                    Slogf.d(TAG, "A request already exists for zoneId %d and attributes %s",
                            zoneId, attributes);
                }
                mAudioControlWrapper.onAudioFocusChange(metadata, zoneId,
                        currentRequest.mFocusStatus);
            } else {
                makeAudioFocusRequestLocked(audioAttributesWrapper, zoneId, focusGain);
            }
        }
    }

    /**
     * See {@link HalFocusListener#abandonAudioFocus(PlaybackTrackMetadata, int)}
     */
    public void abandonAudioFocus(PlaybackTrackMetadata metadata, int zoneId) {
        synchronized (mLock) {
            AudioAttributes attributes = metadataToAudioAttribute(metadata);
            Preconditions.checkArgument(mHalFocusRequestsByZoneAndAttributes.contains(zoneId),
                    "Invalid zoneId %d provided in abandonAudioFocus", zoneId);
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "Abandoning focus with attributes %s for zoneId %d", attributes,
                        zoneId);
            }
            abandonAudioFocusLocked(
                    mCarAudioContext.getAudioAttributeWrapperFromAttributes(attributes), zoneId);
        }
    }

    /**
     * Clear out all existing focus requests. Called when HAL dies.
     */
    public void reset() {
        Slogf.d(TAG, "Resetting HAL Audio Focus requests");
        synchronized (mLock) {
            for (int i = 0; i < mHalFocusRequestsByZoneAndAttributes.size(); i++) {
                int zoneId = mHalFocusRequestsByZoneAndAttributes.keyAt(i);
                Map<AudioAttributesWrapper, HalAudioFocusRequest>
                        requestsByAttributes = mHalFocusRequestsByZoneAndAttributes.valueAt(i);
                Set<AudioAttributesWrapper> wrapperSet =
                        new ArraySet<>(requestsByAttributes.keySet());
                for (AudioAttributesWrapper wrapper : wrapperSet) {
                    abandonAudioFocusLocked(wrapper, zoneId);
                }
            }
        }
    }

    /**
     * Returns the currently active {@link AudioAttributes}' for an audio zone
     */
    public List<AudioAttributes> getActiveAudioAttributesForZone(int audioZoneId) {
        synchronized (mLock) {
            Map<AudioAttributesWrapper, HalAudioFocusRequest> halFocusRequestsForZone =
                    mHalFocusRequestsByZoneAndAttributes.get(audioZoneId);
            List<AudioAttributes> activeAudioAttributes =
                    new ArrayList<>(halFocusRequestsForZone.size());

            for (AudioAttributesWrapper wrapper : halFocusRequestsForZone.keySet()) {
                activeAudioAttributes.add(wrapper.getAudioAttributes());
            }

            return activeAudioAttributes;
        }
    }

    /**
     * dumps the current state of the HalAudioFocus
     *
     * @param writer stream to write current state
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*HalAudioFocus*");

        writer.increaseIndent();
        writer.println("Current focus requests:");
        writer.increaseIndent();
        synchronized (mLock) {
            for (int i = 0; i < mHalFocusRequestsByZoneAndAttributes.size(); i++) {
                int zoneId = mHalFocusRequestsByZoneAndAttributes.keyAt(i);
                writer.printf("Zone %s:\n", zoneId);
                writer.increaseIndent();

                Map<AudioAttributesWrapper, HalAudioFocusRequest> requestsByAttributes =
                        mHalFocusRequestsByZoneAndAttributes.valueAt(i);
                for (HalAudioFocusRequest request : requestsByAttributes.values()) {
                    writer.printf("%s\n", request);
                }
                writer.decreaseIndent();
            }
        }
        writer.decreaseIndent();
        writer.decreaseIndent();
    }

    /**
     * dumps proto of the current state of the HalAudioFocus
     *
     * @param proto proto stream to write current state
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        long halAudioFocusToken = proto.start(CarAudioDumpProto.HAL_AUDIO_FOCUS);
        synchronized (mLock) {
            for (int i = 0; i < mHalFocusRequestsByZoneAndAttributes.size(); i++) {
                int zoneId = mHalFocusRequestsByZoneAndAttributes.keyAt(i);
                long halFocusRequestToken = proto.start(HAL_FOCUS_REQUESTS_BY_ZONE_AND_ATTRIBUTES);
                proto.write(ZONE_ID, zoneId);
                Map<AudioAttributesWrapper, HalAudioFocusRequest> requestsByAttributes =
                        mHalFocusRequestsByZoneAndAttributes.valueAt(i);
                for (HalAudioFocusRequest request : requestsByAttributes.values()) {
                    proto.write(HAL_FOCUS_REQUESTS_BY_ATTRIBUTES, request.toString());
                }
                proto.end(halFocusRequestToken);
            }
        }
        proto.end(halAudioFocusToken);
    }

    @GuardedBy("mLock")
    private void abandonAudioFocusLocked(AudioAttributesWrapper audioAttributesWrapper,
            int zoneId) {
        Map<AudioAttributesWrapper, HalAudioFocusRequest> halAudioFocusRequests =
                mHalFocusRequestsByZoneAndAttributes.get(zoneId);
        HalAudioFocusRequest currentRequest = halAudioFocusRequests.get(audioAttributesWrapper);

        if (currentRequest == null) {
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "No focus to abandon for audio attributes " + audioAttributesWrapper
                        + " and zoneId " + zoneId);
            }
            return;
        } else {
            // remove it from map
            halAudioFocusRequests.remove(audioAttributesWrapper);
        }

        int result = mAudioManager.abandonAudioFocusRequest(currentRequest.mAudioFocusRequest);
        if (result == AUDIOFOCUS_REQUEST_GRANTED) {
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "Abandoned focus for audio attributes " + audioAttributesWrapper
                        + "and zoneId " + zoneId);
            }
            PlaybackTrackMetadata metadata =
                    audioAttributesWrapperToMetadata(audioAttributesWrapper);
            mAudioControlWrapper.onAudioFocusChange(metadata, zoneId, AUDIOFOCUS_LOSS);
        } else {
            Slogf.w(TAG, "Failed to abandon focus for audio attributes " + audioAttributesWrapper
                    + " and zoneId " + zoneId);
        }
    }

    private AudioAttributes generateAudioAttributes(
            AudioAttributesWrapper audioAttributesWrapper, int zoneId) {
        AudioAttributes.Builder builder =
                new AudioAttributes.Builder(audioAttributesWrapper.getAudioAttributes());
        Bundle bundle = new Bundle();
        bundle.putInt(CarAudioManager.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID, zoneId);
        builder.addBundle(bundle);

        return builder.build();
    }

    @GuardedBy("mLock")
    private AudioFocusRequest generateFocusRequestLocked(
            AudioAttributesWrapper audioAttributesWrapper,
            int zoneId, int focusGain) {
        AudioAttributes attributes = generateAudioAttributes(audioAttributesWrapper, zoneId);
        return new AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener((int focusChange) -> {
                    onAudioFocusChange(attributes, zoneId, focusChange);
                })
                .build();
    }

    private void onAudioFocusChange(AudioAttributes attributes, int zoneId, int focusChange) {
        AudioAttributesWrapper wrapper =
                mCarAudioContext.getAudioAttributeWrapperFromAttributes(attributes);
        synchronized (mLock) {
            HalAudioFocusRequest currentRequest =
                    mHalFocusRequestsByZoneAndAttributes.get(zoneId).get(wrapper);
            if (currentRequest != null) {
                if (focusChange == AUDIOFOCUS_LOSS) {
                    mHalFocusRequestsByZoneAndAttributes.get(zoneId).remove(wrapper);
                } else {
                    currentRequest.mFocusStatus = focusChange;
                }
                PlaybackTrackMetadata metadata = audioAttributesWrapperToMetadata(wrapper);
                mAudioControlWrapper.onAudioFocusChange(metadata, zoneId, focusChange);
            }

        }
    }

    @GuardedBy("mLock")
    private void makeAudioFocusRequestLocked(
            AudioAttributesWrapper audioAttributesWrapper,
            int zoneId, int focusGain) {
        AudioFocusRequest audioFocusRequest =
                generateFocusRequestLocked(audioAttributesWrapper, zoneId, focusGain);

        int requestResult = mAudioManager.requestAudioFocus(audioFocusRequest);

        int resultingFocusGain = focusGain;

        if (requestResult == AUDIOFOCUS_REQUEST_GRANTED) {
            HalAudioFocusRequest halAudioFocusRequest =
                    new HalAudioFocusRequest(audioFocusRequest, focusGain);
            mHalFocusRequestsByZoneAndAttributes.get(zoneId)
                    .put(audioAttributesWrapper, halAudioFocusRequest);
            handleNewlyActiveHalPlayback(audioAttributesWrapper.getAudioAttributes(), zoneId);
        } else if (requestResult == AUDIOFOCUS_REQUEST_FAILED) {
            resultingFocusGain = AUDIOFOCUS_LOSS;
        } else if (requestResult == AUDIOFOCUS_REQUEST_DELAYED) {
            // Delayed audio focus is not supported from HAL audio focus
            Slogf.w(TAG, "Delayed result for request with audio attributes "
                    + audioAttributesWrapper + ", zoneId " + zoneId
                    + ", and focusGain " + focusGain);
            resultingFocusGain = AUDIOFOCUS_LOSS;
        }
        PlaybackTrackMetadata metadata = audioAttributesWrapperToMetadata(audioAttributesWrapper);
        mAudioControlWrapper.onAudioFocusChange(metadata, zoneId, resultingFocusGain);
    }

    private void handleNewlyActiveHalPlayback(AudioAttributes attributes, int zoneId) {
        if (mCarAudioPlaybackMonitor == null) {
            return;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            mCarAudioPlaybackMonitor.onActiveAudioPlaybackAttributesAdded(List.of(attributes),
                    zoneId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static final class HalAudioFocusRequest {
        final AudioFocusRequest mAudioFocusRequest;

        int mFocusStatus;

        HalAudioFocusRequest(AudioFocusRequest audioFocusRequest, int focusStatus) {
            mAudioFocusRequest = audioFocusRequest;
            mFocusStatus = focusStatus;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public String toString() {
            return new StringBuilder()
                    .append("Request: ")
                    .append("[Audio attributes: ")
                    .append(mAudioFocusRequest.getAudioAttributes())
                    .append(", Focus request: ")
                    .append(mAudioFocusRequest.getFocusGain())
                    .append("]")
                    .append(", Status: ")
                    .append(mFocusStatus)
                    .toString();
        }
    }
}
