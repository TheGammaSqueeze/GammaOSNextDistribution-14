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

package android.car.oem;

import static android.car.feature.Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION;
import static android.car.feature.Flags.carAudioFadeManagerConfiguration;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.media.AudioAttributes;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Class to encapsulate the audio focus result from the OEM audio service
 *
 * @hide
 */
@SystemApi
public final class OemCarAudioFocusResult implements Parcelable {
    private final @Nullable AudioFocusEntry mAudioFocusEntry;
    private final @NonNull List<AudioFocusEntry> mNewlyLostAudioFocusEntries;
    private final @NonNull List<AudioFocusEntry> mNewlyBlockedAudioFocusEntries;
    private final @NonNull Map<AudioAttributes,
            CarAudioFadeConfiguration> mAttrsToCarAudioFadeConfig;
    private final int mAudioFocusResult;

    OemCarAudioFocusResult(
            @Nullable AudioFocusEntry audioFocusEntry,
            @NonNull List<AudioFocusEntry> newlyLostAudioFocusEntries,
            @NonNull List<AudioFocusEntry> newlyBlockedAudioFocusEntries, int audioFocusResult,
            @NonNull Map<AudioAttributes, CarAudioFadeConfiguration> attrsToCarAudioFadeConfig) {
        Preconditions.checkArgument(newlyLostAudioFocusEntries != null,
                "Newly lost focus entries can not be null");
        Preconditions.checkArgument(newlyBlockedAudioFocusEntries != null,
                "Newly blocked focus entries can not be null");
        mAudioFocusEntry = audioFocusEntry;
        mNewlyLostAudioFocusEntries = newlyLostAudioFocusEntries;
        mNewlyBlockedAudioFocusEntries = newlyBlockedAudioFocusEntries;
        mAudioFocusResult = audioFocusResult;
        mAttrsToCarAudioFadeConfig = Objects.requireNonNull(attrsToCarAudioFadeConfig,
                "Audio attributes to car audio fade configuration can not be null");
    }

    /**
     * Returns the result of the focus request
     * The result can be granted, delayed, or failed. In the case of granted the car audio stack
     * will be changed according to the entries returned in newly lost and newly blocked.
     * For delayed results the entry will be added as the current delayed request and it will be
     * re-evaluated once any of the current focus holders abandons focus. For failed request,
     * the car audio focus stack will not change and the current request will not gain focus.
     */
    public @Nullable AudioFocusEntry getAudioFocusEntry() {
        return new AudioFocusEntry.Builder(mAudioFocusEntry).build();
    }

    /**
     * Returns the entries that were previously holding focus but now have lost focus.
     *
     * <p>Note: the lost can be permanent or transient, in the case of permanent loss the entry
     * will receive permanent focus loss and it will be removed from the car audio focus stack.
     * For transient losses, the new entry will be added as a blocker but will only receive
     * transient focus loss.
     */
    public @NonNull List<AudioFocusEntry> getNewlyLostAudioFocusEntries() {
        return new ArrayList<>(mNewlyLostAudioFocusEntries);
    }

    /**
     * Returns the entries that had previously lost focus and continue to be blocked by new entry
     *
     * <p>Note: the block can be permanent or transient, in the case of permanent block the entry
     * will receive permanent focus loss and it will be removed from the car audio focus stack.
     * For transient losses, the new entry will be added as a blocker but will only receive
     * transient focus loss.
     */
    public @NonNull List<AudioFocusEntry> getNewlyBlockedAudioFocusEntries() {
        return new ArrayList<>(mNewlyBlockedAudioFocusEntries);
    }

    /**
     * Returns the focus results, must be on of {@link AudioManager.AUDIOFOCUS_GAIN},
     * {@link AudioManager.AUDIOFOCUS_LOSS}, {@link AudioManager.AUDIOFOCUS_LOSS_TRANSIENT},
     * {@link AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}
     */
    public int getAudioFocusResult() {
        return mAudioFocusResult;
    }

