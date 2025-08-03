/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car;

/**
 * Declared all dependency into updatable package, mostly for class / method names.
 *
 * <p> This is for tracking all dependencies done through java reflection.
 */
public final class UpdatablePackageDependency {
    private UpdatablePackageDependency() {}

    /** {@code com.android.car.CarServiceImpl} class */
    public static final String CAR_SERVICE_IMPL_CLASS = "com.android.car.CarServiceImpl";

    /** {@code com.android.car.CarPerUserServiceImpl} class */
    public static final String CAR_USER_PER_SERVICE_IMPL_CLASS =
            "com.android.car.CarPerUserServiceImpl";

    /** {@code com.android.car.pm.CarSafetyAccessibilityServiceImpl} class */
    public static final String CAR_ACCESSIBILITY_IMPL_CLASS =
            "com.android.car.pm.CarSafetyAccessibilityServiceImpl";
}
