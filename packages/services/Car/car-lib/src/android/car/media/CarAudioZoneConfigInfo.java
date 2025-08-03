/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static java.util.Collections.EMPTY_LIST;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.feature.Flags;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArraySet;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Class to encapsulate car audio zone configuration information.
 *
 * @hide
 */
@SystemApi
public final class CarAudioZoneConfigInfo implements Parcelable {

    private final String mName;
    private final int mZoneId;
    private final int mConfigId;
    private final boolean mIsConfigActive;
    private final boolean mIsConfigSelected;
    private final boolean mIsDefault;
    private final List<CarVolumeGroupInfo> mConfigVolumeGroups;

    /**
     * Constructor of car audio zone configuration info
     *
     * @param name Name for car audio zone configuration info
     * @param zoneId Id of car audio zone
     * @param configId Id of car audio zone configuration info
     *
     * @hide
     */
    public CarAudioZoneConfigInfo(String name, int zoneId, int configId) {
        this(name, EMPTY_LIST, zoneId, configId, /* isActive= */ true, /* isSelected= */ false,
                /* isDefault= */ false);
    }

    /**
     * Constructor of car audio zone configuration info
     *
     * @param name       Name for car audio zone configuration info
     * @param groups     Volume groups for the audio zone configuration
     * @param zoneId     Id of car audio zone
     * @param configId   Id of car audio zone configuration info
     * @param isActive   Active status of the audio configuration
     * @param isSelected Selected status of the audio configuration
     * @param isDefault  Determines if the audio configuration is default
     *
     * @hide
     */
    public CarAudioZoneConfigInfo(String name, List<CarVolumeGroupInfo> groups, int zoneId,
            int configId, boolean isActive, boolean isSelected, boolean isDefault) {
        mName = Objects.requireNonNull(name, "Zone configuration name can not be null");
        mConfigVolumeGroups = Objects.requireNonNull(groups,
                "Zone configuration volume groups can not be null");
        mZoneId = zoneId;
        mConfigId = configId;
        mIsConfigActive = isActive;
        mIsConfigSelected = isSelected;
        mIsDefault = isDefault;
    }

    /**
     * Creates zone configuration info from parcel
     *
     * @hide
     */
    @VisibleForTesting
    public CarAudioZoneConfigInfo(Parcel in) {
        mName = in.readString();
        mZoneId = in.readInt();
        mConfigId = in.readInt();
        mIsConfigActive = in.readBoolean();
        mIsConfigSelected = in.readBoolean();
        mIsDefault = in.readBoolean();
        List<CarVolumeGroupInfo> volumeGroups = new ArrayList<>();
        in.readParcelableList(volumeGroups, CarVolumeGroupInfo.class.getClassLoader(),
                CarVolumeGroupInfo.class);
        mConfigVolumeGroups = volumeGroups;
    }

    @NonNull
    public static final Creator<CarAudioZoneConfigInfo> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CarAudioZoneConfigInfo createFromParcel(@NonNull Parcel in) {
            return new CarAudioZoneConfigInfo(in);
        }

