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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.feature.Flags;
import android.car.media.CarVolumeGroupInfo;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to encapsulate the audio focus evaluation to the OEM audio service
 *
 * @hide
 */
@SystemApi
public final class OemCarAudioFocusEvaluationRequest implements Parcelable {

    private @Nullable final AudioFocusEntry mAudioFocusRequest;
    private @NonNull final List<CarVolumeGroupInfo>  mMutedVolumeGroups;
    private @NonNull final List<AudioFocusEntry> mFocusHolders;
    private @NonNull final List<AudioFocusEntry> mFocusLosers;
    private final int mAudioZoneId;
    private @Nullable final CarAudioFeaturesInfo mCarAudioFeaturesInfo;

    /**
     * @hide
     */
    @VisibleForTesting
    public OemCarAudioFocusEvaluationRequest(Parcel in) {
        byte flg = in.readByte();
        mAudioFocusRequest = (flg & Builder.FOCUS_REQUEST_FIELDS_SET) == 0
                ? null : AudioFocusEntry.CREATOR.createFromParcel(in);
        mMutedVolumeGroups = new ArrayList<>();
        in.readParcelableList(mMutedVolumeGroups, CarVolumeGroupInfo.class.getClassLoader());
        mFocusHolders = new ArrayList<>();
        in.readParcelableList(mFocusHolders, AudioFocusEntry.class.getClassLoader());
        mFocusLosers = new ArrayList<>();
        in.readParcelableList(mFocusLosers, AudioFocusEntry.class.getClassLoader());
        if (Flags.carAudioDynamicDevices()) {
            mCarAudioFeaturesInfo = in.readParcelable(CarAudioFeaturesInfo.class.getClassLoader(),
                    CarAudioFeaturesInfo.class);
        } else {
            mCarAudioFeaturesInfo = null;
        }
        mAudioZoneId = in.readInt();
    }

    @NonNull
    public static final Creator<OemCarAudioFocusEvaluationRequest> CREATOR =
            new Creator<>() {
                @Override
                public OemCarAudioFocusEvaluationRequest createFromParcel(Parcel in) {
                    return new OemCarAudioFocusEvaluationRequest(in);
                }

                @Override
                public OemCarAudioFocusEvaluationRequest[] newArray(int size) {
                    return new OemCarAudioFocusEvaluationRequest[size];
                }
            };


    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() {
        return 0;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        byte flg = 0;
        if (mAudioFocusRequest != null) {
            flg = (byte) (flg | Builder.FOCUS_REQUEST_FIELDS_SET);
        }
        dest.writeByte(flg);
        if (mAudioFocusRequest != null) {
            mAudioFocusRequest.writeToParcel(dest, flags);
        }
        dest.writeParcelableList(mMutedVolumeGroups, flags);
        dest.writeParcelableList(mFocusHolders, flags);
        dest.writeParcelableList(mFocusLosers, flags);
        if (Flags.carAudioDynamicDevices()) {
            dest.writeParcelable(mCarAudioFeaturesInfo, flags);
        }
        dest.writeInt(mAudioZoneId);
    }

    /**
     * Returns the audio zone id for the request
     */
    public int getAudioZoneId() {
        return mAudioZoneId;
    }

    /**
     * Returns the current audio focus info to evaluate,
     * in cases where the audio focus info is null
     * the request is to re-evaluate current focus holder and losers.
     */
    public @Nullable AudioFocusEntry getAudioFocusRequest() {
        return mAudioFocusRequest;
    }

    /**
     * Returns the currently muted volume groups
     */
    public @NonNull List<CarVolumeGroupInfo> getMutedVolumeGroups() {
        return mMutedVolumeGroups;
    }

    /**
     * Returns the current focus holder
     */
    public @NonNull List<AudioFocusEntry> getFocusHolders() {
        return mFocusHolders;
    }

    /**
     * Returns the current focus losers (.i.e focus request that have transiently lost focus)
     */
    public @NonNull List<AudioFocusEntry> getFocusLosers() {
        return mFocusLosers;
    }

