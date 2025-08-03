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

import com.android.internal.os.BinderInternal;

/** Proxy service for CarServciceImpl */
public class CarService extends ServiceProxy {

    // Binder threads are set to 31. system_server is also using 31.
    // check sMaxBinderThreads in SystemServer.java
    private  static final int MAX_BINDER_THREADS = 31;

    public CarService() {
        super(UpdatablePackageDependency.CAR_SERVICE_IMPL_CLASS);
        // Increase the number of binder threads in car service
        BinderInternal.setMaxThreads(MAX_BINDER_THREADS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // keep it alive.
        return START_STICKY;
    }
}
