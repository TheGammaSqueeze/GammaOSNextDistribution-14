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

import static android.car.feature.Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.media.FadeManagerConfiguration;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Class to encapsulate the fade configuration settings from a car oem audio service
 *
 * @hide
 */
@SystemApi
@FlaggedApi(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION)
public final class CarAudioFadeConfiguration implements Parcelable {

    private final String mName;
    private final FadeManagerConfiguration mFadeManagerConfiguration;

    CarAudioFadeConfiguration(@NonNull String name,
            @NonNull FadeManagerConfiguration fadeManagerConfiguration) {
        mName = Objects.requireNonNull(name,
                "Name for Car audio fade configuration can not be null");
        mFadeManagerConfiguration = Objects.requireNonNull(fadeManagerConfiguration,
                "Fade manager configuration can not be null");
    }

    /**
     * Returns the name of the car audio fade configuration
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the {@link android.media.FadeManagerConfiguration}
     */
    @NonNull
    public FadeManagerConfiguration getFadeManagerConfiguration() {
        return mFadeManagerConfiguration;
    }

    @Override
    public String toString() {
        return "CarAudioFadeConfiguration {name = " + mName + ", fade manager configuration = "
                + mFadeManagerConfiguration + "}";
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        dest.writeString(mName);
        mFadeManagerConfiguration.writeToParcel(dest, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @VisibleForTesting
    public CarAudioFadeConfiguration(@NonNull android.os.Parcel in) {
        mName = in.readString();
        mFadeManagerConfiguration = FadeManagerConfiguration.CREATOR.createFromParcel(in);
    }

    @NonNull
    public static final Parcelable.Creator<CarAudioFadeConfiguration> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public CarAudioFadeConfiguration[] newArray(int size) {
                    return new CarAudioFadeConfiguration[size];
                }

                @Override
                public CarAudioFadeConfiguration createFromParcel(@NonNull android.os.Parcel in) {
                    return new CarAudioFadeConfiguration(in);
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CarAudioFadeConfiguration)) {
            return false;
        }

        CarAudioFadeConfiguration that = (CarAudioFadeConfiguration) o;

        return mName.equals(that.mName)
                && mFadeManagerConfiguration.equals(that.mFadeManagerConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mFadeManagerConfiguration);
    }

    /**
     * A builder for {@link CarAudioFadeConfiguration}
     */
    @SuppressWarnings("WeakerAccess")
    @FlaggedApi(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION)
    public static final class Builder {
        private @NonNull String mName;
        private @NonNull FadeManagerConfiguration mFadeManagerConfiguration;
        private long mBuilderFieldsSet = 0L;

        public Builder(@NonNull FadeManagerConfiguration fadeManagerConfiguration) {
            mFadeManagerConfiguration = Objects.requireNonNull(fadeManagerConfiguration,
                    "Fade manager configuration can not be null");
        }

        /** @see CarAudioFadeConfiguration#getName() */
        public @NonNull Builder setName(@NonNull String name) {
            mName = Objects.requireNonNull(name, "Name can not be null");
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull CarAudioFadeConfiguration build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1; // Mark builder used

            if (mName == null || mName.isEmpty()) {
                mName = generateSimpleName();
            }
            return new CarAudioFadeConfiguration(mName, mFadeManagerConfiguration);
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x1) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }

        private String generateSimpleName() {
            return convertFadeStateToString(mFadeManagerConfiguration.getFadeState())
                    + "_hash#" + mFadeManagerConfiguration.hashCode();
        }

        private String convertFadeStateToString(int fadeState) {
            switch(fadeState) {
                case FadeManagerConfiguration.FADE_STATE_DISABLED:
                    return "FADE_STATE_DISABLED";
                case FadeManagerConfiguration.FADE_STATE_ENABLED_DEFAULT:
                    return "FADE_STATE_ENABLED_DEFAULT";
                default:
                    return "FADE_STATE_UNKNOWN";
            }
        }
    }
}