        @Override
        @NonNull
        public CarAudioZoneConfigInfo[] newArray(int size) {
            return new CarAudioZoneConfigInfo[size];
        }
    };

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the car audio zone configuration name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the car audio zone id
     */
    public int getZoneId() {
        return mZoneId;
    }

    /**
     * Returns the car audio zone configuration id
     */
    public int getConfigId() {
        return mConfigId;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("CarAudioZoneConfigInfo { name = ").append(mName)
                .append(", zone id = ").append(mZoneId)
                .append(", config id = ").append(mConfigId);

        if (Flags.carAudioDynamicDevices()) {
            builder.append(", is active = ").append(mIsConfigActive)
                    .append(", is selected = ").append(mIsConfigSelected)
                    .append(", is default = ").append(mIsDefault)
                    .append(", volume groups = ").append(mConfigVolumeGroups);
        }

        builder.append(" }");
        return builder.toString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mZoneId);
        dest.writeInt(mConfigId);
        dest.writeBoolean(mIsConfigActive);
        dest.writeBoolean(mIsConfigSelected);
        dest.writeBoolean(mIsDefault);
        dest.writeParcelableList(mConfigVolumeGroups, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CarAudioZoneConfigInfo)) {
            return false;
        }

        CarAudioZoneConfigInfo that = (CarAudioZoneConfigInfo) o;
        if (Flags.carAudioDynamicDevices()) {
            return hasSameConfigInfoInternal(that) && mIsConfigActive == that.mIsConfigActive
                    && mIsConfigSelected == that.mIsConfigSelected && mIsDefault == that.mIsDefault
                    && hasSameVolumeGroup(that.mConfigVolumeGroups);
        }

        return hasSameConfigInfoInternal(that);
    }

    private boolean hasSameVolumeGroup(List<CarVolumeGroupInfo> carVolumeGroupInfos) {
        if (mConfigVolumeGroups.size() != carVolumeGroupInfos.size()) {
            return false;
        }
        Set<CarVolumeGroupInfo> groups = new ArraySet<>(carVolumeGroupInfos);
        for (int c = 0; c < mConfigVolumeGroups.size(); c++) {
            if (groups.contains(mConfigVolumeGroups.get(c))) {
                groups.remove(mConfigVolumeGroups.get(c));
                continue;
            }
            return false;
        }
        return groups.isEmpty();
    }

    /**
     * Determines if the configuration has the same name, zone id, and config id
     *
     * @return {@code true} if the name, zone id, config id all match, {@code false} otherwise.
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public boolean hasSameConfigInfo(@NonNull CarAudioZoneConfigInfo info) {
        return hasSameConfigInfoInternal(Objects.requireNonNull(info,
                "Car audio zone info can not be null"));
    }

    @Override
    public int hashCode() {
        if (Flags.carAudioDynamicDevices()) {
            return Objects.hash(mName, mZoneId, mConfigId, mIsConfigActive, mIsConfigSelected,
                    mIsDefault, mConfigVolumeGroups);
        }
        return Objects.hash(mName, mZoneId, mConfigId);
    }

    /**
     * Determines if the configuration is active.
     *
     * <p>A configuration will be consider active if all the audio devices in the configuration
     * are currently active, including those device which are dynamic
     * (e.g. Bluetooth or wired headset).
     *
     * @return {@code true} if the configuration is active and can be selected for audio routing,
     * {@code false} otherwise.
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public boolean isActive() {
        return mIsConfigActive;
    }

    /**
     * Determines if the configuration is selected.
     *
     * @return if the configuration is currently selected for routing either by default or through
     * audio configuration selection via the {@link CarAudioManager#switchAudioZoneToConfig} API,
     * {@code true} if the configuration is currently selected, {@code false} otherwise.
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public boolean isSelected() {
        return mIsConfigSelected;
    }

    /**
     * Determines if the configuration is the default configuration.
     *
     * <p>The default configuration is used by the audio system to automatically route audio upon
     * other configurations becoming inactive. The default configuration is also used for routing
     * upon car audio service initialization. To switch to a non default configuration the
     * {@link CarAudioManager#switchAudioZoneToConfig(CarAudioZoneConfigInfo, Executor,
     * SwitchAudioZoneConfigCallback)} API must be called with the desired configuration.
     *
     * <p><b>Note</b> Each zone only has one default configuration.
     *
     * @return {@code true} if the configuration is the default configuration,
     * {@code false} otherwise.
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public boolean isDefault() {
        return mIsDefault;
    }

    /**
     * Gets all audio volume group infos that belong to the audio configuration
     *
     * <p>This can be query to determine what audio device attributes are available to the volume
     * group.
     *
     * <p><b>Note</b> This information should not be used for managing volume groups at run time,
     * as the currently selected configuration may be different. Instead,
     * {@link CarAudioManager#getAudioZoneConfigInfos} should be queried for the currently
     * selected configuration for the audio zone.
     *
     * @return list of volume groups which belong to the audio configuration.
     */
    @FlaggedApi(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    @NonNull
    public List<CarVolumeGroupInfo> getConfigVolumeGroups() {
        return List.copyOf(mConfigVolumeGroups);
    }

    private boolean hasSameConfigInfoInternal(CarAudioZoneConfigInfo info) {
        return info == null ? false : (mName.equals(info.mName) && mZoneId == info.mZoneId
                && mConfigId == info.mConfigId);
    }

    /**
     * A builder for {@link CarAudioZoneConfigInfo}
     *
     * @hide
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Builder {

        private static final long IS_USED_FIELD_SET = 0x01;

        private final String mName;
        private final int mZoneId;
        private final int mConfigId;
        private boolean mIsConfigActive;
        private boolean mIsConfigSelected;
        private boolean mIsDefault;
        private List<CarVolumeGroupInfo> mConfigVolumeGroups = new ArrayList<>();

        private long mBuilderFieldsSet;

        public Builder(@NonNull String name, int zoneId, int configId) {
            mName = name;
            mZoneId = zoneId;
            mConfigId = configId;
        }

        public Builder(CarAudioZoneConfigInfo info) {
            this(info.mName, info.mZoneId, info.mConfigId);
            mIsConfigActive = info.mIsConfigActive;
            mIsConfigSelected = info.mIsConfigSelected;
            mIsDefault = info.mIsDefault;
            mConfigVolumeGroups.addAll(info.mConfigVolumeGroups);
        }

        /**
         * Sets the configurations volume groups
         *
         * @param configVolumeGroups volume groups to sent
         */
        public Builder setConfigVolumeGroups(List<CarVolumeGroupInfo> configVolumeGroups) {
            mConfigVolumeGroups = Objects.requireNonNull(configVolumeGroups,
                    "Config volume groups can not be null");
            return this;
        }

        /**
         * Sets whether the configuration is active
         *
         * @param isActive active state of the configuration, {@code true} for active,
         * {@code false} otherwise.
         */
        public Builder setIsActive(boolean isActive) {
            mIsConfigActive = isActive;
            return this;
        }

        /**
         * Sets whether the configuration is currently selected
         *
         * @param isSelected selected state of the configuration, {@code true} for selected,
         * {@code false} otherwise.
         */
        public Builder setIsSelected(boolean isSelected) {
            mIsConfigSelected = isSelected;
            return this;
        }

        /**
         * Sets whether the configuration is the default configuration
         *
         * @param isDefault default status of the configuration, {@code true} for default,
         * {@code false} otherwise.
         */
        public Builder setIsDefault(boolean isDefault) {
            mIsDefault = isDefault;
            return this;
        }

        /**
         * Builds the instance
         *
         * @return the constructed volume group info
         */
        public CarAudioZoneConfigInfo build() throws IllegalStateException {
            checkNotUsed();
            mBuilderFieldsSet |= IS_USED_FIELD_SET;
            return new CarAudioZoneConfigInfo(mName, mConfigVolumeGroups, mZoneId, mConfigId,
                    mIsConfigActive, mIsConfigSelected, mIsDefault);
        }

        private void checkNotUsed() throws IllegalStateException {
            if ((mBuilderFieldsSet & IS_USED_FIELD_SET) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }
}
