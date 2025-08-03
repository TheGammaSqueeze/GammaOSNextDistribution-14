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

package android.car.hardware.property;

import static android.car.feature.Flags.FLAG_AREA_ID_CONFIG_ACCESS;
import static android.car.feature.Flags.FLAG_VARIABLE_UPDATE_RATE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.hardware.CarPropertyConfig;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;

/**
 * Represents area ID specific configuration information for a vehicle property.
 *
 * @param <T> matches the type for the {@link android.car.hardware.CarPropertyConfig}.
 */
public final class AreaIdConfig<T> implements Parcelable {
    @NonNull
    public static final Parcelable.Creator<AreaIdConfig<Object>> CREATOR = getCreator();

    private final @CarPropertyConfig.VehiclePropertyAccessType int mAccess;
    private final int mAreaId;
    @Nullable private final T mMinValue;
    @Nullable private final T mMaxValue;
    private final List<T> mSupportedEnumValues;
    private final boolean mSupportVariableUpdateRate;

    private AreaIdConfig(
            int areaId, @Nullable T minValue, @Nullable T maxValue, List<T> supportedEnumValues,
            @CarPropertyConfig.VehiclePropertyAccessType int access,
            boolean supportVariableUpdateRate) {
        mAccess = access;
        mAreaId = areaId;
        mMinValue = minValue;
        mMaxValue = maxValue;
        mSupportedEnumValues = supportedEnumValues;
        mSupportVariableUpdateRate = supportVariableUpdateRate;
    }

    @SuppressWarnings("unchecked")
    private AreaIdConfig(Parcel in) {
        mAccess = in.readInt();
        mAreaId = in.readInt();
        mMinValue = (T) in.readValue(getClass().getClassLoader());
        mMaxValue = (T) in.readValue(getClass().getClassLoader());
        mSupportedEnumValues = in.readArrayList(getClass().getClassLoader());
        mSupportVariableUpdateRate = in.readBoolean();
    }

    private static <E> Parcelable.Creator<AreaIdConfig<E>> getCreator() {
        return new Creator<AreaIdConfig<E>>() {
            @Override
            public AreaIdConfig<E> createFromParcel(Parcel source) {
                return new AreaIdConfig<>(source);
            }

            @Override
            @SuppressWarnings("unchecked")
            public AreaIdConfig<E>[] newArray(int size) {
                AreaIdConfig<E>[] areaIdConfigs = new AreaIdConfig[size];
                for (int i = 0; i < size; i++) {
                    areaIdConfigs[i] = null;
                }
                return areaIdConfigs;
            }
        };
    }

    /**
     * Return the access type of the car property at the current areaId.
     * <p>The access type could be one of the following:
     * <ul>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_NONE}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}</li>
     * </ul>
     *
     * @return the access type of the car property at the current areaId.
     */
    @FlaggedApi(FLAG_AREA_ID_CONFIG_ACCESS)
    public @CarPropertyConfig.VehiclePropertyAccessType int getAccess() {
        return mAccess;
    }

    /**
     * Returns the area ID for this configuration.
     *
     * @return area ID for this configuration.
     */
    public int getAreaId() {
        return mAreaId;
    }

    /**
     * Returns the minimum value supported for the {@link #getAreaId()}.
     *
     * @return minimum value supported for the {@link #getAreaId()}. Will return {@code null} if no
     *     minimum value supported.
     */
    @Nullable
    public T getMinValue() {
        return mMinValue;
    }

    /**
     * Returns the maximum value supported for the {@link #getAreaId()}.
     *
     * @return maximum value supported for the {@link #getAreaId()}. Will return {@code null} if no
     *     maximum value supported.
     */
    @Nullable
    public T getMaxValue() {
        return mMaxValue;
    }

    /**
     * Returns whether variable update rate is supported.
     *
     * If this returns {@code false}, variable update rate is always disabled for this area ID.
     *
     * If this returns {@code true}, variable update rate will be disabled if client calls
     * {@link Subscription.Builder#setVariableUpdateRateEnabled} with {@code false}, or enabled
     * otherwise.
     *
     * @return whether variable update rate is supported.
     */
    @FlaggedApi(FLAG_VARIABLE_UPDATE_RATE)
    public boolean isVariableUpdateRateSupported() {
        return mSupportVariableUpdateRate;
    }

    /**
     * Returns the supported enum values for the {@link #getAreaId()}. If list is empty, the
     * property does not support an enum.
     */
    @NonNull
    public List<T> getSupportedEnumValues() {
        return Collections.unmodifiableList(mSupportedEnumValues);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mAccess);
        dest.writeInt(mAreaId);
        dest.writeValue(mMinValue);
        dest.writeValue(mMaxValue);
        dest.writeList(mSupportedEnumValues);
        dest.writeBoolean(mSupportVariableUpdateRate);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AreaIdConfig{")
                .append("mAccess=").append(mAccess)
                .append("mAreaId=").append(mAreaId);
        if (mMinValue != null) {
            sb.append(", mMinValue=").append(mMinValue);
        }
        if (mMaxValue != null) {
            sb.append(", mMaxValue=").append(mMaxValue);
        }
        if (!mSupportedEnumValues.isEmpty()) {
            sb.append(", mSupportedEnumValues=").append(mSupportedEnumValues);
        }
        return sb.append("}").toString();
    }

    /**
     * @param <T> matches the type for the {@link android.car.hardware.CarPropertyConfig}.
     *
     * This is supposed to be called by CarService only. For history reason, we exposed
     * this as system API, however, client must not use this builder and should use the getXXX
     * method in {@code AreaIdConfig}.
     *
     * @hide
     */
    @SystemApi
    public static final class Builder<T> {
        private final @CarPropertyConfig.VehiclePropertyAccessType int mAccess;
        private final int mAreaId;
        private T mMinValue = null;
        private T mMaxValue = null;
        private List<T> mSupportedEnumValues = Collections.EMPTY_LIST;
        private boolean mSupportVariableUpdateRate = false;

        public Builder(int areaId) {
            mAccess = CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE;
            mAreaId = areaId;
        }

        @FlaggedApi(FLAG_AREA_ID_CONFIG_ACCESS)
        public Builder(@CarPropertyConfig.VehiclePropertyAccessType int access, int areaId) {
            mAccess = access;
            mAreaId = areaId;
        }

        /** Set the min value for the {@link AreaIdConfig}. */
        @NonNull
        public Builder<T> setMinValue(T minValue) {
            mMinValue = minValue;
            return this;
        }

        /** Set the max value for the {@link AreaIdConfig}. */
        @NonNull
        public Builder<T> setMaxValue(T maxValue) {
            mMaxValue = maxValue;
            return this;
        }

        /** Set the supported enum values for the {@link AreaIdConfig}. */
        @NonNull
        public Builder<T> setSupportedEnumValues(@NonNull List<T> supportedEnumValues) {
            mSupportedEnumValues = supportedEnumValues;
            return this;
        }

        /**
         * Sets whether variable update rate is supported.
         *
         * This is supposed to be called by CarService only.
         *
         * @hide
         */
        @NonNull
        public Builder<T> setSupportVariableUpdateRate(boolean supportVariableUpdateRate) {
            mSupportVariableUpdateRate = supportVariableUpdateRate;
            return this;
        }

        /** Builds a new {@link android.car.hardware.property.AreaIdConfig}. */
        @NonNull
        public AreaIdConfig<T> build() {
            return new AreaIdConfig<>(mAreaId, mMinValue, mMaxValue, mSupportedEnumValues, mAccess,
                    mSupportVariableUpdateRate);
        }
    }
}
