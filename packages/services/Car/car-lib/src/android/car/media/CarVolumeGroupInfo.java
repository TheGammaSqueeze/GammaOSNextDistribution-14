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

package android.car.media;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.feature.Flags;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to encapsulate car volume group information.
 *
 * @hide
 */
@SystemApi
public final class CarVolumeGroupInfo implements Parcelable {

    private static final long IS_USED_FIELD_SET = 0x01;

    private final String mName;
    private final int mZoneId;
    private final int mId;
    private final int mVolumeGainIndex;
    private final int mMaxVolumeGainIndex;
    private final int mMinVolumeGainIndex;
    private final boolean mIsMuted;
    private final boolean mIsBlocked;
    private final boolean mIsAttenuated;
    private final List<AudioAttributes> mAudioAttributes;
    private final int mMaxActivationVolumeGainIndex;
    private final int mMinActivationVolumeGainIndex;
    private final boolean mIsMutedBySystem;

    @NonNull
    private final List<AudioDeviceAttributes> mAudioDeviceAttributes;

    private CarVolumeGroupInfo(
            String name,
            int zoneId,
            int id,
            int volumeGainIndex,
            int maxVolumeGainIndex,
            int minVolumeGainIndex,
            boolean isMuted,
            boolean isBlocked,
            boolean isAttenuated,
            List<AudioAttributes> audioAttributes,
            List<AudioDeviceAttributes> audioDeviceAttributes,
            int maxActivationVolumeGainIndex,
            int minActivationVolumeGainIndex,
            boolean isMutedBySystem) {
        mName = Objects.requireNonNull(name, "Volume info name can not be null");
        mZoneId = zoneId;
        mId = id;
        mVolumeGainIndex = volumeGainIndex;
        mMaxVolumeGainIndex = maxVolumeGainIndex;
        mMinVolumeGainIndex = minVolumeGainIndex;
        mIsMuted = isMuted;
        mIsBlocked = isBlocked;
        mIsAttenuated = isAttenuated;
        mAudioAttributes = Objects.requireNonNull(audioAttributes,
                "Audio attributes can not be null");
        mAudioDeviceAttributes = Objects.requireNonNull(audioDeviceAttributes,
                "Audio device attributes can not be null");
        mMaxActivationVolumeGainIndex = maxActivationVolumeGainIndex;
        mMinActivationVolumeGainIndex = minActivationVolumeGainIndex;
        mIsMutedBySystem = isMutedBySystem;
    }

    /**
     * Creates volume info from parcel
     *
     * @hide
     */
    @VisibleForTesting()
    public CarVolumeGroupInfo(Parcel in) {
        int zoneId = in.readInt();
        int id = in.readInt();
        String name = in.readString();
        int volumeGainIndex = in.readInt();
        int maxVolumeGainIndex = in.readInt();
        int minVolumeGainIndex = in.readInt();
        boolean isMuted = in.readBoolean();
        boolean isBlocked = in.readBoolean();
        boolean isAttenuated = in.readBoolean();
        List<AudioAttributes> audioAttributes = new ArrayList<>();
        in.readParcelableList(audioAttributes, AudioAttributes.class.getClassLoader(),
                AudioAttributes.class);
        List<AudioDeviceAttributes> audioDeviceAttributes = new ArrayList<>();
        in.readParcelableList(audioDeviceAttributes, AudioDeviceAttributes.class.getClassLoader(),
                AudioDeviceAttributes.class);
        int maxActivationVolumeGainIndex = in.readInt();
        int minActivationVolumeGainIndex = in.readInt();
        boolean isMutedBySystem = in.readBoolean();
        this.mZoneId = zoneId;
        this.mId = id;
        this.mName = name;
        this.mVolumeGainIndex = volumeGainIndex;
        this.mMaxVolumeGainIndex = maxVolumeGainIndex;
        this.mMinVolumeGainIndex = minVolumeGainIndex;
        this.mIsMuted = isMuted;
        this.mIsBlocked = isBlocked;
        this.mIsAttenuated = isAttenuated;
        this.mAudioAttributes = audioAttributes;
        this.mAudioDeviceAttributes = audioDeviceAttributes;
        this.mMaxActivationVolumeGainIndex = maxActivationVolumeGainIndex;
        this.mMinActivationVolumeGainIndex = minActivationVolumeGainIndex;
        this.mIsMutedBySystem = isMutedBySystem;
    }

    @NonNull
    public static final Creator<CarVolumeGroupInfo> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CarVolumeGroupInfo createFromParcel(@NonNull Parcel in) {
            return new CarVolumeGroupInfo(in);
        }