    /**
     * Returns the map of transient {@link CarAudioFadeConfiguration}
     *
     * @return Map of {@link android.media.AudioAttributes} to
     *     {@link CarAudioFadeConfiguration} when set through {@link Builder} or an empty array if
     *     not set.
     */
    @FlaggedApi(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION)
    @NonNull
    public Map<AudioAttributes,
            CarAudioFadeConfiguration> getAudioAttributesToCarAudioFadeConfigurationMap() {
        ensureCarAudioFadeManagerConfigIsEnabled();
        return mAttrsToCarAudioFadeConfig;
    }

    /**
     * Returns the {@link CarAudioFadeConfiguration} corresponding to the
     * {@link android.media.AudioAttributes}
     *
     * @param audioAttributes The {@link android.media.AudioAttributes} to get the car audio
     *      fade configuration
     * @return {@link CarAudioFadeConfiguration} if one is available for the
     *     {@link android.media.AudioAttributes} or {@code null} if none is assigned
     */
    @FlaggedApi(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION)
    @Nullable
    public CarAudioFadeConfiguration getCarAudioFadeConfigurationForAudioAttributes(
            @NonNull AudioAttributes audioAttributes) {
        ensureCarAudioFadeManagerConfigIsEnabled();
        Objects.requireNonNull(audioAttributes, "Audio attributes can not be null");
        return mAttrsToCarAudioFadeConfig.get(audioAttributes);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("OemCarAudioFocusResult { audioFocusEntry = ").append(mAudioFocusEntry)
                .append(", mNewlyLostAudioFocusEntries = ").append(mNewlyLostAudioFocusEntries)
                .append(", mNewlyBlockedAudioFocusEntries = ")
                .append(mNewlyBlockedAudioFocusEntries)
                .append(", mAudioFocusResult = ").append(mAudioFocusResult)
                .append(convertAttrsToCarAudioFadeConfigurationMap())
                .append(" }").toString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        byte flg = 0;
        if (mAudioFocusEntry != null) {
            flg = (byte) (flg | Builder.FOCUS_ENTRY_FIELDS_SET);
        }
        dest.writeByte(flg);
        if (mAudioFocusEntry != null) {
            mAudioFocusEntry.writeToParcel(dest, flags);
        }
        dest.writeParcelableList(mNewlyLostAudioFocusEntries, flags);
        dest.writeParcelableList(mNewlyBlockedAudioFocusEntries, flags);
        dest.writeInt(mAudioFocusResult);
        if (carAudioFadeManagerConfiguration()) {
            dest.writeMap(mAttrsToCarAudioFadeConfig);
        }
    }

    // TODO(b/260757994): Remove ApiRequirements for overridden methods
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @VisibleForTesting
    public OemCarAudioFocusResult(@NonNull Parcel in) {
        byte flg = in.readByte();
        AudioFocusEntry audioFocusEntry = (flg & Builder.FOCUS_ENTRY_FIELDS_SET) == 0
                ? null : AudioFocusEntry.CREATOR.createFromParcel(in);
        List<AudioFocusEntry> audioFocusLosers = new ArrayList<>();
        in.readParcelableList(audioFocusLosers, AudioFocusEntry.class.getClassLoader(),
                AudioFocusEntry.class);
        List<AudioFocusEntry> audioFocusBlocked = new ArrayList<>();
        in.readParcelableList(audioFocusBlocked, AudioFocusEntry.class.getClassLoader(),
                AudioFocusEntry.class);
        int audioFocusResult = in.readInt();
        ArrayMap<AudioAttributes,
                CarAudioFadeConfiguration> audioAttributesCarAudioFadeConfig = new ArrayMap<>();
        if (carAudioFadeManagerConfiguration()) {
            in.readMap(audioAttributesCarAudioFadeConfig, getClass().getClassLoader(),
                    AudioAttributes.class, CarAudioFadeConfiguration.class);
        }

        mAudioFocusEntry = audioFocusEntry;
        mNewlyLostAudioFocusEntries = audioFocusLosers;
        mNewlyBlockedAudioFocusEntries = audioFocusBlocked;
        mAudioFocusResult = audioFocusResult;
        mAttrsToCarAudioFadeConfig = audioAttributesCarAudioFadeConfig;
    }