    /**
     * Returns the audio features that could be supported by the request,
     * see {@link CarAudioFeaturesInfo#isAudioFeatureEnabled(int)} for further info.
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    @Nullable
    public CarAudioFeaturesInfo getAudioFeaturesInfo() {
        return mCarAudioFeaturesInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OemCarAudioFocusEvaluationRequest)) {
            return false;
        }

        OemCarAudioFocusEvaluationRequest that = (OemCarAudioFocusEvaluationRequest) o;
        return safeEquals(mAudioFocusRequest, that.mAudioFocusRequest)
                && mFocusHolders.equals(that.mFocusHolders)
                && mFocusLosers.equals(that.mFocusLosers)
                && mMutedVolumeGroups.equals(that.mMutedVolumeGroups)
                && mAudioZoneId == that.mAudioZoneId
                && featuresMatches(that);
    }

    private boolean featuresMatches(OemCarAudioFocusEvaluationRequest that) {
        return !Flags.carAudioDynamicDevices()
                || Objects.equals(mCarAudioFeaturesInfo, that.mCarAudioFeaturesInfo);
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(mAudioFocusRequest, mFocusHolders, mFocusLosers, mMutedVolumeGroups,
                mAudioZoneId);
        if (Flags.carAudioDynamicDevices()) {
            hash = Objects.hash(hash, mCarAudioFeaturesInfo);
        }
        return  hash;
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public OemCarAudioFocusEvaluationRequest(
            @Nullable AudioFocusEntry audioFocusEntry,
            @NonNull List<CarVolumeGroupInfo> mutedVolumeGroups,
            @NonNull List<AudioFocusEntry> focusHolders,
            @NonNull List<AudioFocusEntry> focusLosers,
            @Nullable CarAudioFeaturesInfo carAudioFeaturesInfo,
            int audioZoneId) {
        mAudioFocusRequest = audioFocusEntry;
        Preconditions.checkArgument(mutedVolumeGroups != null,
                "Muted volume groups can not be null");
        Preconditions.checkArgument(focusHolders != null,
                "Focus holders can not be null");
        Preconditions.checkArgument(focusLosers != null,
                "Focus losers can not be null");
        mMutedVolumeGroups = mutedVolumeGroups;
        mFocusHolders = focusHolders;
        mFocusLosers = focusLosers;
        mAudioZoneId = audioZoneId;
        mCarAudioFeaturesInfo = carAudioFeaturesInfo;
    }

    @Override
    public String toString() {
        String string = "OemCarAudioFocusEvaluationRequest {audioZoneId = "
                + mAudioZoneId + ", audioFocusInfo = " + mAudioFocusRequest
                + ", mutedVolumeGroups = " + mMutedVolumeGroups
                + ", focusHolders = " + mFocusHolders
                + ", focusLosers = " + mFocusLosers;
        if (Flags.carAudioDynamicDevices()) {
            string += " carAudioFeatureInfo " + mCarAudioFeaturesInfo;
        }
        return string + " }";
    }


    /**
     * A builder for {@link OemCarAudioFocusEvaluationRequest}
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Builder {

        private static final int FOCUS_REQUEST_FIELDS_SET = 0x1;
        private static final int BUILDER_USED_FIELDS_SET = 0x20;

        private int mAudioZoneId;
        private @Nullable CarAudioFeaturesInfo mAudioFeatureInfo;
        private @Nullable AudioFocusEntry mAudioFocusRequest;
        private @NonNull List<CarVolumeGroupInfo> mMutedVolumeGroups;
        private @NonNull List<AudioFocusEntry> mFocusHolders;
        private @NonNull List<AudioFocusEntry> mFocusLosers;

        private long mBuilderFieldsSet = 0L;

        public Builder(
                @NonNull List<CarVolumeGroupInfo> mutedVolumeGroups,
                @NonNull List<AudioFocusEntry> focusHolders,
                @NonNull List<AudioFocusEntry> focusLosers,
                int audioZoneId) {
            Preconditions.checkArgument(mutedVolumeGroups != null,
                    "Muted volume groups can not be null");
            Preconditions.checkArgument(focusHolders != null,
                    " Focus holders can not be null");
            Preconditions.checkArgument(focusLosers != null,
                    "Focus losers can not be null");
            mMutedVolumeGroups = mutedVolumeGroups;
            mFocusHolders = focusHolders;
            mFocusLosers = focusLosers;
            mAudioZoneId = audioZoneId;
        }

        /**
         * set the audio zone id
         */
        public @NonNull Builder setAudioZoneId(int value) {
            checkNotUsed();
            mAudioZoneId = value;
            return this;
        }