        @Override
        @NonNull
        public CarVolumeGroupInfo[] newArray(int size) {
            return new CarVolumeGroupInfo[size];
        }
    };

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the volume group name
     */
    public @NonNull String getName() {
        return mName;
    }

    /**
     * Returns the zone id where the volume group belongs
     */
    public int getZoneId() {
        return mZoneId;
    }

    /**
     * Returns the volume group id
     */
    public int getId() {
        return mId;
    }

    /**
     * Returns the volume group volume gain index
     */
    public int getVolumeGainIndex() {
        return mVolumeGainIndex;
    }

    /**
     * Returns the volume group max volume gain index
     */
    public int getMaxVolumeGainIndex() {
        return mMaxVolumeGainIndex;
    }

    /**
     * Returns the volume group min volume gain index
     */
    public int getMinVolumeGainIndex() {
        return mMinVolumeGainIndex;
    }

    /**
     * Returns the volume mute state, {@code true} for muted
     */
    public boolean isMuted() {
        return mIsMuted;
    }

    /**
     * Determines if the volume is muted by the system.
     *
     * @return {@code true} if the volume is muted by the system
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY)
    public boolean isMutedBySystem() {
        return mIsMutedBySystem;
    }

    /**
     * Returns the volume blocked state, {@code true} for blocked
     */
    public boolean isBlocked() {
        return mIsBlocked;
    }

    /**
     * Returns the volume attenuated state, {@code true} for attenuated
     */
    public boolean isAttenuated() {
        return mIsAttenuated;
    }

    /**
     * Returns a list of audio attributes associated with the volume group
     */
    @NonNull
    public List<AudioAttributes> getAudioAttributes() {
        return mAudioAttributes;
    }

    /**
     * Returns a list of audio device attributes associated with the volume group
     */
    @NonNull
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public List<AudioDeviceAttributes> getAudioDeviceAttributes() {
        return mAudioDeviceAttributes;
    }

    /**
     * Gets the volume group min activation volume gain index
     *
     * @return the volume group min activation volume gain index
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME)
    public int getMinActivationVolumeGainIndex() {
        return mMinActivationVolumeGainIndex;
    }

    /**
     * Gets the volume group max activation volume gain index
     *
     * @return the volume group max activation volume gain index
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME)
    public int getMaxActivationVolumeGainIndex() {
        return mMaxActivationVolumeGainIndex;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append("CarVolumeGroupId { name = ")
                .append(mName).append(", zone id = ").append(mZoneId).append(" id = ").append(mId)
                .append(", gain = ").append(mVolumeGainIndex)
                .append(", max gain = ").append(mMaxVolumeGainIndex)
                .append(", min gain = ").append(mMinVolumeGainIndex);
        if (Flags.carAudioMinMaxActivationVolume()) {
            builder.append(", max activation gain = ").append(mMaxActivationVolumeGainIndex)
                    .append(", min activation gain = ").append(mMinActivationVolumeGainIndex);
        }
        builder.append(", muted = ").append(mIsMuted);
        if (Flags.carAudioMuteAmbiguity()) {
            builder.append(", muted by system = ").append(mIsMutedBySystem);
        }
        builder.append(", blocked = ").append(mIsBlocked)
                .append(", attenuated = ").append(mIsAttenuated).append(", audio attributes = ")
                .append(mAudioAttributes);
        if (Flags.carAudioDynamicDevices()) {
            builder.append(", audio device attributes = ").append(mAudioDeviceAttributes);
        }
        return builder.append(" }").toString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mZoneId);
        dest.writeInt(mId);
        dest.writeString(mName);
        dest.writeInt(mVolumeGainIndex);
        dest.writeInt(mMaxVolumeGainIndex);
        dest.writeInt(mMinVolumeGainIndex);
        dest.writeBoolean(mIsMuted);
        dest.writeBoolean(mIsBlocked);
        dest.writeBoolean(mIsAttenuated);
        dest.writeParcelableList(mAudioAttributes, flags);
        dest.writeParcelableList(mAudioDeviceAttributes, flags);
        dest.writeInt(mMaxActivationVolumeGainIndex);
        dest.writeInt(mMinActivationVolumeGainIndex);
        dest.writeBoolean(mIsMutedBySystem);
    }

    /**
     * Determines if it is the same volume group, only comparing the group name, zone id, and
     * group id.
     *
     * @return {@code true} if the group info is the same, {@code false} otherwise
     */
    public boolean isSameVolumeGroup(@Nullable CarVolumeGroupInfo group) {
        return  group != null && mZoneId == group.mZoneId && mId == group.mId
                && mName.equals(group.mName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CarVolumeGroupInfo)) {
            return false;
        }

        CarVolumeGroupInfo that = (CarVolumeGroupInfo) o;

        return isSameVolumeGroup(that) && mVolumeGainIndex == that.mVolumeGainIndex
                && mMaxVolumeGainIndex == that.mMaxVolumeGainIndex
                && mMinVolumeGainIndex == that.mMinVolumeGainIndex
                && mIsMuted == that.mIsMuted && mIsBlocked == that.mIsBlocked
                && mIsAttenuated == that.mIsAttenuated
                && Objects.equals(mAudioAttributes, that.mAudioAttributes)
                && checkIsSameAudioAttributeDevices(that.mAudioDeviceAttributes)
                && checkIsSameActivationVolume(that.mMaxActivationVolumeGainIndex,
                that.mMinActivationVolumeGainIndex)
                && checkIsSameMutedBySystem(that.mIsMutedBySystem);
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(mName, mZoneId, mId, mVolumeGainIndex, mMaxVolumeGainIndex,
                mMinVolumeGainIndex, mIsMuted, mIsBlocked, mIsAttenuated, mAudioAttributes);
        if (Flags.carAudioDynamicDevices()) {
            hash = Objects.hash(hash, mAudioDeviceAttributes);
        }
        if (Flags.carAudioMinMaxActivationVolume()) {
            hash = Objects.hash(hash, mMaxActivationVolumeGainIndex, mMinActivationVolumeGainIndex);
        }
        if (Flags.carAudioMuteAmbiguity()) {
            hash = Objects.hash(hash, mIsMutedBySystem);
        }
        return hash;
    }

    private boolean checkIsSameAudioAttributeDevices(List<AudioDeviceAttributes> other) {
        if (Flags.carAudioDynamicDevices()) {
            return Objects.equals(mAudioDeviceAttributes, other);
        }
        return true;
    }

    private boolean checkIsSameActivationVolume(int maxActivationVolumeGainIndex,
                                          int minActivationVolumeGainIndex) {
        if (!Flags.carAudioMinMaxActivationVolume()) {
            return true;
        }
        return mMaxActivationVolumeGainIndex == maxActivationVolumeGainIndex
                && mMinActivationVolumeGainIndex == minActivationVolumeGainIndex;
    }

    private boolean checkIsSameMutedBySystem(boolean isMutedBySystem) {
        if (!Flags.carAudioMuteAmbiguity()) {
            return true;
        }
        return mIsMutedBySystem == isMutedBySystem;
    }

    /**
     * A builder for {@link CarVolumeGroupInfo}
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Builder {

        private @NonNull String mName;
        private int mZoneId;
        private int mId;
        private int mVolumeGainIndex;
        private int mMinVolumeGainIndex;
        private int mMaxVolumeGainIndex;
        private boolean mIsMuted;
        private boolean mIsBlocked;
        private boolean mIsAttenuated;
        private List<AudioAttributes> mAudioAttributes = new ArrayList<>();
        private List<AudioDeviceAttributes> mAudioDeviceAttributes = new ArrayList<>();
        private int mMinActivationVolumeGainIndex;
        private int mMaxActivationVolumeGainIndex;
        private boolean mIsMutedBySystem = false;

        private long mBuilderFieldsSet = 0L;

        public Builder(@NonNull String name, int zoneId, int id) {
            mName = Objects.requireNonNull(name, "Volume info name can not be null");
            mZoneId = zoneId;
            mId = id;
        }

        public Builder(@NonNull CarVolumeGroupInfo info) {
            Objects.requireNonNull(info, "Volume info can not be null");
            mName = info.mName;
            mZoneId = info.mZoneId;
            mId = info.mId;
            mVolumeGainIndex = info.mVolumeGainIndex;
            mMaxVolumeGainIndex = info.mMaxVolumeGainIndex;
            mMinVolumeGainIndex = info.mMinVolumeGainIndex;
            mIsMuted = info.mIsMuted;
            mIsBlocked = info.mIsBlocked;
            mIsAttenuated = info.mIsAttenuated;
            mAudioAttributes = info.mAudioAttributes;
            mAudioDeviceAttributes = info.mAudioDeviceAttributes;
            mMaxActivationVolumeGainIndex = info.mMaxActivationVolumeGainIndex;
            mMinActivationVolumeGainIndex = info.mMinActivationVolumeGainIndex;
            mIsMutedBySystem = info.mIsMutedBySystem;
        }

        /**
         * Sets the volume group volume gain index
         */
        public @NonNull Builder setVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group max volume gain index
         */
        public @NonNull Builder setMaxVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mMaxVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group min volume gain index
         */
        public @NonNull Builder setMinVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mMinVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group muted state,  {@code true} for muted
         */
        public @NonNull Builder setMuted(boolean muted) {
            checkNotUsed();
            mIsMuted = muted;
            return this;
        }

        /**
         * Sets the volume group blocked state, {@code true} for blocked
         */
        public @NonNull Builder setBlocked(boolean blocked) {
            checkNotUsed();
            mIsBlocked = blocked;
            return this;
        }

        /**
         * Sets the volume group attenuated state, {@code true} for attenuated
         */
        public @NonNull Builder setAttenuated(boolean attenuated) {
            checkNotUsed();
            mIsAttenuated = attenuated;
            return this;
        }

        /**
         * Sets the list of audio attributes associated with the volume group
         */
        @NonNull
        public Builder setAudioAttributes(@NonNull List<AudioAttributes> audioAttributes) {
            checkNotUsed();
            mAudioAttributes = Objects.requireNonNull(audioAttributes,
                    "Audio Attributes can not be null");
            return this;
        }

        /**
         * Sets the list of audio device attributes associated with the volume group
         */
        @NonNull
        @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
        public Builder setAudioDeviceAttributes(@NonNull List<AudioDeviceAttributes>
                                                                audioDeviceAttributes) {
            checkNotUsed();
            mAudioDeviceAttributes = Objects.requireNonNull(audioDeviceAttributes,
                    "Audio Device Attributes can not be null");
            return this;
        }

        /**
         * Sets the volume group min activation volume gain index
         */
        @FlaggedApi(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME)
        public @NonNull Builder setMinActivationVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mMinActivationVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group max activation volume gain index
         */
        @FlaggedApi(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME)
        public @NonNull Builder setMaxActivationVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mMaxActivationVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group muted by system state, {@code true} for system muted
         *
         * @hide
         */
        public @NonNull Builder setMutedBySystem(boolean isMutedBySystem) {
            checkNotUsed();
            mIsMutedBySystem = isMutedBySystem;
            return this;
        }

        /**
         * Builds the instance.
         *
         * @throws IllegalArgumentException if min volume gain index is larger than max volume
         * gain index, or if the volume gain index is outside the range of max and min volume
         * gain index.
         *
         * @throws IllegalStateException if the constructor is re-used
         */
        @NonNull
        public CarVolumeGroupInfo build() {
            checkNotUsed();
            validateGainIndexRange();

            mBuilderFieldsSet |= IS_USED_FIELD_SET; // Mark builder used

            return new CarVolumeGroupInfo(mName, mZoneId, mId, mVolumeGainIndex,
                    mMaxVolumeGainIndex, mMinVolumeGainIndex, mIsMuted, mIsBlocked, mIsAttenuated,
                    mAudioAttributes, mAudioDeviceAttributes, mMaxActivationVolumeGainIndex,
                    mMinActivationVolumeGainIndex, mIsMutedBySystem);
        }

        private void validateGainIndexRange() {
            Preconditions.checkArgument(mMinVolumeGainIndex < mMaxVolumeGainIndex,
                    "Min volume gain index %d must be smaller than max volume gain index %d",
                    mMinVolumeGainIndex, mMaxVolumeGainIndex);

            Preconditions.checkArgumentInRange(mVolumeGainIndex, mMinVolumeGainIndex,
                    mMaxVolumeGainIndex, "Volume gain index");

            if (Flags.carAudioMinMaxActivationVolume()) {
                Preconditions.checkArgumentInRange(mMinActivationVolumeGainIndex,
                        mMinVolumeGainIndex, mMaxVolumeGainIndex,
                        "Min activation volume gain index");

                Preconditions.checkArgumentInRange(mMaxActivationVolumeGainIndex,
                        mMinVolumeGainIndex, mMaxVolumeGainIndex,
                        "Max activation volume gain index");

                Preconditions.checkArgument(mMinActivationVolumeGainIndex
                                < mMaxActivationVolumeGainIndex, "Min activation volume gain index"
                                + " %d must be smaller than max activation volume gain index %d",
                        mMinActivationVolumeGainIndex, mMaxActivationVolumeGainIndex);
            }
        }

        private void checkNotUsed() throws IllegalStateException {
            if ((mBuilderFieldsSet & IS_USED_FIELD_SET) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }
}
