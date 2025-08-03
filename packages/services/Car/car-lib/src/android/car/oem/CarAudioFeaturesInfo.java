/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.feature.Flags;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Class to encapsulate the features available for the car audio system and should be considered
 * by the OEM car audio service.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
public final class CarAudioFeaturesInfo implements Parcelable {

    /**
     * Used to indicate that no feature is supported by the audio system
     *
     * <p><b>Note</b> Only use internally to create a feature object with no features available.
     *
     * @hide
     */
    public static final int AUDIO_FEATURE_NO_FEATURE = 0x0;

    /**
     * Can be used to determined if the audio focus request for playback on dynamic devices should
     * be treated independently from the rest of the car audio zone. If the feature is enabled,
     * focus requests should only interact if the focus requests are for the same zone and
     * targeting playback will be routed to the same device. Otherwise, the focus requests should
     * interact only if the requests are for the same audio zone.
     */
    public static final int AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS = 0x1 << 0;

    /**
     * Can be used to determine if fade manager configurations are supported. If the feature is
     * available, transient fade manager configurations can be provided as part of the focus request
     * result. These shall be used to fade players whose focus state changed as part of the focus
     * evaluation. If the feature is not available, any provided fade manager configurations will
     * be ignored.
     */
    public static final int AUDIO_FEATURE_FADE_MANAGER_CONFIGS = 0x1 << 1;

    /** @hide */
    @IntDef(flag = true, prefix = "AUDIO_FEATURE", value = {
            AUDIO_FEATURE_NO_FEATURE,
            AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS,
            AUDIO_FEATURE_FADE_MANAGER_CONFIGS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AudioFeature {}

    private final int mCarAudioFeatures;

    CarAudioFeaturesInfo(int carAudioFeatures) {
        mCarAudioFeatures = checkFeatures(carAudioFeatures);
    }

    /**
     * Determines if a particular audio feature is enabled and should be supported
     *
     * @param feature Feature to query, can be {@link #AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS}
     *                or {@link #AUDIO_FEATURE_FADE_MANAGER_CONFIGS}
     * @return {@code true} if the feature is enabled, {@code false} otherwise
     */
    public boolean isAudioFeatureEnabled(@AudioFeature int feature) {
        return (mCarAudioFeatures & feature) == feature;
    }

    @Override
    public String toString() {
        return "CarAudioFeaturesInfo { features = " +  featuresToString(mCarAudioFeatures) + " }";
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        dest.writeInt(mCarAudioFeatures);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @VisibleForTesting
    public CarAudioFeaturesInfo(@NonNull android.os.Parcel in) {
        mCarAudioFeatures = in.readInt();
    }

    @NonNull
    public static final Creator<CarAudioFeaturesInfo> CREATOR = new Creator<>() {
        @Override
        public CarAudioFeaturesInfo[] newArray(int size) {
            return new CarAudioFeaturesInfo[size];
        }

        @Override
        public CarAudioFeaturesInfo createFromParcel(@NonNull android.os.Parcel in) {
            return new CarAudioFeaturesInfo(in);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CarAudioFeaturesInfo)) {
            return false;
        }

        CarAudioFeaturesInfo that = (CarAudioFeaturesInfo) o;
        return mCarAudioFeatures == that.mCarAudioFeatures;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCarAudioFeatures);
    }

    private static String featuresToString(int features) {
        if (features == AUDIO_FEATURE_NO_FEATURE) {
            return "NONE";
        }
        StringBuilder builder = new StringBuilder();
        if (checkFeature(features, AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)) {
            builder.append("ISOLATED_DEVICE_FOCUS");
        }
        if (checkFeature(features, AUDIO_FEATURE_FADE_MANAGER_CONFIGS)) {
            builder.append(builder.isEmpty() ? "" : "|");
            builder.append("FADE_MANAGER_CONFIGS");
        }
        return builder.toString();
    }

    private static int checkFeatures(int carAudioFeatures) {
        if (carAudioFeatures == AUDIO_FEATURE_NO_FEATURE) {
            return carAudioFeatures;
        }
        int tempFeatures = carAudioFeatures;
        tempFeatures = tempFeatures & ~AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS;
        tempFeatures = tempFeatures & ~AUDIO_FEATURE_FADE_MANAGER_CONFIGS;
        if (tempFeatures != 0) {
            throw new IllegalArgumentException("Car audio features " + tempFeatures
                    + " are invalid");
        }
        return carAudioFeatures;
    }

    private static int checkFeature(int feature) {
        if (feature == AUDIO_FEATURE_NO_FEATURE) {
            return feature;
        }
        if (checkFeature(feature, AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)) {
            return feature;
        }
        if (checkFeature(feature, AUDIO_FEATURE_FADE_MANAGER_CONFIGS)) {
            return feature;
        }
        throw new IllegalArgumentException("Car audio feature " + feature + " is invalid");
    }

    private static boolean checkFeature(int feature, int checkFeature) {
        return (feature & checkFeature) == checkFeature;
    }

    /**
     * A builder for {@link CarAudioFeaturesInfo}
     *
     * @hide
     */
    public static final class Builder {

        private boolean mBuilderUsed = false;
        private int mAudioFeatures;

        public Builder(@NonNull CarAudioFeaturesInfo entry) {
            this(entry.mCarAudioFeatures);
        }

        public Builder(int audioFeatures) {
            mAudioFeatures = checkFeatures(audioFeatures);
        }

        /**
         * Adds an audio feature to the audio features info
         *
         * @param feature Feature to append, can be {@link #AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS}
         *                or {@link #AUDIO_FEATURE_FADE_MANAGER_CONFIGS}
         * @throws IllegalArgumentException if the feature parameter is not a supported feature
         * @throws IllegalStateException if the builder is re-used after calling {@link #build()}
         */
        public @NonNull Builder addAudioFeature(int feature) {
            checkNotUsed();
            mAudioFeatures |= checkFeature(feature);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull CarAudioFeaturesInfo build() {
            checkNotUsed();
            mBuilderUsed = true;
            return new CarAudioFeaturesInfo(mAudioFeatures);
        }

        private void checkNotUsed() {
            if (mBuilderUsed) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }
}
