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
package android.car.app;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.feature.Flags;
import android.os.IBinder;

/**
 * A {@link CarManagerBase} meant to initialize {@link CarDisplayCompatContainer}
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_DISPLAY_COMPATIBILITY)
@SystemApi
public final class CarDisplayCompatManager extends CarManagerBase {

    /**
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY)
    @Nullable
    public CarDisplayCompatContainer initializeDisplayCompatContainer(
            @NonNull CarDisplayCompatContainer.Builder builder) {
        return builder.build();
    }

    /** @hide */
    public CarDisplayCompatManager(Car car, @NonNull IBinder service) {
        super(car);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
    }
}