        /**
         * Sets the audio feature which should be supported for the focus request
         *
         * @param featuresInfo Feature that should be supported
         */
        @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
        public @NonNull Builder setAudioFeaturesInfo(@NonNull CarAudioFeaturesInfo featuresInfo) {
            checkNotUsed();
            mAudioFeatureInfo = Objects.requireNonNull(featuresInfo,
                    "Car audio features can not be null");
            return this;
        }

        /**
         * Sets the current focus info to evaluate
         */
        @NonNull
        public Builder setAudioFocusRequest(@NonNull AudioFocusEntry audioFocusRequest) {
            Preconditions.checkArgument(audioFocusRequest != null,
                    "Audio focus request can not be null");
            checkNotUsed();
            mAudioFocusRequest = audioFocusRequest;
            return this;
        }

        /**
         * Sets the currently muted group volumes
         */
        @NonNull
        public Builder setMutedVolumeGroups(
                @NonNull List<CarVolumeGroupInfo> mutedVolumeGroups) {
            Preconditions.checkArgument(mutedVolumeGroups != null,
                    "Muted volume groups can not be null");
            checkNotUsed();
            mMutedVolumeGroups = mutedVolumeGroups;
            return this;
        }

        /** @see #setMutedVolumeGroups */
        public @NonNull Builder addMutedVolumeGroups(@NonNull CarVolumeGroupInfo mutedVolumeGroup) {
            Preconditions.checkArgument(mutedVolumeGroup != null,
                    "Muted volume group can not be null");
            if (mMutedVolumeGroups == null) setMutedVolumeGroups(new ArrayList<>());
            mMutedVolumeGroups.add(mutedVolumeGroup);
            return this;
        }

        /**
         * Sets the focus holders
         */
        public @NonNull Builder setFocusHolders(@NonNull List<AudioFocusEntry> focusHolders) {
            Preconditions.checkArgument(focusHolders != null,
                    "Focus holders can not be null");
            checkNotUsed();
            mFocusHolders = focusHolders;
            return this;
        }

        /** @see #setFocusHolders */
        public @NonNull Builder addFocusHolders(@NonNull AudioFocusEntry focusHolder) {
            Preconditions.checkArgument(focusHolder != null,
                    "Focus holder can not be null");
            if (mFocusHolders == null) setFocusHolders(new ArrayList<>());
            mFocusHolders.add(focusHolder);
            return this;
        }

        /**
         * Sets the focus losers
         */
        public @NonNull Builder setFocusLosers(@NonNull List<AudioFocusEntry> focusLosers) {
            Preconditions.checkArgument(focusLosers != null,
                    "Focus losers can not be null");
            checkNotUsed();
            mFocusLosers = focusLosers;
            return this;
        }

        /** @see #setFocusLosers */
        public @NonNull Builder addFocusLosers(@NonNull AudioFocusEntry focusLoser) {
            Preconditions.checkArgument(focusLoser != null,
                    "Focus loser can not be null");
            if (mFocusLosers == null) setFocusLosers(new ArrayList<>());
            mFocusLosers.add(focusLoser);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        @NonNull
        public OemCarAudioFocusEvaluationRequest build() {
            checkNotUsed();
            mBuilderFieldsSet |= BUILDER_USED_FIELDS_SET; // Mark builder used

            return new OemCarAudioFocusEvaluationRequest(mAudioFocusRequest, mMutedVolumeGroups,
                    mFocusHolders, mFocusLosers, mAudioFeatureInfo, mAudioZoneId);
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & BUILDER_USED_FIELDS_SET) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}
