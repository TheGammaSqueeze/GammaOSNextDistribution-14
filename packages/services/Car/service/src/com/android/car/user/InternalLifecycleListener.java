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

package com.android.car.user;

import android.annotation.Nullable;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;

/**
 * Helper DTO to hold info about an internal service-level {@code UserLifecycleListener} with
 * filter.
 */
final class InternalLifecycleListener {

    public final UserLifecycleListener listener;
    public final @Nullable UserLifecycleEventFilter filter;

    InternalLifecycleListener(UserLifecycleListener listener,
            @Nullable UserLifecycleEventFilter filter) {
        this.listener = listener;
        this.filter = filter;
    }
}
