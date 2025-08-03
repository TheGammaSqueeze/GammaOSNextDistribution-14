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

import android.content.Intent;

/** Proxy service for CarPerUserServiceImpl */
public class CarPerUserService extends ServiceProxy {
    private static final boolean DBG = false;
    private static final String TAG = CarPerUserService.class.getSimpleName();

    public CarPerUserService() {
        super(UpdatablePackageDependency.CAR_USER_PER_SERVICE_IMPL_CLASS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // keep it alive.
        return START_STICKY;
    }
}
