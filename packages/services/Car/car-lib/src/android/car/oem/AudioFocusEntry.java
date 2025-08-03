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

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.media.AudioFocusInfo;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Class to encapsulate the focus information of evaluation from a car oem audio focus service
 *
 * @hide
 */
@SystemApi
public final class AudioFocusEntry implements Parcelable {

    @NonNull
    private final AudioFocusInfo mAudioFocusInfo;
    private final int mAudioContextId;
    private final int mAudioVolumeGroupId;
    private final int mAudioFocusResult;

    AudioFocusEntry(
            @NonNull AudioFocusInfo audioFocusInfo,
            int audioContextId,
            int audioVolumeGroupId,
            int focusResult) {
        mAudioFocusInfo = Objects.requireNonNull(audioFocusInfo,
                "Audio focus info can not be null");
        mAudioContextId = audioContextId;
        mAudioVolumeGroupId = audioVolumeGroupId;
        mAudioFocusResult = focusResult;
    }

    /**
     * Returns the audio focus info
     */
    public @NonNull AudioFocusInfo getAudioFocusInfo() {
        return mAudioFocusInfo;
    }

    /**
     * Returns the caudio context as evaluated from the audio attributes
     */
    public int getAudioContextId() {
        return mAudioContextId;
    }

    /**
     * Returns the volume group as evaluated from the audio attributes
     */
    public int getAudioVolumeGroupId() {
        return mAudioVolumeGroupId;
    }

    /**
     * Returns the focus results, must be on of {@link AudioManager.AUDIOFOCUS_GAIN},
     * {@link AudioManager.AUDIOFOCUS_LOSS}, {@link AudioManager.AUDIOFOCUS_LOSS_TRANSIENT},
     * {@link AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}
     **/
    public int getAudioFocusResult() {
        return mAudioFocusResult;
    }

    @Override
    public String toString() {
        return new StringBuilder().append("AudioFocusEntry { audioFocusInfo = ")
                .append(getAudioFocusInfoString()).append(", audioContextId = ")
                .append(mAudioContextId).append(", audioVolumeGroupId = ")
                .append(mAudioVolumeGroupId).append(", focusResult = ")
                .append(mAudioFocusResult).append(" }").toString();
    }

    private String getAudioFocusInfoString() {
        return new StringBuilder().append("{ attributes: ").append(mAudioFocusInfo.getAttributes())
                .append(", UID : ").append(mAudioFocusInfo.getClientUid())
                .append(", client Id: ").append(mAudioFocusInfo.getClientId())
                .append(", pkg: ").append(mAudioFocusInfo.getPackageName())
                .append(", gain: ").append(mAudioFocusInfo.getGainRequest())
                .append(", loss received: ").append(mAudioFocusInfo.getLossReceived())
                .append(", flags: ").append(mAudioFocusInfo.getFlags())
                .append("}").toString();
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        mAudioFocusInfo.writeToParcel(dest, flags);
        dest.writeInt(mAudioContextId);
        dest.writeInt(mAudioVolumeGroupId);
        dest.writeInt(mAudioFocusResult);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @VisibleForTesting
    public AudioFocusEntry(@NonNull android.os.Parcel in) {
        AudioFocusInfo audioFocusInfo = AudioFocusInfo.CREATOR.createFromParcel(in);
        int audioContextId = in.readInt();
        int audioVolumeGroupId = in.readInt();
        int focusResult = in.readInt();

        mAudioFocusInfo = audioFocusInfo;
        mAudioContextId = audioContextId;
        mAudioVolumeGroupId = audioVolumeGroupId;
        mAudioFocusResult = focusResult;
    }

    @NonNull
    public static final Parcelable.Creator<AudioFocusEntry> CREATOR =
            new Parcelable.Creator<>() {
        @Override
        public AudioFocusEntry[] newArray(int size) {
            return new AudioFocusEntry[size];
        }

        @Override
        public AudioFocusEntry createFromParcel(@NonNull android.os.Parcel in) {
            return new AudioFocusEntry(in);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AudioFocusEntry)) {
            return false;
        }

        AudioFocusEntry that = (AudioFocusEntry) o;

        return mAudioContextId == that.mAudioContextId
                && mAudioFocusResult == that.mAudioFocusResult
                && mAudioVolumeGroupId == that.mAudioVolumeGroupId
                && mAudioFocusInfo.equals(that.mAudioFocusInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAudioFocusInfo.hashCode(), mAudioContextId, mAudioFocusResult,
                mAudioVolumeGroupId);
    }

    /**
     * A builder for {@link AudioFocusEntry}
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Builder {

        private @NonNull AudioFocusInfo mAudioFocusInfo;
        private int mAudioContextId;
        private int mAudioVolumeGroupId;
        private int mAudioFocusResult;

        private long mBuilderFieldsSet = 0L;

        public Builder(@NonNull AudioFocusEntry entry) {
            this(Objects.requireNonNull(entry, "Audio focus entry can not be null")
                            .mAudioFocusInfo, entry.mAudioContextId, entry.mAudioVolumeGroupId,
                    entry.mAudioFocusResult);
        }

        public Builder(
                @NonNull AudioFocusInfo audioFocusInfo,
                int audioContextId,
                int audioVolumeGroupId,
                int focusResult) {
            mAudioFocusInfo = Objects.requireNonNull(audioFocusInfo,
                    "Audio focus info can not be null");
            mAudioContextId = audioContextId;
            mAudioVolumeGroupId = audioVolumeGroupId;
            mAudioFocusResult = focusResult;
        }

        /** see {@link AudioFocusEntry#getAudioFocusInfo()} */
        public @NonNull Builder setAudioFocusInfo(@NonNull AudioFocusInfo audioFocusInfo) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mAudioFocusInfo = Objects.requireNonNull(audioFocusInfo,
                    "Audio focus info can not be null");
            return this;
        }

        /** see {@link AudioFocusEntry#getAudioContextId()} */
        public @NonNull Builder setAudioContextId(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mAudioContextId = value;
            return this;
        }

        /** see {@link AudioFocusEntry#getAudioVolumeGroupId()} */
        public @NonNull Builder setAudioVolumeGroupId(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mAudioVolumeGroupId = value;
            return this;
        }

        /** see {@link AudioFocusEntry#getAudioFocusResult()} */
        public @NonNull Builder setAudioFocusResult(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mAudioFocusResult = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull AudioFocusEntry build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x10; // Mark builder used

            AudioFocusEntry o = new AudioFocusEntry(
                    mAudioFocusInfo,
                    mAudioContextId,
                    mAudioVolumeGroupId,
                    mAudioFocusResult);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x10) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }
}
