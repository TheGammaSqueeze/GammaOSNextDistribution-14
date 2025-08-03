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

package com.android.car.internal.property;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.hardware.CarPropertyConfig;
import android.os.Parcel;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public final class CarPropertyConfigList extends LargeParcelableList<CarPropertyConfig> {

    private CarPropertyConfigList(Parcel in) {
        super(in);
    }

    public CarPropertyConfigList(List<CarPropertyConfig> carPropertyConfigs) {
        super(carPropertyConfigs);
    }

    public List<CarPropertyConfig> getConfigs() {
        return super.getList();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    @Override
    public void serialize(@NonNull Parcel dest, int flags) {
        super.serialize(dest, flags);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    @Override
    public void serializeNullPayload(@NonNull Parcel dest) {
        super.serializeNullPayload(dest);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    @Override
    public void deserialize(@NonNull Parcel src) {
        super.deserialize(src);
    }

    @Override
    protected Creator<CarPropertyConfig> getCreator() {
        return CarPropertyConfig.CREATOR;
    }

    public static final @NonNull Creator<CarPropertyConfigList> CREATOR = new Creator<>() {
        @Override
        public CarPropertyConfigList createFromParcel(@Nullable Parcel in) {
            if (in == null) {
                return new CarPropertyConfigList(new ArrayList<>());
            }
            return new CarPropertyConfigList(in);
        }

        @Override
        public CarPropertyConfigList[] newArray(int size) {
            return new CarPropertyConfigList[size];
        }
    };
}