    @NonNull
    public static final OemCarAudioFocusResult EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS =
            new OemCarAudioFocusResult(null,
                    /* newlyLostAudioFocusEntries= */ new ArrayList<>(/* initialCapacity= */ 0),
                    /* newlyBlockedAudioFocusEntries= */ new ArrayList<>(/* initialCapacity= */ 0),
                    AUDIOFOCUS_REQUEST_FAILED,
                    /* attrsToCarAudioFadeConfig= */ new ArrayMap<>(/* initialCapacity= */ 0));

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OemCarAudioFocusResult)) {
            return false;
        }

        OemCarAudioFocusResult that = (OemCarAudioFocusResult) o;

        return Objects.equals(mAudioFocusEntry, that.mAudioFocusEntry)
                && mAudioFocusResult == that.mAudioFocusResult
                && mNewlyBlockedAudioFocusEntries.equals(
                that.mNewlyBlockedAudioFocusEntries)
                && mNewlyLostAudioFocusEntries.equals(that.mNewlyLostAudioFocusEntries)
                && Objects.equals(getAttrsToCarAudioFadeConfigMap(),
                that.getAttrsToCarAudioFadeConfigMap());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAudioFocusEntry, mAudioFocusResult,
                mNewlyBlockedAudioFocusEntries, mNewlyLostAudioFocusEntries,
                getAttrsToCarAudioFadeConfigMap());
    }

    @NonNull
    public static final Parcelable.Creator<OemCarAudioFocusResult> CREATOR =
            new Parcelable.Creator<OemCarAudioFocusResult>() {
        @Override
        public OemCarAudioFocusResult[] newArray(int size) {
            return new OemCarAudioFocusResult[size];
        }

        @Override
        public OemCarAudioFocusResult createFromParcel(@NonNull Parcel in) {
            return new OemCarAudioFocusResult(in);
        }
    };

    private Map<AudioAttributes, CarAudioFadeConfiguration> getAttrsToCarAudioFadeConfigMap() {
        return carAudioFadeManagerConfiguration() ? mAttrsToCarAudioFadeConfig : null;
    }

    private String convertAttrsToCarAudioFadeConfigurationMap() {
        return carAudioFadeManagerConfiguration()
                ? ", mAttrsToCarAudioFadeConfig = " + mAttrsToCarAudioFadeConfig : "";
    }

    private static void ensureCarAudioFadeManagerConfigIsEnabled() {
        Preconditions.checkState(carAudioFadeManagerConfiguration(),
                "Car audio fade manager configuration not supported");
    }

    /**
     * A builder for {@link OemCarAudioFocusResult}
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Builder {

        private static final int FOCUS_ENTRY_FIELDS_SET = 0x1;
        private static final int NEWLY_LOSS_FIELDS_SET = 0x2;
        private static final int NEWLY_BLOCKED_FIELDS_SET = 0x4;
        private static final int FOCUS_RESULT_FIELDS_SET = 0x8;
        private static final int BUILDER_USED_FIELDS_SET = 0x10;
        private @Nullable AudioFocusEntry mAudioFocusEntry;
        private @NonNull List<AudioFocusEntry> mNewlyLostAudioFocusEntries;
        private @NonNull List<AudioFocusEntry> mNewlyBlockedAudioFocusEntries;
        private int mAudioFocusResult;
        private final @NonNull Map<AudioAttributes,
                CarAudioFadeConfiguration> mAttrsToCarAudioFadeConfig = new ArrayMap<>();

        private long mBuilderFieldsSet = 0L;

        public Builder(
                @NonNull List<AudioFocusEntry> newlyLostAudioFocusEntries,
                @NonNull List<AudioFocusEntry> newlyBlockedAudioFocusEntries,
                int audioFocusResult) {
            Preconditions.checkArgument(newlyLostAudioFocusEntries != null,
                    "Newly lost focus entries can not be null");
            Preconditions.checkArgument(newlyBlockedAudioFocusEntries != null,
                    "Newly blocked focus entries can not be null");
            mNewlyLostAudioFocusEntries = newlyLostAudioFocusEntries;
            mNewlyBlockedAudioFocusEntries = newlyBlockedAudioFocusEntries;
            mAudioFocusResult = audioFocusResult;
        }

        /** @see OemCarAudioFocusResult#getAudioFocusEntry */
        @NonNull
        public Builder setAudioFocusEntry(@NonNull AudioFocusEntry focusEntry) {
            Preconditions.checkArgument(focusEntry != null,
                    "Focus entry can not be null");
            checkNotUsed();
            mBuilderFieldsSet |= FOCUS_ENTRY_FIELDS_SET;
            mAudioFocusEntry = focusEntry;
            return this;
        }

        /** @see OemCarAudioFocusResult#getNewlyLostAudioFocusEntries */
        @NonNull
        public Builder setNewlyLostAudioFocusEntries(
                @NonNull List<AudioFocusEntry> newlyLostAudioFocusEntries) {
            Preconditions.checkArgument(newlyLostAudioFocusEntries != null,
                    "Newly lost focus entries can not be null");
            checkNotUsed();
            mBuilderFieldsSet |= NEWLY_LOSS_FIELDS_SET;
            mNewlyLostAudioFocusEntries = newlyLostAudioFocusEntries;
            return this;
        }

        /** @see #setNewlyLostAudioFocusEntries */
        @NonNull
        public Builder addNewlyLostAudioFocusEntry(@NonNull AudioFocusEntry lossEntry) {
            Preconditions.checkArgument(lossEntry != null,
                    "Newly lost focus entry can not be null");
            if (mNewlyLostAudioFocusEntries == null) {
                setNewlyLostAudioFocusEntries(new ArrayList<>());
            }
            mNewlyLostAudioFocusEntries.add(lossEntry);
            return this;
        }

        /** @see OemCarAudioFocusResult#getNewlyBlockedAudioFocusEntries */
        @NonNull
        public Builder setNewlyBlockedAudioFocusEntries(
                @NonNull List<AudioFocusEntry> newlyBlockedAudioFocusEntries) {
            Preconditions.checkArgument(newlyBlockedAudioFocusEntries != null,
                    "Newly blocked focus entries can not be null");
            checkNotUsed();
            mBuilderFieldsSet |= NEWLY_BLOCKED_FIELDS_SET;
            mNewlyBlockedAudioFocusEntries = newlyBlockedAudioFocusEntries;
            return this;
        }

        /** @see #setNewlyBlockedAudioFocusEntries */
        @NonNull
        public Builder addNewlyBlockedAudioFocusEntry(
                @NonNull AudioFocusEntry blockedEntry) {
            Preconditions.checkArgument(blockedEntry != null,
                    "Newly blocked focus entry can not be null");
            if (mNewlyBlockedAudioFocusEntries == null) {
                setNewlyBlockedAudioFocusEntries(new ArrayList<>());
            }
            mNewlyBlockedAudioFocusEntries.add(blockedEntry);
            return this;
        }

        /** @see OemCarAudioFocusResult#getAudioFocusResult */
        @NonNull
        public Builder setAudioFocusResult(int audioFocusResult) {
            mBuilderFieldsSet |= FOCUS_RESULT_FIELDS_SET;
            mAudioFocusResult = audioFocusResult;
            return this;
        }

        /** @see OemCarAudioFocusResult#getAudioAttributesToCarAudioFadeConfigurationMap() **/
        @FlaggedApi(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION)
        @NonNull
        public Builder setAudioAttributesToCarAudioFadeConfigurationMap(@NonNull
                Map<AudioAttributes, CarAudioFadeConfiguration> attrsToCarAudioFadeConfig) {
            ensureCarAudioFadeManagerConfigIsEnabled();
            Objects.requireNonNull(attrsToCarAudioFadeConfig,
                    "Audio attributes to car audio fade configuration map can not be null");
            mAttrsToCarAudioFadeConfig.clear();
            mAttrsToCarAudioFadeConfig.putAll(attrsToCarAudioFadeConfig);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        @NonNull
        public OemCarAudioFocusResult build() {
            checkNotUsed();
            mBuilderFieldsSet |= BUILDER_USED_FIELDS_SET; // Mark builder used

            OemCarAudioFocusResult o = new OemCarAudioFocusResult(
                    mAudioFocusEntry,
                    mNewlyLostAudioFocusEntries,
                    mNewlyBlockedAudioFocusEntries,
                    mAudioFocusResult,
                    mAttrsToCarAudioFadeConfig);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & BUILDER_USED_FIELDS_SET) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }
}
